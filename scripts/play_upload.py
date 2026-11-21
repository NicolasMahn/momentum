#!/usr/bin/env python3
"""Upload a signed bundle to a Google Play test track and send it for Google's review.

    play_upload.py app/build/outputs/bundle/release/app-release.aab

The closed-test workflow runs this after every merge to `main`. It signs in with the service
account key in `~/.config/momentum/play-upload-key.json` (or the file `PLAY_UPLOAD_KEY` names);
that account may release this app to test tracks and nothing else. What testers read about the
release comes from `docs/store/release-notes/<language>.txt`, one file per store language.

Needs `google-auth` and `requests`.
"""

import argparse
import os
import sys
from pathlib import Path

from google.auth.transport.requests import AuthorizedSession
from google.oauth2 import service_account

PACKAGE = "de.nicolasmahn.momentum"
API = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}"
UPLOAD_API = f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PACKAGE}"
DEFAULT_KEY = Path.home() / ".config/momentum/play-upload-key.json"
DEFAULT_NOTES = Path(__file__).resolve().parent.parent / "docs/store/release-notes"
# Google Play's limit for one language's release notes.
NOTES_LIMIT = 500


def read_notes(folder):
    """The release notes as Google Play takes them, one entry per `<language>.txt`."""
    notes = []
    for file in sorted(folder.glob("*.txt")):
        text = file.read_text(encoding="utf-8").strip()
        if len(text) > NOTES_LIMIT:
            raise SystemExit(f"{file} holds {len(text)} characters; Google Play takes {NOTES_LIMIT}.")
        if text:
            notes.append({"language": file.stem, "text": text})
    return notes


def answer(response, step):
    """The JSON of a call that worked; a failed call ends the upload with Google's own words."""
    if not response.ok:
        raise SystemExit(f"{step} failed ({response.status_code}): {response.text.strip()}")
    return response.json() if response.content else {}


def upload(bundle, track, notes, session):
    edit = answer(session.post(f"{API}/edits"), "Opening an edit")["id"]
    try:
        with bundle.open("rb") as data:
            uploaded = answer(
                session.post(
                    f"{UPLOAD_API}/edits/{edit}/bundles",
                    params={"uploadType": "media"},
                    data=data,
                    headers={"Content-Type": "application/octet-stream"},
                    timeout=600,
                ),
                "Uploading the bundle",
            )
        code = uploaded["versionCode"]

        current = answer(session.get(f"{API}/edits/{edit}/tracks/{track}"), f"Reading track `{track}`")
        released = [int(c) for release in current.get("releases", []) for c in release.get("versionCodes", [])]
        if released and code <= max(released):
            raise SystemExit(
                f"The bundle has version code {code}, but track `{track}` already holds {max(released)}. "
                "The code counts the commits, so this build came from a shallow or older checkout."
            )

        release = {"status": "completed", "versionCodes": [str(code)], "releaseNotes": notes}
        answer(
            session.put(f"{API}/edits/{edit}/tracks/{track}", json={"track": track, "releases": [release]}),
            f"Putting the release on track `{track}`",
        )

        committed = session.post(f"{API}/edits/{edit}:commit")
        if committed.status_code == 400 and "changesNotSentForReview" in committed.text:
            # Google Play holds some changes back until a person sends them, such as after a
            # rejected review. The release is then saved, and the Console's publishing overview
            # has the button.
            answer(
                session.post(f"{API}/edits/{edit}:commit", params={"changesNotSentForReview": "true"}),
                "Saving the release",
            )
            print(f"::warning::Release {code} is saved on `{track}` but not sent for review. "
                  "Send it from the Play Console's publishing overview.")
            return code
        answer(committed, "Sending the release for review")
    except BaseException:
        session.delete(f"{API}/edits/{edit}")
        raise
    print(f"Release {code} is on track `{track}` and sent for review.")
    return code


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("bundle", type=Path, help="the signed .aab")
    parser.add_argument("--track", default="alpha", help="Google Play's name of the track; the closed test is `alpha`")
    parser.add_argument("--notes", type=Path, default=DEFAULT_NOTES, help="folder with one <language>.txt each")
    args = parser.parse_args()

    if not args.bundle.is_file():
        raise SystemExit(f"No bundle at {args.bundle}.")
    key = Path(os.environ.get("PLAY_UPLOAD_KEY", DEFAULT_KEY))
    if not key.is_file():
        raise SystemExit(f"No service account key at {key}.")

    credentials = service_account.Credentials.from_service_account_file(
        str(key), scopes=["https://www.googleapis.com/auth/androidpublisher"]
    )
    upload(args.bundle, args.track, read_notes(args.notes), AuthorizedSession(credentials))


if __name__ == "__main__":
    sys.exit(main())
