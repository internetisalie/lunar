---
id: "BUG-542-BATCH"
title: "LuaCATS grammar batch: delivery plan for BUG-542–549, BUG-502 and BUG-514"
type: "plan"
parent_id: "BUG"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# LuaCATS grammar batch: delivery plan

There are ten reports and eight defects. All of them are gaps in `luacats.bnf`/`luacats.flex`, and
they ship as one PR with one commit per defect.

Each report's §4–§6 holds its root cause, its grammar edit and its tests. This plan holds only what
spans reports:
- merges
- the masking mechanism
- the shared test helper
- order and rule interactions
- regeneration
- corpus movement
- the stop rule

Paths are relative to the repository root. `bnf` means
`src/main/kotlin/net/internetisalie/lunar/luacats/lang/parser/luacats.bnf` and `flex` means
`…/luacats/lang/lexer/luacats.flex`.

## 1. Scope and merges

| # | Commit | Defect | Edits |
| :-- | :-- | :-- | :-- |
| 1 | [[BUG-549]] | `<TAG_SEE>` cannot lex `\|text\|` | flex |
| 2 | [[BUG-548]] | `<TAG_FIELD>` has no string-literal rule | flex |
| 3 | [[BUG-542]] + [[BUG-502]] | no `?` after a table key or a `fun` parameter name | bnf, `TypeParser.kt` |
| 4 | [[BUG-543]] | `<TAG_FIELD>` has no `NUMBER`; a table key cannot be `[type]` | flex, bnf, `TypeParser.kt` |
| 5 | [[BUG-544]] + [[BUG-545]] | no nested `T?`, no `(A, B)` return list | bnf, `TypeParser.kt` |
| 6 | [[BUG-546]] | no `async` prefix on `fun` | bnf |
| 7 | [[BUG-547]] | no type-only `fun` parameter | bnf, Kotlin consumers |
| 8 | [[BUG-514]] | `<TAG_RETURN>` has no string rule; no `...` in `fun` parameters | flex, bnf, Kotlin consumers |

Merges:
- **502 into 542.** It is the same rule, `bnf:195`. The `@param` host hides it behind `ldocParamTag`
  (`bnf:153`).
- **544 with 545.** Both are the same rule, `bnf:200`, and 545's corpus site needs both halves.

BUG-514 and BUG-548 are the same *class* of defect (string lexing per state), but they sit in
different states with separate reports, so they stay separate commits.

## 2. The host-tag fallback hides failures

Read from code, not executed (probe P1). The grammar has no pins below `commentLine` (`bnf:39`), so a
failed type rolls back silently. What happens next depends on the tag:
- **Optional type, then `description`.** `@alias` (`bnf:82`) and the `@type`/`@return` tail fall to
  `description ::= (NAME | NUMBER | STRING | SYMBOL | TEXT | CODE)+` (`bnf:214`). That rule takes the
  failed text as prose, with **no error**, unless it reaches a `KEYWORD` such as `fun` (`flex:217`),
  which `description` rejects.
- **Prose fallback tag.** `@param` falls to `ldocParamTag` (`bnf:153`), again with no error.
- **Required type, no fallback.** `@field` (`bnf:108`) reports an error.

So several "controls" in the reports parse only as prose:
- 542's `@alias Q5`
- 543's `P3` and `Q9`
- 544's `Q1`
- 545's `@type` forms
- 548's `@param`

A test that asserts only "no `PsiErrorElement`" passes on HEAD for those forms. **Every positive test
in this batch also asserts the type text** (§3).

## 3. Shared test helper

Add one helper to the existing class `src/test/kotlin/net/internetisalie/lunar/luacats/lang/parser/LuaCatsParserTest.kt`.
That class is a JUnit 5 `BaseDocumentTest`, the light `CodeInsightTestFixture` every LuaCATS parser
test uses.

