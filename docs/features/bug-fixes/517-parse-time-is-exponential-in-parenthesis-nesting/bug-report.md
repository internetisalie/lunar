---
id: "BUG-517"
title: "Parse time is exponential in the nesting depth of parenthesised expressions"
type: "bug"
parent_id: "BUG"
status: "done"
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

- **Expected:** parse time linear in the text. A 6 KB file parses in milliseconds, as
  `shared.lua` (50 KB) does.
- **Actual:** parse time is exponential in two kinds of nesting depth, on every reparse of the
  file (§4 has the counts):
  - **Nested expressions.** It grows 4^d for parentheses, 2^d for calls and indexes, and 8^d for
    `f(a * (`…`))`. A `(` nested nine deep takes 2.5 s.
  - **Nested call statements whose arguments hold a function body.** That is the busted
    `describe`/`it` and `vim.schedule(function() … end)` shape, `f(function() g(function() … end)
    end)`. It grows 3^(d+1): ten levels take 2.2 s.

## 3. Context

- **Where it is paid, measured on TARGET-11's lazy.nvim probe (TARGET-11 DR-08):**
  - `LuaDeprecatedApiInspection` resolves lazy.nvim's `M.parse(…)` calls into this file's
    `function M.parse` ([[BUG-508]]'s shape), then reads that declaration's PSI, which builds the AST.
  - Six such reads cost 13.5–15.4 s per highlighting pass over 54 files.
  - Skipping this one file in that inspection cuts the library-on pass from 62.9 s to 48.1 s,
    against a 33.4 s control.
- **Language level:** all of them; this is the expression grammar
  (`src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf`, `expr` / `varOrExp` / `args`).
- **Stub indexing:** `LuaFileElementType` is an `IStubFileElementType`, which builds stubs from the
  parsed tree, so first indexing parses with the same `LuaParser`. Inferred from the code; not
  measured.
- **Evidence:** `~/.cache/claude-scratch/lunar/bug-517/` holds the probe sources (`probe-src/`), the
  saved files of both parsers (`saved/`) and every run's log and JUnit XML (`results/<run>/`).

## 4. Root Cause

`expr` is a Grammar-Kit Pratt rule. Its atom alternatives are tried in order
(`LuaParser.expr`, `src/main/gen/…/LuaParser.java:1289`; `funcCall` then `prefixExpr` at
1296–1297), and two of them start with the same sub-rule. Each failed alternative rolls its
markers back, so whatever it parsed is parsed again by the next alternative. Three such re-parses
sit on the path of a suffixed expression (`lua.bnf` lines 256–259 and 292–303 at `a5be6b08`). A
fourth sits on the path of a statement (`statement`, lines 101–106: `assignmentStatement` before
`exprStatement`):

| Re-parse | Rules | Why the first parse is discarded |
| :-- | :-- | :-- |
| A | `varOrExp ::= var \| '(' expr ')'`, with `var ::= … \| '(' expr ')' varSuffix+` | For `( e )` with no index after it, `var`'s second alternative parses `e`, finds no `varSuffix`, and fails. `varOrExp`'s second alternative parses `e` again. |
| B | `funcCall ::= varOrExp nameAndArgs+`, then `prefixExpr ::= varOrExp` | When the chain does not end in a call, `funcCall` parses all of `varOrExp`, fails on `nameAndArgs+`, and `prefixExpr` parses `varOrExp` again. |
| C | `var ::= nameRef varSuffix*`, `varSuffix ::= nameAndArgs* indexExpr` | For `f( e )`, `varSuffix` parses the call `( e )`, finds no `indexExpr`, and fails. `funcCall`'s `nameAndArgs+` parses `( e )` again. |
| D | `statement ::= … \| assignmentStatement \| exprStatement …`, `assignmentStatement ::= varList '=' exprList` | For a call statement `f( … )`, `varList`'s `var` parses the whole argument list, any `function() … end` body included, as a `varSuffix`. The statement then fails on `indexExpr` or `=`, and `exprStatement` parses it all again. |

Each re-parse doubles the work below it, and the factors multiply at every nesting level:

| Shape | Re-parses per level | Calls of `expr` at the innermost position (executed) |
| :-- | :-- | :-- |
| `(`…`a`…`)` | A × B = 4 | 4^d: 1,048,576 at d = 10 (1,398,103 `expr` calls in all) |
| `a * (`…`b`…`)` | A × B = 4 | 4^d: 1,048,576 at d = 10 |
| `f(`…`a`…`)`, `o:m(`…`)` | C = 2 | 2^d: 4,096 at d = 12 |
| `t[`…`a`…`]` | B = 2 | 2^d: 4,096 at d = 12 |
| `f(a * (`…`b`…`))` | C × A × B = 8 | 8^d: 2,097,152 at d = 7 |
| `{`…`a`…`}` | none | 1 at every depth |
| `f(function()`…`x()`…`end)` statements (callbacks), also `o:m("s", function()`… | D + C = 3 | 3^(d+1): 19,683 at d = 8; 177,147 at d = 10 (2.2 s) |
| `y = f(function()`…`end)`, `local y = f(function()`…`end)` | C = 2 (the statement is an assignment, so D does not apply) | 3·2^d: 196,608 at d = 16 (3.6 s) |

The `local G = P({ … })` statement of `_snippet_grammar.lua` mixes these shapes. Parsing it calls
`expr` 906,585 times, 131,072 (2^17) of them at a single position.

The counts come from a probe `recursion_guard_` that tallies every generated rule entry by name and
by token index. Generated code static-imports its primitives from the `parserUtilClass`, so the
count needs no grammar change. The probe ran on the builder at `a5be6b08`, alongside the timings in
§1. With A, B and C removed but D kept (`5133da91`), callback statements still cost 2^(d+1):
131,072 at d = 16, which takes 3.0 s.

## 5. Fix Strategy

Parse a suffixed expression once, the way Lua's own parser does (`lparser.c`, `suffixedexp`): a
primary, then a loop over suffixes. That removes A, B and C. For D, try an expression statement
that is not followed by `=` or `,` before trying an assignment. The loop builds the nodes that the
ordered choice used to build, using `PsiBuilder.Marker.doneBefore` to end `VAR` and `VAR_OR_EXP`
before any trailing calls. The
PSI interfaces do not change, and neither does the tree for any input in the equivalence corpus
(§7).

The fix edits `lua.bnf` and `LuaParserUtil.kt`, adds `lang/parser/LuaSuffixedExprParser.kt`,
and regenerates `LuaParser.java`. It was prototyped on branch `bug-517` as commits `5133da91`
(A–C) and `de4346c9` (D); the measurements below name them.

**`lua.bnf`.** Replace `funcCall | prefixExpr` in `expr` with `suffixedExpr`, and the `prefixExpr`
rule with:

```
// BUG-517: a suffixed expression (`f(x).y:m()`, `(e)[k]`) is parsed ONCE, by
// LuaParserUtil.parseSuffixedExpr. It builds the varOrExp / var / varSuffix / nameAndArgs
// children and the node is a prefixExpr, or a funcCall when the chain ends in a call. The
// former `funcCall | prefixExpr` alternatives re-parsed every nested expression up to four
// times per nesting level. prefixExpr and funcCall stay for their element types and PSI.
suffixedExpr ::= <<parseSuffixedExpr>> {extends=expr elementType=prefixExpr name="prefix expr"}
fake prefixExpr ::= varOrExp {extends=expr}
```

`funcCall`, `varOrExp`, `var` and `varSuffix` stay as they are. Each attribute is load-bearing:
- `suffixedExpr` must be a public rule. A `private` atom turns `expr` into a plain choice:
  Grammar-Kit then emits `expr(builder, level)` with no priority argument, an `EXPR` element type,
  and drops `FUNC_CALL` / `PREFIX_EXPR`. That was tried and failed to compile.
- `elementType=prefixExpr` plus `fake prefixExpr` is this grammar's `binOpExpr` pattern. It keeps
  `PREFIX_EXPR` and `LuaPrefixExpr.getVarOrExp()` as they are. Putting `<<parseSuffixedExpr>>` in
  the body of `prefixExpr` itself deletes `getVarOrExp()`, and `methods=[varOrExp="varOrExp"]`
  cannot restore it ("'varOrExp' not found in 'prefixExpr'").
- `funcCall` must stay a real rule, not `fake`. A fake rule that nothing names in `elementType=`
  generates no `FUNC_CALL` constant. Its generated `funcCall()` method becomes unused.
- `name="prefix expr"` keeps the frame name `<prefix expr>` in error messages.

Grammar-Kit generates `suffixedExpr` with a `_COLLAPSE_` frame of type `PREFIX_EXPR`. When the
helper has completed a `FUNC_CALL` node starting at the frame's position, the frame takes that node's
type (`FUNC_CALL` and `PREFIX_EXPR` are in the same extends set). Otherwise the frame wraps the
helper's bare `VAR_OR_EXP` as `PREFIX_EXPR`. Regenerate with
`.claude/skills/generate-parser/scripts/generate.sh`. Only `LuaParser.java` changes. The atoms
renumber (`2: ATOM(suffixedExpr)`), and every operator's priority drops by one, keeping its relative
order.

**`lua.bnf`, statements (re-parse D).** In `statement`, insert `exprOnlyStatement` between
`globalVarDecl` and `assignmentStatement`, and add after `exprStatement ::= expr`:

```
// BUG-517: an expression statement not followed by `=` or `,` is taken BEFORE trying an
// assignment. Trying assignmentStatement first parsed a call statement's whole argument list
// (callback bodies included) as a varSuffix, failed, and exprStatement parsed it again, so
// `f(function() g(function() ... end) end)` cost 2^depth. A real assignment's first target ends
// at its `=` or `,`, so this rule fails there and assignmentStatement parses as before;
// exprStatement stays as the fallback for broken input such as `f() = 1`.
exprOnlyStatement ::= expr !('=' | ',') {elementType=exprStatement name="expr statement"}
```

Why each part is there:
- **Valid trees are unchanged.** In a valid assignment, `expr`'s longest suffix chain ends exactly
  where the first target ends, at `=` or `,`. Its binary-operator loop consumes neither token. So
  `exprOnlyStatement` fails there, and `assignmentStatement` runs as before. In a valid call
  statement, `assignmentStatement` used to fail anyway.
- **`elementType=exprStatement`** builds the same `EXPR_STATEMENT` node. Generated PSI does not
  change: only `LuaParser.java` gains `exprOnlyStatement`.
- **`exprStatement` stays after `assignmentStatement`.** Broken input such as `f() = 1` or
  `a.b(c) = 1` therefore still gets main's tree.
- **What it still costs.** An assignment target whose chain holds a function body,
  `f(function() … end).x = 1`, is parsed twice at that level. So is a statement where both rules
  fail.

**`LuaParserUtil.kt`.** Add the entry point, which mirrors the guards the generated atoms had:

```kotlin
/** BUG-517: `<<parseSuffixedExpr>>` in `lua.bnf`; see [LuaSuffixedExprParser]. */
@JvmStatic
fun parseSuffixedExpr(
    builder: PsiBuilder,
    level: Int,
): Boolean {
    if (!recursion_guard_(builder, level, "parseSuffixedExpr")) return false
    if (!nextTokenIsSmart(builder, LuaElementTypes.IDENTIFIER, LuaElementTypes.LPAREN)) return false
    return LuaSuffixedExprParser.parse(builder, level)
}
```

**`lang/parser/LuaSuffixedExprParser.kt`** (new `internal object`, about 150 lines). The algorithm:

1. Open a simple section (`enter_section_(builder)`), then two Grammar-Kit frames, opened as the
   generated rules open them: `enter_section_(builder, level + 1, _NONE_, VAR_OR_EXP,
   "<var or exp>")` and then `(…, level + 2, VAR, "<var>")`. Every sub-rule (`nameRef`, `expr`,
   `nameAndArgs`, `indexExpr`) is called at `level + 1`, which keeps the recursion-guard depth no
   greater than main's (§7).
2. Primary: `LuaParser.nameRef`. If that fails, `'(' LuaParser.expr(…, -1) ')'`. If both fail, exit
   both frames with `result = false`, which rolls them back, then exit the section with `false`.
   `isVar` = the primary is a name.
3. Suffix loop. Each iteration opens a frame `(…, VAR_SUFFIX, "<var suffix>")` and parses
   `LuaParser.nameAndArgs` repeatedly, then `LuaParser.indexExpr` once.
   - If an index follows, exit the frame with `true`, which completes it as `VAR_SUFFIX`. Set
     `isVar = true` and continue.
   - If there were no calls and no index, exit the frame with `false` (an empty rollback; the frame
     reports `<var suffix>`). The chain ends with no trailing calls.
   - If there were calls but no index, they are trailing calls. If `isVar`, call
     `variable.doneBefore(VAR, group)`; otherwise `variable.drop()`. Then call
     `varOrExp.doneBefore(VAR_OR_EXP, group)`, `group.drop()`, and exit the frame with marker
     `null` and `result = false`. The chain ends with trailing calls.
4. Pop the `VAR` and `VAR_OR_EXP` frames.
   - With no trailing calls, `VAR` is completed if `isVar`; otherwise its marker is dropped and the
     frame exits with `null`, `false`. `VAR_OR_EXP` is completed.
   - With trailing calls, both nodes are already closed, so both frames exit with marker `null`.
5. With no trailing calls, and the next token not in `{ ':', '(', '{', STRING }`, call
   `addVariant(builder, "<name and args>")`. This is the expected-token entry that `funcCall`'s
   failed `nameAndArgs+` used to record. Do not call `nameAndArgs` again: near the 1000-level
   recursion guard, a call at a shallower level succeeds where the deeper one was refused. The
   prototype hung there.
6. Exit the section with `FUNC_CALL` when there were trailing calls, else with `null`.

**Considered and rejected:**
- **Failure memoisation** (cache "rule R failed at token i" in `recursion_guard_`). The generated
  methods report success or failure only through `exit_section_`, so caching needs Grammar-Kit's
  frame internals. It also skips the expected-token entries that a failed attempt records, and by
  the counts in §4 it would still re-parse each level once (O(d²)). Not built.
- **Token-level lookahead predicates** (scan to the matching `)` and test the next token before
  trying `var`'s paren alternative or `funcCall`). These are O(n·d), and they are exact only if
  bracket balance on tokens agrees with the grammar's, which needs a proof for broken code. Not
  built.
- **An `upper` rule for B** (`prefixExpr ::= varOrExp callTail?`). It removes B only; A and C remain
  exponential, because C's trailing calls are consumed inside `VAR` and only a rollback or a
  hand-written `doneBefore` can move them out. Not built.

## 6. Test Strategy

1. **`src/test/kotlin/net/internetisalie/lunar/lang/parser/LuaParserNestingCostTest.kt`**
   (`BaseDocumentTest`, in the prototype).
   - **How it measures.** It counts `PsiBuilder.mark()` calls through a `PsiBuilderAdapter`
     subclass passed to `LuaParser().parseLight(…)`. Counting rather than timing makes it
     deterministic.
   - **What it asserts.** For five shapes, it asserts `marks(depth 6) < 2 × marks(depth 3)`. The
     shapes are `parens` `(`, `calls` `f(`, `index` `t[`, `callMulParens` `f(a * (`, and
     `callbacks`, the statement `f(function()` … `x()` … `end)`. Linear work plus a constant
     prefix keeps the ratio below 2, while k re-parses per level make it about k³.
   - **How it reports.** It checks every shape, collects the super-linear ones, and asserts once,
     so a failure lists all of them.
   - **Speed.** It is fast on both sides. Main's costliest case, `f(a * (` at depth 6, takes 3.7 s.

   Every shape is shown able to fail. Each mutation was applied to the final prototype and the test
   run once:

   | Run | Shapes reported (marks at depth 3 → depth 6) |
   | :-- | :-- |
   | final prototype | none (green) |
   | main's parser (`a5be6b08` `lua.bnf` / `LuaParser.java` / `LuaParserUtil.kt`) | parens 3179 → 202091; calls 582 → 4670; index 659 → 5363; callMulParens 32410 → 16589978; callbacks 3710 → 101639 |
   | A: in `parseParenPrimary`, mark, parse `'(' expr`, `rollbackTo`, then parse for real | parens 394 → 2970; callMulParens 758 → 6246 |
   | B: in `LuaParserUtil.parseSuffixedExpr`, mark, run `LuaSuffixedExprParser.parse`, `rollbackTo`, run it again | parens 515 → 3987; calls 599 → 4743; index 613 → 4869; callMulParens 5625 → 360441; callbacks 1326 → 11294 |
   | C: in `parseSuffixGroup`, mark, run the `nameAndArgs` loop, `rollbackTo`, run it again | calls 524 → 4108; callMulParens 1280 → 10912; callbacks 1251 → 10659 |
   | D: delete `exprOnlyStatement` from `statement` and regenerate | callbacks 1236 → 10532 |
2. **Tree-shape golden:**
   `src/test/kotlin/net/internetisalie/lunar/lang/parser/LuaSuffixedExprTreeGoldenTest.kt`, which
   compares against `src/test/resources/parser/suffixed-expr-trees.golden`, recorded from main.
   - **Snippets.** Dump 80 snippets with `DebugUtil.psiToString(file, false, true)`. They cover
     every suffix combination: name and paren primaries; `.`, `[ ]`, `:m()`, `()`, `{}` and string
     calls; calls after indexes and indexes after calls; assignment targets; comments and newlines
     inside chains; callback statements; and `global x = f(a).b` and
     `global function g() return f(a).b end`. Lua 5.5's soft `global` keyword parses at every
     language level, so no level setting is needed. 28 of the snippets are broken, such as
     `x = (a`, `x = a.`, `x = a:b`, `a.b(c) d`, `f() = 1`, `a.b(c) = 1`, `a, = 1` and
     `f(function() x = end)`.
   - **Assertion.** Assert the dumps equal a `testData` golden recorded from main. Main's output is
     `results/r6-old-344/dumps/old-snippets.txt`; the final prototype matches it byte for byte,
     error messages included.
   - **Source.** The snippet list is in
     `~/.cache/claude-scratch/lunar/bug-517/probe-src/Bug517DumpProbeTest.kt` (`snippets`).
   - **Durability.** The scratch files are outside the repository and will not survive. The
     checked-in golden test is the durable guard of tree shape. The corpus re-check in §7 can be
     repeated only while the scratch directory exists.
3. Keep `TestLuaParsingExhaustive`, `TestStandaloneExpression` and the rest of the suite green.

## 7. Verification

Executed on the libvirt builder, prototype against `a5be6b08`. Rows before the callback rows
were measured at `5133da91`, whose expression path the final prototype keeps unchanged. Callback
rows and the corpus total were measured on the final prototype.

| Measure | main | prototype |
| :-- | --: | --: |
| `(`×9 … `)`×9 (ms, min of 3) | 2542 | 0 |
| `(`×100 … `)`×100 | — | 2 |
| `f(`×18 / `t[`×18 | 3802 / 4729 | 0 / 0 |
| `f(a * (`×6 / ×100 | 3658 / — | 0 / 47 |
| `_snippet_grammar.lua` whole (two runs of 3) | 6385, 6449, 6466 | 40, 35, 25; 6, 5, 6 |
| its `local G = P({ … })` statement | 6316, 6291, 6230 | 9, 7, 7; 2, 1, 2 |
| `vim/_core/shared.lua` (50 KB) | 116, 78, 67 | 162, 92, 82; 43, 40, 32 |
| `expr` calls for the `G` statement | 906,585 | 164 |
| `f(function()` … `end)` statements, d = 10 / d = 16 | 2195 / — | 1 / 3 |
| `o:m("s", function()` … `end)` statements, d = 10 / d = 16 | 2478 / — | 1 / 4 |
| `local y = f(function()` … `end)`, d = 16 | 3526 | 2 |
| `expr` calls at one position, `f(function()` … `end)`, d = 16 | — (177,147 at d = 10) | 2 |
| corpus below, 6,550 parses (ms, total) | 126,588–141,319 (two runs) | 28,593 |

**Recursion guard.** Grammar-Kit's 1000-level guard still bounds nesting. The helper calls its
sub-rules one level down, as generated code does, so each `(` costs 3 levels (main: 4 on its
success path) and each call level 8 (main: 8). On the prototype, valid input first produces a parse
error at these depths: `(` 330, `f(` 125, `t[` 198, `a * (` 247 (measured at `5133da91`; the
statement change adds no levels to an expression). Main cannot be measured at those depths,
because its parse time is exponential.

**Equivalence.** The corpus is 1,310 files: `test/` (1,070), the bundled
`src/main/resources/runtime` stubs (108), Neovim `runtime/lua` at the pinned `779c3696` (128 plus 2
copies), and `src/test/resources` (2). Each file was parsed as is and in four broken variants:
truncated at half; first `)` after ⅓ deleted; first `(` after ⅔ deleted; first `end` after ½
deleted. Both parsers dumped every parse with `DebugUtil.psiToString(file, false, true)`, which
includes whitespace, comments, ranges and error elements:
- **Tree, including every error element's position and the error count:** identical in 6,550 of
  6,550 parses.
- **Full dump, including error-message text:** identical in 6,206 of 6,550 parses. That includes
  1,307 of the 1,310 unmodified files; 96 of the 1,310 already contain errors.
- **The other 344 parses differ only in 355 error-message texts.** They are 341 broken variants
  and three unmodified files that are themselves invalid Lua: luacheck's
  `compound_operators.lua` (`i += 10`, two copies) and `test/examples/completion.malformed_psi.lua`.
  The 355 fall into four groups:
  - **138: an expected-token list no longer offers `=` or `,` after an expression statement.**
    The assignment attempt that added them is no longer made when the statement parses as an
    expression. 39 of these also drop `<var or exp>`. Example: `nvim/tutor.lua` truncated reads
    `<name and args>, <statement>, <var suffix>, '&', '..', '^', 'and', 'else', 'elseif', 'end',
    'or', 'return', '|' or '~' expected`; main also listed `','` and `'='`.
  - **78: frame names drop out of an expected-token list.** In 58, `<var or exp>` drops out. In 14,
    `<assignment statement>`, `<var list>`, `<var>` and `<var or exp>` all do. In 6, `<var>` and
    `<var or exp>` do. Example: at an unbalanced `(` in `coxpcall.lua`, the list matches main's
    except that `<var or exp>` is missing.
  - **108: a `(` that cannot start a function's parameter list.** This is the unpinned-`funcBody`
    case in `.agents/AGENTS.md`. These read `'(' expected, got '('` (63) or `'(', '.' or ':'
    expected, got '('` (45), where main read `'(' unexpected`. Example:
    `vim/_core/help.lua` with an `end` deleted.
  - **31 others: the expected set differs in some other way.** Examples:
    - `i += 10` in `compound_operators.lua` (10 cases) reads `<expr>, '&', '..', '^', 'and', 'or',
      '|' or '~' expected, got '='`, where main read `<expr> expected, got '='`.
    - `luacheck/spec/cli_spec.lua` with a `)` deleted (6 cases) drops `'('`, `':'` and `STRING`.
    - At a statement start (8 cases), the list drops `<assignment statement>` and the statement
      keywords.
    - The remaining 7 add or drop one to seven entries.

  **These differences are accepted.** The tree and every error's position and count are unchanged,
  and only the wording of messages on already-broken code differs. Grammar-Kit builds those
  messages from the expected-token entries recorded by every attempt that reached the error
  position, and it renames or truncates them as frames fail. Main made extra attempts, so matching
  its wording exactly would mean making them again: re-parsing.

Full unit suite on the final prototype (`test --rerun --no-build-cache`): 3,284 tests, 0 failures,
0 errors, 4 skipped. A first run failed only
`LuaRenameConflictTest.testCancellationIsCheckedPerFileNameRefNotPerCollisionsCall` ("cost 9").
That is the intermittent [[BUG-529]]. The class then passed twice on its own, and a second full run
was green.
`ktlintCheck` passes.

### Verification of the delivered fix

Libvirt builder, the delivered tree (base `a5be6b08`).

- **Regeneration.** `generate.sh` over the delivered `lua.bnf` leaves `git diff src/main/gen`
  empty, so the committed `LuaParser.java` is the generator's output.
- **Production code.** `src/main` and `LuaParserNestingCostTest.kt` are identical to `de4346c9`,
  the tree the equivalence corpus above was run on. The corpus probe was not re-run.
- **Full suite** (`test --rerun --no-build-cache`): 3,285 tests, 0 failures, 0 errors, 4 skipped.
  That is the prototype's 3,284 plus the golden test. A first run hung: the EDT spun at 100% CPU
  in the test framework's event-dispatch loop inside
  `DefinitionPinProvisioningTest.testStaleCachedTreeIsReplacedFromTheArchive`
  (`highlightDescriptions` → `doHighlighting`), with no parser frame in any thread. That class
  passed 8 of 8 on its own and in the rerun, and in all four prototype full runs. Not attributed
  to this change; not filed.
- **`ktlintCheck`:** passes.
- **The tests can fail.** Each mutation was run against the delivered code, then restored:

  | Run | Cost test | Golden test |
  | :-- | :-- | :-- |
  | delivered | green | green |
  | main's `lua.bnf`, `LuaParser.java`, `LuaParserUtil.kt` | red: all five shapes (parens 3179 → 202091; calls 582 → 4670; index 659 → 5363; callMulParens 32410 → 16589978; callbacks 3710 → 101639) | **green**, so the golden is main's output |
  | A: `parseParenPrimary` parses `'(' expr`, rolls back, parses again | red: parens 394 → 2970; callMulParens 758 → 6246 | green (trees unchanged) |
  | D: `exprOnlyStatement` removed from `statement`, regenerated | red: callbacks 1236 → 10532 | green (trees unchanged) |
  | `VAR` dropped before trailing calls (`doneBefore` replaced by `drop`) | green | red: 33 snippets moved, each named |
- **Live.** GoLand 2026.2.3 `runIde`, with Lunar loaded from this tree. The project held
  `_snippet_grammar.lua` and a 12-level `describe(…, function() … end)` file. Typing a new
  `extra = V(1) + ((((((((Ct(Cg(V(2), 3)))))))))) / node(Type.Text),` entry inside
  `local G = P({` rendered and highlighted within the 1 s before the screenshot. The editor's
  error count moved from 44 to 47, so analysis re-ran; the errors are type-assignability findings
  on LPeg operators, not parse errors. Typing a nested `it(function() vim.schedule(function()
  print(((((((((((2))))))))))) end) end)` at depth 13 in the callback file behaved the same. No
  freeze dump and no `UI was frozen` line appeared in the IDE log.
