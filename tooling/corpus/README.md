# Corpus fixtures

Pinned real-world Lua projects that Lunar is swept across, to catch regressions no synthetic
snippet reaches: the project *shapes* users actually have.

The unit suite tests behaviour we can state in advance. A corpus sweep tests the opposite — that
nothing gets worse across a body of code nobody wrote expectations for. It is a **ratchet**:
`LuaCorpusSweepTest` measures defect counts and compares them to a recorded baseline, failing only
when a number regresses.

## Usage

```bash
tooling/corpus/fetch-corpus.sh
```

Fetches every row of `corpus.tsv` into `test/corpus/<name>` (out-of-repo, via the tracked `test`
symlink — the checkouts are never committed). Re-running is a no-op when already at the pin.

Then, on the builder:

```bash
tooling/gce-builder/gce-builder.sh run "test --tests *Corpus* -PwithCorpus"
```

The sweeps are excluded from the routine `test` loop; `-PwithCorpus` opts in. To rewrite the
baselines after re-pinning the corpus — or after the plugin legitimately improves — add
`-PrecordCorpusBaseline`. The recorded content is echoed to the console as well as written, since
the suite runs on the remote builder.

## Adding a project

Append a row to `corpus.tsv`: name, clone URL, **a commit SHA resolved from a release tag**, the
subdirectories to index, and optionally directories to delete after checkout. Never pin a branch —
the ratchet needs a byte-identical tree across runs. Then add a `@Test` to `LuaCorpusSweepTest` and
record a baseline.

Prune **binaries only** (`luarocks/win32` is 22 vendored `.exe`/`.dll`). Do not prune config or
text ballast: the non-Lua files in a real project are the record of which ecosystem tools it
actually uses, and that is a second thing the corpus is good for — see below.

Prefer projects that differ in *shape* rather than in size: build tooling, an application with a
custom class system, a config-style tree, an embedded/host-API dialect.

## What is measured

| Metric | Gated | Meaning |
|---|---|---|
| `files` | identity | Lua files indexed. A change means the checkout is dirty. |
| `parseErrors` | **yes** | `PsiErrorElement`s — syntax the parser cannot represent. |
| `requires` | identity | `require(...)` calls the reference contributor recognised. |
| `unresolvedRequires` | **yes** | Of those, how many resolve to nothing. |
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
