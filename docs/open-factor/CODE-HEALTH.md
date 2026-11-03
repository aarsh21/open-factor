# Code health and Desloppify

## Two different kinds of checks

**The existing build, lint, architecture, typecheck and test workflows are strict
validation gates. Desloppify findings are advisory, not a replacement for those
gates.** A green code-health job means the requested mechanical snapshot ran
successfully with its prerequisites; it does not mean all warnings were fixed.
The Android code-health job additionally enforces a **strict Kotlin-aware
unused-import gate**, using ktlint 1.5.0 with
[`tooling/kotlin-imports.editorconfig`](../../tooling/kotlin-imports.editorconfig).
That focused native lint check is distinct from advisory scanner findings and
does not declare the rest of ktlint's formatting warnings fixed. The Python job
also blocks unused imports, redefinitions and undefined names with Ruff
(`F401,F811,F821`) across `tooling/` and the backend's Python helpers.

The separate [code-health workflow](../../.github/workflows/open-factor-code-health.yml)
runs on relevant pull requests, pushes to `main`, and manual dispatch. It does not
change the Android, media, or release workflows. It has read-only repository
permissions and does not resolve findings, apply fixes, import scores, run LLM
reviewers, or upload source to a review service. Its only uploaded results are
GitHub Actions scan artifacts.

| Scope | Scanner | Additional prerequisites |
| --- | --- | --- |
| `openfactor/` | Kotlin | JDK 17, ktlint **1.5.0** |
| `tooling/` | Python | Ruff **0.14.14**, Bandit **1.9.4** on `PATH` |
| `backend/media/` | TypeScript | Node 22, dependencies installed with Bun **1.4.4** and the committed lockfile |

The scanner is pinned to **`desloppify[full]==1.0`**. Tree-sitter and its language
pack are pinned as well; see
[`tooling/requirements-code-health.txt`](../../tooling/requirements-code-health.txt).
These are isolated tooling dependencies, not Android or service runtime
requirements. Transitive Python packages are still resolved at installation;
this is not a fully hermetic dependency lock. CI downloads ktlint's standalone
JAR from Maven Central and verifies the committed SHA-256 before using it:

```text
a27854622198800f50971a049dcdba38f2105d47e7e7258786c7d28045c5735d
```

## Policy: advisory findings, strict scan integrity

Every invocation uses `scan --profile ci --no-badge` and fresh project-specific
state. The CI profile intentionally omits slow duplication phases and subjective
review phases. It is a **fast mechanical snapshot**, not a full audit. Raw scores
are retained in the scanner's JSON, but are not used as pass/fail thresholds.
Overall/strict scores incorporate unassessed subjective placeholders; gating on
one would not be a meaningful regression policy.

The runner fails on missing prerequisites, a different scanner version, a
nonzero scan exit, timeout, missing/malformed evidence, an empty source scan,
wrong project/profile, reduced detector coverage, or recognized logged detector
failures even if the scanner exits zero. All three pinned CI plugins must persist
their security-detector coverage record; a matching query with an absent or empty
detector map is not sufficient evidence. Native ktlint and TypeScript invocation
evidence is checked separately. Findings such as unused imports, complexity and
security warnings do not themselves fail this advisory workflow.
No `continue-on-error` or `|| true` hides scan infrastructure failures.

Reviewers should inspect newly introduced findings in the artifact and validate
credible issues with the native tools and behavioral tests. There is **no reviewed
Desloppify finding baseline yet**, and this workflow does not claim to enforce
zero new warnings. To introduce a blocking regression policy later:

1. Review a pinned-tool snapshot by detector and file, against native validation.
2. Record accepted findings/false positives with a specific rationale and owner;
   do not copy an unreviewed score or all raw finding IDs into an allowlist.
3. Compare new findings only for explicitly trusted detectors and stable scopes.
   Fail on missing coverage, not just on increased counts.
4. Review tool/configuration updates and baseline changes together. Do not allow
   broad suppressions to conceal new issues.

### Compatibility checks in the runner

