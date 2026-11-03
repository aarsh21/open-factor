#!/usr/bin/env python3
"""Build an indexed, private MP4 tour from chapter clips and reviewed exclusions.

Requires ffmpeg/ffprobe. Inputs and generated media belong outside the public repo.
The manifest has chapters [{id, title, clips, covered, gaps}] and known_limits.
Exclusions map a clip filename to reviewed [start_seconds, end_seconds] ranges.
This tool does not detect private content; review recordings before distribution.
Rebuilding invalidates previous completion indexes before media can be replaced;
a failed rebuild must be retried and reviewed before distributing its output.
"""

import argparse
import html
import json
import math
from pathlib import Path
import subprocess
import tempfile


def positive_timeout(value):
    seconds = float(value)
    if not math.isfinite(seconds) or seconds <= 0:
        raise argparse.ArgumentTypeError("Timeout must be a finite positive number of seconds")
    return seconds


def duration(path, timeout=30):
    seconds = float(subprocess.check_output([
        "ffprobe", "-v", "error", "-show_entries", "format=duration",
        "-of", "default=nw=1:nk=1", str(path),
    ], timeout=timeout))
    if not math.isfinite(seconds) or seconds <= 0:
        raise ValueError(f"Invalid media duration for {path}")
    return seconds


def concat_line(path):
    # ffmpeg concat syntax, not shell syntax; subprocess always receives argv.
    return "file '" + str(path.resolve()).replace("'", "'\\''") + "'\n"


def clock(seconds):
    seconds = int(seconds)
    return f"{seconds // 3600:02}:{seconds // 60 % 60:02}:{seconds % 60:02}"


def write_text_atomic(path, contents):
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=path.parent,
                                         prefix=f".{path.name}.", suffix=".partial", delete=False) as stream:
            temporary = Path(stream.name)
            stream.write(contents)
        temporary.replace(path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--clips", required=True, type=Path)
    parser.add_argument("--exclusions", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--crop-top", type=int, default=100)
    parser.add_argument("--probe-timeout", type=positive_timeout, default=30,
                        help="Maximum seconds per ffprobe call (default: 30)")
    parser.add_argument("--encode-timeout", type=positive_timeout, default=3600,
                        help="Maximum seconds per encode or concat; increase for long tours (default: 3600)")
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text())
    exclusions = json.loads(args.exclusions.read_text())
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    # FFmpeg -y may truncate an existing chapter before failing or timing out.
    # Invalidate every published index first, not merely the machine receipt.
    for name in ("index.json", "index.html", "INDEX.md"):
        (out / name).unlink(missing_ok=True)
    chapters = []
    offset = 0.0
    for n, chapter in enumerate(manifest["chapters"], 1):
        clips = [args.clips / name for name in chapter["clips"]]
        if not clips or any(not c.is_file() for c in clips):
            raise ValueError(f"Missing clips for {chapter['id']}")
        ranges, clip_offset = [], 0.0
        for clip in clips:
            length = duration(clip, timeout=args.probe_timeout)
            for a, b in exclusions.get(clip.name, []):
                if not 0 <= a < b <= length + 0.1:
                    raise ValueError(f"Invalid exclusion {a}, {b} for {clip}")
                ranges.append((clip_offset + a, clip_offset + b))
            clip_offset += length
        listing = out / f"{n:02}-input.txt"
        listing.write_text("".join(map(concat_line, clips)))
        target = out / f"{n:02}-{chapter['id']}.mp4"
        filters = ["fps=15"]
        if ranges:
            checks = "+".join(f"between(t,{a:.6f},{b:.6f})" for a, b in ranges)
            filters.append(f"select='not({checks})'")
        filters.extend(["setpts=N/15/TB", f"crop=iw:ih-{args.crop_top}:0:{args.crop_top}", "scale=636:-2"])
        subprocess.run([
            "ffmpeg", "-nostdin", "-y", "-v", "error", "-threads", "2",
            "-f", "concat", "-safe", "0", "-i", str(listing), "-an",
            "-vf", ",".join(filters), "-c:v", "libx264", "-preset", "fast",
            "-crf", "23", "-threads", "2", "-pix_fmt", "yuv420p",
            "-movflags", "+faststart", str(target),
        ], check=True, timeout=args.encode_timeout)
        length = duration(target, timeout=args.probe_timeout)
        chapters.append({**chapter, "video": target.name, "start": offset,
                         "end": offset + length, "duration": length,
                         "excluded_intervals": len(ranges)})
        offset += length
        print(f"{n:02} {chapter['title']}: {length:.1f}s", flush=True)
    meta = out / "chapters.ffmetadata"
    meta.write_text(";FFMETADATA1\ntitle=MacroFactor Workouts accessible feature reference\n" + "".join(
        f"[CHAPTER]\nTIMEBASE=1/1000\nSTART={round(c['start']*1000)}\n"
        f"END={round(c['end']*1000)}\ntitle={c['title']}\n" for c in chapters))
    listing = out / "chapters-concat.txt"
    listing.write_text("".join(concat_line(out / c["video"]) for c in chapters))
    combined = out / "workouts-feature-tour.mp4"
    subprocess.run(["ffmpeg", "-nostdin", "-y", "-v", "error", "-f", "concat",
                    "-safe", "0", "-i", str(listing), "-i", str(meta),
                    "-map_metadata", "1", "-map_chapters", "1", "-c", "copy",
                    "-movflags", "+faststart", str(combined)], check=True, timeout=args.encode_timeout)
    result = {**manifest, "status": "assembled-review-required", "chapters": chapters,
              "video": combined.name, "duration": duration(combined, timeout=args.probe_timeout)}
    rows = []
    for c in chapters:
        rows.append(f"- [{clock(c['start'])} — {c['title']}]({combined.name}#t={c['start']:.3f})\n"
                    + "  - Captured: " + "; ".join(c.get("covered", [])) + ".\n"
                    + "  - Gaps: " + "; ".join(c.get("gaps", [])) + ".\n")
    write_text_atomic(out / "INDEX.md", "# Accessible feature reference\n\n"
        "Private source-app recording. Captured controls do not prove all resulting behavior.\n\n"
        + "\n".join(rows) + "\n## Remaining coverage limits\n\n"
        + "\n".join("- " + s for s in manifest.get("known_limits", [])) + "\n")
    buttons = "".join(f'<button data-time="{c["start"]:.3f}">{clock(c["start"])} '
                      f'{html.escape(c["title"])}</button>' for c in chapters)
    write_text_atomic(out / "index.html", '''<!doctype html><html lang="en"><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>Workouts reference</title>
<style>body{font:16px system-ui;background:#111;color:#eee;margin:24px}main{display:flex;gap:24px;flex-wrap:wrap}video{height:85vh;max-width:100%}nav{display:grid;gap:8px;align-content:start}button{font:inherit;text-align:left;padding:12px;background:#292929;color:white;border:1px solid #555;border-radius:8px}a{color:#a8caff}</style>
<h1>MacroFactor Workouts reference</h1><p>Accessible feature tour. See <a href="INDEX.md">coverage and gaps</a>.</p>
<main><video controls preload="metadata" src="workouts-feature-tour.mp4"></video><nav>'''
        + buttons + '''</nav></main><script>const v=document.querySelector('video');
document.querySelectorAll('button').forEach(b=>b.onclick=()=>{v.currentTime=+b.dataset.time;v.play()});
</script></html>''')
    # Publish the machine-readable completion receipt last, only after both
    # human-readable indexes and every referenced video are ready.
    write_text_atomic(out / "index.json", json.dumps(result, indent=2) + "\n")


if __name__ == "__main__":
    main()
