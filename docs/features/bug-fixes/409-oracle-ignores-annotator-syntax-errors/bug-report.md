---
id: "BUG-409"
title: "The parse oracle ignores annotator-level syntax errors, so Lunar looks more permissive than it is"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-409: The parse oracle ignores annotator-level syntax errors

> **This report replaces the original framing.** BUG-409 was recorded as *"a bare name parses as a
> statement, so non-Lua text reports no syntax error"*, with a fix direction of narrowing
> `exprStatement` in `lua.bnf`. Probing shows the premise is false and that fix would be harmful.
> The defect is in the **measurement**, not the parser.

## Reproduction

Agent-run probe, `BasePlatformTestCase`, 2026-08-06:

| input | `PsiErrorElement`s | ERROR-severity highlights |
| :-- | --: | --: |
| `from __future__ import braces` (the recorded witness) | 0 | **4** |
| `9` | 0 | **1** |
| `a b c d` | 0 | **4** |
| `print(1)` | 0 | 0 |
| `local x = 1` / `print(x)` | 0 | 0 |

Every ERROR reads *"Expression cannot be used as a statement"*.

## Expected vs actual

**Recorded as:** Lunar reports no syntax error for non-Lua text, so downstream inspections run over
nonsense as if it were valid Lua.

**Actually:** Lunar reports an error on **every** offending statement. `LuaStandaloneExpressionAnnotator`
(`LuaStandaloneExpressionAnnotator.kt:13-22`, registered at `plugin.xml:179`) flags any
`LuaExprStatement` whose expression is not a `LuaFuncCall`. This is the ordinary IntelliJ division of
labour: parse permissively so half-typed code still yields a usable tree, then diagnose at the
semantic layer.

What is wrong is the oracle's definition of acceptance:

```kotlin
private val lunarAccepts get() = tally.parseErrors == 0        // CorpusSweep.kt:47
val falseAccept get() = oracle is Verdict.Reject && parseErrors == 0   // LuaTortureCorpusTest.kt:116
```

Both count `PsiErrorElement`s only. A file Lunar rejects loudly through an annotator is scored as
"Lunar accepts", so the oracle records a disagreement that does not exist.

## Root cause

`lunarAccepts` conflates *"the parser built a tree without error elements"* with *"Lunar considers
this valid Lua"*. Those differ by exactly the set of diagnostics the syntax annotators produce —
today, one annotator covering `exprStatement`.

`exprStatement ::= expr` (`lua.bnf:123`) is **deliberate**, not the defect: Lua's real grammar is
`stat ::= varlist '=' explist | functioncall | …`, and the permissive rule exists so a half-typed
line still parses. `funcCall ::= varOrExp nameAndArgs+` (`lua.bnf:297`) is already the exact shape
Lua permits, and the annotator enforces it.

**Why the original fix direction is rejected.** Narrowing `exprStatement` to `funcCall` would: make
`LuaStandaloneExpressionAnnotator` dead code; replace a precise, well-worded diagnostic with a
generic parse error; degrade error recovery for every partially-typed line; and require a lexer/parser
regeneration — all to correct a number in a test harness.

## Scale

The torture corpus records `oracleFalseAccepts = 364` of 1 696 inputs, and the project corpus records
2 (`python_code.lua`, which is this class, and `lua53_ops.lua`, which is the by-design level-agnostic
parse). How much of the 364 this class accounts for is **not yet known** — measuring it is the first
implementation step, not an assumption.

## Fix strategy

**Step 1 — one definition of "syntactically valid", consulted by both sides.**
Extract the annotator's rule into `net.internetisalie.lunar.lang.syntax.LuaSyntaxDiagnostics`:

```kotlin
object LuaSyntaxDiagnostics {
    /** Statements Lua does not permit: an expression statement that is not a call. */
    fun invalidStatements(file: PsiFile): List<LuaExprStatement>
}
```

`LuaStandaloneExpressionAnnotator` consults it instead of testing inline. This is the MAINT-34
lesson applied early: two code paths that must agree about the same rule will drift unless they share
one implementation.