Desloppify 1.0 places `plan.json` beside an explicitly selected state file, but
still writes `query.json` to the repository's shared `.desloppify/` directory.
The runner immediately copies that query into the project artifact and checks
that its detector coverage (including scan timestamps) matches the state. CI
projects run in separate matrix checkouts. **Do not run scans concurrently in the
same local checkout**; separate `--state` paths alone do not isolate queries in
this version.

The Kotlin plugin expects a flat JSON diagnostic array, but ktlint 1.5.0 emits
nested per-file `errors` arrays and can mix log messages into standard output.
Without adaptation, the scanner reports reduced coverage even though ktlint is
installed. A scan-local `ktlint` shim invokes the real linter without changing
source, writes its JSON reporter to `ktlint-report.json`, checks the exit status
and schema, and passes flattened diagnostics to Desloppify. `ktlint.log` and
`ktlint-result.json` retain operational evidence. No rules are disabled by this
adapter. Multiple violations at one file/line may be merged by the scanner's
finding IDs; the raw ktlint report retains every diagnostic. Existing formatting
warnings remain advisory and are **not** claimed fixed by the import cleanup.

The TypeScript plugin also hard-codes a root `tsconfig.app.json`, which this
repository does not have, and can mistake compiler errors for a clean unused
check. For this version only, the runner supplies a scan-local `npx` shim that
handles **only** the plugin's `tsc` invocation. It runs the locked media compiler
against `backend/media/tsconfig.json` with `--noUnusedLocals`,
`--noUnusedParameters`, `--noEmit`, and plain diagnostics. It records the actual
command, exit code, original diagnostics and normalized diagnostics in
`typescript-compiler.json`. A clean compiler exit or recognized unused-only
diagnostics (`TS6133`, `TS6138`, `TS6192`, `TS6196`, `TS6198`, `TS6199`) are accepted.
TypeScript 7.0.2 reports these with exit 1; older compilers can use exit 2. The
adapter validates the diagnostic content, not just the exit status, and normalizes
unused types/properties and binding groups to the pinned scanner's supported
format. Grouped unused bindings retain their source location and a descriptive
group name; the untouched original compiler output stays in the evidence file.
Missing compiler evidence, configuration errors, unknown diagnostics and mixed
unused/type errors fail the scan job instead of silently passing.

The media CI job runs an actual-compiler contract test after installing the locked
TypeScript 7.0.2 dependency. It exercises locals, parameters, interfaces, aliases,
private properties, destructuring and genuine/mixed type errors; mocked exit
codes alone are not sufficient. This adapter does not change source or committed
compiler configuration. The separate media workflow still runs its normal strict
`bun test` and `bun run typecheck` commands.

## Known interpretation limits

- Kotlin's generic unused-import detector has reported wildcard imports as unused
  even when referenced symbols compile correctly. Its orphan detector reports
  Gradle entry points, tests, same-package references and framework entry points
  without understanding all Kotlin/Gradle semantics. Do not delete them based on
  these warnings. Use Kotlin-aware lint/compilation and runtime tests.
- ktlint provides Kotlin syntax-aware lint, **not** complete compiler or Android
  semantic validation. Keep the app's compilation, unit tests, Android lint,
  architecture contracts and API 26/35 device tests as independent strict gates.
  Desloppify's advisory ktlint findings do not themselves enforce a strict ktlint
  policy. The separate focused import command below is blocking in this workflow;
  it is not a full formatting policy.
- Python smoke tooling intentionally uses assertions and subprocesses. Bandit
  warnings require a trust-boundary/data-flow review, not blanket removal or a
  global skip list. This integration does not suppress those categories.
- Test-health scores are static heuristics, not measured runtime coverage.
  Security scores are not security certifications. The CI profile's omitted
  duplication phases must not be represented as fully checked or clean.
- These scopes are not an all-language repository audit. In particular, Python
  helpers inside the media backend, release Bash scripts, promo helpers and HTML
  compositions are not independently scanned here.
- The runner checks the pinned tool's persisted coverage and known log failure
  messages. It cannot certify that every upstream detector reports every internal
  failure. Revalidate these contracts with actual scans when upgrading the tool.

## Initial remediation and remaining work

The first remediation pass removed Kotlin-aware unused imports, reused the shared
name dialog, and added device tests for blank names, trimming and cancellation.
It also removed an offline-tooling import cycle and source-text `exec`, hardened
licensed-preview URL/provenance checks and media staging, bounded subprocesses,
and added runtime media-index validation and S3 adapter contract tests.

