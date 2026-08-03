---
id: "BUG-392"
title: "Parser reports an error in luarocks' cmd.lua, which reference Lua accepts"
type: "bug"
parent_id: "BUG"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-392: Parser reports an error in valid Lua (`luarocks/src/luarocks/cmd.lua`)

> **RESOLVED 2026-08-03.** A long string whose opening bracket is followed by a **blank line**
> (`[[` + two or more newlines) lexed as three tokens instead of one, leaking its body into the
> token stream as tokens the grammar has no rule for.
>
> `lua.flex`'s `XLONGSTRING_BEGIN` state returns `NL_BEFORE_LONGSTRING` for a newline **without
> leaving the state** (`lua.flex:170`), so a run of *n* leading newlines yields *n* such tokens.
> `LongStringMergingLexerAdapter` consumed at most one (`if`, not `while`), ending the merged
> `STRING` at `[[\n` and emitting the rest as raw `LONGSTRING` / `LONGSTRING_END`. One word in
> `LuaLexer.kt:127` fixes it.
>
> **Both symptoms had this one root cause** — §5 had recorded that as an untested hypothesis.
> Measured on the corpus: luarocks `parseErrors` **1 → 0** and `highlightFailures` **1 → 0**; the
> corpus now has no parse errors outside luacheck's deliberately-malformed `spec/samples/`, and no
> highlight failures at all. luacheck `LuaTypeAssignability` fell **462 → 449** as a bonus — those
> long strings were not typing as strings either.
>
> luarocks' `LuaTypeAssignability` (1767 → 1893) and `LuaReturnTypeMismatch` (223 → 233) **rose**,
> for the same reason they rose after BUG-390: `cmd.lua` had been aborting mid-highlight, so its
> warnings were never counted. The failure was hiding diagnostics, not preventing them.
>
> Regression tests: `LuaLongStringBlankLineTest` (the verbatim reproducer + a single-token lexer
> assertion), four lexer cases in `TestLuaLexer.long strings`, and
> `TestLuaParsingExhaustive.testLongStringOpeningOnBlankLine` (8 cases).

Lunar produces a `PsiErrorElement` in a file that the reference Lua implementation parses without
complaint. It is the **only** genuine parse failure anywhere in the MAINT-33 corpus — every other
parse error in the corpus is in luacheck's deliberately-malformed `spec/samples/`.

## 1. Reproduction

```bash
tooling/corpus/fetch-corpus.sh
tooling/gce-builder/gce-builder.sh run "test --tests *Corpus* -PwithCorpus"
```

`src/test/resources/corpus/luarocks.baseline` recorded `parseErrors=1` with
`parseErrorFile=src/luarocks/cmd.lua:439`.

Minimal, independent of luarocks entirely:

```lua
s = [[

]]
```

`[[` followed by one newline was always fine — and was covered by an existing test
(`TestLuaLexer` "opening-newline"). Two newlines was the defect, and nothing covered it.

## 2. Expected vs Actual Behavior

- **Expected**: no error element. The file is valid Lua.
- **Actual**: one `PsiErrorElement`.

**Reference cross-check** (this is what made it a defect rather than a judgement call):

```
$ luac -p test/corpus/luarocks/src/luarocks/cmd.lua   # Lua 5.4.7
   (exit 0 — parses clean)
$ for f in $(find test/corpus/luarocks/src -name '*.lua'); do luac -p "$f" || echo "REJECTED: $f"; done
   reference-rejected files: 0
```

All 104 files under `luarocks/src` are accepted by the reference parser; Lunar rejected one.

## 3. Root Cause

The lexer splits a long string into `LONGSTRING_BEGIN` / `NL_BEFORE_LONGSTRING` / `LONGSTRING`* /
`LONGSTRING_END`, and `LongStringMergingLexerAdapter` re-merges them into the single
`LuaElementTypes.STRING` the grammar expects. Only that merged type is ever exposed — every
consumer (`LuaParserDefinition`, `LuaSyntaxHighlighter`, the TODO indexer, Find Usages) constructs
`LuaLexer()`, and nothing reads the raw flex lexer. So the merge function is the whole contract.

`lua.flex:168-171`:

```
<XLONGSTRING_BEGIN>
{
    {nl}     { return NL_BEFORE_LONGSTRING; }
    .        { yypushback(yytext().length()); yybegin(XLONGSTRING); return advance(); }
}
```

The `{nl}` rule does not `yybegin` anywhere, so the lexer stays in `XLONGSTRING_BEGIN` and emits
one `NL_BEFORE_LONGSTRING` per leading newline. The merge consumed exactly one:

