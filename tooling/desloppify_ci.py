"""Run a fresh, advisory Desloppify snapshot; fail on broken scan infrastructure.

Findings are NOT a quality gate. See docs/open-factor/CODE-HEALTH.md for scope,
known false positives, and the separate strict build/lint/test gates.
"""

import argparse
from collections import Counter
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
PROJECTS = {
    "android": ("kotlin", "openfactor", ("ktlint", "java")),
    "tooling": ("python", "tooling", ("ruff", "bandit")),
    "media": ("typescript", "backend/media", ("node",)),
}
# The pinned scanner sometimes logs a detector failure but exits zero.
FAILURE_LOG = re.compile(
    r"\btimed\s+out\b|\bTimeoutExpired\b|traceback \(most recent call last\)|"
    r"coverage reduced|tooling unavailable|"
    r"(?:detector|phase)\b[^\n]*(?:failed|crashed)",
    re.IGNORECASE,
)


def flatten_ktlint_report(report):
    """Convert ktlint 1.5's nested JSON to Desloppify 1.0's flat JSON schema."""
    if not isinstance(report, list):
        raise ValueError("Expected ktlint JSON array")
    entries = []
    for source in report:
        if not isinstance(source, dict) or not isinstance(source.get("file"), str):
            raise ValueError("Invalid ktlint file record")
        errors = source.get("errors")
        if not isinstance(errors, list):
            raise ValueError("Invalid ktlint error list")
        for error in errors:
            if (not isinstance(error, dict) or not isinstance(error.get("line"), int)
                    or not isinstance(error.get("message"), str) or not isinstance(error.get("rule"), str)):
                raise ValueError("Invalid ktlint diagnostic")
            entries.append({"file": source["file"], "line": error["line"],
                            "message": error["rule"] + ": " + error["message"]})
    return entries


def run_kotlin_linter(root, directory, executable, arguments):
    if arguments != ["--reporter=json"]:
        raise ValueError("The scan-local ktlint shim supports only --reporter=json")
    raw_path = directory / "ktlint-report.json"
    command = [executable, "--log-level=none", f"--reporter=json,output={raw_path}"]
    result = subprocess.run(command, cwd=root / "openfactor", capture_output=True,
                            text=True, timeout=110, check=False)
    (directory / "ktlint.log").write_text(result.stdout + result.stderr, encoding="utf-8")
    entries = flatten_ktlint_report(json.loads(raw_path.read_text(encoding="utf-8")))
    valid = result.returncode == (1 if entries else 0)
    (directory / "ktlint-result.json").write_text(json.dumps({
        "command": command, "exit_code": result.returncode, "valid_lint_check": valid,
        "diagnostic_count": len(entries),
    }, indent=2) + "\n", encoding="utf-8")
    if not valid:
        raise ValueError(f"Unexpected ktlint exit {result.returncode}; see ktlint.log")
    print(json.dumps(entries))
    return result.returncode


def install_kotlin_shim(root, directory):
    executable = shutil.which("ktlint")
    shim_directory = directory / "bin"
    shim_directory.mkdir()
    shim = shim_directory / "ktlint"
    command = [sys.executable, "-I", str(Path(__file__).resolve()), "--kotlin-linter",
               str(root), str(directory), executable]
    shim.write_text("#!/bin/sh\nexec " + shlex.join(command) + ' "$@"\n', encoding="utf-8")
    shim.chmod(0o755)
    return str(shim_directory)


def normalize_unused_diagnostics(diagnostics):
    """Adapt unused-only TypeScript diagnostics to the pinned scanner's schema.

    Keep the original diagnostics in the evidence file. Desloppify 1.0 only
    recognizes TS6133 and TS6192, so its input needs normalization for types,
    constructor properties and groups of unused bindings as well.
    """
    normalized = []
    for line in diagnostics.splitlines():
        if not line.strip():
            continue
        match = re.fullmatch(r"(.+)\((\d+),(\d+)\): error TS(\d+): (.+)", line)
        if not match:
            return None
        filename, row, column, code, message = match.groups()
        prefix = f"{filename}({row},{column}): error "
        if code == "6192" and message == "All imports in import declaration are unused.":
            normalized.append(prefix + "TS6192: " + message)
            continue
        if code in {"6133", "6138", "6196"}:
            symbol = re.fullmatch(r"(?:Property )?'(.*)' is declared but(?: its value is never read| never used)\.", message)
            if not symbol:
                return None
            # Quoted property names can contain spaces, quotes or be empty. The
            # scanner's TS6133 parser cannot represent those spellings; retain the
            # real name in raw evidence and use a descriptive scanner-safe label.
            name = symbol[1] if symbol[1] and not re.search(r"\s|['\"]", symbol[1]) else "(unused-symbol)"
        elif code == "6198" and message == "All destructured elements are unused.":
            name = "(destructured-bindings)"
        elif code == "6199" and re.fullmatch(r"All declarations of '.+' are unused\.", message):
            name = "(unused-declarations)"
        else:
            return None
        normalized.append(prefix + f"TS6133: '{name}' is declared but its value is never read.")
    return "\n".join(normalized) + ("\n" if normalized else "")


