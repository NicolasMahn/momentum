import json
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer

from feedback_server import PER_CLIENT_PER_HOUR, Limits, Refused, handler_for, issue_for, parse_message


def message(**changes):
    sent = {"kind": "problem", "text": "The timer stops\nwhen I lock the phone", "facts": "Momentum 2.3.1\nAndroid 16", "language": "en-US"}
    sent.update(changes)
    return json.dumps(sent).encode()


class FeedbackServerTest(unittest.TestCase):
    def test_only_what_the_app_sends_is_taken(self):
        self.assertEqual("problem", parse_message(message())["kind"])
        self.assertIsNone(parse_message(message(facts=None))["facts"])
        for refused in (b"{", b"[]", message(kind="spam"), message(text="   "), message(text="x" * 4001), message(facts=7)):
            with self.assertRaises(Refused) as caught:
                parse_message(refused)
            self.assertEqual(400, caught.exception.status)

    def test_a_message_can_ping_nobody_and_link_nothing(self):
        issue = issue_for(parse_message(message(text="@octocat see #12")))

        self.assertNotIn("@octocat", issue["title"] + issue["body"])
        self.assertNotIn("#12", issue["title"] + issue["body"])
        self.assertIn("@​octocat", issue["body"])

    def test_the_title_is_the_first_line_and_facts_are_left_out_when_unticked(self):
        issue = issue_for(parse_message(message(facts=None)))

        self.assertEqual("Problem: The timer stops", issue["title"])
        self.assertNotIn("Android", issue["body"])
        self.assertEqual(["from the app", "problem"], issue["labels"])

    def test_a_client_is_held_to_its_hourly_share_and_gets_it_back(self):
        now = [0.0]
        limits = Limits(clock=lambda: now[0])
        for _ in range(PER_CLIENT_PER_HOUR):
            limits.take("a")
        with self.assertRaises(Refused) as caught:
            limits.take("a")
        self.assertEqual(429, caught.exception.status)
        limits.take("b")
        now[0] += 3601
        limits.take("a")


class FakeGitHub:
    def __init__(self):
        self.issues = []

    def open_issue(self, issue):
        self.issues.append(issue)
        return "https://github.com/example/issues/1"


class FeedbackEndpointTest(unittest.TestCase):
    def setUp(self):
        self.github = FakeGitHub()
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), handler_for(self.github, Limits()))
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.addCleanup(self.server.shutdown)

    def post(self, body, path="/api/feedback"):
        url = f"http://127.0.0.1:{self.server.server_port}{path}"
        request = urllib.request.Request(url, data=body, method="POST", headers={"Content-Type": "application/json", "X-Forwarded-For": "203.0.113.7"})
        try:
            with urllib.request.urlopen(request) as response:
                return response.status
        except urllib.error.HTTPError as error:
            return error.code

    def test_a_message_becomes_one_issue_and_a_flood_is_turned_away(self):
        self.assertEqual(201, self.post(message()))
        self.assertEqual("Problem: The timer stops", self.github.issues[0]["title"])
        self.assertEqual(400, self.post(message(kind="spam")))
        self.assertEqual(404, self.post(message(), path="/other"))
        for _ in range(PER_CLIENT_PER_HOUR - 1):
            self.assertEqual(201, self.post(message()))
        self.assertEqual(429, self.post(message()))
        self.assertEqual(PER_CLIENT_PER_HOUR, len(self.github.issues))


if __name__ == "__main__":
    unittest.main()
