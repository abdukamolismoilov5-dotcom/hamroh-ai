#!/usr/bin/env python3
"""O'zbekiston bo'yicha POI bazasini OpenStreetMap'dan yig'ib, ilova bilan birga keladigan
android/app/src/main/assets/poi_seed.csv faylini yaratadi (FR-2.1). Internet kerak.

    python poi_import.py                 # butun O'zbekiston (viloyatlar bo'yicha, bir necha daqiqa)
    python poi_import.py --bbox 40.2,71.4,40.7,72.1   # faqat Farg'ona atrofi

Format: id,TYPE,lat,lon,speed_limit,name
"""
import argparse
import json
import time
import urllib.parse
import urllib.request
from pathlib import Path

OUT = Path(__file__).resolve().parents[1] / "android" / "app" / "src" / "main" / "assets" / "poi_seed.csv"
UZ_BBOX = (37.1, 55.9, 45.6, 73.2)
QUERY = """[out:json][timeout:180];
(
  nwr["amenity"="school"]({b});
  nwr["amenity"="kindergarten"]({b});
  node["highway"="speed_camera"]({b});
  node["traffic_calming"]({b});
  nwr["amenity"="fuel"]["fuel:cng"="yes"]({b});
  nwr["amenity"="fuel"]["fuel:lpg"="yes"]({b});
);
out center tags;"""


def classify(tags):
    if tags.get("amenity") == "school":
        return "SCHOOL"
    if tags.get("amenity") == "kindergarten":
        return "KINDERGARTEN"
    if tags.get("highway") == "speed_camera":
        return "SPEED_CAMERA"
    if tags.get("amenity") == "fuel":
        return "GAS_STATION"
    if tags.get("traffic_calming", "no") != "no":
        return "SPEED_BUMP"
    return None


def fetch(bbox):
    b = ",".join(str(x) for x in bbox)
    data = urllib.parse.urlencode({"data": QUERY.format(b=b)}).encode()
    req = urllib.request.Request("https://overpass-api.de/api/interpreter", data=data,
                                 headers={"User-Agent": "HamrohAI-poi-import/1.0"})
    with urllib.request.urlopen(req, timeout=200) as r:
        return json.load(r)["elements"]


def tiles(bbox, step=2.0):
    s, w, n, e = bbox
    lat = s
    while lat < n:
        lon = w
        while lon < e:
            yield (lat, lon, min(lat + step, n), min(lon + step, e))
            lon += step
        lat += step


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--bbox", help="janub,g'arb,shimol,sharq")
    a = ap.parse_args()
    bbox = tuple(float(x) for x in a.bbox.split(",")) if a.bbox else UZ_BBOX
    rows = {}
    for t in tiles(bbox):
        for attempt in range(3):
            try:
                els = fetch(t)
                break
            except Exception as ex:  # noqa: BLE001
                print("qayta urinish", t, ex)
                time.sleep(20)
        else:
            continue
        for e in els:
            tags = e.get("tags", {})
            typ = classify(tags)
            if not typ:
                continue
            lat = e.get("lat") or e.get("center", {}).get("lat")
            lon = e.get("lon") or e.get("center", {}).get("lon")
            if lat is None:
                continue
            limit = "".join(ch for ch in tags.get("maxspeed", "") if ch.isdigit())
            name = (tags.get("name:uz") or tags.get("name") or "").replace(",", " ")
            rows[f"osm-{e['type']}-{e['id']}"] = f"osm-{e['type']}-{e['id']},{typ},{lat:.7f},{lon:.7f},{limit},{name}"
        print(t, "jami:", len(rows))
        time.sleep(5)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("# Hamroh AI POI seed — © OpenStreetMap contributors, ODbL\n" + "\n".join(rows.values()) + "\n", encoding="utf-8")
    print("yozildi:", OUT, len(rows))


if __name__ == "__main__":
    main()
