---
id: "MAINT-35"
title: "35: Lexer/Parser Torture Testing (differential oracle + invariants)"
type: "feature"
parent_id: "MAINT"
status: "todo"
priority: "medium"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-35: Lexer/Parser Torture Testing

MAINT-33's corpus sweep counts `parseErrors` but **cannot tell a true parse error from a false
one**. That gap is not theoretical: BUG-392 (valid luarocks code rejected) sat in the baseline as an
expected number until a human read the file and noticed the Lua was fine. The ratchet guarded the
count; nothing guarded the *correctness* of the count.

This feature adds the missing judge — PUC Lua itself — plus two oracle-free invariants that would
each have caught a shipped defect, and a pinned torture corpus of pathological inputs.

Prompted by two upstream projects
([squeek502/fuzzing-lua](https://github.com/squeek502/fuzzing-lua),
[ligurio/lunapark](https://github.com/ligurio/lunapark)). Both fuzz **C** Lua with libFuzzer/AFL
under ASAN/UBSAN, and lunapark adds CBMC proofs — none of which transfers to a JVM plugin. What
transfers is their *oracle design*: squeek502 validates an alternate implementation (Zua) against
PUC Lua's token stream, which is exactly Lunar's relationship to PUC Lua.

## Why these three checks

Each is chosen because a **defect this repo actually shipped** would have been caught by it:

| Check | Would have caught |
| :-- | :-- |
| Differential accept/reject vs `luac -p` | **BUG-392** — valid Lua rejected; found by a human reading a baseline |
| Lexer round-trip (token texts concatenate to the source) | **BUG-392** again, at the lexer, directly: the long-string merge dropped a token run |
| Crash-freedom under lex/parse | **BUG-390** — `StackOverflowError` from the defeated cycle guard |

## Scope

**In scope**

- A **parse oracle**: for any input, does PUC `luac -p` accept it, and does Lunar report zero
  `PsiErrorElement`s? Disagreement in either direction is a defect.
- Applying that oracle across the existing MAINT-33 corpus, as a new gated metric.
- **Lexer round-trip** and **crash-freedom** invariants over the same inputs.
- A pinned **torture corpus** of pathological inputs (squeek502's published *minimized* lexer
  corpora), swept by the same invariants. These inputs are mostly *not* valid Lua, which is the
  point: the oracle judges them, no golden files are authored.

**Out of scope**

- **Sanitizers (ASAN/UBSAN) and CBMC.** Both upstream projects lean on them; neither applies to a
  JVM plugin with no native memory. Named here so their absence is a decision, not an oversight.
- **A fuzzing engine** (Jazzer/JQF) and **grammar-driven generation** from `lua.bnf`. Both are
  worthwhile and both are deferred: they *produce* inputs, and are worth little until something can
  judge the output. This feature builds the judge. Tracked in `risks-and-gaps.md` as follow-ons.
- Fuzzing the **LuaCATS** comment grammar. PUC Lua has no opinion on doc comments, so there is no
  oracle; MAINT-34's stub↔AST parity harness is the right instrument there.
- Any production-code change. This feature is test infrastructure only.

## Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| MAINT-35-00 | **Build the oracle from pinned source** | **M** | Not Implemented | `tooling/corpus/luac.json` pins a PUC Lua version + sha256 per language level (key/value, **not** TSV — `corpus.tsv`'s shell parser mis-binds every column after an empty field, BUG-407); `tooling/corpus/fetch-luac.py` downloads, verifies, builds and caches `test/luac/<version>/luac`. **No apt package and no `PATH` search**: a distro-supplied `luac` is unpinned (Debian ships `5.1.5-11`, `5.4.7-1+b2` — patched, and floating with the distro), which is incoherent inside a ratchet that pins its corpus to commit SHAs. Only `build-essential` comes from the system, and is added to `builder-bootstrap.sh:11`, `startup-script.sh:20-22` and `.gitea/workflows/build-plugin.yml:116` — a compiler does not decide the oracle's verdicts. |
| MAINT-35-01 | Version-matched parse oracle | **M** | Not Implemented | A test-only helper resolves a `luac` matching the input's declared `LuaLanguageLevel` and returns Accept / Reject. Version matching is mandatory, not advisory — `luac5.4` accepts `1 // 2` and `luac5.1` rejects it, so an unmatched oracle manufactures disagreements. |
| MAINT-35-02 | Oracle applied across the corpus | **M** | Not Implemented | Every swept corpus file is judged. New gated metric `oracleDisagreements`; the offending paths are recorded diagnostically so a regression is locatable. |
| MAINT-35-03 | A missing oracle fails fast | **M** | Not Implemented | If a sweep needs a `luac` that has not been built, it **fails immediately** with the exact remedy (`tooling/corpus/fetch-luac.py`) — before any file is judged. It never degrades to a partial or absent metric. This replaces an earlier design in which the metric was nullable and the ratchet tolerated absence; owning the dependency (MAINT-35-00) makes tolerance unnecessary, and fail-fast strictly safer than a gate that can silently disable itself. |
| MAINT-35-03a | *(withdrawn)* | — | — | Existed only to route around apt's missing `lua5.5`. With MAINT-35-00 building from lua.org tarballs, 5.5 is one more `luac.json` block and needs no special case. Note for the record: Lunar's own provisioner *does* build `luac` (`PucLuaBuildRecipe.kt:115-126`), and it is deliberately **not** used as the oracle — a judge must not share failure modes with the code it judges. |
| MAINT-35-04 | Lexer round-trip invariant | **M** | Not Implemented | For every input, concatenating each token's text in order must reproduce the source **byte for byte**. Gated count. |
| MAINT-35-05 | Crash-freedom invariant | **M** | Not Implemented | Lexing and parsing any input must not throw — including `StackOverflowError`, which is why `Throwable` is caught rather than `Exception`. Both sites are recorded in one gated map keyed `lex:<Class>` / `parse:<Class>`; only the class name is kept, never the message (paths would churn the baseline). |
| MAINT-35-06 | Pinned torture corpus | **S** | Not Implemented | squeek502's minimized lexer corpus, pinned by release asset + checksum in `torture.json`, swept by -04/-05 and judged by -01. Opt-in with the rest of the corpus. |
| MAINT-35-07 | Baseline round-trip for the new metrics | **M** | Not Implemented | `CorpusBaseline.render`/`parse`/`compare` carry all five new fields, and `BaselineRatchetTest.renderParseRoundTrip` — which asserts `original == parse(render(original))` over the whole data class — stays green. `oracleDisagreements` is a plain `Int`: with MAINT-35-00 owning the dependency there is no absent state to encode. |

## Test Cases

| TC | Input | Expected |
| :-- | :-- | :-- |
| TC-1 | `local x = 1` at LUA51 | oracle Accept; Lunar 0 parse errors; **agree** |
| TC-2 | `local = = =` at LUA51 | oracle Reject; Lunar >0 parse errors; **agree** |
| TC-3 | `local a = 1 // 2` at **LUA51** | oracle Reject (verified: `luac5.1` rejects); disagreement iff Lunar accepts |
| TC-4 | the same `1 // 2` at **LUA54** | oracle Accept (verified: `luac5.4` accepts) — locks that the version match is real and not incidental |
| TC-5 | BUG-392's fixture: `[[` followed by two blank lines then a body | oracle Accept; Lunar 0 errors. This is the regression that motivated the feature |
| TC-6 | any input, e.g. `--[==[ x ]==] local y = "a\z\n b"` | round-trip: concatenated token texts `==` the source exactly |
| TC-7 | a deeply self-referential table/subscript chain (BUG-390's shape) | no throwable escapes lex or parse |
| TC-8 | a sweep at a level whose pinned `luac` has not been built | **fails immediately**, before judging any file, naming the expected path and `fetch-luac.py` |
| TC-9 | `fetch-luac.py` against a tarball whose sha256 does not match | **refuses to build**, leaving no `test/luac/<version>/luac` — an unverified oracle is never installed |

## Definition of Done

- All `Must` requirements implemented; TC-1…TC-9 green.
- **The oracle is pinned, not assumed** — a fresh builder runs `fetch-luac.py` + the corpus gate
  green with no system `luac` involved, and two machines judge identically by construction.
- A missing oracle fails fast with an actionable message; there is no state in which the gate
  silently judges nothing (TC-8).
- The corpus ratchet gates `oracleDisagreements`, `lexerRoundTripFailures` and `crashes` (per key), per design §4.5.
- Baselines re-recorded for all four corpus members, with any disagreements the oracle finds either
  fixed or filed as bugs before recording — **a disagreement must not be baselined as expected
  without a filed defect**.
- Full suite green (`--rerun --no-build-cache`) and the corpus ratchet green, run **separately**:
  `test -PwithCorpus` in a single invocation wedged the daemon on 2026-08-04.
