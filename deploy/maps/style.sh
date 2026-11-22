#!/usr/bin/env bash
# Rebuilds the route map's style, app/src/main/assets/map/style-{en,de}.json, from Protomaps'
# own style package at a pinned version, through deploy/maps/style.mjs. The style ships in the
# app; only its tiles come from the server, and the {tiles} placeholder in it is filled with
# BuildConfig.MAP_TILES_URL. Run it after changing style.mjs or the version, then look at a map.
set -euo pipefail
BASEMAPS_VERSION=5.7.2
here="$(cd "$(dirname "$0")" && pwd)"
assets="$here/../../app/src/main/assets/map"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cp "$here/style.mjs" "$work/"
(cd "$work" && npm init -y >/dev/null && npm install --silent "@protomaps/basemaps@$BASEMAPS_VERSION")
for lang in en de; do
  node "$work/style.mjs" "$lang" > "$assets/style-$lang.json"
done
echo "Wrote $assets/style-en.json and style-de.json from @protomaps/basemaps $BASEMAPS_VERSION"