def run_typescript_compiler(root, evidence, arguments):
    """Adapt 1.0's hard-coded tsconfig.app.json to this repo's backend config.

    Called only by our scan-local npx shim. Persist actual compiler diagnostics
    because 1.0 otherwise silently treats non-unused TypeScript errors as clean.
    """
    if not arguments or arguments[0] != "tsc":
        raise ValueError("The scan-local npx shim supports only tsc")
    command = [
        str(root / "backend/media/node_modules/.bin/tsc"),
        "--project", str(root / "backend/media/tsconfig.json"), "--noEmit",
        "--noUnusedLocals", "--noUnusedParameters", "--pretty", "false",
    ]
    result = subprocess.run(command, cwd=root, capture_output=True, text=True, timeout=120, check=False)
    diagnostics = result.stdout + result.stderr
    # TypeScript 7's native compiler exits 1 for diagnostics; older tsc exits 2.
    # Neither status is sufficient: every diagnostic must be a recognized unused
    # finding. Mixed type/configuration errors remain infrastructure failures.
    normalized = normalize_unused_diagnostics(diagnostics)
    valid = (result.returncode == 0 and not diagnostics.strip()) or (
        result.returncode in {1, 2} and bool(normalized)
    )
    evidence.write_text(json.dumps({
        "command": command, "exit_code": result.returncode,
        "valid_unused_check": valid, "diagnostics": diagnostics,
        "normalized_diagnostics": normalized,
    }, indent=2) + "\n", encoding="utf-8")
    print(normalized if valid else diagnostics, end="")
    return result.returncode


def install_typescript_shim(root, directory):
    compiler = root / "backend/media/node_modules/.bin/tsc"
    if not compiler.is_file():
        raise ValueError("Missing media TypeScript compiler; run bun install --frozen-lockfile in backend/media")
    shim_directory = directory / "bin"
    shim_directory.mkdir()
    shim = shim_directory / "npx"
    command = [sys.executable, "-I", str(Path(__file__).resolve()),
               "--typescript-compiler", str(root), str(directory / "typescript-compiler.json")]
    shim.write_text("#!/bin/sh\nexec " + shlex.join(command) + ' "$@"\n', encoding="utf-8")
    shim.chmod(0o755)
    return str(shim_directory)


def read_object(path):
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"Expected JSON object: {path}")
    return value


def validate_snapshot(state, query, language, scan_path, log):
    """Check the pinned 1.0 snapshot contract, not a subjective score threshold."""
    if state.get("scan_count") != 1 or not state.get("last_scan"):
        raise ValueError("Expected exactly one completed scan in fresh state")
    if state.get("scan_path") != scan_path:
        raise ValueError("Scan scope does not match the requested project")
    if query.get("command") != "scan" or query.get("profile") != "ci":
        raise ValueError("Missing completed CI-profile scan query")
    if query.get("scan_coverage") != state.get("scan_coverage"):
        raise ValueError("Query does not match this scan's detector evidence")
    # 'fast' is intentional: CI excludes slow duplication phases, not a full scan.
    if state.get("scan_completeness", {}).get(language) != "fast":
        raise ValueError("Unexpected scan completeness for CI profile")
    metrics = state.get("codebase_metrics", {}).get(language, {})
    if not isinstance(metrics.get("total_files"), int) or metrics["total_files"] <= 0:
        raise ValueError("Scan did not cover any source files")
    coverage_records = state.get("scan_coverage")
    if not isinstance(coverage_records, dict):
        raise ValueError("Missing detector coverage records")
    coverage = coverage_records.get(language, {})
    if not isinstance(coverage, dict) or coverage.get("status") != "full" or coverage.get("confidence") != 1.0:
        raise ValueError(f"Reduced or missing detector coverage: {coverage}")
    detectors = coverage.get("detectors")
    # All three pinned 1.0 CI plugins persist security coverage. Full native
    # ktlint/tsc invocations have their own evidence checks in run_scan; requiring
    # records the tool never emits would incorrectly reject successful scans.
    if not isinstance(detectors, dict) or "security" not in detectors:
        raise ValueError("Missing required security detector evidence")
    for detector, entry in detectors.items():
        if not isinstance(entry, dict) or entry.get("status") != "full" or entry.get("confidence") != 1.0:
            raise ValueError(f"Reduced detector coverage: {detector}: {entry}")
    if coverage.get("warnings"):
        raise ValueError(f"Detector coverage warnings: {coverage['warnings']}")
    failure = FAILURE_LOG.search(log)
    if failure:
        raise ValueError(f"Scanner logged an incomplete phase: {failure.group(0)}")
    items = state.get("work_items")
    if not isinstance(items, dict) or any(not isinstance(item, dict) for item in items.values()):
        raise ValueError("Missing or invalid work_items in scan state")
    counts = Counter(
        item["detector"] for item in items.values() if item.get("status") == "open"
    )
    return dict(sorted(counts.items()))


