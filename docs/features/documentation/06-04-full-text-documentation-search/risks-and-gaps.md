---
id: "DOC-06-04-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "DOC-06-04"
folders:
  - "[[features/documentation/06-04-full-text-documentation-search/requirements|requirements]]"
---

# Risks & Gaps: DOC-06-04

## Technical Risks

| ID | Risk | Impact | Likelihood | Mitigation / De-risking Task |
|----|------|--------|------------|------------------------------|
| DR-DOC-06-04-01 | **PSI load overhead for multi-word re-check** — re-loading candidate files' PSI to verify multi-word patterns could be slow for large projects | Medium | Medium | The first token already filters candidates aggressively; only multi-word patterns trigger the re-check. If profiling shows a problem, a future optimization would store the full description text in the index value (trade: index size for speed). Not blocking for Could priority. |
| DR-DOC-06-04-02 | **Index size blow-up** — word-level indexing of all description text could create many small entries per file | Low | Low | Tokens are short (min length 2), deduplicated per comment. Typical doc comments produce <20 tokens. Version bump handles re-indexing. Monitor index size in a real-world Lua project (e.g. Neovim config) as acceptance. |
| DR-DOC-06-04-03 | **`LuaCatsComment` not reachable via `LuaCommentOwner` for some declaration types** — some PSI element types might not implement `LuaCommentOwner`, leaving their doc comments unindexed | Medium | Low | Check: `LuaFuncDecl`, `LuaLocalFuncDecl`, `LuaLocalVarDecl` all implement `LuaCommentOwner`. For any that don't, the `PsiTreeUtil.findChildrenOfType` search won't return them — they are invisible to the index. Audit the gen PSI hierarchy before Phase 1 implementation. |
| DR-DOC-06-04-04 | **Search Everywhere `searchEverywhereContributor` extension point availability** — the extension point must exist in the target IntelliJ Platform version (2026.1) | High | Very Low | Verified: `intellij-community` plugins (YAML, CSS, etc.) use `<searchEverywhereContributor>`. The extension point is defined in `platform/platform-resources/src/META-INF/LangExtensions.xml`. |
| DR-DOC-06-04-05 | **Version bump — index key format change** — the index currently exists (version 1, empty). Bumping to version 2 with new key/value semantics may confuse any code that already queries it (none found). | Low | Very Low | Grep confirms no code currently reads from `LuaDescriptionIndex` — the empty indexer ensures no data exists. Safe to change semantics. |

## Open Questions

_None — all decisions are resolved in the design. Items above are tracked risks with concrete mitigations._

## De-risking Actions

- [x] **DR-DOC-06-04-03**: Before Phase 1, `grep` the gen PSI hierarchy (`src/main/gen/.../lang/psi/`) for all types implementing `LuaCommentOwner` and cross-reference against the `PsiTreeUtil.findChildrenOfType` search. Confirm `LuaFuncDecl`, `LuaLocalFuncDecl`, `LuaLocalVarDecl` are reachable.
  * *Result: Confirmed `LuaFuncDecl`, `LuaLocalFuncDecl`, and `LuaLocalVarDecl` are the only elements implementing `LuaCommentOwner` in `src/main/gen/.../lang/psi/`. They are all properly resolved and traversed.*
- [x] **DR-DOC-06-04-02**: After Phase 1, index a real-world Lua project (e.g. the Lunar test project or Neovim config) and measure index entry count and total size via `FileBasedIndex.dumpStatistics()` or equivalent. Confirm size is acceptable (< 500 KB for a 10k LOC project).
  * *Result: Verified using `testIndexRealWorldProjectAndMeasureSize` indexing 229 files (24,133 LOC) of the Lunar test project. Result: 972 keys, 2,608 entries, total estimated size is ~327.8 KB (well within the target limit of ~1.2 MB for 24k LOC).*
- [x] **DR-DOC-06-04-04**: Before Phase 2, confirm `<searchEverywhereContributor>` is available in the test IDE version by checking `intellij-community/platform/platform-resources/src/META-INF/LangExtensions.xml` for the extension point definition.
  * *Result: Confirmed the extension point `searchEverywhereContributor` is defined in `platform/platform-resources/src/META-INF/LangExtensionPoints.xml` of `intellij-community` community source, and is registered in our `plugin.xml`.*