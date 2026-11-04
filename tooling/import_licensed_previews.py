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
import shutil
import tempfile
from urllib.parse import urlsplit
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


MAX_DOWNLOAD_BYTES = 16 * 1024 * 1024


def validate_source_url(url):
    endpoint = urlsplit(url)
    if (endpoint.scheme != "https" or endpoint.hostname != "wger.de"
            or endpoint.port not in (None, 443) or endpoint.username or endpoint.password):
        raise ValueError("Licensed previews must be fetched from https://wger.de")


class WgerRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        # Validate redirect destinations too; validating only the API's first URL
        # would still permit a downgrade or a redirect to an unrelated origin.
        validate_source_url(new_url)
        return super().redirect_request(request, response, code, message, headers, new_url)


def fetch(url):
    validate_source_url(url)
    request = urllib.request.Request(url, headers={"User-Agent": "OpenFactor licensed-preview-import/1.0"})
    opener = urllib.request.build_opener(WgerRedirectHandler())
    with opener.open(request, timeout=45) as response:
        lengths = response.headers.get_all("Content-Length", [])
        expected_length = None
        if lengths:
            value = lengths[0].strip()
            if len(lengths) != 1 or not value.isascii() or not value.isdecimal():
                raise ValueError("Invalid licensed-preview Content-Length")
            expected_length = int(value)
            if expected_length > MAX_DOWNLOAD_BYTES:
                raise ValueError("Licensed-preview response exceeds 16 MiB")
        # HTTPResponse.read(amt) does not raise IncompleteRead for an early EOF
        # in a fixed-length body, unlike an unbounded read(). Check it ourselves.
        data = response.read(MAX_DOWNLOAD_BYTES + 1)
    if len(data) > MAX_DOWNLOAD_BYTES:
        raise ValueError("Licensed-preview response exceeds 16 MiB")
    if expected_length is not None and len(data) != expected_length:
        raise ValueError("Incomplete licensed-preview response (Content-Length mismatch)")
    return data


def validate_source(source, exercise_id):
    # These are provenance checks on remote data, not optional smoke assertions.
    if source["exercise"] != exercise_id:
        raise ValueError(f"Unexpected exercise for image {source['id']}")
    if source["license"] != 1 or source["license_author"] != "Everkinetic":
        raise ValueError(f"Unreviewed license or author for image {source['id']}")
    if source["is_ai_generated"]:
        raise ValueError(f"Unreviewed AI-generated image {source['id']}")


def publish_staged(staging, files):
    """Publish individual files, restoring the prior set on recoverable failures.

    This is not crash-atomic across files. Callers must exclude concurrent writers;
    readers may see a mixed set until the manifest (published last) is replaced.
    """
    retain_recovery = False
    published = []
    try:
        backups = staging / "backups"
        backups.mkdir()
        originals = []
        # Finish every backup before changing any destination, including manifest.
        for index, (_, target) in enumerate(files):
            backup = backups / str(index)
            if target.exists():
                shutil.copy2(target, backup)
                originals.append(backup)
            else:
                originals.append(None)
        # From the first possible mutation until commit/confirmed rollback, an
        # unexpected interruption must retain recovery files, never discard them.
        retain_recovery = True
        try:
            for (source, target), backup in zip(files, originals):
                target.parent.mkdir(parents=True, exist_ok=True)
                # Register intent first: SIGINT can arrive after rename succeeds
                # but before Python executes the next line. Restoring an unchanged
                # destination is harmless; omitting a changed one loses recovery.
                published.append((target, backup))
                source.replace(target)
        except BaseException as error:
            rollback_errors = []
            for target, backup in reversed(published):
                try:
                    if backup is None:
                        target.unlink(missing_ok=True)
                    else:
                        backup.replace(target)
                except BaseException as rollback_error:
                    rollback_errors.append(f"{target}: {type(rollback_error).__name__}: {rollback_error}")
            if rollback_errors:
                retain_recovery = True
                raise RuntimeError(
                    f"Licensed-preview publication failed; rollback incomplete: {rollback_errors}. "
                    f"Recovery files retained at {staging}"
                ) from error
            retain_recovery = False
            raise
        else:
            retain_recovery = False
    finally:
        if not retain_recovery:
            shutil.rmtree(staging)


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
            validate_source(source, exercise_id)
            jobs.append((name, ids_by_name[name], exercise_id, source))

    def download(job):
        name, catalog_id, exercise_id, source = job
        data = fetch(source["image"])
        if not data.startswith(b"\x89PNG\r\n\x1a\n"):
            raise ValueError(f"Expected original PNG for image {source['id']}")
        asset = f"previews/wger-{source['id']}.png"
        path = staging / "new" / asset
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

    # Same-filesystem staging makes each later replacement atomic, but not the
    # entire multi-file set. No existing asset is touched during downloads.
    staging = Path(tempfile.mkdtemp(prefix=".licensed-previews-", dir=ASSETS))
    try:
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
        staged_manifest = staging / "new" / "exercise-previews.json"
        staged_manifest.parent.mkdir(parents=True, exist_ok=True)
        staged_manifest.write_text(json.dumps(manifest, indent=2) + "\n")
        files = [(staging / "new" / entry["asset"], ASSETS / entry["asset"]) for entry in entries]
        files.append((staged_manifest, MANIFEST))
    except BaseException:
        # The executor has joined all workers before their staging is removed.
        shutil.rmtree(staging)
        raise
    publish_staged(staging, files)


def verify():
    manifest = json.loads(MANIFEST.read_text())
    for entry in manifest["entries"]:
        path = ASSETS / entry["asset"]
        data = path.read_bytes()
        if hashlib.sha256(data).hexdigest() != entry["sha256"] or len(data) != entry["bytes"]:
            raise ValueError(f"Licensed-preview bytes differ: {path}")
        if entry["author"] != "Everkinetic" or entry["license_url"] != LICENSE:
            raise ValueError(f"Unreviewed licensed-preview attribution: {path}")
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
