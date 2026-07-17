---
id: "SYNTAX-18-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "SYNTAX-18"
folders:
  - "[[features/syntax/18-parser-error-recovery/requirements|requirements]]"
---

# SYNTAX-18: Implementation Plan

## Phases

### Phase 0: De-risk pin/recover shape [Must]
- **Goal**: confirm grammar-kit 2023.3.2 emits pinned `exit_section_` + recover on Lunar's rules
  before committing the full grammar edit, and pin the FOR-disambiguation decision.
- **Tasks**:
  - [ ] Run `risks-and-gaps.md` DR-01: add `pin=1` + `recoverWhile=lua_statement_recover` to
    `ifStatement` only (design §2.1, §2.2), run `generate.sh`, inspect the `ifStatement` body in
    `src/main/gen/.../LuaParser.java` for the pinned `exit_section_` overload and a `_recover` call.
  - [ ] Run DR-02: `configureByText("for k,v in pairs(t) do end")` and `("for i=1,10 do end")` after
    applying `pin=3` to both FOR rules; confirm both still parse error-free (design §3.1 note, §6).
- **Exit criteria**: pinned `exit_section_` observed in generated `ifStatement`; both valid `for`
  forms parse clean under `pin=3`. Covers the design §3.1 FOR-ambiguity assumption.

### Phase 1: Grammar edits [Must]
- **Goal**: add pins + shared recover predicate to all nine block rules.
- **Tasks**:
  - [ ] Edit `src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf`: add `pin` +
    `recoverWhile=lua_statement_recover` to `doStatement` (:125, pin=1), `whileStatement`
    (:129, pin=1), `repeatStatement` (:133, pin=1), `ifStatement` (:137, pin=1),
    `numericForStatement` (:140, pin=3), `genericForStatement` (:144, pin=3),
    `localFuncDecl` (:162, pin=2), `funcDecl` (:174, pin=1), `globalFuncDecl` (:208, pin=2) —
    realizes design §2.1, §3.1.
  - [ ] Add `private lua_statement_recover ::= !( … boundary set … )` per design §2.2 — realizes §3.3.
- **Exit criteria**: `grep -c 'recoverWhile=lua_statement_recover' lua.bnf` == 9; the new predicate
  rule present.

### Phase 2: Regenerate parser [Must]
- **Goal**: produce and commit the generated delta.
- **Tasks**:
  - [ ] Run `.claude/skills/generate-parser/scripts/generate.sh` (design §7); review the
    `src/main/gen/net/internetisalie/lunar/lang/parser/LuaParser.java` diff — expect each of the nine
    rule bodies to switch to the pinned `exit_section_` overload with the `lua_statement_recover` ref.
  - [ ] Run `generate.sh` a second time; confirm `git diff src/main/gen` is empty (SYNTAX-18-06).
- **Exit criteria**: TC 15 holds (idempotent regen); generated delta staged.

### Phase 3: Harden name accessor [Must]
- **Goal**: remove the `!!` NPE risk on partial nodes.
- **Tasks**:
  - [ ] Edit `LuaNameRefElementImpl.getName()` at
    `src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaBaseElements.kt:71-73` to
    `findChildByType<PsiElement?>(LuaElementTypes.IDENTIFIER)?.text` — realizes design §2.3.
- **Exit criteria**: TC 13 passes; no other `getName()` caller regresses (full suite green).

### Phase 4: Tests [Must]
- **Goal**: lock in partial-node shape, error localization, boundary recovery, and no regression.
- **Tasks**:
  - [ ] Extend `TestLuaParsingExhaustive`
    (`src/test/kotlin/net/internetisalie/lunar/lang/parser/TestLuaParsingExhaustive.kt`) with a new
    `testPartialBlockNodes()` asserting TC 1–9 via
    `PsiTreeUtil.findChildOfType(myFixture.file, LuaIfStatement::class.java)` (and the eight siblings).
  - [ ] Add `testErrorLocalization()` for TC 10, 14 (assert `PsiErrorElement.textOffset` > opener end).
  - [ ] Add `testRecoveryBoundary()` for TC 11 (`if x\nreturn 1` → `LuaIfStatement` + sibling
    `LuaFinalStatement`).
  - [ ] Add `testGetNameOnPartialNode()` for TC 13 (`getName()` returns `null`, no throw).
  - [ ] Confirm existing `testValidControlFlow`/`testValidAssignments`/`testValidExpressions`/
    `testVarargsCoverage`/`testInvalidSyntax` remain green (TC 12, and design §6 note).
- **Exit criteria**: new tests pass; existing suite unchanged and green.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| SYNTAX-18-01 | M | Phase 1, Phase 2 |
| SYNTAX-18-02 | M | Phase 1, Phase 2 |
| SYNTAX-18-03 | M | Phase 0 (FOR de-risk), Phase 1, Phase 4 (regression) |
| SYNTAX-18-04 | M | Phase 1 (recover predicate), Phase 4 |
| SYNTAX-18-05 | M | Phase 3 |
| SYNTAX-18-06 | S | Phase 2 |

## Verification Tasks
- [ ] Add `testPartialBlockNodes` — covers TC 1–9.
- [ ] Add `testErrorLocalization` — covers TC 10, 14.
- [ ] Add `testRecoveryBoundary` — covers TC 11.
- [ ] Add `testGetNameOnPartialNode` — covers TC 13.
- [ ] Confirm valid-case suites unchanged — covers TC 12.
- [ ] Confirm double `generate.sh` is a no-op — covers TC 15.
- [ ] Run `tooling/gce-builder/gce-builder.sh run test` (full suite) after Phase 4.
- [ ] Run `tooling/gce-builder/gce-builder.sh run "ktlintFormat ktlintCheck"` on the edited Kotlin.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 0: De-risk pin/recover shape | done | Must |
| Phase 1: Grammar edits | done | Must |
| Phase 2: Regenerate parser | done | Must |
| Phase 3: Harden name accessor | done | Must |
| Phase 4: Tests | done | Must |

## As-Built Deviations (2026-07-16)

The plan's `recoverWhile` half was **dropped during implementation** — four empirically-gated
design iterations (direct pin+recover → public wrappers → private wrappers → predicate-guarded
recover → **pins only**) proved `recoverWhile` unusable in this grammar and both wrapper
factorings dead ends; see risks-and-gaps.md Blockers 3.2–3.5 for the full ledger. What shipped:

- **Grammar (commit `3be7407f`)**: `pin` only, on all nine block rules (do/while/repeat/if/
  funcDecl=1, localFuncDecl/globalFuncDecl=2, numericFor/genericFor=3); no predicate rule, no
  `recoverWhile`; regen idempotent (tree-hash-verified).
- **Null-hardening (commit `446c5c05`)**: pinned rules' generated getters flip `@Nullable` —
  guarded LuaScopeProcessor (×3), LuaDebugValueParser, LuaRemoteStack, and getName() (Phase 3).
- **Downstream adaptations (commit `fc88905f`, unplanned but forced)**: Smart Enter opener-leaf
  feeding, LuaKeywordBlockCloser/LuaEnterHandler stolen-terminator balance check, REPL
  LuaChunkCompletion funcBody-rollback secondary check, stub createStub node-based access,
  LuaTypesVisitor partial-decl guards.
- **Tests (commit `4568dc5e`)**: as planned, except TC 11 asserts the typed `LuaFinalStatement`
  **nested** inside the recovered if (grammar-kit's greedy pinned continuation), not an outer
  sibling; all PSI reads under `runReadAction`.
- **Gate**: full clean uncached suite + ktlint green (1998 tests, 0 failures).
