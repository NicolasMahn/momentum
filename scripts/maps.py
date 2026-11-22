#!/usr/bin/env python3
"""The route map's tiles: an OpenStreetMap extract in Protomaps' format, served by `pmtiles serve`.

    scripts/maps.py publish [--bbox …]   put the extract and its container on the web server
    scripts/maps.py serve [--bbox …]     serve a small extract on this PC to a test device

`publish` has the server fetch the extract itself, straight from Protomaps' daily build, so the
gigabytes never pass through this PC, then starts `deploy/maps/docker-compose.yml`. The website's
Caddy (`deploy/website/Caddyfile`, put there by `scripts/website.py publish`) sends
`https://momentum.nicolas-mahn.de/maps/tiles/` to it. Run it again to refresh the map; roads
change slowly, so twice a year is plenty. It makes the map public under the owner's domain:
publish only what is merged.

`serve` is for trying a map change: it extracts a small area into `~/.local/share/momentum-dev/maps`
and serves it on port 8089. `scripts/emulator.py reverse 8089` lets the test device reach it, for a
build made with `-Pmomentum.mapTiles=http://127.0.0.1:8089/basemap`. Only the `debug` build may speak plain HTTP,
and only to 127.0.0.1 (`app/src/debug/res/xml/network_security_config.xml`).
"""

import argparse
import json
import os
import subprocess
import tarfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COMPOSE = ROOT / "deploy/maps/docker-compose.yml"
SERVER = os.environ.get("MOMENTUM_SITE_SERVER", "root@momentum.nicolas-mahn.de")
SERVER_KEY = Path(os.environ.get("MOMENTUM_SITE_KEY", Path.home() / ".ssh/av_stuttgart_hetzner"))
SERVER_DIRECTORY = "/opt/momentum-maps"

PMTILES_VERSION = "1.31.2"
PMTILES_IMAGE = f"protomaps/go-pmtiles:v{PMTILES_VERSION}"
BUILDS = "https://build-metadata.protomaps.dev/builds.json"

# Germany, where the owner and most testers run. Elsewhere a route still draws, on an empty ground.
GERMANY = "5.86,47.27,15.05,55.06"
# Stuttgart, small enough to fetch in seconds for a test device.
STUTTGART = "9.05,48.70,9.30,48.85"
MAX_ZOOM = 15

LOCAL = Path.home() / ".local/share/momentum-dev/maps"
LOCAL_PORT = 8089


def run(*command):
    subprocess.run(command, check=True)


def latest_build():
    """The newest daily planet build Protomaps lists, as its URL."""
    with urllib.request.urlopen(BUILDS) as response:
        builds = json.load(response)
    return f"https://build.protomaps.com/{builds[-1]['key']}"


def publish(bbox):
    options = ["-i", str(SERVER_KEY), "-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=accept-new"]
    ssh = " ".join(["ssh", *options])
    source = latest_build()
    run("ssh", *options, SERVER, f"mkdir -p {SERVER_DIRECTORY}/data")
    run("rsync", "--chmod=F644", "--rsh", ssh, str(COMPOSE), f"{SERVER}:{SERVER_DIRECTORY}/docker-compose.yml")
    # Extracted beside the one in use and moved over it, so the map never serves half a file.
    extract = (
        f"docker run --rm -v {SERVER_DIRECTORY}/data:/data {PMTILES_IMAGE} "
        f"extract {source} /data/basemap.pmtiles.new --bbox={bbox} --maxzoom={MAX_ZOOM}"
    )
    run("ssh", *options, SERVER, f"{extract} && mv {SERVER_DIRECTORY}/data/basemap.pmtiles.new {SERVER_DIRECTORY}/data/basemap.pmtiles")
    run("ssh", *options, SERVER, f"cd {SERVER_DIRECTORY} && docker compose up --detach --quiet-pull --force-recreate")
    print(f"Published {source} for {bbox}. https://momentum.nicolas-mahn.de/maps/tiles/basemap.json")


def pmtiles():
    """The pmtiles tool for this PC, fetched once."""
    tool = LOCAL / "pmtiles"
    if not tool.exists():
        LOCAL.mkdir(parents=True, exist_ok=True)
        archive = LOCAL / "pmtiles.tgz"
        url = f"https://github.com/protomaps/go-pmtiles/releases/download/v{PMTILES_VERSION}/go-pmtiles_{PMTILES_VERSION}_Linux_x86_64.tar.gz"
        urllib.request.urlretrieve(url, archive)
        with tarfile.open(archive) as tar:
            tar.extract("pmtiles", LOCAL, filter="data")
        archive.unlink()
    return tool


def serve(bbox):
    tool = pmtiles()
    tiles = LOCAL / "serve"
    tiles.mkdir(parents=True, exist_ok=True)
    extract = tiles / "basemap.pmtiles"
    if not extract.exists():
        run(str(tool), "extract", latest_build(), str(extract), f"--bbox={bbox}", f"--maxzoom={MAX_ZOOM}")
    print(f"Serving {extract} on http://127.0.0.1:{LOCAL_PORT}/basemap; stop with Ctrl+C.")
    print(f"For a test device: scripts/emulator.py reverse {LOCAL_PORT}")
    run(str(tool), "serve", str(tiles), f"--port={LOCAL_PORT}", "--interface=127.0.0.1")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("publish", help="put the map on the web server").add_argument("--bbox", default=GERMANY)
    commands.add_parser("serve", help="serve a small extract to a test device").add_argument("--bbox", default=STUTTGART)
    arguments = parser.parse_args()
    if arguments.command == "publish":
        publish(arguments.bbox)
    else:
        serve(arguments.bbox)


if __name__ == "__main__":
    main()
