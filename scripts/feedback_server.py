#!/usr/bin/env python3
"""Put Momentum's feedback service on the web server.

    scripts/feedback_server.py deploy    copy the service and its secrets, build and start it
    scripts/feedback_server.py test      run the service's tests here

The service (`deploy/feedback`) opens each message the app sends as a GitHub issue under the
Momentum feedback GitHub App. Its secrets stay outside the repository, in
`~/.config/momentum/feedback/`:

    github-app.env   GITHUB_APP_ID=…, GITHUB_INSTALLATION_ID=…, GITHUB_REPOSITORY=owner/name
    github-app.pem   the App's private key

They land in `/opt/momentum-feedback` on the server, in a folder only root can enter. The website's
Caddy (`deploy/website/Caddyfile`, published by `scripts/website.py publish`) routes
`/api/feedback` to the service.
"""

import argparse
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "deploy/feedback"
SECRETS = Path.home() / ".config/momentum/feedback"
SERVER = os.environ.get("MOMENTUM_SITE_SERVER", "root@momentum.nicolas-mahn.de")
SERVER_KEY = Path(os.environ.get("MOMENTUM_SITE_KEY", Path.home() / ".ssh/av_stuttgart_hetzner"))
SERVER_DIRECTORY = "/opt/momentum-feedback"


def run(*command, cwd=None):
    subprocess.run(command, check=True, cwd=cwd)


def test():
    run(sys.executable, "-m", "unittest", "-q", "test_feedback_server", cwd=SERVICE)


def deploy():
    missing = [name for name in ("github-app.env", "github-app.pem") if not (SECRETS / name).is_file()]
    if missing:
        sys.exit(f"Missing in {SECRETS}: {', '.join(missing)}")
    test()
    options = ["-i", str(SERVER_KEY), "-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=accept-new"]
    ssh = " ".join(["ssh", *options])
    run("ssh", *options, SERVER, f"install -d -m 700 {SERVER_DIRECTORY}")
    code = [str(SERVICE / name) for name in ("Dockerfile", "docker-compose.yml", "feedback_server.py")]
    run("rsync", "--chmod=F644", "--rsh", ssh, *code, f"{SERVER}:{SERVER_DIRECTORY}/")
    # Readable inside the container, which runs as nobody; the folder keeps everyone else out.
    secrets = [str(SECRETS / name) for name in ("github-app.env", "github-app.pem")]
    run("rsync", "--chmod=F644", "--rsh", ssh, *secrets, f"{SERVER}:{SERVER_DIRECTORY}/")
    run("ssh", *options, SERVER, f"cd {SERVER_DIRECTORY} && docker compose up --detach --build --quiet-pull")
    print("Deployed. The app posts to https://momentum.nicolas-mahn.de/api/feedback")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=["deploy", "test"])
    {"deploy": deploy, "test": test}[parser.parse_args().command]()


if __name__ == "__main__":
    main()
