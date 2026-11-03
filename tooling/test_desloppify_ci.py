"""Offline tests of the pinned scanner contract and fail-closed CI policy."""

import copy
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


_spec = importlib.util.spec_from_file_location("desloppify_ci", Path(__file__).with_name("desloppify_ci.py"))
ci = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(ci)


def snapshot(language="python", scan_path="tooling"):
    state = {
        "scan_count": 1,
        "last_scan": "2026-11-04T00:00:00+00:00",
        "scan_path": scan_path,
        "scan_completeness": {language: "fast"},
        "codebase_metrics": {language: {"total_files": 3}},
        "scan_coverage": {
            language: {
                "status": "full", "confidence": 1.0, "warnings": [],
                "updated_at": "2026-11-04T00:00:00+00:00",
                "detectors": {"security": {"status": "full", "confidence": 1.0}},
            }
        },
        "work_items": {
            "open": {"status": "open", "detector": "security"},
            "fixed": {"status": "fixed", "detector": "unused"},
        },
    }
    query = {"command": "scan", "profile": "ci", "scan_coverage": copy.deepcopy(state["scan_coverage"])}
    return state, query


class SnapshotValidationTest(unittest.TestCase):
    def validate(self, state, query, log="Scan complete\n"):
        return ci.validate_snapshot(state, query, "python", "tooling", log)

    def test_open_findings_are_advisory_not_an_overall_score_gate(self):
        state, query = snapshot()
        state["overall_score"] = 0
        state["strict_score"] = 0
        self.assertEqual({"security": 1}, self.validate(state, query))

    def test_each_language_has_its_own_scope_and_contract(self):
        for language, scope, _ in ci.PROJECTS.values():
            with self.subTest(language=language):
                state, query = snapshot(language, scope)
                self.assertEqual({"security": 1}, ci.validate_snapshot(state, query, language, scope, ""))

    def test_incomplete_or_wrong_scope_snapshots_fail(self):
        cases = [
            ("scan_count", 0), ("scan_count", 2), ("last_scan", None),
            ("scan_path", "."), ("scan_completeness", {"python": "full"}),
            ("codebase_metrics", {"python": {"total_files": 0}}),
            ("codebase_metrics", {}), ("scan_coverage", {}),
            ("work_items", None), ("work_items", {"broken": "item"}),
        ]
        for key, value in cases:
            with self.subTest(key=key, value=value):
                state, query = snapshot()
                state[key] = value
                with self.assertRaises(ValueError):
                    self.validate(state, query)

    def test_missing_or_stale_query_fails(self):
        for change in ({"profile": "full"}, {"command": "status"}, {"scan_coverage": {}}):
            state, query = snapshot()
            query.update(change)
            with self.subTest(change=change), self.assertRaises(ValueError):
                self.validate(state, query)

    def test_zero_exit_but_reduced_detector_coverage_fails(self):
        for change in (
            {"status": "reduced"}, {"confidence": 0.6},
            {"warnings": [{"summary": "missing bandit"}]},
            {"detectors": {"ktlint_violation": {"status": "reduced", "confidence": 0}}},
        ):
            state, query = snapshot()
            state["scan_coverage"]["python"].update(change)
            query["scan_coverage"] = copy.deepcopy(state["scan_coverage"])
            with self.subTest(change=change), self.assertRaises(ValueError):
                self.validate(state, query)

    def test_missing_or_malformed_detector_evidence_fails_even_when_query_matches(self):
        for detectors in (None, {}, [], {"other": {"status": "full", "confidence": 1.0}},
                          {"security": None}, {"security": {}}):
            with self.subTest(detectors=detectors):
                state, query = snapshot()
                if detectors is None:
                    del state["scan_coverage"]["python"]["detectors"]
                else:
                    state["scan_coverage"]["python"]["detectors"] = detectors
                query["scan_coverage"] = copy.deepcopy(state["scan_coverage"])
                with self.assertRaises(ValueError):
                    self.validate(state, query)

    def test_detects_logged_failures_even_with_complete_state(self):
        for log in (
            "jscpd timed out after 120s", "Tooling unavailable (tool_timeout)",
            "Coverage reduced (security)", "Phase imports failed",
            "Traceback (most recent call last):", "detector foo crashed",
        ):
            with self.subTest(log=log), self.assertRaises(ValueError):
                self.validate(*snapshot(), log)

    def test_intentional_fast_profile_and_unassessed_reviews_are_not_failures(self):
        log = "Fast scan — slow phases (duplicates) skipped\n20 subjective dimensions are unassessed\n"
        self.assertEqual({"security": 1}, self.validate(*snapshot(), log))

    def test_advisory_timeout_findings_are_not_scanner_timeouts(self):
        log = "smells: subprocess call without timeout\nsmells::module.py::missing_timeout\n"
        self.assertEqual({"security": 1}, self.validate(*snapshot(), log))
        with self.assertRaises(ValueError):
            self.validate(*snapshot(), "subprocess.TimeoutExpired: ktlint")


class RunnerTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.output = self.root / "reports"
        self.directory = self.output / "tooling"
        self.summary = self.root / "github-summary.md"
        self.addCleanup(patch.stopall)
        patch.dict(os.environ, {"GITHUB_STEP_SUMMARY": str(self.summary)}).start()
        patch("sys.stdout", new_callable=io.StringIO).start()
        patch.object(ci.shutil, "which", side_effect=lambda name: f"/bin/{name}").start()

    def fake_run(self, command, **kwargs):
        if "--version" in command:
            return subprocess.CompletedProcess(command, 0, stdout="desloppify 1.0\nPython 3.12\n")
        state, query = snapshot()
        state_path = Path(command[command.index("--state") + 1])
        state_path.write_text(json.dumps(state), encoding="utf-8")
        query_path = self.root / ".desloppify/query.json"
        query_path.parent.mkdir(exist_ok=True)
        query_path.write_text(json.dumps(query), encoding="utf-8")
        kwargs["stdout"].write("Scan complete\n")
        return subprocess.CompletedProcess(command, 0)

    def run_scan(self):
        return ci.run_scan("tooling", self.output, self.root)

    def test_success_keeps_advisory_findings_and_all_evidence(self):
        with patch.object(ci.subprocess, "run", side_effect=self.fake_run) as run:
            self.assertEqual(0, self.run_scan())
        report = ci.read_object(self.directory / "result.json")
        self.assertEqual("complete", report["status"])
        self.assertEqual(1, report["open_findings"])
        command = run.call_args.args[0]
        self.assertEqual("ci", command[command.index("--profile") + 1])
        self.assertIn("--no-badge", command)
        self.assertEqual(self.directory / "state-python.json", Path(command[command.index("--state") + 1]))
        self.assertEqual(self.root, run.call_args.kwargs["cwd"])
        self.assertEqual("true", run.call_args.kwargs["env"]["npm_config_offline"])
        self.assertTrue((self.directory / "query.json").is_file())
        self.assertIn("Advisory mechanical findings", self.summary.read_text())
        with self.assertRaises(FileExistsError):
            self.run_scan()

    def test_missing_prerequisite_fails_without_launching_scan(self):
        with patch.object(ci.shutil, "which", return_value=None), patch.object(ci.subprocess, "run") as run:
            self.assertEqual(1, self.run_scan())
        run.assert_not_called()
        self.assertIn("Missing prerequisites", ci.read_object(self.directory / "result.json")["error"])

    def test_wrong_tool_version_fails(self):
        version = subprocess.CompletedProcess([], 0, stdout="desloppify 2.0\n")
        with patch.object(ci.subprocess, "run", return_value=version) as run:
            self.assertEqual(1, self.run_scan())
        self.assertEqual(1, run.call_count)

    def test_crash_timeout_and_missing_state_are_never_success(self):
        for failure in (subprocess.CompletedProcess([], 3), subprocess.TimeoutExpired("scan", 600), None):
            with self.subTest(failure=failure):
                # Each attempt needs fresh evidence; previously failed runs stay intact.
                self.output = self.root / f"attempt-{len(list(self.root.iterdir()))}"
                self.directory = self.output / "tooling"

                def fail(command, **kwargs):
                    if "--version" in command:
                        return self.fake_run(command, **kwargs)
                    kwargs["stdout"].write("partial scan output\n")
                    if isinstance(failure, Exception):
                        raise failure
                    return failure or subprocess.CompletedProcess(command, 0)

                with patch.object(ci.subprocess, "run", side_effect=fail):
                    self.assertEqual(1, self.run_scan())
                self.assertEqual("failed", ci.read_object(self.directory / "result.json")["status"])
                self.assertIn("partial scan output", (self.directory / "scan.log").read_text())

    def test_corrupt_state_is_reported_without_losing_log(self):
        def corrupt(command, **kwargs):
            result = self.fake_run(command, **kwargs)
            if "--state" in command:
                Path(command[command.index("--state") + 1]).write_text("{broken", encoding="utf-8")
            return result

        with patch.object(ci.subprocess, "run", side_effect=corrupt):
            self.assertEqual(1, self.run_scan())
        self.assertIn("JSONDecodeError", ci.read_object(self.directory / "result.json")["error"])
        self.assertTrue((self.directory / "scan.log").is_file())


