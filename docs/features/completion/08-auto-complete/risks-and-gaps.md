---
id: COMP-08-RISKS
title: Auto Complete Risks & Gaps
type: risk
parent_id: COMP-08
folders:
  - "[[features/completion/08-auto-complete/requirements|requirements]]"
---

# COMP-08: Risks & Gaps

Surfaces what could go wrong in the Enter-handler expansion (balance check, opener coverage,
between-pair indent, reformat) before building. Each risk has a mitigation; each unknown a de-risking
task to run early.

## Critical Risks

### Risk 1.1: Balance check is wrong on nested / unbalanced blocks
- **Impact**: the COMP-08-02 fix could over-suppress (skip a needed `end`/`until`/`}` and leave the
  block open) or under-suppress (still insert a redundant terminator) when blocks are nested or the
  surrounding code is mid-edit (error PSI). This is the crux of the feature and the hardest part.
- **Likelihood**: medium.
- **Mitigation**: the design (§3.2) scopes the balance test to the single statement node that owns the
  caret's opener leaf and relies on the grounded fact that the terminator is a **direct child** of that
  node (a sibling of `LuaBlock`, per `lua.bnf:121–136,261`), so `node.findChildByType(terminatorType)`
  cannot see a nested block's terminator. Prototype against the live PSI to confirm the unbalanced/EOF
  parse still yields the `LuaBlockParent` node with a null terminator child (DR-01) before coding.

### Risk 1.2: Reformat needs a committed document / current PSI
- **Impact**: calling `CodeStyleManager.adjustLineIndent` in the wrong phase (preprocess, before the
  Enter newline exists) or against an uncommitted document yields wrong indentation or a stale caret.
- **Likelihood**: medium.
- **Mitigation**: do reformat in `postProcessEnter` only, after
  `PsiDocumentManager.commitDocument` (§3.5), and **stateless** — re-derive the just-inserted
  terminator from the committed document rather than carrying a `pendingReformatRange` instance field
  (see Risk 1.3). DR-04 validates the stateless re-derivation.

### Risk 1.3: A shared delegate instance makes any instance field unsafe
- **Impact**: the platform registers ONE `EnterHandlerDelegate` instance and reuses it across every
  editor/caret. A mutable `private var pendingReformatRange` handoff between `preprocessEnter` and
  `postProcessEnter` is shared mutable state — under multi-caret or interleaved Enter actions it can
  reformat the wrong range or fire spuriously.
- **Likelihood**: medium (multi-caret is common).
- **Mitigation**: §3.5 carries **no** state between phases; `postProcessEnter` re-derives the target
  from the committed document, exactly as the existing stateless `lang.format.LuaEnterHandlerDelegate`
  does. Validated by DR-04.

## Design Gaps

### Gap 2.1: Table `{` is reachable but is not the only `{` context
- **Question**: `LCURLY` also opens non-`LuaTableConstructor` contexts? In Lua grammar `{` only starts a
  `tableConstructor` (`lua.bnf:265`), so the parent lookup is unambiguous — but confirm no other PSI node
  (e.g. an error node during typing) shadows it.
- **Options / leaning**: lean on `PsiTreeUtil.getParentOfType(leaf, LuaTableConstructor::class.java)`
  returning null → `Continue` for any non-table `{`, which is safe.
- **Resolved by**: DR-02; fold the confirmation back into design §3.3 when done.

## Technical Debt & Future Work
- **TBD: Smart-Enter / Complete-Statement (Ctrl+Shift+Enter)** — parked in `requirements.md` backlog;
  separate `SmartEnterProcessorWithFixers` wiring, out of COMP-08 scope.
- **TBD: Typed-`end` reindent** and **settings toggle** — parked backlog (`requirements.md`).
- **TBD: clarity rename** of `lang.format.LuaEnterHandlerDelegate` → `LuaDocCommentEnterHandler`
  (design §9) — cosmetic `MAINT` nicety, not required here.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| COMP-08-00-DR-01 | Prototype the §3.2 balance check against live PSI: dump `statement.node.children` for `if a then⏎ if b then⏎` (nested unbalanced), `if true then⏎end` (balanced), an EOF `function f()`, AND a half-typed `function f()` / `if x then` **nested inside an already-balanced `do … end`** (the case that could mis-suppress); confirm `findChildByType(END/UNTIL/RCURLY)` is null exactly when *this* construct's terminator is missing, and that the parent lookup resolves to the inner half-typed `LuaFuncDecl`/`LuaLocalFuncDecl`/`LuaIfStatement`, not the balanced outer block | Risk 1.1 | done |
| COMP-08-00-DR-02 | Confirm `{` always parents to `LuaTableConstructor` (no error-node shadowing while typing) | Gap 2.1 | done |
| COMP-08-00-DR-03 | Verify the two `enterHandlerDelegate`s (`LuaEnterHandler`, `LuaEnterBetweenBlockHandler`) plus the DOC `lang.format.LuaEnterHandlerDelegate` do not fight: each returns `Result.Continue` when not applicable, and for a matched pair neither inserts (§3.4 ordering note). Test all three registered together | Risk 1.1 / §3.4 | done |
| COMP-08-00-DR-04 | Validate the **stateless** §3.5 `postProcessEnter`: re-derive the just-inserted terminator from the committed document (no instance field). Confirm it reindents correctly after a §3.2/§3.3 insert and does NOT fire on an ordinary Enter that inserted no terminator, including under **multi-caret** (where a shared delegate instance would corrupt any instance-field handoff — Risk 1.3) | Risk 1.2 / Risk 1.3 | done |

## Test Case Gaps
- `requirements.md` TC 1–6 now cover: `then`, no-redundant-`end`, table `{`, between-pair,
  reformat+caret (COMP-08-05), and the `while`/`for`/`do`/`repeat` openers (COMP-08-03). Remaining
  to add when implementing: generic-`for` and a long-string-vs-block edge, tracked as
  verification tasks in `implementation-plan.md`.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
