---
id: "MAINT-35-RISKS"
title: "Risks and Gaps"
type: "risk"
parent_id: "MAINT-35"
folders:
  - "[[features/maint/35-parser-torture-testing/requirements|requirements]]"
---

# MAINT-35: Risks and Gaps

## De-risking Tasks

### MAINT-35-00-DR-01 — What does the oracle actually say about today's corpus? [Must]

The feature is justified by BUG-392, a single known false reject. Nobody has run `luac -p` across
the corpus, so the disagreement count is **unknown** — it could be 0, or it could be hundreds
(luarocks and ZeroBrane both carry Lua that Lunar indexes at `LUA51`).

**Action**: throwaway harness — `luac5.1 -p` over every file in all four members, diffed against the
recorded `parseErrors`. Record the count and a representative sample.

**Why it gates the plan**: the number decides whether Phase 4 is "record a clean baseline" or "file
N defects first". A large count is not a reason to weaken the gate; it is the finding.

#### ANSWERED 2026-08-05 — and it changes the design

Ran `luac5.1 -p` over all 419 indexed files in the four members and diffed against each baseline's
recorded `parseErrorFile` entries:

| member | files | luac rejects | Lunar rejects | agree | **false reject** | **false accept** |
| :-- | --: | --: | --: | --: | --: | --: |
| luacheck | 132 | 4 | 2 | 2 | **0** | **2** |
| luarocks | 159 | 0 | 0 | 0 | 0 | 0 |
| zerobrane | 72 | 0 | 0 | 0 | 0 | 0 |
| penlight | 56 | 0 | 0 | 0 | 0 | 0 |

**False rejects: zero across 419 files.** That is BUG-392's direction — Lunar rejecting valid Lua —
and it is clean. Phase 4 therefore has no backlog to file in the direction that motivated the
feature.

The two false accepts are **different in kind**, which is the finding:

1. `src/luacheck/vendor/sha1/lua53_ops.lua` — Lua 5.3 bitwise operators (`<<`, `&`, `|`, `>>`, `~`)
   in a corpus pinned at `LUA51`. `luac5.1` rejects at `:4`. Lunar accepts **by design**: the parser
   is level-agnostic and level violations are reported by `LuaLanguageLevelInspection`, which fires
   17 times on this member. Not a defect — **a false positive of the oracle**.
2. `spec/samples/python_code.lua` — literally `from __future__ import braces`. `luac5.1` rejects
   with `'=' expected near '__future__'`. Lunar reports **0 parse errors**, parsing it as four
   separate `EXPR_STATEMENT`s each wrapping a bare `NAME_REF` (probed, PSI dumped). Lua permits only
   assignments and calls as statements, so a bare name is a syntax error. **A real defect — filed as
   BUG-409.**

**Design consequence (blocking, folded into design §2.3).** `oracleDisagreements` as specified —
"both directions count" — would gate on a metric with a *systematic* false positive, because Lunar
deliberately parses a superset of any single language level. A gate that is known-flaky gets
disabled, which is the reasoning `CorpusMetrics.compare` already applies to inspection counts. So:

> **Gate on false rejects. Report false accepts diagnostically.**

False rejects have no such confound: if `luac` accepts a file at the corpus's pinned level and Lunar
does not, that is a defect with no legitimate explanation.

### MAINT-35-00-DR-03 — Pin the PUC Lua tarballs [Must]

`luac.json` needs an exact version + sha256 per level. lua.org publishes the tarballs and their
checksums; the values must be recorded from the real artefacts, not assumed.

**Action**: for 5.1.5, 5.2.4, 5.3.6, 5.4.7 (and 5.5.x when a corpus row needs it) record URL and
sha256, and confirm each builds a working `luac` on the builder with `build-essential` alone.

**Why it gates Phase 1**: the whole point of MAINT-35-00 is that the oracle is pinned. A `luac.json`
with a guessed checksum is worse than no pin — `fetch-luac.py` would refuse to build, or worse,
someone would "fix" it by removing the check.