**Step 2 — the oracle counts it.**
`lunarAccepts` becomes `parseErrors == 0 && invalidStatements.isEmpty()`. `CorpusSweep.tally` and
`LuaTortureCorpusTest.judge` both already hold the `PsiFile`, so this needs no editor and no
highlighting pass — the torture sweep's 5.7 s budget is unaffected.

**Step 3 — re-measure, then itemize the remainder.**
With Step 2 in place, re-record the baselines and enumerate whatever false accepts survive. For those:

- Write `src/test/resources/corpus/torture-fuzzing-lua.expected-accepts`, one entry per line as
  `<path>  # <luac's rejection reason>`. The reason is mandatory: 364 content-addressed sha1
  filenames are unreviewable, and an allowlist nobody can read freezes real defects by accident.
- `oracleFalseAccepts` counts only **unlisted** inputs, and is then **gated at 0** — turning today's
  diagnostic into a real gate. Any newly-accepted invalid input fails the build.
- A listed entry that is no longer a false accept **fails loudly** and prompts a re-record, the same
  discipline `CorpusBaseline.compare` already applies to improvements. Allowlists rot; this is what
  stops it.

The archive is pinned by sha256, so the input set cannot change without the pin moving — an explicit
per-input allowlist is stable by construction.

## Test strategy

- **Unit** — `LuaSyntaxDiagnostics` returns the offending statements for `from __future__ import
  braces` (4), `9` (1), `a b c d` (4), and nothing for `print(1)` or `local x = 1`. Mutation-proof by
  reverting Step 1's extraction: the annotator's own tests must go red too, or the two paths were
  never really sharing the rule.
- **Oracle** — a corpus fixture containing only `from __future__ import braces` scores as a
  disagreement **before** Step 2 and as agreement after. This is the test that must go red without
  the fix; asserting on `oracleFalseAccepts` alone would pass either way while the number moved.
- **Project corpus** — `parseErrors` must not move on any of the four project members. They are real
  Lua; a correct change touches none of them. This is the regression guard.
- **Torture corpus** — `oracleFalseAccepts` drops; the residual is allowlisted with reasons and gated
  at 0.

## Out of scope

Changing `lua.bnf`. The permissive `exprStatement` is load-bearing for error recovery, and the
diagnostic it needs already exists and already works.

## Outcome (2026-08-06)

**Step 1** — `LuaSyntaxDiagnostics` owns the rule; `LuaStandaloneExpressionAnnotator` and both
sweeps consult it, so the annotator and the oracle cannot drift about what "valid Lua" means.

**Step 2** — acceptance became `parseErrors == 0 && invalidStatements == 0`. Measured effect:

| | before | after |
| :-- | --: | --: |
| torture `oracleFalseAccepts` | 364 | **26** |
| luacheck `oracleFalseAccepts` | 2 | **1** |

**338 of 364 (93%)** were the artefact. luacheck's remaining site is `lua53_ops.lua`, the by-design
level-agnostic parse; `python_code.lua` — this bug's own witness — is gone. No new false rejects
appeared on any project member, which was the regression guard.

**Step 3** — the surviving 26 are enumerated in `torture-fuzzing-lua.expected-accepts` with PUC's
reason per entry, and they turn out to be one coherent class:

| `luac` reason | count |
| :-- | --: |
| `unfinished long comment near '<eof>'` | 19 |
| `unfinished string near '<eof>'` | 5 |
| `unfinished long string near '<eof>'` | 1 |
| `nesting of [[...]] is deprecated` | 1 |

Unterminated constructs — exactly the leniency an editor must have while a `--[[` is being typed.
`oracleFalseAccepts` now counts only unlisted inputs and is **gated at 0**.

Both halves are mutation-proved: removing one allowlist entry fails the build with
`oracleFalseAccepts: baseline 0 → observed 1`, and adding an entry that is no longer a false accept
fails with *"the allowlist has rotted"*.

Incidental: `ParseOracle` now strips luac's own absolute path from its message, which would otherwise
churn every recorded reason across machines.

**Caveat, tracked as BUG-415.** Gating required re-recording the four project baselines, which had
gone stale when BUG-397's type-engine change landed without the (opt-in, non-CI) corpus sweep being
run. zerobrane's `LuaTypeAssignability` moved 846 → 2594. Those recorded values are an unvalidated
snapshot of post-BUG-397 behaviour and are **not** endorsed here.
