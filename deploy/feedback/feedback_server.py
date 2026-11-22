"""Momentum's feedback service: takes a message the app posts and opens it as a GitHub issue.

The app has no account and sends no name, so the issue is opened by Momentum's own GitHub App
(momentum-feedback[bot]) with an installation token minted from the App's private key. Only the
website's Caddy reaches this service, at `/api/feedback`; it passes the client's address on in
`X-Forwarded-For`, which is all the limits below count by. Nothing is stored: a message lives
in memory only until GitHub has it.

Configuration, from the environment:
    GITHUB_APP_ID            the App's id
    GITHUB_INSTALLATION_ID   its installation on the repository
    GITHUB_APP_KEY_FILE      the App's private key (PEM)
    GITHUB_REPOSITORY        owner/name of the repository issues go to
"""

import json
import os
import threading
import time
import urllib.error
import urllib.request
from collections import defaultdict, deque
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import jwt

KINDS = {"problem": "Problem", "idea": "Idea", "other": "Something else"}
MAX_TEXT = 4000  # FeedbackMaxLength in the app
MAX_FACTS = 300
MAX_BODY = 16_000

# One person may send a handful an hour; the whole service a day's worth of a busy test.
PER_CLIENT_PER_HOUR = 5
TOTAL_PER_DAY = 200

# A zero-width space after "@" and "#" keeps a message from pinging GitHub users or linking issues.
NEUTRAL = {"@": "@​", "#": "#​"}


class Refused(Exception):
    def __init__(self, status, reason):
        super().__init__(reason)
        self.status = status


def neutral(text):
    return "".join(NEUTRAL.get(character, character) for character in text)


def parse_message(raw):
    """The message from the request body, or Refused(400) when it is not one the app sends."""
    try:
        message = json.loads(raw)
    except (ValueError, UnicodeDecodeError):
        raise Refused(400, "not JSON")
    if not isinstance(message, dict):
        raise Refused(400, "not an object")
    kind, text, facts, language = (message.get(key) for key in ("kind", "text", "facts", "language"))
    if kind not in KINDS:
        raise Refused(400, "unknown kind")
    if not isinstance(text, str) or not text.strip() or len(text) > MAX_TEXT:
        raise Refused(400, "bad text")
    if facts is not None and (not isinstance(facts, str) or len(facts) > MAX_FACTS):
        raise Refused(400, "bad facts")
    if not isinstance(language, str) or len(language) > 35:
        raise Refused(400, "bad language")
    return {"kind": kind, "text": text.strip(), "facts": facts, "language": language}


def issue_for(message):
    """Title, body and labels of the issue a message becomes."""
    first_line = message["text"].splitlines()[0].strip()
    title = first_line if len(first_line) <= 70 else first_line[:69].rstrip() + "…"
    parts = [neutral(message["text"]), "", "---", f"Sent from the app · {KINDS[message['kind']]} · {message['language']}"]
    if message["facts"]:
        parts += ["", "```", message["facts"], "```"]
    return {
        "title": f"{KINDS[message['kind']]}: {neutral(title)}",
        "body": "\n".join(parts),
        "labels": ["from the app", message["kind"]],
    }


class Limits:
    """Sliding-window counts: per client address an hour, and for everyone a day."""

    def __init__(self, clock=time.monotonic):
        self.clock = clock
        self.lock = threading.Lock()
        self.clients = defaultdict(deque)
        self.everyone = deque()

    def take(self, client):
        now = self.clock()
        with self.lock:
            sent = self.clients[client]
            while sent and now - sent[0] > 3600:
                sent.popleft()
            while self.everyone and now - self.everyone[0] > 86_400:
                self.everyone.popleft()
            if len(sent) >= PER_CLIENT_PER_HOUR or len(self.everyone) >= TOTAL_PER_DAY:
                raise Refused(429, "too many")
            sent.append(now)
            self.everyone.append(now)


class GitHub:
    """Opens issues as the App, minting a fresh installation token when the last has run out."""

    def __init__(self, app_id, installation_id, key, repository):
        self.app_id, self.installation_id, self.key, self.repository = app_id, installation_id, key, repository
        self.token, self.expires = None, 0
        self.lock = threading.Lock()

    def _request(self, method, url, token, scheme, payload=None):
        request = urllib.request.Request(
            url,
            method=method,
            data=None if payload is None else json.dumps(payload).encode(),
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"{scheme} {token}",
                "User-Agent": "momentum-feedback",
                "X-GitHub-Api-Version": "2022-11-28",
            },
        )
        with urllib.request.urlopen(request, timeout=15) as response:
            return json.load(response)

    def _installation_token(self):
        with self.lock:
            if self.token and time.time() < self.expires - 300:
                return self.token
            now = int(time.time())
            app_token = jwt.encode({"iat": now - 60, "exp": now + 540, "iss": self.app_id}, self.key, algorithm="RS256")
            answer = self._request("POST", f"https://api.github.com/app/installations/{self.installation_id}/access_tokens", app_token, "Bearer")
            self.token, self.expires = answer["token"], now + 3600
            return self.token

    def open_issue(self, issue):
        token = self._installation_token()
        return self._request("POST", f"https://api.github.com/repos/{self.repository}/issues", token, "token", issue)["html_url"]


def handler_for(github, limits):
    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            try:
                if self.path != "/api/feedback":
                    raise Refused(404, "no such path")
                length = int(self.headers.get("Content-Length") or 0)
                if length <= 0 or length > MAX_BODY:
                    raise Refused(413, "too large")
                message = parse_message(self.rfile.read(length))
                client = (self.headers.get("X-Forwarded-For") or self.client_address[0]).split(",")[0].strip()
                limits.take(client)
                try:
                    github.open_issue(issue_for(message))
                except (urllib.error.URLError, KeyError, ValueError) as error:
                    self.log_message("GitHub refused: %s", error)
                    raise Refused(502, "GitHub")
                self._answer(201)
            except Refused as refused:
                self._answer(refused.status)

        def _answer(self, status):
            self.send_response(status)
            self.send_header("Content-Length", "0")
            self.end_headers()

        def log_message(self, format, *args):
            # A status and GitHub's refusals only: never a message, a path or an address in the log.
            if format.startswith("GitHub"):
                super().log_message(format, *args)
            elif format.startswith('"%s"') and len(args) >= 2:
                super().log_message("%s %s", self.command, args[1])

        def address_string(self):
            return "-"

    return Handler


def main():
    with open(os.environ["GITHUB_APP_KEY_FILE"]) as key_file:
        key = key_file.read()
    github = GitHub(os.environ["GITHUB_APP_ID"], os.environ["GITHUB_INSTALLATION_ID"], key, os.environ["GITHUB_REPOSITORY"])
    ThreadingHTTPServer(("0.0.0.0", 8080), handler_for(github, Limits())).serve_forever()


if __name__ == "__main__":
    main()