This is **not** a finding-free baseline. Important remaining categories are:

- **Kotlin style and structure:** the full linter still reports wrapping,
  formatting, naming and wildcard-import policy differences. Large Compose
  screens and command surfaces remain refactoring candidates. The focused import
  cleanup is not a claim that these architectural/style decisions were resolved.
- **Generic Kotlin import/dependency/test warnings:** wildcard imports, same-package
  references, Gradle entry points and discovered tests need compiler/framework
  context. Sampled false positives are documented above, not grounds to assume
  every warning in those categories is false.
- **Python trust-boundary warnings:** intentional acceptance assertions,
  argument-vector subprocess calls and controlled-emulator XML parsing remain
  visible. The dictionary-flow detector also overlooks unpacked preferences and
  serialized staged result states. It mislabels the CI runner's deliberate
  `failed` to `complete` result transition as an unread overwrite.
- **Procedural smoke flows:** onboarding now follows the current three-step
  profile setup and Settings → Profile → Review setup path. Its control-flow and
  preservation tests are distinct from live-device evidence. Other historical
  program/UI flows still need their own recertification; instrumentation tests
  and host tests do not imply every separate ADB smoke script passes.
- **Backend entry point and route complexity:** HTTP/storage contract tests do not
  certify a deployed service's startup, signals, credentials or real S3 provider.
  The startup module and cohesive route handlers remain review candidates.
- **Subjective review:** no naming, architecture or other subjective assessment
  has been imported. Unassessed placeholders must not be presented as reviews.

No raw category was globally suppressed, and remaining findings were not marked
fixed or accepted solely to improve scores. Use the uploaded fresh snapshots to
review the remaining work rather than treating green advisory scans as closure.

## Reproduce locally

Run from the repository root. Use Python 3.12 (CI's version), and keep the tooling
virtual environment **outside the repository** so scanners cannot count installed
third-party source as application code:

```sh
python3 -m venv "$HOME/.cache/open-factor-code-health-1.0"
. "$HOME/.cache/open-factor-code-health-1.0/bin/activate"
python -m pip install -r tooling/requirements-code-health.txt
python3 -I -m unittest discover -s tooling -p 'test_desloppify_ci.py' -v
```

For Android, install JDK 17 and put ktlint 1.5.0 on `PATH`; the workflow contains
the checksum-verified standalone-JAR installation. An Android SDK is not required
for the mechanical scan, but **is** required for the native build/lint/test gates.
The workflow runs this strict import check independently of the advisory scan:

```sh
ktlint --log-level=none --editorconfig=tooling/kotlin-imports.editorconfig \
  'openfactor/**/*.kt' 'openfactor/**/*.kts' '!openfactor/**/build/**'
```

For media, use the pinned Bun version and install the lockfile first:

```sh
(cd backend/media && bun install --frozen-lockfile)
python3 -I tooling/desloppify_ci.py --project android
python3 -I tooling/desloppify_ci.py --project tooling
python3 -I tooling/desloppify_ci.py --project media
```

Default output paths are `.desloppify/ci/<project>/`, each containing its own
`state-<language>.json`, copied `query.json`, `scan.log`, `result.json` and
`summary.md`. The Android project also records the raw ktlint report and lint
invocation evidence; the media project records compiler evidence. These generated
files stay ignored by Git. The runner refuses to reuse an existing project output
directory, including a failed run, to prevent stale success evidence and avoid
overwriting a local improvement plan. Choose a new directory to scan again:

```sh
python3 -I tooling/desloppify_ci.py --project android --output-dir .desloppify/local-pass-2
```

CI adds the human-readable summary to each job and uploads the project directory
with `if: always()`, explicitly including hidden files under `.desloppify/`.
Successful and failed scan evidence is retained for 14 days. A prerequisite setup
failure can occur before any scan artifacts exist; that job still fails and its
setup logs remain available. Existing strict validation commands are documented
in [CONTRIBUTING.md](../../CONTRIBUTING.md) and
[VALIDATION.md](VALIDATION.md).
