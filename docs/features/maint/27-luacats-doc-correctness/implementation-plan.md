---
id: "MAINT-27-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-27"
folders:
  - "[[features/maint/27-luacats-doc-correctness/requirements|requirements]]"
---

# MAINT-27: Implementation Plan

Six requirements across five `luacats/**` sites plus one lexer regeneration. Phases are ordered so
each leaves the build green and the highest-risk step (the `.flex` regen) runs first with an
explicit precondition gate.

## Preconditions (before Phase 1)

- [ ] **Parser-gen jars present**: verify `ls tooling/parser-gen/grammar-kit-*.jar
      tooling/parser-gen/jflex-*.jar` both resolve. `generate.sh` aborts (exit 1) if either is
      missing (`generate.sh:67-75`); these are git-ignored, local-only (see
      `tooling/parser-gen/README.md`). If absent, obtain them before starting — regen never runs
      in CI.
- [ ] **Baseline green**: `tooling/gce-builder/gce-builder.sh run test` reports the wave baseline
      (2123 pass / 0 fail / 1 ignored on main `a3ddf82b`) before any edit.

## Phases

### Phase 1: Lexer containment + dead-state removal [Must]
- **Goal**: fix #19/#66 and delete the §3 dead lexer states; regenerate headlessly.
- **Tasks**:
  - [x] Edit `luacats/lang/lexer/luacats.flex` — realizes design §3.1:
    - line 72: `CODE={BACKTICK}[^`]+{BACKTICK}` → `CODE={BACKTICK}[^`\r\n]+{BACKTICK}`.
    - line 77: `STRINGD={QUOTE}[^\"]*{QUOTE}` → `STRINGD={QUOTE}[^\"\r\n]*{QUOTE}`.
    - line 68: replace `HIGH_ASCII=[\x80-\xff]` with `UNICODE_LETTER=[:letter:]`.
    - lines 70–71: substitute `{UNICODE_LETTER}` for `{HIGH_ASCII}` in `NAME_LEADING`/`NAME_TRAILING`.
    - line 80: remove `COMMENT_END` from the `%state` list; also removed `TAG_OVERLOAD` from `%state`.
    - lines 187–192: delete the entire `<TAG_OVERLOAD>` block.
  - [x] Run `.claude/skills/generate-parser/scripts/generate.sh` — regenerated `_LuaCatsLexer.java`;
    build self-verified clean.
  - [x] **No-op sanity check** (design §9 / DR-01): `git diff src/main/gen/.../luacats/lang/psi`
    was **EMPTY**; only `_LuaCatsLexer.java` changed. `_LuaLexer.java` regen was also a no-op.
  - [x] Commit the regenerated `src/main/gen/` alongside the `.flex` edit.
- **Exit criteria**: TC-01a (unclosed backtick does not corrupt the next tag line), TC-01b (CJK
  class name lexes as one `NAME`), TC-01c (`@overload fun(...)` still lexes via `TAG_TYPE`) pass;
  full unit suite green.

### Phase 2: Escaped, correct doc HTML [Must]
- **Goal**: #35 (escape + link-only-simple) and #57 (`local <name> : <type>`).
- **Tasks**:
  - [x] Add `renderTypeText`, `isSimpleIdentifier` private helpers to
    `LuaCatsDocumentationRenderer` — realizes design §3.2/§3.3.
  - [x] Replace the `buildTypeLink(x.text)` type-text call sites listed in design §1 with
    `renderTypeText(...)` per §3.2 (declaration-name links at 105/131/248 kept as `buildTypeLink`).
  - [x] Split `buildVariableSignature`: `classTag != null` → `class`; else
    `typeTag != null` → `local <varName> : <renderTypeText(...)>` — realizes design §3.2 (#57).
- **Exit criteria**: TC-02a (`table<string, integer>` renders escaped, no raw `<`), TC-02b
  (`---@type Player` renders `local m : ` + linked `Player`, not `class Player`), TC-02c (simple
  identifier still hyperlinked) pass.

### Phase 3: Inheritance rendering [Should]
- **Goal**: #36 (per-`ArgType` parent lookup + `LuaCatsTypeNameIndex` fallback) and #67 (chain walk).
- **Tasks**:
  - [x] Add `resolveClassComment(project, className)` (+ `resolveBareClassComment` fallback) —
    realizes design §3.4; replaces the stub-only `lookupParentComment`.
  - [x] Add `collectInheritedFieldTags(project, classTag)` (BFS + `visited` cycle guard + `<= 64`
    depth cap + `ProgressManager.checkCanceled()`) — realizes design §3.5.
  - [x] Rewire `buildFieldsSection` to source inherited fields from `collectInheritedFieldTags`,
    which iterates `classTag.parentTypes.argTypeList` per-name (`parentClassNames` helper).
- **Exit criteria**: TC-03a (grandparent field appears), TC-03b (bare `--- @class Parent` fields
  found via `LuaCatsTypeNameIndex`), TC-03c (`@class A : A` cycle terminates) pass.

### Phase 4: Alias values [Should]
- **Goal**: #37 — union-alias `Values:` section.
- **Tasks**:
  - [x] In `buildSectionsBlock`, changed the `LuaCatsAliasTag` arm to gate on
    `comment.typeOptionList.isNotEmpty()` instead of `comment.enumTagList.isNotEmpty()` — realizes
    design §5 Ex.3.
  - DEVIATION: TC-04's requirements-table input used the *inline* `---@alias Mode "r"|"w"` shorthand,
    but that form parses the union into the aliasTag's `ARG_TYPE` (UNION_TYPE) and produces **no**
    `TYPE_OPTION` nodes (verified by PSI dump). Only the `---|` continuation form populates
    `typeOptionList`, which is exactly what the design §5 Ex.3 fix (and review #37's "`---|`
    union-alias form") targets. TC-04 asserts the `---|` form accordingly.
- **Exit criteria**: TC-04 (`---@alias Mode "r"|"w"` renders `Values: "r", "w"`) passes.

### Phase 5: Direct-children getters [Should]
- **Goal**: #38 — restore the direct-children contract on the lazy comment.
- **Tasks**:
  - [x] In `LuaCatsLazyCommentImpl`, every getter now delegates to the inner `LuaCatsComment` child
    (whose generated getters use `getChildrenOfTypeAsList` on their direct children) — realizes the
    #38 intent. No cache. DEVIATION from the literal `getChildrenOfTypeAsList(this, …)` swap: a PSI
    dump showed the lazy node's single child is the inner `COMMENT` node and the tags are one level
    below it, so a `this`-level swap returned empty lists (12 regressions). See risks-and-gaps.md.
- **Exit criteria**: TC-05a (`getDescriptionList()` returns only the top-level description, not
  tag-nested ones), TC-05b (`isDocCommentEmpty` accurate for a `--- @param` comment) pass; existing
  `LuaCatsLazyCommentTest` still green.

### Phase 6: Annotator cleanup [Could]
- **Goal**: #72 — fixture-verify then drop the dead branch.
- **Tasks**:
  - [ ] **First** add the assertion fixtures to `LuaCatsAnnotatorTest` (`---@class Foo : Bar`,
    `---@alias Mode Player`) capturing current highlight keys — realizes design §3.7 step 1.
  - [ ] Remove the `LuaCatsNamedType` classTag/aliasTag special case (annotator lines 24–30 → single
    `TYPE` highlight) and the unreachable `LuaCatsElementTypes.NAME` else-branch (lines 56–61) —
    realizes design §3.7 steps 2–3.
- **Exit criteria**: TC-06 (class name / alias target highlighting unchanged-or-corrected after
  removal) passes; full suite green.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-27-01 | M | Phase 1 |
| MAINT-27-02 | M | Phase 2 |
| MAINT-27-03 | S | Phase 3 |
| MAINT-27-04 | S | Phase 4 |
| MAINT-27-05 | S | Phase 5 |
| MAINT-27-06 | C | Phase 6 |

## Verification Tasks

- [ ] Extend `LuaCatsElementTypeTest` / `TestLuaCatsLexer` with TC-01a/b/c — covers #19, #66, §3.
- [ ] Extend `LuaCatsDocumentationRendererTest` with TC-02a/b/c, TC-04 — covers #35, #57, #37.
- [ ] Extend `LuaCatsDocumentationRendererSectionsTest` with TC-03a/b/c — covers #36, #67.
- [ ] Extend `LuaCatsLazyCommentTest` with TC-05a/b — covers #38.
- [ ] Extend `LuaCatsAnnotatorTest` with TC-06 — covers #72.
- [ ] Run `tooling/gce-builder/gce-builder.sh run "clean build"` — gates `verifyPlugin` + full suite.
- [ ] Run `human-verification-checklists.md`.

- [ ] Out-of-`luacats/**` consumers of the #38 getter swap (reviewer recommendation): assert
  `collectDescriptionText`-fed surfaces stay correct — `LuaDescriptionIndex` content (run
  `LuaDescriptionIndexTest` on the builder where the external fixture tree exists; CI skips it) and
  `isDocCommentEmpty` consumers (`LuaEnterHandlerDelegate`, `LuaGenerateDocIntention`) — the
  de-duplication preserves the indexed-term SET (each tag description still enters via
  `LuaComment.kt:124-129`), so these are regression checks, not new behavior.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Lexer containment + dead-state removal | done | Must |
| Phase 2: Escaped, correct doc HTML | done | Must |
| Phase 3: Inheritance rendering | done | Should |
| Phase 4: Alias values | done | Should |
| Phase 5: Direct-children getters | done | Should |
| Phase 6: Annotator cleanup | todo | Could |