```kotlin
if (delegate.tokenType == LuaTokenTypes.NL_BEFORE_LONGSTRING) { delegate.advance() }
```

so for `[[\n\nbody]]` it returned a `STRING` covering just `[[\n`, then stopped. The second newline
surfaced as `NL_BEFORE_LONGSTRING` — which `LuaSyntax.WhiteSpaceTokens` classifies as whitespace,
so it vanished silently — and `body` and `]]` reached the parser as bare `LONGSTRING` and
`LONGSTRING_END` tokens that appear nowhere in `lua.bnf`.

The fix is `if` → `while` (`LuaLexer.kt:127`). The flex state machine is left alone deliberately:
making `{nl}` switch to `XLONGSTRING` would be more faithful to Lua's skip-one-leading-newline
rule, but it changes generated code for no observable gain, since a tolerant merge produces an
identical token stream for every consumer.

### Why the reported error was 92 lines away from the defect

The recorded symptom was a **zero-length `ERROR_ELEMENT` between the function name and its `(`**,
with the message `LuaTokenType.( expected, got '('` — a token apparently rejected for being
exactly what was demanded. That was an artifact, and reading it literally cost most of the
investigation.

`funcBody`'s generated section is **unpinned** (`LuaParser.java:489,495`):

```java
Marker marker_ = enter_section_(builder_);
result_ = consumeToken(builder_, LPAREN);
...
exit_section_(builder_, marker_, null, result_);
```

so any failure *inside* it — here, the loose `LONGSTRING` tokens 400 characters later — rolls the
builder back to the section start. Only then does the pinned `localFuncDecl` report, at the
rolled-back position, against the variants collected there. And `got '('` prints
`builder.getTokenText()` (`GeneratedParserUtilBase.java:784`), the token **text**, so the message
never asserted anything about the token's type.

Both facts had to be established before the error location could be discarded as noise. Dumping
the PSI tree of the reproducer — rather than probing the reported position — showed the split
`STRING` / `long string` / `long string end bracket` sequence immediately.

## 4. Hypotheses tested and refuted

Recorded because each was asserted at some point in this investigation and each was wrong.

- **"A SYNTAX-18 pin interaction."** Asserted in an earlier revision of this report on the strength
  of the zero-length error node under `localFuncDecl`'s `pin = 2`. **Wrong.** The pins are
  innocent; `pin = 2` only explains why the rule could not roll back and therefore had to report
  *something*, not why anything failed. `lua.bnf:145-151`'s reasoning about `recoverWhile` was
  never implicated.
- **Grammar-Kit parser tracing.** Proposed as a next step. **No such thing exists** — the complete
  `KnownAttribute` set in Grammar-Kit 2023.3.2 has no tracing or debug attribute, and
  `JavaParserGenerator` emits no logging. Regeneration would not have helped, and was never needed
  anyway: `parserUtilClass` already routes every primitive through the project's own
  `LuaParserUtil`.
- **`MAX_RECURSION_LEVEL` exhaustion.** `funcBody` opens with `recursion_guard_`, which fails
  silently without consuming — the exact observed signature. Probed directly by raising
  `-Dgrammar.kit.gpub.max.level` from 1000 to 100000: **byte-identical failure**. Refuted.
- **A duplicate `IElementType` instance** (same name, failing `==`), given `nextTokenIsFast` is
  reference identity. Plausible, and consistent with the repo's known registry-size hazard, but
  moot once rollback explained the position.
- Ruled out earlier by delta debugging, each parsing clean in isolation: escaped quotes; trailing
  colon method chaining; long-bracket concatenation; `|`/`<(`/`>`/`~`/`$` inside long strings;
  trailing comma before `}`; multi-line call arguments; concatenation chains to 80 operands; 16
  splices in one string; 24 chained `:m(1)` calls.

The reduction plateaued at 22 lines with the note that *"synthetic reconstructions of that shape do
not reproduce it"*. That was true and was the most useful clue in the report — the trigger was not
the shape at all, but a two-character detail (`[[` then a blank line) that every synthetic rewrite
happened to normalise away.

## 5. Other Notes

- **The corpus's self-locating parse errors are what made this tractable.** `CorpusSweep.tally`
  records `path:line:errorDescription`, which pinned the file and line on the first run.
- **The corpus also proved the fix**, across 363 files of third-party Lua, in a way no unit test
  could: two metrics to zero, one improved, and the rest unmoved.
