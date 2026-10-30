# Corpus fixtures

Pinned real-world Lua projects that Lunar is swept across, to catch regressions no synthetic
snippet reaches: the project *shapes* users actually have.

The unit suite tests behaviour we can state in advance. A corpus sweep tests the opposite — that
nothing gets worse across a body of code nobody wrote expectations for. It is a **ratchet**:
`LuaCorpusSweepTest` measures defect counts and compares them to a recorded baseline, failing only
when a number regresses.

## Usage

```bash
python3 tooling/corpus/fetch-corpus.py
```

Fetches every entry of `corpus.json` into `test/corpus/<name>` (out-of-repo, via the tracked `test`
symlink — the checkouts are never committed; `LUNAR_CORPUS_ROOT` overrides the directory). A
`.corpus-sha` stamp makes a re-run a no-op when a checkout is already at its pin. It then fetches
the definition-library archives the entries pin into `test/corpus/.definitions/` (see
[Definition libraries](#definition-libraries)). Any failure exits `1`.

Then, on the builder:

```bash
tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache --tests '*Corpus*' -PwithCorpus"
```

The sweeps are excluded from the routine `test` loop; `-PwithCorpus` opts in. To rewrite the
baselines after re-pinning the corpus — or after the plugin legitimately improves — add
`-PrecordCorpusBaseline`. The recorded content is echoed to the console as well as written, since
the suite runs on the remote builder.

**Gate and record with the same `--tests '*Corpus*'` filter, never a single member** (BUG-418).
Counts are byte-deterministic per invocation shape, but cross-member JVM state shifts them by a few,
so a member-alone run can read a phantom regression against a full-shape baseline. CI's `corpus`
job (`.gitea/workflows/build-plugin.yml`) runs the gate in the same shape.

## Adding a project

Append an entry to `corpus.json` — the fields are documented in its `$comment`: `name`, `url`,
**a commit SHA resolved from a release tag**, the `roots` to index, `luaLevel`, and optionally
`prune`, `moduleRoot`, `definitionLibraries` and `target` (a non-Standard target such as
`LUAJIT:2.1`). Never pin a branch — the ratchet needs a byte-identical tree across runs. `name` is
lowercase ASCII letters only: the sweep method is `test` + `name` with its first letter
upper-cased + `Corpus` (`lazynvim` → `testLazynvimCorpus`), which must be a Kotlin identifier, and
CI's ratchet diagnostics match `\[corpus:[a-z-]*\]` only. Add that method to `LuaCorpusSweepTest`
and record a baseline with the full `'*Corpus*'` filter (above).

Prune **binaries only** (`luarocks/win32` is 22 vendored `.exe`/`.dll`). Do not prune config or
text ballast: the non-Lua files in a real project are the record of which ecosystem tools it
actually uses, and that is a second thing the corpus is good for — see below.

Prefer projects that differ in *shape* rather than in size: build tooling, an application with a
custom class system, a config-style tree, an embedded/host-API dialect.

## Definition libraries

A project written against a library — a busted test suite, a wxLua application — reports that
library's globals as undeclared unless the sweep enables its definitions, as a user would. An
entry's optional **`definitionLibraries`** names the ids it uses from the plugin's catalog,
`src/main/resources/definitions/lunar-definitions-catalog.json`, e.g. `"definitionLibraries":
["busted"]`. The catalog is the version authority: the manifest repeats no version, and each id
brings its catalog `requires` closure (`busted` brings `luassert`). An id the catalog lacks fails
both `fetch-corpus.py` and the sweep, naming it.

- **Fetch.** `fetch-corpus.py` downloads each archive in the closure to
  `test/corpus/.definitions/<id>-<version>.tar.gz` and accepts it only when its `sha256` and byte
  size equal the catalog's. A file already there that matches is not downloaded again; a mismatch on
  every URL exits `1`, never moves a mismatched download into place, and leaves no `.part` behind. The sweep itself never
  touches the network.
- **Verify.** `python3 tooling/corpus/definition_pins.py --verify test/corpus/.definitions` re-checks
  every archive the manifest's pins resolve to. It prints
  `definitions ok (<n> archives verified in <dir>)` and exits `0`, or exits `1` naming each archive
  that is missing or differs (`definition_pins: not provisioned: …`). CI's `corpus` job runs it in
  its fixture step. `--manifest` and `--catalog` override the two input files.
- **Enable.** Per member, `LuaCorpusSweepTest` stages each archive into the definition cache and
  enables the libraries through `LuaDefinitionLibraryEnabler`, the class behind the settings page,
  so the settings broadcast and the resolve-cache drop run as they do for a user; after the member it
  unticks them and deletes the trees. An archive absent from `test/corpus/.definitions/` fails the
  member with `Run: tooling/corpus/fetch-corpus.py`.
- **Identity.** The baseline records one `library.<id>=<version>` line per library in the closure.
  Any change — a pin added or removed, or a catalog version moved — fails the gate with
  `Definition-library pins changed`, and the change must carry its re-record.

### Changing a pin

Adding, removing or re-pinning a library is a re-record, and every movement it causes must be
attributed. The one procedure for it is MAINT-37's record sequence:
[implementation-plan.md](../../docs/features/maint/37-pinned-definition-libraries/implementation-plan.md),
Phase 4, **record sequence S1–S12** — for a manifest pin and for a catalog re-pin alike. Its outputs
are appended to
[rebaseline-attribution.md](../../docs/features/maint/37-pinned-definition-libraries/rebaseline-attribution.md).
The tools it drives are below.

### Attribution tools

**Attribution arms.** `-PcorpusAttribution=bare|pinned` turns a member-alone sweep into one arm of a
pair:

```bash
tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache --tests 'net.internetisalie.lunar.corpus.LuaCorpusSweepTest.test<Name>Corpus' -PwithCorpus -PcorpusAttribution=<bare|pinned>"
```

The arm writes `build/corpus-attribution/<bare|pinned>/<name>.tally` and neither ratchets nor
records: the tally is the baseline's lines, then the refresh lines `settingsBroadcasts=`,
`rootsTicks=` and `physicalPsiChanges=` (what the member's settings refresh was observed to do),
then the uncapped `allsymbol.<inspection>.<symbol>=` counts. None of these extra lines ever reaches a
baseline. `pinned` enables the member's pins; `bare` enables nothing but gives the member the same
settings refresh. A bare arm that registered a library or recorded no symbol hits fails and writes
no tally, and combining `-PcorpusAttribution` with `-PrecordCorpusBaseline` is an error. The builder
has no pull command, so copy the tallies back with `rsync` before classifying them.

**`attribute_delta.py`** classifies every movement between the two arms of one member:

```bash
python3 tooling/corpus/attribute_delta.py --corpus <name> --bare <dir> --pinned <dir>
```

`<dir>` holds `<name>.tally`. `--definitions` (default `test/corpus/.definitions`) and `--catalog`
locate the archives whose declared globals the classification checks against. It prints a table
for the member; each row is `EXPLAINED`, `EXPECTED`, `UNEXPLAINED` or `TRIAGE-DROP`/`-GLOBAL`/`-CONTRACT`.
A library may only lower undeclared-variable counts for names it declares and resolve requires.
**Exit codes:** `0` no `UNEXPLAINED` row; `1` at least one `UNEXPLAINED` row (the pin is withheld);
`2` the arms are not comparable (`attribute_delta: not comparable: …` on stderr — a different
corpus commit or file count, refresh lines that differ or are missing, a library in the bare arm or
none in the pinned arm, highlight failures, or a pinned version the catalog or the local archives do
not carry).

**`reconcile_baseline.py`** accounts for a re-recorded full-shape baseline, in three modes:

```bash
# pair: a member whose pin changed
python3 tooling/corpus/reconcile_baseline.py --corpus <name> --old <old.baseline> --new <new.baseline> --before <dir> --bare <dir> --pinned <dir>
# no pair: any other member
python3 tooling/corpus/reconcile_baseline.py --corpus <name> --old <old.baseline> --new <new.baseline>
# offset: one record against a member-alone tally of the same configuration
python3 tooling/corpus/reconcile_baseline.py --corpus <name> --offset --old <name.tally> --new <name.baseline>
```

The **pair** mode subtracts the attributed delta (`before` tally → `pinned` tally) from the
committed one (`old` → `new` baseline), and prints the residual table and a separate **refresh
effect** (first pin) or **previous pin effect** (re-pin) table. The **no-pair** mode prints the
committed delta itself as residual. The **offset** mode prints the shape offset between the two
records. A residual blocks when it is on a structural, `crash.*` or `highlightFailures` key, or when
an `inspection.*` residual exceeds ±4 (±2 in offset mode). A refresh-effect or previous-pin-effect
row blocks on a structural, `crash.*` or `highlightFailures` key, except that a previous pin's
`unresolvedRequires` effect blocks only when positive. `--before`, `--bare` and `--pinned` go
together, and `--offset` takes none of them. **Exit codes:** `0` no blocking row; `1` a blocking
row; `2` inconsistent arguments.

### Findings per commit (MAINT-40)

Every non-attribution sweep also writes `build/corpus-findings/<member>.json`: one finding (rule,
path, offsets, line, column, message, symbol) per hit behind the baseline's counters. A
`-PwithCorpus` run empties that directory first, so it holds one invocation's files.
`corpus_ctrf.py` turns them into a CTRF 0.2.0 document, one `failed` test per finding, whose
`testId` is a fingerprint that survives rewording. The committed baselines stay the gate.

- **`assemble`**: `python3 tooling/corpus/corpus_ctrf.py assemble --findings build/corpus-findings
  --manifest tooling/corpus/corpus.json --out corpus.ctrf.json`. The same findings give the same
  bytes. `results.environment.testEnvironment` is the run's identity, `full-<hex>` for the full
  `'*Corpus*'` shape or `member-<name>-<hex>` for one member. Files from two JVMs, or a partial
  set, exit `2`.
- **`store`**: `corpus_ctrf.py store --findings build/corpus-findings --manifest
  tooling/corpus/corpus.json --out build/corpus-ctrf/corpus.ctrf.json` assembles, then puts the
  document against `HEAD` as check `a3` suite `corpus` (`a3.corpus.ctrf.json`), unless `HEAD`
  already has a record: an identical one prints `store: already stored, identical`, a different
  one a `WARNING` line, and both exit `0`. Any run that is not full-shape is refused with exit `2`
  before `loom-cli` is called; a `loom-cli` failure exits `1`. The Gitea `corpus` CI job runs it on
  every run, with the store credentials in its environment.
- **`fetch`**: `corpus_ctrf.py fetch --commit origin/main --out before.ctrf.json [--like DOC]
  [--max-depth N]` reads loom's conformance store (check `a3`, suite `corpus`) through `loom-cli`,
  walking first-parent ancestors. It prints `fetched <sha> identity=… distance=…` and exits `0`;
  `3` when nothing is stored within the depth (default 50); `1` on a `loom-cli` failure. `--like`
  accepts only a document with that document's identity. It needs loom-cli v0.90.0 or later
  (`--suite`).
- **`diff`**: `corpus_ctrf.py diff before.ctrf.json after.ctrf.json` prints `NEW`, `RESOLVED` and
  per-rule `COUNT` lines, sorted, and exits `1` if any finding is new, else `0`. Documents with
  different identities, members, `files` or `requires` print `NOT COMPARABLE: …` and exit `2`.

The `loom-cli` calls behind `store` and `fetch` live in `tooling/ctrf/loom_store.py`, which the
unit-test results tool (`tooling/ctrf/junit_ctrf.py`, MAINT-48) shares.

The Python tools have unit tests:
`python3 -m unittest discover -s tooling/corpus -p 'test_*.py'`.

## What is measured

| Metric | Gated | Meaning |
|---|---|---|
| `files` | identity | Lua files indexed. A change means the checkout is dirty. |
| `parseErrors` | **yes** | `PsiErrorElement`s — syntax the parser cannot represent. |
| `requires` | identity | `require(...)` calls the reference contributor recognised. |
| `unresolvedRequires` | **yes** | Of those, how many resolve to nothing. |
| `library.<id>` | identity | The catalog version of each enabled definition library, dependency closure included. |
| `inspection.<toolId>` | **yes** | Warnings per inspection, attributed by `HighlightInfo.getInspectionToolId()`. The false-positive floor. |
| `inspection.unattributed` | **yes** | Null-id highlights (annotators), net of parse errors. |
| `inspection.highlightFailures` | **yes** | Files whose highlight pass threw — see BUG-390. |
| `ballast.<claimed\|unclaimed>.<key>` | no | Every file the sweep did not index, grouped and marked. |

`parseErrorFile=` lines list the offending files so a regression is triageable from the baseline
diff alone. Wall-clock is printed per project as an advisory `elapsedMs=` line and deliberately
never baselined. Not yet measured: unresolved qualified-name references.

**Identity-checked** means any change fails with a re-record prompt, not just an increase.
`requires` is identity-checked rather than gated because recognised-require coverage may
legitimately *rise* when a resolution bug is fixed (BUG-389 takes luacheck from 3 to ~155),
leaving the old `unresolvedRequires` floor incomparable rather than merely stale.

## The ballast is a second signal

A pinned real project also inventories what the Lua ecosystem expects an IDE to understand — and
unlike the defect metrics, this one is reported rather than gated: a new unclaimed group is a
discovery, not a regression.

Measured across the three pinned projects (2026-08-03):

| Ballast | Count | Status |
|---|---|---|
| `*.tl` | 117 | **Unsupported** — Teal; luarocks core is being ported to it |
| `*_config.luacheckrc` | 18 | **Unsupported** — `plugin.xml` registers the *exact* name `.luacheckrc` only, so `myproject.luacheckrc` gets nothing |
| `config.ld` (`ld`) | 1 | **Unsupported** — LDoc config, plain Lua syntax |
| `.luacov` | 1 | **Unsupported** — luacov *config*; we claim only `luacov.report.out` |
| `*.rockspec` | 85 | Supported (SCHEMA-02) — and a real validation corpus for it |
| `.luacheckrc` / `.busted` | 4 / 2 | Supported (SCHEMA-03 / -04) |

Read `unclaimed` with care: a group is currently marked claimed only when *every* member is, so a
single outlier flips a large group (luacheck's 54 `.rockspec` report unclaimed while luarocks' 31
report claimed). See risks Gap 2.3 — the fix is to record an unclaimed *count* per group.

`plugin.xml` claims `extensions="lua;rockspec"` and `fileNames=".luacheckrc;.busted"`. Every
unsupported entry above is a Lua-syntax file, so association is close to free.

Groups unclaimed purely because the headless fixture lacks the bundled Markdown/YAML plugins
(`md`, `yml`, `sh`, `png`, …) are noise, not findings.

## Note on `test/luacheck`

Unrelated to this corpus. It is a clone of the internal `glimmer/luacheck` fork carrying commits
that do not exist upstream, and belongs to `LuaRecursiveReferenceTest`. The corpus fetches its own
`test/corpus/luacheck` pinned to upstream `lunarmodules/luacheck` v1.2.0.
