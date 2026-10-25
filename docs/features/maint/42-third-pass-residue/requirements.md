---
id: "MAINT-42"
title: "42: Third-Pass Residue — P3 Cleanups in the Files the July Review Never Covered"
type: "feature"
parent_id: "MAINT"
status: "todo"
priority: "low"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-42: Third-pass residue

The remaining P3 findings of the third review pass ([`docs/review.md`](../../../review.md) §6), from nine
files that §1–§4 never named. Coalesced for the reason MAINT-36 gave: about fifteen one-line diffs across six
files. The P2s are filed as BUG-520 to BUG-523, the `LuaDocGenerator`-related findings (#102, #103, and the
`isDocCommentEmpty` half of #96) are in MAINT-36, and wildcard imports belong to BUG-421.

## Findings in scope

| Finding | File | Issue | Note |
| :-- | :-- | :-- | :-- |
| #93 | `lang/syntax/LuaComment.kt:10-16` | `LuaDocDescription` and `LuaDocSummary` are empty objects with zero references and a TODO naming a class that no longer exists | Delete |
| #94 | `LuaComment.kt:83-90` | `summarize` compares the trimmed first line with the untrimmed input, so `--[[ note ]]` folds to `--[[note...]]` | Not executed; `LuaFoldingTest` covers only multi-line cases |
| #95 | `LuaComment.kt:56-81` | Delimiter helpers assume a terminated long comment; an unterminated `--[[ abc` drops two characters. The long-bracket level is computed in three places (here, `LuaLiterals`, `ExtendedSyntaxStrCommentHandler.getLevel`) | Fold into `LuaLiterals` |
| #96 | `LuaComment.kt:92-147` | `collectDescriptionText` omits `ldocParamTagList` and `typeOptionList` | The `isDocCommentEmpty` half is MAINT-36 |
| #97 | `LuaComment.kt:19-53` | `LuaCatsSummary.getText` tests `next.text == "\n"`, but `luacats.flex` emits whitespace and newline as separate tokens, so a blank doc line with trailing spaces truncates the summary | Not executed; settle with a `DebugUtil.psiToString` dump |
| #99 | `LuaSyntaxHighlighter.kt:34-61` | A per-instance `MutableMap` is rebuilt on every construction, including per documentation render | Move to a companion `Map` |
| #101 | `LuaCrossFileCompletionScopeProcessor.kt` | `isTopLevel` is always true, the prefix pre-filter is case-sensitive, two branches are dead | Moot if BUG-520 deletes the file |
| #104 | `ExtendedSyntaxStrCommentHandler.kt:50-56` | `getLevel` guards `cs.length > comment + level` but reads `cs[comment + 1 + level]` | Safe only because of the callers' regexes |
| #105 | `ExtendedSyntaxStrCommentHandler.kt:32` | `longQLevel` lives outside the JFlex state and `reset()` never clears it; correct only because the merging adapters present one token | Make it private and state the dependency in KDoc; not executed |
| #107 | `LuaApplicationSettingsPanel.kt:62-70` | `getData` has a side effect (`dropResolveCaches()`) | Moot once BUG-522 removes the panel content |
| #108 | `lang/psi/types/LuaScope.kt:20-30` | `declare`'s return value is discarded at every production site, and `isShadowing` is called only by one test | Return `Unit` and delete `isShadowing`, or route the shadowing inspection through it |
| #110 | `run/LuaSuspendContext.kt` | Write-only `breakpoint` field, 4-parameter constructors, hard `Project` | Rides with BUG-523 |
| #111 | nine files | Contract sweep except wildcard imports: fully-qualified inline names (`LuaCrossFileCompletionScopeProcessor.kt:20`, `LuaGenerateDocIntention.kt:61`, `LuaLocalVarStubElementType.kt:20`, `LuaApplicationSettingsPanel.kt:42`); public mutable state (`longQLevel`, `results`, the highlighter map); non-localized UI strings (`LuaGenerateDocIntention.kt:12,14`, `LuaApplicationSettingsPanel.kt:43`); `LuaComment.kt` is in `lang/syntax/` but holds LuaCATS text helpers | Move `LuaCatsSummary` and `collectDescriptionText` to `luacats/lang/doc/` |

## Requirements

Not planned. Plan with `plan-feature`. Each requirement will need a test that can fail; #94 and #97 first need a
reproduction run, since the review only read them.