```kotlin
private fun assertTagType(code: String, tagClass: Class<out PsiElement>, expectedType: String) {
    doTest(code)
    runReadAction {
        val tag = requireNotNull(PsiTreeUtil.findChildOfType(myFixture.file, tagClass)) { "no ${tagClass.simpleName}" }
        val argType = PsiTreeUtil.getChildOfType(tag, LuaCatsArgType::class.java)
        Assertions.assertEquals(expectedType, argType?.text?.trim())
    }
}
```

- `tagClass` is the PSI interface whose direct `LuaCatsArgType` child is the type: `LuaCatsAliasTag`,
  `LuaCatsTypeTag`, `LuaCatsFieldTag` or `LuaCatsReturnTypeDescriptor`.
- Controls use the existing `doTest(code, expectErrors = true)`.
- They assert presence, never the error's offset or text. An unpinned failure reports where its
  section started, not where it failed (AGENTS.md, "A parse error's LOCATION…").

## 4. Order and rule interactions

The order is lexer-only commits first, then grammar commits in dependency order. Each commit compiles
and passes on its own, so a later regression bisects to one commit.

- **549, 548 first.** They are lexer-only and independent. 549 is also the regeneration smoke test.
- **542 before 543.** `view/render.lua:630` writes `[3]?:`, which needs 542's key `?` on 543's
  bracket key.
- **542, 543, 544 before 547 and 514.** All of them rewrite `functionSignatureArgument`. 547 and 514
  turn it into alternatives and change its accessors, so they come last, and only they need Kotlin
  null-safety edits.
- **548, 543, 514** each add rules to the same `<TAG_FIELD>` block (`flex:161-168`).

End state of the shared rules. Each report shows only its own delta.

```
tableLiteralEntry ::= (simpleType | fieldKeyDescriptor) '?'? ':' optionalType          // 542, 543, 544
dictionaryType ::= '{' '[' type ']' ':' optionalType '}'                               // 544
functionSignatureType ::= 'async'? 'fun' '(' functionSignatureArguments? ')' functionSignatureReturnType?  // 546
functionSignatureArgument ::= (<<ArgName argumentName>> '?'? <<ArgSymbol (':')>> <<ArgType optionalType>>)  // 542, 544
                            | <<ArgName ('...')>>                                       // 514
                            | <<ArgType optionalType>>                                  // 547
private argumentName ::= NAME | '...'                                                   // 514
functionSignatureReturnType ::= <<ArgSymbol (':')>> <<ArgType functionReturnTypes>>     // 544
private functionReturnTypes ::= '(' optionalType { ',' optionalType }* ')' | optionalType
private optionalType ::= type '?'?
```

`<TAG_FIELD>` ends as: the three scope keywords, `{NUMBER}` (543), `{NAME}`, `{ELLIPSIS}` (514),
`{STRINGD}` and `{STRINGS}` (548), `{TYPES}`, then the catch-all. `{NUMBER}` must precede `{NAME}`,
as in `<TAG_TYPE>`.

Constraints these edits respect (AGENTS.md lessons):
- **Every new helper is `private`.** No public rule is added. 543 reuses the existing public
  `fieldKeyDescriptor` (`bnf:113`).
- **No `recoverWhile`.**
- **A top-level tag type gains no `?`.** A `?` there would put `string?` into `argType.text`, and
  `LuaTypeGraphBridge.isSimpleTypeName` (`LuaTypeGraphBridge.kt:20`) would look it up as a class name.

## 5. Regeneration points

**Regenerate once per commit**, after that commit's grammar and Kotlin edits. Regenerating once at the
end is rejected for two reasons:
- CI never regenerates, so every intermediate commit would carry a stale `src/main/gen` and could not
  be built, tested or bisected.
- `generate.sh` compiles Kotlin *before* regenerating (step 1) and again after (step 7). Each commit's
  consumer edits are proven against exactly one old and one new accessor set.

Per-commit protocol:
1. Add the report's tests. Run them red on the builder:
   `tooling/gce-builder/gce-builder.sh run "test --tests *LuaCatsParserTest* --tests *TestLuaCatsLexer* --tests *TestTypeParser* --tests *LuaCatsDeclarationsTest* --tests *LuaParameterInfoHandlerTest*"`.
