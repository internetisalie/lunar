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

Lunar produces a `PsiErrorElement` in a file that the reference Lua implementation parses without
complaint. It is the **only** genuine parse failure anywhere in the MAINT-33 corpus — every other
parse error in the corpus is in luacheck's deliberately-malformed `spec/samples/`.

## 1. Reproduction

```bash
tooling/corpus/fetch-corpus.sh
tooling/gce-builder/gce-builder.sh run "test --tests *Corpus* -PwithCorpus"
```

`src/test/resources/corpus/luarocks.baseline` records `parseErrors=1` with
`parseErrorFile=src/luarocks/cmd.lua`.

## 2. Expected vs Actual Behavior

- **Expected**: no error element. The file is valid Lua.
- **Actual**: one `PsiErrorElement`.

**Reference cross-check** (this is what makes it a defect rather than a judgement call):

```
$ luac -p test/corpus/luarocks/src/luarocks/cmd.lua   # Lua 5.4.7
   (exit 0 — parses clean)
$ for f in $(find test/corpus/luarocks/src -name '*.lua'); do luac -p "$f" || echo "REJECTED: $f"; done
   reference-rejected files: 0
```

All 104 files under `luarocks/src` are accepted by the reference parser; Lunar rejects one.

## 3. Context / Environment

- **Confidence**: high — reference-validated, reproducible, isolated to a single file.
- **Not a language-level artefact.** The corpus currently pins luarocks to `LUA51`, but the first
  baseline — recorded before the `luaLevel` manifest column existed, i.e. under the `LUA54`
  default — already reported `parseErrors=1` for this same file. It reproduces at both levels, so
  it is not a level-gated construct being rejected.
- **Corpus pin**: luarocks `990ec6ca` (v3.12.2), file is 810 lines.
- **Leading suspect, unconfirmed**: `cmd.lua` is Teal-generated and contains a dense run of
  long-bracket strings concatenated across lines ~455-475, of the form
  `]] .. basename .. [[ completion bash > ~/.local/share/...]] .. basename .. [[`, with shell
  snippets containing `(`, `~`, `$` and `/` inside the brackets. Long-bracket lexing is a plausible
  culprit, and `LuaLongStringAnnotator`/`LuaLongCommentAnnotator` already exist as separate
  handling for that token class. This is a hypothesis from reading the file, **not** from the
  reported error offset.

### Second symptom in the same file

After BUG-390 was fixed, corpus-wide `highlightFailures` fell from 131 files to **one** — and that
one is `cmd.lua` again, failing differently:

```
[corpus] highlight failed on src/luarocks/cmd.lua: TestLoggerAssertionError
```

`TestLoggerAssertionError` is the platform test framework failing on an ERROR-level log, so
something logs an error while highlighting this file. The Lunar frames on that stack, once each
(not recursion):

```
LuaShadowingVariableInspection.inspectIdentifier (LuaShadowingVariableInspection.kt:67)
  → LuaFunctionExtKt.processDeclarations        (LuaFunctionExt.kt:110)
    → LuaResolveUtil.scopeCrawlUp               (LuaResolveUtil.kt:19)
```

Neither site contains an explicit `LOG.error`, so the log originates in the platform — the message
text was not captured and is the first thing to retrieve.

**Whether the two symptoms share a root cause is unproven.** What is established is that the
corpus's only parse-error file is also its only highlight-failure file, which makes a malformed PSI
tripping the scope crawl the obvious hypothesis to test first — and if it holds, fixing the parse
error fixes both.

## 4. Other Notes

- **First step should be to get the offset.** The sweep currently records only the *file* for a
  parse error (`parseErrorFile=`), not where in it. Extending that to `path:offset` — or printing
  `PsiErrorElement.errorDescription` — would point straight at the construct and is a small change
  to `CorpusSweep.tally`. Worth doing regardless: it makes every future parse regression
  self-locating instead of requiring a bisect.
- Once the offset is known, reduce to a minimal snippet and add it to
  `TestLuaParsingExhaustive`, which is exactly the home for "this construct must parse" cases.
- **Not** related to SYNTAX-18 (parser error recovery). That feature is `done` and deliberately
  uses pins with zero `recoverWhile`; this is a plain parse failure on valid input, not a recovery
  behaviour.
