// Momentum's route map style: Protomaps' dark flavor without the sprite it would need, so the map
// asks the server for tiles only. Run by deploy/maps/style.sh.
import { layers, namedFlavor } from "@protomaps/basemaps";
const lang = process.argv[2];
const dropped = new Set(["roads_oneway", "roads_shields", "pois"]);
const kept = layers("protomaps", namedFlavor("dark"), { lang })
  .filter(l => !dropped.has(l.id))
  .map(l => {
    if (!l.layout) return l;
    const layout = Object.fromEntries(Object.entries(l.layout).filter(([k]) => !k.startsWith("icon-")));
    return { ...l, layout };
  });
const style = {
  version: 8,
  glyphs: "asset://map/fonts/{fontstack}/{range}.pbf",
  sources: {
    protomaps: {
      type: "vector",
      tiles: ["{tiles}/{z}/{x}/{y}.mvt"],
      maxzoom: 15,
      attribution: "© OpenStreetMap",
    },
  },
  layers: kept,
};
process.stdout.write(JSON.stringify(style));