2. Make the Kotlin consumer edits (commits 3, 4, 5, 7, 8). They must compile against the
   *current* generated code.
3. Edit bnf/flex. Run `.claude/skills/generate-parser/scripts/generate.sh`.
   - It runs `git checkout --` on 14 Lua-grammar stub files under `src/main/gen/.../lang/psi/`.
   - None of those files is touched by this batch. Keep no uncommitted edits there.
4. Check `git diff --stat src/main/gen`. It must list only the files predicted below. Any file outside
   `src/main/gen/net/internetisalie/lunar/luacats/` means `lua.bnf` regeneration was not a no-op.
   Stop.
5. Run the same tests green, then commit: grammar, generated code, Kotlin, tests and the report.

| Commit | Predicted `src/main/gen` delta | Accessor change → consumer edits (made before step 3) |
| :-- | :-- | :-- |
| 549, 548 | `_LuaCatsLexer.java` | none |
| 542, 546 | `LuaCatsParser.java` | none |
| 543 | `_LuaCatsLexer.java`, `LuaCatsParser.java`, `LuaCatsTableLiteralEntry.java` + `Impl` | adds `getFieldKeyDescriptor()`; no consumer edit needed |
| 544 + 545 | `LuaCatsParser.java` | none |
| 547 | `LuaCatsParser.java`, `LuaCatsFunctionSignatureArgument.java` + `Impl` | `getArgName`/`getArgSymbol` become nullable → `LuaParameterInfoHandler.kt:182,228`, `TypeParser` |
| 514 | `_LuaCatsLexer.java`, `LuaCatsParser.java`, `LuaCatsFunctionSignatureArgument.java` + `Impl` | `getArgType` becomes nullable → `LuaParameterInfoHandler.kt:183,229`, `TypeParser` |

The nullability predictions are probe P2.

## 6. Expected corpus movement

Metric definitions:
- `parseErrors` counts `PsiErrorElement`s (`CorpusSweep.kt:356`). `parseErrorFile=` lists one line
  per element.
- `oracleDisagreements` counts *files* that `luac` accepts and Lunar rejects (`CorpusSweep.kt:57-60`).

Expected `lazynvim.baseline` values after each commit (all paths under `lua/lazy/`):

| After | `parseErrorFile=` lines removed | `parseErrors` | `oracleDisagreements` | `oracleSite=falseReject:` lines removed |
| :-- | :-- | :-- | :-- | :-- |
| HEAD | none | 17 | 14 | none |
| 549 | `core/cache.lua:228` | 16 | 13 | `core/cache.lua` |
| 548 | `pkg/init.lua:11` | 15 | 12 | `pkg/init.lua` |
| 542 + 502 | `core/plugin.lua:16`, `pkg/rockspec.lua:13`, `view/float.lua:8`, `view/float.lua:28`, `view/init.lua:11`, `view/render.lua:19` | 9 | 9 | `pkg/rockspec.lua`, `view/float.lua`, `view/init.lua` |
| 543 | `core/handler/keys.lua:13`, `manage/runner.lua:6`, `manage/semver.lua:4`, `types.lua:52`, `view/render.lua:630` | 4 | 4 | `core/handler/keys.lua`, `manage/runner.lua`, `manage/semver.lua`, `types.lua`, `view/render.lua` |
| 544 + 545 | `view/sections.lua:13`, `core/plugin.lua:149` | 2 | 2 | `view/sections.lua`, `core/plugin.lua` |
| 546 | `manage/task/init.lua:12` | 1 | 1 | `manage/task/init.lua` |
| 547 | `manage/task/plugin.lua:65` | 0 | 0 | `manage/task/plugin.lua` |
| 514 | none | 0 | 0 | none |

Each site was matched to its defect by reading the source line at the pinned commit `85c7ff37`. It
was not executed.

The table lists only the first error in each comment block. After a failed line, the rest of the block
stays unparsed and reports nothing, so a second defect in the same block shows up only once the first
is fixed. `core/handler/keys.lua:14` (`---@field [2]? …`, an optional bracket key in a `@field` name) is
one such site; it is fixed under [[BUG-543]].

