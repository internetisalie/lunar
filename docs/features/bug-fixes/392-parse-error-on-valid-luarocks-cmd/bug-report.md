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
### Located: `cmd.lua:439`, inside `get_parser`

The error is a **zero-length `ERROR_ELEMENT`** emitted between the function name and its parameter
list, with a `BLOCK` node opening before the `(`:

```
LuaTokenType.local      :: local
LuaTokenType.function   :: function
LuaTokenType.IDENTIFIER :: get_parser
ERROR_ELEMENT           ::              <- empty, at offset 14578
BLOCK                   ::
LuaTokenType.(          :: (            <- the token the parser said was missing
LuaTokenType.IDENTIFIER :: description
```

So `funcBody ::= '(' [parList] ')' block END` (`lua.bnf:311`) failed its `'('` against a token that
*is* `LuaTokenType.(`, immediately adjacent. `localFuncDecl` carries `pin = 2` (`lua.bnf:176-177`),
so the rule is already committed at that point and the failure cannot roll back — an empty error
node plus premature `BLOCK` entry is the signature of a pinned rule committing and then failing
inside. That places the defect in the SYNTAX-18 pin scheme rather than in the lexer.

### Minimal reproducer (22 lines, `luac -p` clean)

```lua
local function get_parser(description, cmd_modules)
   help_max_width(80):
   add_help_command():
   add_complete_command({
      help_max_width = 100,
      summary = "Output a shell completion script.",
      description = [[

Enabling completions for Bash:

   Add the following line to your ~/.bashrc:
      source <(]] .. basename .. [[ completion bash)
   Add the following line to your ~/.config/fish/config.fish:
      ]] .. basename .. [[ completion fish | source
   or save the completion script to the local completion directory:
      ]] .. basename .. [[ completion fish > ~/.config/fish/completions/]] .. basename .. [[.fish
]], }):
   command_target("command"):
   require_command(false)

end
```

### Ruled out by reduction

Delta-debugging from the 92-line `get_parser` (every valid single-chunk deletion tested against
the real parser) plateaued at the above. Each of these reproduces **clean** in isolation and is
therefore *not* the trigger on its own:

- escaped quotes in strings, including the exact `" (\"" .. x .. "\")"` from line 430
- trailing-colon method chaining across lines (`f(1):\n g(2):\n h(3)`) — also accepted by `luac`
- long-bracket concatenation `[[a]] .. b .. [[c]]`, with newlines, with `<(`, with `>`/`~`/`$`
- a `|` inside a long string; two, three and four `]] .. x .. [[` splices; a splice ending `.fish`
- table constructor with a trailing comma before `}` then `):` chaining
- multi-line call arguments

Also ruled out, by scaling probes (all parse clean):

- concatenation chains of 5/10/20/40/**80** operands — not a chain-length or right-recursion limit
- 2/4/8/**16** `]] .. x .. [[` splices in one long string
- 3/6/12/**24** chained `:m(1)` calls — not a left-recursion or `MAX_RECURSION_LEVEL` limit
- 1/2/4/8 splices inside a table constructor inside a chained call — i.e. the real *shape*,
  synthesised at several sizes

The trigger appears to require a **combination** of the chained call, the table-constructor
argument and the spliced long string, not any single construct — and, importantly, **synthetic
reconstructions of that shape do not reproduce it**. Only the literal 22 lines fail. That is
consistent with a pin/backtracking interaction sensitive to something in the concrete token
sequence rather than to structure or size.

### Where to pick this up

Text-level reduction has plateaued; the next step needs visibility *inside* the parser rather than
more black-box probes. Two concrete options, in order of expected value:

1. **Step the generated parser** on the 22-line reproducer (the repo has a `jdb-debugger` skill).
   Break in `LuaParser.funcBody` / `GeneratedParserUtilBase.consumeToken` at the failing offset and
   read why `'('` is rejected when the current token is `LuaTokenType.(` — that answers it directly,
   where every black-box probe so far has not.
2. **Regenerate the parser with Grammar-Kit tracing** and diff the rule-entry log around the
   failure. `.claude/skills/generate-parser/scripts/generate.sh` is headless.

Until then the corpus baseline is the gate: luarocks `parseErrors=1`, with
`parseErrorFile=src/luarocks/cmd.lua:439:LuaTokenType.( expected, got '('` recording the exact
site.

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

- **Done — the sweep now self-locates.** `CorpusSweep.tally` records
  `path:line:errorDescription` per `PsiErrorElement`, so every future parse regression points at
  its own construct instead of needing a bisect. That change is what located this one.
- **Likely IS a SYNTAX-18 pin interaction** — an earlier draft of this report asserted the
  opposite, before the token dump existed. The evidence in §3 (a zero-length error node between the
  function name and its `(`, with `BLOCK` opening early, under `localFuncDecl`'s `pin = 2`) points
  at the pin scheme, not at lexing. `lua.bnf:145-151` records that pins were chosen over
  `recoverWhile` precisely because `recoverWhile` "destroys sibling backtracking"; this looks like
  the pin doing something adjacent on a rule whose prefix is shared with `localVarDecl`
  (`LOCAL …`). Worth re-reading that comment's reasoning against this case.
- **Suggested next step**: reduce further with the same delta-debug harness (the reproducer is in
  §3 and each round costs ~25 s), then add the minimal case to `TestLuaParsingExhaustive` — the
  home for "this construct must parse". Until then the corpus baseline is the gate: `parseErrors=1`
  on luarocks, with the file and line recorded.
