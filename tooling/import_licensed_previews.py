#!/usr/bin/env python3
"""Import the explicitly reviewed Everkinetic illustrations from wger.

Run --verify for an offline hash/attribution check. --refresh fetches original,
unmodified images and current per-image attribution from the public wger API.
Only the pinned exercise/image joins below are permitted; variants are not guessed.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "openfactor/app/src/main/assets"
MANIFEST = ASSETS / "exercise-previews.json"
LICENSE = "https://creativecommons.org/licenses/by-sa/3.0/"
# Exact catalog name -> reviewed wger exercise ID and image IDs.
MAPPINGS = {
    "Barbell bench press": (73, [68, 70]),
    "Dumbbell bench press": (75, [113, 114]),
    "Close grip bench press": (76, [15, 16]),
    "Barbell biceps curl": (91, [27, 28]),
    "Standing dumbbell biceps curl": (92, [23, 24]),
    "Cable straight bar biceps curl": (95, [19, 20]),
    "Chin-up": (152, [73, 75]),
    "Dumbbell walking lunge": (206, [121, 122, 123, 124]),
    "EZ bar skull crusher": (246, [60, 61]),
    "Cross-arm grip front squat": (257, [149, 150]),
    "Standing dumbbell hammer curl": (272, [47, 48]),
    "Cable rope hammer curl": (275, [51, 52]),
    "Standing dumbbell lateral raise": (348, [177, 178]),
    "Lying hamstring curl": (365, [179, 180]),
    "Seated hamstring curl": (366, [181, 182]),
    "EZ bar preacher curl": (465, [93, 94]),
    "Seated barbell overhead press": (566, [171, 172]),
    "Seated dumbbell overhead press": (567, [173, 174]),
    "Barbell shrug": (571, [43, 44]),
    "Standing dumbbell shrug": (572, [39, 40]),
    "Barbell good morning": (1392, [66, 67]),
}


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": "OpenFactor licensed-preview-import/1.0"})
    with urllib.request.urlopen(request, timeout=45) as response:
        return response.read()


def refresh():
    catalog = json.loads((ASSETS / "catalog.json").read_text())
    ids_by_name = {exercise["name"]: exercise["id"] for exercise in catalog}
    images = json.loads(fetch("https://wger.de/api/v2/exerciseimage/?limit=1000"))
    if images["next"]:
        raise ValueError("The API now paginates beyond 1000 images; update this importer before refreshing")
    by_id = {image["id"]: image for image in images["results"]}
    jobs = []
    for name, (exercise_id, image_ids) in MAPPINGS.items():
        for image_id in image_ids:
            source = by_id[image_id]
            assert source["exercise"] == exercise_id
            assert source["license"] == 1 and source["license_author"] == "Everkinetic"
            assert not source["is_ai_generated"]
            jobs.append((name, ids_by_name[name], exercise_id, source))

    def download(job):
        name, catalog_id, exercise_id, source = job
        data = fetch(source["image"])
        if not data.startswith(b"\x89PNG\r\n\x1a\n"):
            raise ValueError(f"Expected original PNG for image {source['id']}")
        asset = f"previews/wger-{source['id']}.png"
        path = ASSETS / asset
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        return {
            "exercise_id": catalog_id,
            "exercise_name": name,
            "asset": asset,
            "author": source["license_author"],
            "license": "CC BY-SA 3.0",
            "license_url": LICENSE,
            "source_url": source["image"],
            "source_page": f"https://wger.de/en/exercise/{exercise_id}/view/",
            "wger_exercise_id": exercise_id,
            "wger_image_id": source["id"],
            "sha256": hashlib.sha256(data).hexdigest(),
            "bytes": len(data),
            "changes": "None; original unmodified PNG",
            "source_attribution": {key: source.get(key) for key in (
                "license_title", "license_object_url", "license_author_url",
                "license_derivative_source_url", "author_history",
            )},
        }

    with ThreadPoolExecutor(max_workers=6) as pool:
        entries = list(pool.map(download, jobs))
    manifest = {
        "source": "https://wger.de/api/v2/exerciseimage/",
        "retrieved_at": datetime.now(timezone.utc).isoformat(),
        "license": "CC BY-SA 3.0",
        "license_url": LICENSE,
        "author": "Everkinetic",
        "entries": entries,
    }
    MANIFEST.write_text(json.dumps(manifest, indent=2) + "\n")


def verify():
    manifest = json.loads(MANIFEST.read_text())
    for entry in manifest["entries"]:
        path = ASSETS / entry["asset"]
        data = path.read_bytes()
        assert hashlib.sha256(data).hexdigest() == entry["sha256"], path
        assert len(data) == entry["bytes"], path
        assert entry["author"] == "Everkinetic" and entry["license_url"] == LICENSE
    print(json.dumps({
        "exercises": len({entry["exercise_id"] for entry in manifest["entries"]}),
        "images": len(manifest["entries"]),
        "bytes": sum(entry["bytes"] for entry in manifest["entries"]),
        "hashes": "passed",
    }, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--refresh", action="store_true")
    parser.add_argument("--verify", action="store_true")
    args = parser.parse_args()
    if args.refresh:
        refresh()
    verify()