Other baselines:
- `luacheck`, `luarocks`, `penlight` and `zerobrane` have no LuaCATS parse errors to lose. Their
  `parseErrors` must not change.
- **Inspection counts will probably move, and nobody can predict their direction.** The
  `highlightFailures` key is absent, so `inspection.*` keys are gated (`CorpusMetrics.kt:280-283`).
- lazynvim's pinned `neovim` library (`779c3696`) uses these constructs on hundreds of annotation
  lines (grep of the archive):
  - `fun(…):T?`: 48 lines
  - `name?:` in `fun`: 24 lines
  - `k?:` in tables: 29 lines
  - `fun(...`: 26 lines
  - `async fun`: 4 lines
  - `@see |…|`: 33 lines
  - `@field … "…"`: 9 lines
  - `{[`: 20 lines
- The LUAJIT 2.1 stub `runtime/luajit/luajit-2.1/coroutine.lua:30` (`fun(...: any): ...`) becomes a
  real `@param` after 514.
- BUG-502's fix should remove a false *Too many arguments* hit. Whether that hit is in the current
  baseline is not established.

### Measured movement

Recorded once, after the last commit, with the full `*Corpus*` gate. Only `lazynvim.baseline` moved;
the other members' baselines are byte-identical, and the type goldens did not move.

| Key | HEAD | Measured |
| :-- | :-- | :-- |
| `parseErrors` | 17 | 0 |
| `oracleDisagreements` | 14 | 0 |
| `parseErrorFile=` / `oracleSite=falseReject:` lines | 17 / 14 | none / none |
| `inspection.LuaTypeAssignability` | 447 | 526 |
| `inspection.LuaReturnTypeMismatch` | 56 | 64 |
| `inspection.LuaSuspiciousConcatenation` | 14 | 12 |

The inspection rises were triaged before recording. Every hit's file, line and message was dumped from
the sweep on HEAD and on the last commit, and the two multisets were diffed. Each group's cause was then
reproduced by a light-fixture probe on `8e7a48f2`, before any of this batch's edits. Counts are hits
added; "net" also subtracts hits that disappeared. TA is `LuaTypeAssignability`, RTM `LuaReturnTypeMismatch`.

| Group | Hits | Example site (`lua/lazy/`) | Class | Action |
| :-- | :-- | :-- | :-- | :-- |
| A method's optional `---@param name?` is required in its declared signature | +140 *Too few*, −60 *Too few*, −28 *Too many*; net +52 TA | `view/render.lua:175` `self:append("Tasks: ", "LazyH2")` | pre-existing engine gap | filed [[BUG-554]] |
| `---@field name T?` is required; the top-level `?` admits no nil | +9 TA, +1 RTM | `core/plugin.lua:95` `Missing required field 'url'` | pre-existing engine gap | filed [[BUG-555]] |
| A bracket-keyed `@field [1] T` is a required member named `"[1]"` | +14 TA, +7 RTM | `core/plugin.lua:95` `Missing required field '[1]'` | pre-existing engine gap | filed [[BUG-556]] |
| A `@class` declaration's own table is checked for instance fields | +6, −4 TA | `core/handler/ft.lua:5` `local M = {}` | pre-existing engine gap | filed [[BUG-558]] |
| Several `@class` blocks and a `@type` are all applied to the next statement | +1 TA | `pkg/init.lua:30` `M.cache = nil` | pre-existing engine gap | filed [[BUG-557]] |
| An empty `{}` is not assignable to a table-literal array | +1 TA | `core/plugin.lua:150` `local modspecs = {}` | pre-existing engine gap | already [[BUG-481]] |
| `---@return LazyPkg?` returns a `LazyPkgSpec`-shaped table | +1 RTM | `pkg/lazy.lua:21` `Missing required field 'spec'` | genuine finding in lazy.nvim | kept |
| `string \| { ... } \| { ... }` returned against `nil \| string` | −1 RTM | `core/util.lua:118` | removed | — |

