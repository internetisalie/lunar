---
id: "SYNTAX-18-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "SYNTAX-18"
folders:
  - "[[features/syntax/18-parser-error-recovery/requirements|requirements]]"
---

# SYNTAX-18: Risks & Gaps

## Critical Risks

### Risk 1.1: PSI-shape change breaks existing tests / consumers
- **Impact**: partial typed nodes now appear where an anonymous error subtree used to be; any test
  or feature that assumed "malformed block ⇒ no `LuaIfStatement`" flips. Concretely,
  `TestLuaParsingExhaustive.testInvalidSyntax` (`TestLuaParsingExhaustive.kt:137-149`) and
  `testOfficialLuaErrorTests` (`:230-235`) assert only on `PsiErrorElement` presence, which still
  holds (design §6), but any structural assertion elsewhere could break.
- **Likelihood**: medium
- **Mitigation**: DR-02 greps the whole `src/test` tree for structural assertions on malformed
  blocks before regenerating; Phase 4 runs the full suite. The `expectErrors` assertions are
  preserved because a pinned node still carries a `PsiErrorElement`.

### Risk 1.2: `recoverWhile` over- or under-recovers
- **Impact**: too-narrow boundary set ⇒ recovery swallows a following valid statement (TC 11 fails);
  too-wide ⇒ recovery stops immediately and consumes nothing, leaving stray tokens as siblings.
- **Likelihood**: medium
- **Mitigation**: the boundary set (design §2.2, §3.3) is derived directly from `statement ::=`
  (`lua.bnf:100-117`) plus the four block terminators (`END`/`ELSE`/`ELSEIF`/`UNTIL`) and `RETURN`;
  TC 11 explicitly asserts a following `return 1` parses as a sibling. DR-03 validates nesting.

### Risk 1.3: Non-deterministic / drifting generated output
- **Impact**: a wrong grammar-kit version produces a spurious `src/main/gen` diff (e.g. reordered
  methods), polluting the commit and defeating TC 15.
- **Likelihood**: low
- **Mitigation**: `generate.sh` pins grammar-kit **2023.3.2** (`tooling/parser-gen/README.md:37`);
  a regen over unchanged sources is a documented no-op. Phase 2 runs `generate.sh` twice and asserts
  an empty second diff.

### Risk 1.4: NPE on partial nodes beyond `getName()`
- **Impact**: `docs/review.md:154` lists ~40 `!!` sites; partial nodes may reach some (e.g.
  `LuaElementFactory.kt:24`, `structure/LuaStructureViewTreeElement.kt:10`) that previously only saw
  well-formed trees, surfacing new NPEs in structure view / resolve on malformed input.
- **Likelihood**: medium
- **Mitigation**: this feature hardens the one accessor known to be hit by recovery nodes
  (`LuaBaseElements.kt:72`, SYNTAX-18-05). The remaining `!!` sites are logged as Technical Debt
  below (not in scope); DR-03's structure-view probe flags any that trip on the new partial nodes so
  they can be scheduled.

## Design Gaps

### Gap 2.1: Half-typed `for` before the disambiguating token stays unrecovered
- **Question**: `for i` (no `=` or `in` yet) is below both FOR-rule pins (offset 3), so it still
  rolls back to `block` recovery instead of building a `LuaNumericForStatement`/`LuaGenericForStatement`.
- **Options / leaning**: (a) accept — committing at `pin=1` mis-routes `for k,v in …` into the numeric
  rule (design §3.1 note, §6); (b) split the shared `FOR` prefix into a common sub-rule. **Leaning
  (a)** — the mis-routing cost outweighs recovering the rare bare-`for` case.
- **Resolved by**: DR-02 (confirm both valid `for` forms parse under `pin=3`); decision recorded in
  design §3.1 / §6. Answer folded into design: `pin=3` on both FOR rules; bare `for i` accepted as
  unrecovered.

## Execution Blockers (all RESOLVED — empirical design ledger, 2026-07-16)

Implementation went through four empirically-gated design iterations; each blocker below was
reproduced on a clean, uncached full-suite run (`gce-builder run "clean test --no-build-cache"`).

### Blocker 3.1 (RESOLVED): pinned rules flip generated getters to `@Nullable`
Pinning makes post-pin children optional, so grammar-kit regenerates `getBlock()`/`getExpr()`/
`getNameRef()` on the pinned rules as `@Nullable`, breaking compile at four pre-existing
dereference sites. **Resolution**: null-safe guards in `LuaScopeProcessor` (×3),
`LuaDebugValueParser`, `LuaRemoteStack`, plus the planned `getName()` harden (commit `446c5c05`).
Note: the three hand-stubbed decl types (`LuaFuncDecl`/`LuaLocalFuncDecl`/`LuaLocalVarDecl`) are
restored by `generate.sh`, so their getters stay `@NotNull` and THROW on partial nodes — callers
on the partial-node path use node-based access instead (stub creation, types visitor).

