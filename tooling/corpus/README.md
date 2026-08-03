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

`parseErrorFile=` lines list the offending files so a regression is triageable from the baseline
diff alone.

Not yet measured, in rough priority order: inspection hit counts (the false-positive rate is
probably the highest-value signal here), indexing wall-clock, and unresolved qualified-name
references.

## The ballast is a second signal

A pinned real project also inventories what the Lua ecosystem expects an IDE to understand. From
the first two checkouts alone:

| Ballast | Count | Status |
|---|---|---|
| `*.rockspec` | 84 | Supported (SCHEMA-02) — and a real validation corpus for it |
| `.luacheckrc` | 22 | Supported (SCHEMA-03), incl. deeply nested ones |
| `.busted` | 2 | Supported (SCHEMA-04) |
| `*.tl` + `tlconfig.lua` | 118 | **Unsupported** — luarocks core is being ported to Teal |
| `config.ld` | 1 | **Unsupported** — LDoc config, plain Lua syntax |
| `.luacov` | 1 | **Unsupported** — luacov *config*; we claim only `luacov.report.out` |

`plugin.xml` currently claims `extensions="lua;rockspec"` and `fileNames=".luacheckrc;.busted"`.
The three unsupported entries above are all Lua-syntax files, so association is close to free.

Worth turning into a measured metric rather than a one-off observation: have the sweep inventory
non-`.lua` files by extension and flag the ones no registered file type claims, so every corpus
addition surfaces integration candidates automatically.

## Note on `test/luacheck`

Unrelated to this corpus. It is a clone of the internal `glimmer/luacheck` fork carrying commits
that do not exist upstream, and belongs to `LuaRecursiveReferenceTest`. The corpus fetches its own
`test/corpus/luacheck` pinned to upstream `lunarmodules/luacheck` v1.2.0.
