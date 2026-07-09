---
id: "EDITOR-06-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "EDITOR-06"
folders:
  - "[[features/editor/06-unwrap-remove/requirements|requirements]]"
---

# EDITOR-06: Risks & Gaps

## Critical Risks

### Risk 1.1: Body hoist changes local scoping
- **Impact**: Unwrapping a block hoists its statements into the parent scope. Locals declared inside the body (`local x = ...`) become visible to following parent-scope statements, and a hoisted local can shadow an outer name — changing program behaviour, not just layout.
- **Likelihood**: medium
- **Mitigation**: This is inherent and expected (it also happens in Java/Groovy unwrap; the user opts in and can undo). We do **not** rename or guard. We preserve statement text verbatim and rely on the platform's single write-command undo. Scoping change is documented in `design.md` §6 and surfaced in the human-verification checklist so a reviewer confirms the resulting text is what the user intended. No inference/rename work is attempted.

### Risk 1.2: `UnwrapHandler` interactive popup makes unit tests flaky
- **Impact**: When several unwrappers apply at the caret, `UnwrapHandler.invoke` shows a `JBPopup`; a headless test that just calls `invoke` will not deterministically select an option.
- **Likelihood**: high
- **Mitigation**: Follow the platform test idiom (`UnwrapTestCase`, `intellij-community` `platform/testFramework/.../UnwrapTestCase.java`): either (a) position the caret so exactly one unwrapper applies, or (b) drive `LuaUnwrapDescriptor().collectUnwrappers(...)` + call the selected `Unwrapper.unwrap(editor, element)` under a `WriteCommandAction` directly. Both are captured in implementation-plan §Verification "Test note".

## Design Gaps

### Gap 2.1: Convergence with EDITOR-05 shared block-structure helper
- **Question**: EDITOR-06's soft dependency is the "block-structure PSI helpers introduced by EDITOR-05". EDITOR-05 is unimplemented (only `requirements.md`), so EDITOR-06 ships its own `LuaBlockStructure` (design §2.4). When EDITOR-05 lands, do the two duplicate?
- **Options / leaning**: EDITOR-06 defines the contract EDITOR-05 must satisfy: `primaryBody(construct): LuaBlock?`, `blockParent(e): LuaBlockParent?`, `ifBranches(ifStmt): List<LuaIfBranch>`, `hasElseOrElseIf`. Leaning: keep `LuaBlockStructure` owned by EDITOR-06; EDITOR-05 (Surround) consumes `primaryBody`/`blockParent` from it rather than re-introducing helpers. Whichever ships second must extend, not duplicate (per the grounding bar).
- **Resolved by**: DR-01. Ordering recommendation: implement EDITOR-06 first (or, if EDITOR-05 lands first, EDITOR-06 imports its helper and deletes the overlapping methods).

### Gap 2.2: Anonymous `funcDef` (expression-position function) hoist can produce invalid Lua
- **Question**: `LuaConstruct.FUNCTION` matches `LuaFuncDef` (`local f = function() body end`). Hoisting `body` out of an expression-position function yields statements where an expression was expected — syntactically invalid.
- **Options / leaning**: (a) restrict `FUNCTION` applicability to statement-context decls only (parent is a `LuaBlock`): `LuaFuncDecl`/`LuaLocalFuncDecl`/`LuaGlobalFuncDecl`, excluding `LuaFuncDef`; (b) allow it and rely on user undo (Java's stance). Leaning: **(a)** — exclude `LuaFuncDef` from unwrap applicability so the option is not offered where it would corrupt the file. Fold the chosen rule into `LuaConstruct.matches(FUNCTION)` / `LuaBlockUnwrapper.isApplicableTo`.
- **Resolved by**: DR-02 (decide before Phase 1 codes `LuaConstruct.matches`).

## Technical Debt & Future Work
- **TBD: `repeat…until` unwrap** — out of the requirements' construct list (`if/while/for/do/function`); `LuaRepeatStatement` is deliberately excluded from `LuaConstruct`. Add later if requested.
- **TBD: "Unwrap to `pcall`-free body"** — the inverse of EDITOR-05-05 `pcall` surround is not required by EDITOR-06 requirements; defer.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| EDITOR-00-DR-01 | Confirm EDITOR-05 implementation order; if EDITOR-05 lands first, adopt its helper and drop `LuaBlockStructure` overlap — else EDITOR-05 consumes `LuaBlockStructure`. Record the ownership decision. | Gap 2.1 | todo |
| EDITOR-00-DR-02 | Decide `LuaFuncDef` exclusion (leaning: exclude from unwrap applicability). Encode in `LuaConstruct.matches(FUNCTION)`. | Gap 2.2 | todo |
| EDITOR-00-DR-03 | Spike: in a `BasePlatformTestCase`, invoke `UnwrapHandler().invoke` on a single-option `if` fixture and confirm `myFixture.checkResult` sees the hoisted text (validates the platform `extract`/`addRangeBefore` path works over Lua PSI before building all five unwrappers). | Risk 1.2 | todo |

## Test Case Gaps
- Nested-construct option ordering (caret inside `if` inside `function`) — covered by TC-04; ensure the option list order matches `collectUnwrappers` parent-walk order.
- Empty-body construct (`do end`) unwrap/remove — add a test that removing/unwrapping an empty block leaves valid, reformatted text (design §3.1 edge path).

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