### Blocker 3.2 (RESOLVED): `recoverWhile` + multi-token pin destroys sibling backtracking
`pin=2/3` + `recoverWhile` on the prefix-sharing rules (`localFuncDecl`, `numericForStatement`…)
regressed VALID parsing (`local a, b = 1, 2`, `for k,v in pairs(t) do end` → `Empty element
parsed in 'root'`): a recover-exit does not roll back a mid-prefix failure, so the sibling
alternative starts mid-statement. 155→342-failure class regressions across two variants.

### Blocker 3.3 (RESOLVED): wrapper factorings are dead ends
Public wrappers (`forStatement ::= FOR forTail`) parse correctly but insert a PSI node between
block and decl → 155 failures across the type engine, stub indexing, inlay hints, docs and
inspections (pervasive tree-shape dependency). Private wrappers do not compile: a
`statement`-typed tail inlined into `statement` makes grammar-kit emit an abstract
`getStatement()` on `LuaStatement` (renaming the wrapper out of `.*Statement` fixes the
element-type collision but not this).

### Blocker 3.4 (RESOLVED): `recoverWhile` poisons the choice even behind a zero-width guard
`&(FOR IDENTIFIER '=')`-guarded pins still regressed valid parsing (342 failures) — a failed
lookahead inside a recovered rule leaves the choice frame corrupted. Additionally, recovery junk
consumption lands inside the `statement ::=` `_COLLAPSE_` frame; when the collapse fails a real
`STATEMENT` node materializes and the generated factory throws `AssertionError("Unknown element
type: STATEMENT")` (`LuaStatementImpl` is abstract) during indexing/annotation.

### Resolution — pins only (shipped)
`pin` on all nine rules, NO `recoverWhile` anywhere. Plain pins backtrack cleanly below the pin,
build the typed partial node with the error at the missing token, and leave trailing-junk
handling byte-identical to the unpinned parser. Downstream features keyed to the old
error-tree shape were adapted (commit `fc88905f`): Smart Enter opener-leaf feeding (composite
`getChildren()` skips leaves), stolen-terminator balance check (a pinned inner block greedily
consumes the enclosing block's `end`), REPL funcBody-rollback secondary check, stub/type-visitor
node-based access. Full clean suite + ktlint green (1998 tests, 0 failures).

## Technical Debt & Future Work
- **`@NotNull` getters on partial nodes** — the hand-stubbed decl getters (`funcName`/`nameRef`)
  and the non-flipped generated getters (`genericForStatement.getNameList()`/`getExprList()`)
  throw if called on a partial node; only the paths exercised by the suite are guarded. New
  partial-node consumers must use node-based access or guard explicitly.
- **End-stealing** — a pinned inner block's greedy continuation consumes the enclosing block's
  terminator (`do while x do<caret> end` → the `while` owns the `end`). The balance checks
  compensate; other tree consumers observing malformed nests should be aware.
- **`recoverWhile` is banned in `lua.bnf`** — see Blockers 3.2/3.4; revisit only with a
  grammar-kit version that fixes the recover-exit rollback, and only via an empirical spike.
- **TBD: Remaining `!!` hardening** — the other ~39 `!!` sites in `docs/review.md:154` (e.g.
  `LuaElementFactory.kt:24`, `LuaComplexTypes.kt:14`, `structure/LuaStructureViewTreeElement.kt:10`)
  are out of scope; only the recovery-hit accessor is fixed here.
- **TBD: EDITOR-08 Smart-Enter simplification** — with typed partial nodes now available, the
  `net.internetisalie.lunar.lang.smartenter` fixers (currently keying off child tokens via
  `LuaBlockPairs`, per EDITOR-08 R-01) could resolve the construct from the typed node. Downstream
  effect, not this feature's scope.
- **TBD: pin/recover on non-block rules** — expressions, `tableConstructor`, `funcBody` arg lists,
  and assignment/var lists still roll back; deferred.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| SYNTAX-18-00-DR-01 | Add pin+recover to `ifStatement` only, run `generate.sh`, confirm the generated `ifStatement` uses the pinned `exit_section_` overload with a `lua_statement_recover` recover ref. | Risk 1.1, Risk 1.3 | todo |
| SYNTAX-18-00-DR-02 | Apply `pin=3` to both FOR rules; `configureByText` the two valid `for` forms (`for i=1,10 do end`, `for k,v in pairs(t) do end`) and confirm zero `PsiErrorElement`s; grep `src/test` for structural assertions on malformed blocks. | Gap 2.1, Risk 1.1 | todo |
| SYNTAX-18-00-DR-03 | `configureByText("while c do if x end")` and dump the tree: confirm the inner `if` recovers and the outer `while` still closes on `end`; probe structure-view/resolve on the partial nodes for new NPEs. | Risk 1.2, Risk 1.4 | todo |

## Test Case Gaps
- Nested malformed block (`while c do if x end`) — covered by DR-03 probe; add a `testNestedRecovery`
  case to `TestLuaParsingExhaustive` in Phase 4 if DR-03 surfaces an issue.
- Structure-view / find-usages behavior on partial function-decl nodes — not asserted here; tracked
  under Technical Debt (remaining `!!` hardening).

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
