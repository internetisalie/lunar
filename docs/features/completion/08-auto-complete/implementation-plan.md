---
id: COMP-08-PLAN
title: Auto Complete Plan
type: plan
status: planned
parent_id: COMP-08
folders:
  - "[[features/completion/08-auto-complete/requirements|requirements]]"
---

# COMP-08: Implementation Plan

Sequences the COMP-08 design (`design.md`) into shippable phases. COMP-08-01 is already built
(`LuaEnterHandler`); this plan delivers the balance-check bug fix (02) first, then opener coverage (03),
then between-pair indent (04), with reformat (05) folded into the highest-priority phase since the
balance check and reformat share the same handler and `postProcessEnter` wiring.

## Phases

### Phase 1: Balance check + shared pair map + reformat [Must]
- **Goal**: COMP-08-02 (no redundant `end`/`until`/`}`) and COMP-08-05 (reformat + caret) shipped on the
  existing `LuaEnterHandler`. This is the correctness bug fix and the highest priority.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.lang.syntax.LuaBlockPairs` object with `terminatorByOpener`
        and `insertTextFor` maps — realizes design §2.3.
  - [ ] Refactor `LuaPairedBraceMatcher.getPairs()` to derive its keyword `BracePair`s from
        `LuaBlockPairs.terminatorByOpener` (keep `LPAREN`/`RPAREN`, `LBRACK`/`RBRACK` local) — realizes
        design §2.3 (single source of truth; no behavior change).
  - [ ] Edit `LuaEnterHandler.preprocessEnter` to add the balance check: resolve the owning
        `LuaBlockParent`, `statement.node.findChildByType(terminatorType)`, skip insert when balanced and
        return `Result.DefaultForceIndent` — realizes design §3.2.
  - [ ] Add the `pendingReformatRange` field + `postProcessEnter` reformat/caret step on
        `LuaEnterHandler` using `CodeStyleManager.adjustLineIndent` — realizes design §3.5.
- **Exit criteria**: TC 1 still passes; **TC 2 passes** (`if true then⏎end` opens an indented body line
  with NO second `end`); `LuaPairedBraceMatcher` behavior unchanged (brace-matching tests green).

### Phase 2: Full opener coverage incl. table `{`→`}` [Should]
- **Goal**: COMP-08-03 — every Lua block opener plus the table literal completes on Enter.
- **Tasks**:
  - [ ] Extend `LuaEnterHandler` to look up ANY opener in `LuaBlockPairs.terminatorByOpener` (drop the
        hard-coded `THEN`/`DO`/`FUNCTION`/`REPEAT` `if`), and select the parent class
        (`LuaTableConstructor` for `LCURLY`, else `LuaBlockParent`) — realizes design §3.3.
  - [ ] Add the `LCURLY`→`RCURLY` insert path (`"\n}"`) with the table-scoped balance check — realizes
        design §3.3.
- **Exit criteria**: **TC 3 passes** (`local t = {⏎` ⇒ `{`…`}` with indented body line); `while…do`,
  numeric/generic `for…do`, bare `do`, balanced cases all behave per the §3.3 table.

### Phase 3: Between-pair smart indent [Should]
- **Goal**: COMP-08-04 — Enter between a matched opener and terminator indents without inserting.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.lang.completion.LuaEnterBetweenBlockHandler :
        EnterHandlerDelegateAdapter` implementing the §3.4 algorithm — realizes design §3.4.
  - [ ] Register `<enterHandlerDelegate>` for it in `plugin.xml` adjacent to `LuaEnterHandler` —
        realizes design §7.
- **Exit criteria**: **TC 4 passes** (caret between `function f()` and its existing `end` ⇒ indented
  blank body line, no terminator inserted); no double-action with Phase 1's handler.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| COMP-08-01 | M | (built — `LuaEnterHandler`) |
| COMP-08-02 | M | Phase 1 |
| COMP-08-03 | S | Phase 2 |
| COMP-08-04 | S | Phase 3 |
| COMP-08-05 | S | Phase 1 |

## Verification Tasks
- [ ] Extend `src/test/kotlin/.../lang/completion/LuaEnterHandlerTest.kt` with the explicit
      **"no redundant end"** test for `if true then⏎end` — covers TC 2 (COMP-08-02). Assert exactly one
      `end` remains and the caret lands on an indented body line.
- [ ] Add `testEnterAfterTableBrace` (`local t = {<caret>` ⇒ `{`…`}`) — covers TC 3 (COMP-08-03).
- [ ] Add coverage for `while…do`, numeric `for…do`, generic `for…do`, bare `do`, `repeat…until` openers
      — covers COMP-08-03.
- [ ] Add `testEnterBetweenMatchedFunction` (caret between `function f()` and its `end`) — covers TC 4
      (COMP-08-04).
- [ ] Add a reformat/caret assertion (terminator line and body line correctly indented) — covers
      COMP-08-05.
- [ ] Regression: confirm `LuaPairedBraceMatcher` and the DOC `lang.format.LuaEnterHandlerDelegate`
      tests still pass after the `LuaBlockPairs` refactor and the new delegate registration.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Balance check + shared pair map + reformat | todo | Must |
| Phase 2: Full opener coverage incl. table `{`→`}` | todo | Should |
| Phase 3: Between-pair smart indent | todo | Should |