#### ANSWERED 2026-08-05

lua.org publishes a sha256 per tarball **on the FTP index page itself** (no `.sha256` sidecar files;
`lua-X.Y.Z.tar.gz.sha256` and `checksums.html` both 404). Each tarball was downloaded and its digest
compared against the published value — **all five match**. That cross-check matters: a self-computed
digest pins *what I downloaded*, it says nothing about authenticity.

| level | version | sha256 (upstream == downloaded) | builds | `1 // 2` |
| :-- | :-- | :-- | :-- | :-- |
| `LUA51` | **5.1.5** | `2640fc56…95333` | ✓ | REJECT |
| `LUA52` | **5.2.4** | `b9e2e4aa…69f4b` | ✓ | REJECT |
| `LUA53` | **5.3.6** | `fc5fd69b…66d60` | ✓ | ACCEPT |
| `LUA54` | **5.4.8** | `4f18ddae…0629ae` | ✓ | ACCEPT |
| `LUA55` | **5.5.1** | `1c4b4068…373dce` | ✓ | ACCEPT |

All five built on the builder with the C toolchain alone (`make linux`), and each reports its own
version from `luac -v`. **Two pins in the design were guesses and are corrected**: `5.4.7` → **5.4.8**
and `5.5.x` → **5.5.1**, both now the newest of their series. Policy: pin the newest patch of each
series — the grammar does not change within a patch series, so an older patch is churn without
benefit.

Verified on the **built** binaries, not the apt ones:

- **TC-3/TC-4 discrimination is real**: `1 // 2` is rejected by 5.1.5 and 5.2.4, accepted by 5.3.6,
  5.4.8 and 5.5.1 — the integer-division operator arrived in 5.3, exactly as the version-matching
  requirement assumes.
- **`luac -p -` reads stdin on every version**, including 5.5.1. This re-confirms §2.2 on the
  binaries that will actually ship, not just on Debian's.

#### MAINT-35-00-DR-02 — ANSWERED 2026-08-05

`squeek502/fuzzing-lua` has two GitHub releases; the latest, **v0.2.0** (2020-06-07), carries a
single asset: **`fuzzing-lua-data.tar.gz`, 181 282 bytes**. Stable, addressable and checksummable, so
MAINT-35-06 can pin it the same way. Its size is negligible against MAINT-33's corpus budget. The
release is five years old and unlikely to be re-uploaded, which is the failure mode a checksum pin
guards anyway.

### MAINT-35-00-DR-02 — Is the upstream torture corpus stable enough to pin? [Should]

> **Answered — see the summary under DR-03.**

MAINT-35-06 pins squeek502's *minimized* corpus by **archive sha256**, because it is a release asset
rather than a git tree — a weaker pin than the commit SHAs `corpus.tsv` uses, and one that breaks if
the author re-uploads an asset in place.

**Action**: confirm the asset exists at a stable URL and record its sha256. If it is not stably
addressable, vendor a snapshot into the out-of-repo `test/` tree instead, or drop -06 to `Could`.

**Why it does not block**: -06 is a `Should`; MAINT-35-01…-05 stand on the existing corpus.

## Risks

