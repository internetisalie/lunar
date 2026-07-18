---
id: "MAINT-27-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-27"
folders:
  - "[[features/maint/27-luacats-doc-correctness/requirements|requirements]]"
---

# MAINT-27: Risks & Gaps

## Critical Risks

### Risk 1.1: `.flex` regeneration drifts the generated PSI
- **Impact**: `generate.sh` regenerates `luacats.bnf` (step 4b) as well as `luacats.flex`
  (step 5b). If the lexer edit inadvertently changed the token vocabulary consumed by the bnf, the
  regen would rewrite `src/main/gen/.../luacats/lang/psi`, silently altering PSI accessors the
  renderer and annotator depend on.
- **Likelihood**: low — #19/#66/§3 are macro/state edits only; no `%type` token or element type is
  added or removed.
- **Mitigation**: the no-op sanity check (implementation-plan Phase 1) — `git diff
  src/main/gen/.../luacats/lang/psi` must be empty; only the lexer directory may change. Resolved
  empirically by DR-01.

### Risk 1.2: Parser-gen jars absent locally
- **Impact**: Phase 1 cannot run; `generate.sh` exits 1 at the jar precheck (`generate.sh:67-75`).
- **Likelihood**: medium — the jars are git-ignored and machine-local (`tooling/parser-gen/`).
- **Mitigation**: the Preconditions gate verifies both jars before starting (implementation-plan).
  BUG-361 (`0566cfbc`) proves the pipeline works when the jars are present; obtain per
  `tooling/parser-gen/README.md` if missing.

### Risk 1.3: `[:letter:]` broadens `NAME` beyond intent
- **Impact**: `[:letter:]` (Unicode letters) is broader than `[\x80-\xff]`. If a downstream rule
  relied on the byte-range narrowness, a previously-`BAD_CHARACTER` sequence could now lex as a
  `NAME`.
- **Likelihood**: low — LuaCATS names are intended to accept identifier characters; broadening to
  the full Unicode letter set is the correct behavior and matches `intellij-community` lexers.
- **Mitigation**: TC-01b asserts a CJK class name lexes as one `NAME`; the existing lexer/parser
  tests (`TestLuaCatsLexer`, `LuaCatsParserTest`) guard ASCII regressions. Resolved by DR-02.

## Design Gaps

_None open._ Every design decision the implementer would otherwise have to make is pinned in
`design.md`:

- #35 escaping predicate → §3.3 (`^[\p{L}_][\p{L}\p{N}_.]*$`).
- #38 caching choice → §3.6 (no cache; rationale in §9).
- #36 parent-lookup fallback API → §3.4 (`LuaCatsTypeNameIndex.KEY` +
  `FileBasedIndex.getContainingFiles`, mirroring `LuaCatsTypeNavigation`).
- #67 cycle-guard location → §3.5 (local `visited` set + `<= 64` depth cap).
- #72 verify-then-drop protocol → §3.7 (fixture first, then remove).
- #66 Unicode class → §3.1 (`[:letter:]`).
- luacats.bnf regen scope → §9 (flex-only ⇒ bnf regen is a no-op).

## Implementation Deviation (Phase 5, #38)

Design §1/§3.6 assumed `LuaCatsLazyCommentImpl` and the generated `LuaCatsCommentImpl` are the
**same** node, so a blanket getter swap to `getChildrenOfTypeAsList(this, …)` would return the
tags as direct children. A PSI dump (`DebugUtil.psiToString`) disproved this: `parseContents`
returns `root.firstChildNode`, so the lazy chameleon's *single* child is an inner `COMMENT` node
(the generated `LuaCatsCommentImpl`), and **the tags are direct children of that inner comment**,
one level below the lazy node. A direct `getChildrenOfTypeAsList(this, …)` therefore returned empty
lists and broke `getSeeTagList`/`getParamTagList`/`getVersionTagList`/`LuaDescriptionIndex` (12 test
regressions).

Resolution (preserves the #38 intent): every lazy getter now **delegates to the inner
`LuaCatsComment` child** (`PsiTreeUtil.getChildOfType(this, LuaCatsComment::class.java)`), whose own
generated getters already use `getChildrenOfTypeAsList` on *their* direct children. Result is the
same as the design's goal — `getDescriptionList()` returns only the inner comment's direct
(top-level) descriptions, excluding tag-nested ones — but reached via delegation, not a `this`-level
direct-children read. No cache, matching design §3.6/§9.

## Implementation Deviation (Phase 6, #72)

Design §3.7 step 3 directed removing **both** the `LuaCatsNamedType` classTag/aliasTag special case
**and** the `LuaCatsElementTypes.NAME` else-branch as "unreachable". The verify-first fixtures
(added before any edit, per the protocol) disproved the second half: the class name `Foo` in
`---@class Foo : Bar` is a **raw `NAME` token** (`typeName ::= parameterizedName | NAME`, bnf line
93), and the `LuaCatsElementTypes.NAME` else-branch is the **only** producer of its `NAME` highlight
— the baseline fixture `testClassNameHighlightUnchanged` passes *because of* that branch. Removing it
would strip the class-name highlight, violating requirements TC-06 ("class name Foo highlighting
unchanged from the captured baseline").

Resolution: removed **only** the genuinely-dead `LuaCatsNamedType` special case (empirically dead —
the alias target `Player` and parent `Bar` already highlight `TYPE` before and after removal, so
`testAliasTargetHighlightsAsType` / `testClassParentTypeHighlightsAsType` are unchanged). Kept the
load-bearing `LuaCatsElementTypes.NAME` else-branch. This is exactly the outcome the design's
"verify FIRST, then drop only what the fixture proves dead" protocol is meant to produce.

## Technical Debt & Future Work

- **TBD: full LuaCATS type-string parsing for hyperlinking substructure** — #35 links only whole
  simple identifiers; a structured type like `table<string, K>` renders as escaped code, not with
  its `string`/`K` members individually linked. Deferred; out of scope for a correctness fix.
- **TBD: stubbing the LuaCATS comment PSI** — would unlock Find Usages/Rename on types and remove
  the `LuaCatsTypeNameIndex` file-based fallback, but is heavy (lazy-parseable element + the
  `IElementType` registry-size limit; see `.agents/AGENTS.md`). Out of scope.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| MAINT-27-00-DR-01 | Run `generate.sh` on the edited `luacats.flex`; assert `git diff src/main/gen/.../luacats/lang/psi` is empty and only the lexer dir changed | Risk 1.1 | done (psi diff EMPTY; only `_LuaCatsLexer.java` changed) |
| MAINT-27-00-DR-02 | Add TC-01b (CJK `@class` name) and TC-01a (unclosed backtick) lexer fixtures; confirm containment + Unicode NAME before wider renderer work | Risk 1.3 | done (lexer + parser-level fixtures green) |
| MAINT-27-00-DR-03 | Verify both `tooling/parser-gen/grammar-kit-*.jar` and `jflex-*.jar` resolve before Phase 1 | Risk 1.2 | done (both present) |

## Test Case Gaps

- Real-flow (VNC) verification of the doc popup rendering escaped structured types is covered by
  `human-verification-checklists.md`; the unit `renderDoc` assertions (TC-02*) confirm the HTML
  string but not the rendered pixels.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
