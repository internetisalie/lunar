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

### MAINT-35-00-DR-03 — Pin the PUC Lua tarballs [Must]

`luac.json` needs an exact version + sha256 per level. lua.org publishes the tarballs and their
checksums; the values must be recorded from the real artefacts, not assumed.

**Action**: for 5.1.5, 5.2.4, 5.3.6, 5.4.7 (and 5.5.x when a corpus row needs it) record URL and
sha256, and confirm each builds a working `luac` on the builder with `build-essential` alone.

**Why it gates Phase 1**: the whole point of MAINT-35-00 is that the oracle is pinned. A `luac.json`
with a guessed checksum is worse than no pin — `fetch-luac.py` would refuse to build, or worse,
someone would "fix" it by removing the check.

### MAINT-35-00-DR-02 — Is the upstream torture corpus stable enough to pin? [Should]

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