No hit is a false positive introduced by code this batch changed. The arity group is the largest. Its
removed *Too many* hits are [[BUG-502]]'s intended fix, `view/render.lua:514` among them. Its added
*Too few* hits come from the same `@param` tags, which now parse.

## 7. Gates and stop rule

Gates run after the last commit:
- the full suite `tooling/gce-builder/gce-builder.sh run test` (a `--tests` subset can mask a
  full-suite failure)
- `run ktlintCheck`
- the corpus gate `run "test -PwithCorpus --tests *Corpus*"`. Use the full `*Corpus*` filter, never
  one member (BUG-418).

Read the gate output before recording:
1. **Stop and do not record** if `parseErrors` rises on any member, or if any `parseErrorFile=` line
   appears that is not in HEAD's baseline. That is a grammar regression.
   - Find the commit by running the same corpus gate at commit boundaries.
   - Fix it inside that commit. If one fix attempt does not clear it, drop that commit from the PR,
     record the finding on its report, and deliver the rest.
2. **Stop** if a §6 line that should be removed survives. That commit's fix is incomplete, and
   recording would bury it.
3. **Triage before recording** if a gated `inspection.*` key rises on any member. Diff the
   `symbol.<Inspection>.<name>` rows to locate the new hits.
   - A hit caused by the new PSI being read wrongly is a defect of the responsible commit. Example:
     an optional parameter treated as required. Rule 1 applies.
   - True positives are recorded, and the PR description lists them.
4. Only then record with `run "test -PwithCorpus -PrecordCorpusBaseline --tests *Corpus*"`. Copy the
   echoed `[corpus] recorded …` baselines into the working tree, and add them as a final commit
   (`test(corpus): …`). Recording once, in the gate's invocation shape, costs one sweep instead of
   eight.

## 8. Probes (not executed) and deferred findings

Probes:
- **P0.** Run `generate.sh` on unmodified HEAD. `git diff --stat src/main/gen` must be empty. This
  proves the pipeline before commit 1.
- **P1.** On HEAD, check the masking model in §2. `---@alias Q5 {name:string, file?:string}` must give
  a `LuaCatsAliasTag` with no `LuaCatsArgType` and a `LuaCatsDescription`. `---@param k "a"|"b"` must
  give a `LuaCatsLdocParamTag`. Each report's positive tests going red in step 1 of §5 is the same
  check.
- **P2.** The accessor predictions in §5. Grammar-Kit gives a child required in every branch
  `@NotNull`; compare `LuaCatsType.getUnionType()` for `bnf:177`.
- **P3.** The §6 table, measured by the corpus gate.
- **LuaLS acceptance.** Reports say LuaLS accepts each construct. That rests on the LuaLS annotation
  documentation and on the pinned neovim library using the forms. No LuaLS run backs it.

Deferred findings. Each is out of scope here and is filed as its own bug with a roadmap row.
- `<TAG_PARAM>` (`flex:187-193`) has no string-literal rule. `---@param k "a"|"b"` becomes LDoc prose
  (548's control). Filed as [[BUG-559]].
- `type ::= '(' unionType ')'` (`bnf:177`) takes no `[]`, so the `[]` in `(A|B)[]` is prose. This
  affects the 543 and 547 corpus forms, which are not errors. Filed as [[BUG-560]].
- The bundled stubs spell literals `'"nil"'` and varargs `fun(...any)`. No lexer state accepts either
  (514 §4). That leaves the stub sites in BUG-514 §3 unparsed until either the stubs or `STRINGS`
  (`flex:76`) change. Filed as [[BUG-561]].
- `<TAG_RETURN>` has no `{NUMBER}` rule, so `---@return 1|2` fails the same way BUG-543 did under
  `@field`. Filed as [[BUG-562]].
- `<TAG_CAST>` has no `{ELLIPSIS}` rule, so `---@cast x fun(...)` reads as a class named `fun`. Filed
  as [[BUG-563]].