def write_report(directory, report):
    (directory / "result.json").write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    lines = [
        f"## Desloppify: {report['project']} — {report['status']}",
        "",
        "Advisory mechanical findings; **not** a clean-code certification or a regression gate.",
        "CI profile deliberately omits slow duplication phases. No subjective reviews were run.",
        "Existing strict compilation, lint, architecture, typecheck and test gates remain separate.",
        "",
    ]
    if report.get("error"):
        # JSON quoting keeps arbitrary exception text out of Markdown structure.
        lines += ["Scan infrastructure failed: " + json.dumps(report["error"]), ""]
    for detector, count in report.get("open_by_detector", {}).items():
        lines.append(f"- `{detector}`: {count} open finding(s)")
    lines += ["", "See scan.log, state JSON and query.json in this job's artifact for details.", ""]
    markdown = "\n".join(lines)
    (directory / "summary.md").write_text(markdown, encoding="utf-8")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with Path(summary).open("a", encoding="utf-8") as output:
            output.write(markdown)
    print(markdown)


def run_scan(project, output_dir, root=ROOT):
    language, scan_path, tools = PROJECTS[project]
    directory = output_dir.resolve() / project
    # Never overwrite local improvement plans or reuse old evidence after a crash.
    directory.mkdir(parents=True, exist_ok=False)
    state_path = directory / f"state-{language}.json"
    command = [
        "desloppify", "--lang", language, "scan", "--path", scan_path,
        "--state", str(state_path), "--profile", "ci", "--no-badge",
    ]
    report = {
        "project": project,
        "language": language,
        "scan_path": scan_path,
        "tool_version": "1.0",
        "profile": "ci",
        "policy": "advisory-findings; strict-scan-integrity",
        "command": command,
        "status": "failed",
    }
    try:
        missing = [tool for tool in ("desloppify", *tools) if not shutil.which(tool)]
        if missing:
            raise ValueError("Missing prerequisites on PATH: " + ", ".join(missing))
        version = subprocess.run(
            ["desloppify", "--version"], cwd=root, check=True,
            capture_output=True, text=True, timeout=30,
        )
        if version.stdout.splitlines()[0].strip() != "desloppify 1.0":
            raise ValueError(f"Expected pinned desloppify 1.0; got {version.stdout!r}")
        env = dict(os.environ, NO_COLOR="1", DESLOPPIFY_NO_BADGE="true", DESLOPPIFY_ROOT=str(root),
                   npm_config_offline="true", npm_config_yes="false")
        if language in {"kotlin", "typescript"}:
            installer = install_kotlin_shim if language == "kotlin" else install_typescript_shim
            shim_directory = installer(root, directory)
            env["PATH"] = shim_directory + os.pathsep + env.get("PATH", "")
        with (directory / "scan.log").open("w", encoding="utf-8") as log:
            result = subprocess.run(
                command, cwd=root, env=env, stdout=log, stderr=subprocess.STDOUT,
                text=True, timeout=600, check=False,
            )
        report["exit_code"] = result.returncode
        if result.returncode != 0:
            raise ValueError(f"Scanner exited {result.returncode}; see scan.log")
        if language == "kotlin":
            linter = read_object(directory / "ktlint-result.json")
            if linter.get("valid_lint_check") is not True:
                raise ValueError("Kotlin lint invocation failed; see ktlint-result.json")
        if language == "typescript":
            compiler = read_object(directory / "typescript-compiler.json")
            if compiler.get("valid_unused_check") is not True:
                raise ValueError("TypeScript unused check failed; see typescript-compiler.json")
        # 1.0 isolates plan.json with --state but still writes a shared query.
        # Copy it immediately; validate matching coverage timestamps below.
        shutil.copyfile(root / ".desloppify/query.json", directory / "query.json")
        counts = validate_snapshot(
            read_object(state_path), read_object(directory / "query.json"),
            language, scan_path, (directory / "scan.log").read_text(encoding="utf-8"),
        )
        report.update(status="complete", open_by_detector=counts, open_findings=sum(counts.values()))
    except (OSError, ValueError, KeyError, TypeError, IndexError, subprocess.SubprocessError) as error:
        report["error"] = f"{type(error).__name__}: {error}"
    write_report(directory, report)
    return 0 if report["status"] == "complete" else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", choices=PROJECTS, required=True)
    parser.add_argument("--output-dir", type=Path, default=ROOT / ".desloppify/ci")
    args = parser.parse_args()
    try:
        return run_scan(args.project, args.output_dir)
    except FileExistsError as error:
        parser.exit(1, f"Refusing to reuse scan artifacts; choose a fresh --output-dir: {error}\n")


if __name__ == "__main__":
    if len(sys.argv) >= 5 and sys.argv[1] == "--kotlin-linter":
        raise SystemExit(run_kotlin_linter(Path(sys.argv[2]), Path(sys.argv[3]), sys.argv[4], sys.argv[5:]))
    if len(sys.argv) >= 4 and sys.argv[1] == "--typescript-compiler":
        raise SystemExit(run_typescript_compiler(Path(sys.argv[2]), Path(sys.argv[3]), sys.argv[4:]))
    raise SystemExit(main())
