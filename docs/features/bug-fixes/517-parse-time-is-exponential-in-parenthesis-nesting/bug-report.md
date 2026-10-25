---
id: "BUG-517"
title: "Parse time is exponential in the nesting depth of parenthesised expressions"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-517: parse time is exponential in parenthesis nesting

A Lua expression nested in parentheses takes time exponential in the nesting depth to parse. A
ten-deep `((((((((((a))))))))))` takes 11 s. One 1,437-character statement in Neovim's
`runtime/lua/vim/lsp/_snippet_grammar.lua`, an LPeg grammar, takes 8 s. Every consumer that builds
that file's AST pays it. On TARGET-11's lazy.nvim sweep it accounts for about two-thirds of the
extra time that enabling the Neovim library costs (TARGET-11 DR-08, which gives the share per
metric).

Found while attributing TARGET-11's cost. The parse times were executed; the cause was not
investigated.

## 1. Reproduction (executed)

Libvirt builder, `test --rerun --no-build-cache`, HEAD `a6d2605c` (main `33326f3f` plus docs). Each
text is parsed with `PsiFileFactory.createFileFromText("n.lua", LuaLanguage, text)`, then
`file.node.textLength`, timed around both, in a `BasePlatformTestCase`. The probe source is
`~/.cache/claude-scratch/lunar/target-11/r5/probe-src/Target11R5ParseCostProbeTest.kt`; the output is
`…/r5/results/p5-nesting/probe.txt` and `…/r5/results/p4-parse-cost/probe.txt`.

```lua
local x = ((((((((((a))))))))))          -- depth 10
local x = f(f(f(f(f(f(f(f(f(f(a))))))))))   -- "calls" shape
local x = a * (a * (a * (a * (b))))      -- "mulParens" shape
local x = f(a * (f(a * (b))))            -- "callMulParens" shape
```

Milliseconds by depth (each shape stops after the first depth over 3 s):

| Shape | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
| :-- | --: | --: | --: | --: | --: | --: | --: |
| `(`…`a`…`)` | 16 | 61 | 152 | 217 | 732 | 2691 | 10901 |
| `a * (`…`b`…`)` | 9 | 26 | 58 | 231 | 830 | 3447 | — |
| `f(a * (`…`b`…`))` | 51 | 407 | 3382 | — | — | — | — |

The `f(`…`a`…`)` shape grows more slowly: 244 ms at depth 14, 938 ms at 16 and 3754 ms at 18.

The real file, from the pinned Neovim archive (`neovim-779c3696…/runtime/lua/vim/lsp/_snippet_grammar.lua`,
180 lines, 6289 characters), parsed three times:

| Text | ms |
| :-- | :-- |
| whole file | 10170, 8190, 8046 |
| only the `local G = P({ … })` statement (1,437 characters) | 8014, 7782, 7599 |
| the file without that statement (4,852 characters) | 14, 13, 18 |
| control: `vim/_core/shared.lua` (50,527 characters) | 107, 88, 64 |

## 2. Expected vs actual

- **Expected:** parse time roughly linear in the text. A 6 KB file parses in milliseconds, as
  `shared.lua` (50 KB) does.
- **Actual:** exponential in nesting depth. Any user file with an expression nested about nine
  deep stalls the IDE for seconds, on every reparse of that file.

## 3. Context

- **Where it is paid, measured on TARGET-11's lazy.nvim probe (TARGET-11 DR-08):**
  - `LuaDeprecatedApiInspection` resolves lazy.nvim's `M.parse(…)` calls into this file's
    `function M.parse` ([[BUG-508]]'s shape), then reads that declaration's PSI, which builds the AST.
  - Six such reads cost 13.5–15.4 s per highlighting pass over 54 files.
  - Skipping this one file in that inspection cuts the library-on pass from 62.9 s to 48.1 s,
    against a 33.4 s control.
- **Language level:** all of them; this is the expression grammar
  (`src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf`, `expr` / `varOrExp` / `args`).
- **Not investigated:**
  - why the time grows. Backtracking between alternatives that both start with `(`
    (`varOrExp ::= var | '(' expr ')'`, call arguments) is a guess, not a finding;
  - whether stub indexing pays the same 8 s when it first indexes the file.