| ID | Risk | Likelihood | Impact | Mitigation |
| :-- | :-- | :--: | :--: | :-- |
| R1 | **The gate runs without a judge.** A sweep whose `luac` is missing could measure nothing while reporting success — the failure mode a ratchet can least afford. | Low *(was High)* | **High** | Reduced by **owning the dependency** (MAINT-35-00) rather than tolerating its absence: `lua5.1`–`lua5.4` are provisioned in all three paths, and a missing binary fails the sweep before it judges a file, naming the apt package. An earlier design instead made the metric nullable and taught the ratchet to survive absence; that was more code, more cases, and strictly weaker — a gate that cannot run is louder than one that runs empty. |
| R2 | **Version skew invents disagreements.** `luac5.4` accepts `1 // 2`, `luac5.1` rejects it — verified both ways. Any `PATH`-resolved binary has an unknowable version. | High if unmanaged | High | The oracle is built from a **pinned tarball + sha256** per level and resolved by table lookup to one path; `PATH` is never consulted. TC-3/TC-4 assert the same input gets opposite verdicts at the two levels. |
| R3 | **Disagreements get baselined as expected**, converting live defects into permanent ones — exactly the failure MAINT-33 already has with `parseErrors`, reproduced one layer up. | Medium | **High** | Phase 4 forbids recording a disagreement without a filed bug ID, and the DoD repeats it. |
| R4 | **Process-spawn cost.** One `luac` per file across **419** corpus files (baselines sum to 419). | Certain | Low | ~10 ms/spawn ≈ 4 s, against a corpus budget already measured in minutes. **Batching is NOT the fallback** — verified: `luac5.1 -p ok.lua bad.lua ok2.lua` aborts at the first reject and reports only that file, so it cannot produce the per-file verdicts MAINT-35-02 requires. If cost ever matters, the fallback is to judge only files whose `parseErrors` differ from the previous run. |
| R5a | **`luac`'s own stderr is not always valid UTF-8.** Confirmed in DR-01's first run: decoding it strictly threw `UnicodeDecodeError` on luacheck's `utf8_error.lua`, because `luac` echoes the offending token back. | **Certain** | Medium | `ParseOracle` decodes process output with a replacing decoder. The `Reject(message)` payload is diagnostic only — it is never compared or baselined — so replacement characters are harmless. |
| R5 | **The fuzz corpus is not valid UTF-8** and a lossy decode breaks the round-trip invariant at the decode rather than the lexer, producing false failures that look like lexer bugs. | High | Medium | Inputs decoded ISO-8859-1 (design §5), which is byte-preserving for round-trip purposes. |
| R6 | **`luac -p` is a parser, not a lexer.** It rejects some inputs the *lexer* handles fine (valid tokens, invalid grammar), so it cannot judge the lexer in isolation. | Certain | Low | Accepted: the oracle judges the parser. The lexer is judged by the oracle-free round-trip and crash invariants, which need no reference implementation. Stated so nobody later mistakes the oracle for lexer-level ground truth. |
| R7 | **Corpus runtime ceiling.** MAINT-33 parked KOReader for pushing the sweep past 10 minutes; this feature adds work to every file plus a new member. | Medium | Medium | Phase 5 records sweep time against the ceiling. If exceeded, the torture member is the first thing to park — it is a `Should`. |
| R8 | **The oracle floats with the environment.** `lua5.1` reached the builder as an *automatic* dependency of `luarocks` on 2026-07-18 (verified in `/var/log/apt/history.log`) — nobody chose its version. A distro bump, a different VM image or a contributor's machine would each judge by a different `luac`, while the ratchet's baselines claim to be ground truth. | High *(before -00)* | **High** | MAINT-35-00 removes the class of problem rather than the instance: the oracle is built from a sha256-pinned tarball, so every machine judges identically by construction. The residue is that `build-essential` must be provisioned — a compiler, which does not affect verdicts. |

## Gaps

- **No input generation.** This feature judges inputs; it does not create them. A JVM fuzzer
  (Jazzer/JQF) and a generator driven by `lua.bnf` are the natural follow-ons and are deliberately
  out of scope — they are worth little until the judge exists, which is the ordering both upstream
  projects imply.
- **No LuaCATS coverage.** PUC Lua has no opinion on doc comments, so there is no oracle for the
  LuaCATS grammar. BUG-393 and BUG-406 both live there, and MAINT-34's stub↔AST parity harness is
  the instrument for that half.
- **Token-level differential is not attempted.** squeek502 compares *token streams* against
  `luaX_token2str` output, which is strictly stronger than accept/reject — it would catch a lexer
  that tokenises differently but still parses. Doing that needs a C harness built against PUC Lua;
  accept/reject plus round-trip gets most of the value for none of the build complexity. Worth
  revisiting if the round-trip invariant proves too weak.