class KotlinAdapterTest(unittest.TestCase):
    def test_nested_report_preserves_every_rule_and_message(self):
        report = [{"file": "app/Example.kt", "errors": [
            {"line": 1, "column": 2, "rule": "standard:no-unused-imports", "message": "Unused import"},
            {"line": 1, "column": 3, "rule": "standard:indent", "message": "Unexpected indent"},
        ]}]
        self.assertEqual([
            {"file": "app/Example.kt", "line": 1, "message": "standard:no-unused-imports: Unused import"},
            {"file": "app/Example.kt", "line": 1, "message": "standard:indent: Unexpected indent"},
        ], ci.flatten_ktlint_report(report))

    def test_unknown_report_shapes_fail_closed(self):
        for report in ({}, ["wrong"], [{"file": "a.kt"}], [{"file": "a.kt", "errors": [None]}]):
            with self.subTest(report=report), self.assertRaises(ValueError):
                ci.flatten_ktlint_report(report)

    def test_reporter_writes_raw_evidence_and_only_flat_json_to_stdout(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            (directory / "ktlint-report.json").write_text("[]")
            result = subprocess.CompletedProcess([], 0, stdout="informational log\n", stderr="")
            with patch.object(ci.subprocess, "run", return_value=result) as run, patch(
                "sys.stdout", new_callable=io.StringIO
            ) as output:
                self.assertEqual(0, ci.run_kotlin_linter(directory, directory, "/tools/ktlint", ["--reporter=json"]))
            self.assertEqual([], json.loads(output.getvalue()))
            self.assertIn("informational log", (directory / "ktlint.log").read_text())
            self.assertTrue(ci.read_object(directory / "ktlint-result.json")["valid_lint_check"])
            self.assertIn("--log-level=none", run.call_args.args[0])
            self.assertEqual(directory / "openfactor", run.call_args.kwargs["cwd"])

    def test_unexpected_exit_code_cannot_be_treated_as_clean_lint(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            (directory / "ktlint-report.json").write_text("[]")
            result = subprocess.CompletedProcess([], 2, stdout="", stderr="failure")
            with patch.object(ci.subprocess, "run", return_value=result), self.assertRaises(ValueError):
                ci.run_kotlin_linter(directory, directory, "/tools/ktlint", ["--reporter=json"])
            self.assertFalse(ci.read_object(directory / "ktlint-result.json")["valid_lint_check"])

    def test_lint_shim_never_accepts_an_autofix_command(self):
        with self.assertRaises(ValueError):
            ci.run_kotlin_linter(Path("/repo"), Path("/tmp"), "/tools/ktlint", ["--format"])


class TypeScriptAdapterTest(unittest.TestCase):
    def test_compiler_uses_real_backend_config_and_allows_only_unused_diagnostics(self):
        unused = "backend/media/src/app.ts(1,1): error TS6133: 'unused' is declared but its value is never read.\n"
        cases = [
            (0, "", True), (1, unused, True), (2, unused, True),
            (1, "backend/media/src/app.ts(1,1): error TS6192: All imports in import declaration are unused.\n", True),
            (1, "backend/media/src/app.ts(1,1): error TS6196: 'Unused' is declared but never used.\n", True),
            (1, "backend/media/src/app.ts(1,1): error TS6138: Property 'unused' is declared but its value is never read.\n", True),
            (1, "backend/media/src/app.ts(1,1): error TS6198: All destructured elements are unused.\n", True),
            (1, "backend/media/src/app.ts(1,1): error TS6199: All declarations of 'unused' are unused.\n", True),
            (1, unused + "error TS2307: Cannot find module 'hono'.\n", False),
            (2, "error TS2307: Cannot find module 'hono'.\n", False),
            (2, "invalid compiler configuration\n", False),
            (1, "compiler crashed\n", False),
            (0, unused, False), (1, "", False), (3, unused, False),
            (1, "backend/media/src/app.ts(1,1): error TS6196: Unsupported changed diagnostic.\n", False),
        ]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            evidence = root / "typescript-compiler.json"
            for exit_code, diagnostics, valid in cases:
                result = subprocess.CompletedProcess([], exit_code, stdout=diagnostics, stderr="")
                with self.subTest(exit_code=exit_code, diagnostics=diagnostics), patch.object(
                    ci.subprocess, "run", return_value=result
                ) as run, patch("sys.stdout", new_callable=io.StringIO):
                    self.assertEqual(exit_code, ci.run_typescript_compiler(
                        root, evidence, ["tsc", "--project", "tsconfig.desloppify.json", "--noEmit"]
                    ))
                report = ci.read_object(evidence)
                self.assertEqual(valid, report["valid_unused_check"])
                self.assertEqual(diagnostics, report["diagnostics"])
                command = run.call_args.args[0]
                self.assertIn(str(root / "backend/media/tsconfig.json"), command)
                self.assertIn("--noUnusedLocals", command)
                self.assertIn("--noUnusedParameters", command)
                self.assertNotIn("tsconfig.desloppify.json", command)

    def test_pinned_compiler_contract_with_real_unused_and_type_diagnostics(self):
        compiler = ci.ROOT / "backend/media/node_modules/.bin/tsc"
        if not compiler.is_file():
            if os.environ.get("OPENFACTOR_REQUIRE_TYPESCRIPT_CONTRACT") == "1":
                self.fail("The pinned media compiler must be installed for this contract test")
            self.skipTest("Install the locked media dependencies to run the real compiler contract")
        package = ci.read_object(ci.ROOT / "backend/media/node_modules/typescript/package.json")
        self.assertEqual("7.0.2", package["version"])
        cases = [
            ("clean", "export const value = 1;", True, None),
            ("unused-local", "export {}; const unused = 1;", True, "6133"),
            ("unused-parameter", "export function answer(unused: number) { return 42; }", True, "6133"),
            ("unused-interface", "export {}; interface Unused { value: number }", True, "6196"),
            ("unused-type", "export {}; type Unused = number;", True, "6196"),
            ("unused-property", "export class Value { constructor(private unused: number) {} }", True, "6138"),
            ("unused-quoted-property", 'export class Value { private "unused value" = 1; }', True, "6133"),
            ("unused-quote-in-name", "export class Value { private \"it's unused\" = 1; }", True, "6133"),
            ("unused-empty-property", 'export class Value { private "" = 1; }', True, "6133"),
            ("unused-destructure", "export {}; const { left, right } = { left: 1, right: 2 };", True, "6198"),
            ("type-error", "export const value: number = 'invalid';", False, "2322"),
            ("mixed", "export const value: number = 'invalid'; const unused = 1;", False, "2322"),
        ]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            backend = root / "backend/media"
            binary = backend / "node_modules/.bin/tsc"
            binary.parent.mkdir(parents=True)
            binary.symlink_to(compiler.resolve())
            (backend / "tsconfig.json").write_text(json.dumps({
                "compilerOptions": {"strict": True, "target": "ES2022", "types": [], "skipLibCheck": True},
                "include": ["fixture.ts"],
            }))
            evidence = root / "compiler.json"
            for name, source, valid, code in cases:
                with self.subTest(case=name), patch("sys.stdout", new_callable=io.StringIO) as output:
                    (backend / "fixture.ts").write_text(source)
                    status = ci.run_typescript_compiler(root, evidence, ["tsc"])
                    report = ci.read_object(evidence)
                    self.assertEqual(valid, report["valid_unused_check"], report["diagnostics"])
                    if code:
                        self.assertNotEqual(0, status)
                        self.assertIn(f"error TS{code}:", report["diagnostics"])
                    else:
                        self.assertEqual(0, status)
                    if valid and code:
                        self.assertIn("error TS6133:", output.getvalue())
                        self.assertNotIn("error TS6196:", output.getvalue())
                        if name in {"unused-quoted-property", "unused-quote-in-name", "unused-empty-property"}:
                            self.assertIn("'(unused-symbol)'", output.getvalue())
                            self.assertIn("'(unused-symbol)'", report["normalized_diagnostics"])

    def test_adapter_refuses_other_npx_commands(self):
        with self.assertRaises(ValueError):
            ci.run_typescript_compiler(Path("/repo"), Path("/tmp/report.json"), ["jscpd"])

    def test_adapter_requires_installed_compiler_and_quotes_paths(self):
        with tempfile.TemporaryDirectory(prefix="scan space ") as temporary:
            root = Path(temporary)
            directory = root / "reports"
            directory.mkdir()
            with self.assertRaises(ValueError):
                ci.install_typescript_shim(root, directory)
            compiler = root / "backend/media/node_modules/.bin/tsc"
            compiler.parent.mkdir(parents=True)
            compiler.touch()
            shim_directory = Path(ci.install_typescript_shim(root, directory))
            shim = shim_directory / "npx"
            self.assertTrue(os.access(shim, os.X_OK))
            self.assertIn("--typescript-compiler", shim.read_text())
            self.assertIn(' "$@"', shim.read_text())

    def test_missing_or_broken_compiler_evidence_fails_media_scan(self):
        for compiler_report in (None, {"valid_unused_check": False}):
            with self.subTest(report=compiler_report), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                compiler = root / "backend/media/node_modules/.bin/tsc"
                compiler.parent.mkdir(parents=True)
                compiler.touch()

                def fake_run(command, **kwargs):
                    if "--version" in command:
                        return subprocess.CompletedProcess(command, 0, stdout="desloppify 1.0\n")
                    if compiler_report is not None:
                        evidence = root / "reports/media/typescript-compiler.json"
                        evidence.write_text(json.dumps(compiler_report))
                    return subprocess.CompletedProcess(command, 0)

                with patch.object(ci.shutil, "which", return_value="/bin/tool"), patch.object(
                    ci.subprocess, "run", side_effect=fake_run
                ), patch("sys.stdout", new_callable=io.StringIO), patch.dict(os.environ, {"GITHUB_STEP_SUMMARY": ""}):
                    self.assertEqual(1, ci.run_scan("media", root / "reports", root))
                self.assertEqual("failed", ci.read_object(root / "reports/media/result.json")["status"])


if __name__ == "__main__":
    unittest.main()
