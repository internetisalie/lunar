---
id: "REFACT-09-PLAN"
title: "09: Implementation Plan"
type: "plan"
parent_id: "REFACT-09"
folders:
  - "[[features/refactoring/09-colon-method-rename/requirements|requirements]]"
---

# REFACT-09: Implementation Plan

Phases 1-2 build the predicate and pin it with unit tests before anything is wired into the rename; Phase 3 wires it; Phase 4 repairs the two existing tests the replaced message breaks;
Phase 5 adds the conflict arm. Every phase leaves the build green **except Phase 3**, which is where
the two known `LuaRenameTest` failures appear and Phase 4 closes them — so Phases 3 and 4 land in one
commit if the branch must stay green commit-by-commit.

**Standing constraints from `docs/engineering-contract.md`, all of which this feature has tripped
over in planning:**
- **≤3 arguments per function, private helpers included.** `design.md` §2.1 bundles the four-value
  context into `Target` for exactly this reason. Self-audit every helper before opening a PR.
- **≤30 logic lines per function.** `design.md` §3.2 names the split: `planFor` /
  `uniqueFileLocalBinding` / `scanOccurrences` / `decideRemainingSites` — use those names,
  `design.md` §3.2 is the source of truth for them. Do not inline them back.
- **No `!!`, no wildcard imports, `val` over `var`, read-only collection types on public API.**
- `tooling/gce-builder/gce-builder.sh run ktlintFormat` on the VM, then rsync the `.kt` files back,
  then `ktlintCheck` alone. Never `run "ktlintFormat ktlintCheck"` (BUG-445).

## Phases

### Phase 1: `LuaColonMethodRename` — the predicate [Must]

- **Goal**: the decision function, with no rename wired to it.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.refactoring.rename.LuaColonMethodRename` — realizes
        design §2.1, §3.2, §3.3, §3.3.1, §3.4, §3.5, §3.6, §3.8, §3.9. `internal object`, in
        `src/main/kotlin/net/internetisalie/lunar/refactoring/rename/`. All four public functions
        of §2.1 are written in this phase, `callSiteReferences` and `memberDeclarationsNamed`
        included (§3.9), even though their callers arrive in Phases 3 and 5.
  - [ ] **Never call `LuaFuncDecl.getFuncName()`.** It is `findNotNullChildByClass` and raises on a
        declaration whose name slot holds a keyword (design §3.8, executed). Every site that needs a
        declaration's `funcName` goes through the private `funcNameOf` of design §3.8 — `planFor`
        step 2, `selfBindsTo`, `declarationLeafOfCallSite`, `memberDeclarationsNamed` — and every
        traversal over the file enumerates `LuaFuncName`, never `LuaFuncDecl`.
  - [ ] Add **design §7.2's "Phase 1" key group, all of it and none of the other groups**, to
        `src/main/resources/net/internetisalie/lunar/LuaBundle.properties`. §7.2 splits the keys by
        the phase that renders them precisely so this step cannot pick up Phase 5's
        `refactoring.rename.conflict.memberExists` or miss
        `refactoring.rename.colonMethod.notADeclaration`. Neither the bracket step nor the member
        read needs a key of its own — both render through `receiverEscapes` (design §3.3).
        Do **not** remove `refactoring.rename.colonMethod` yet; Phase 3 removes it with its call site.
- **Exit criteria**: compiles; `tooling/gce-builder/gce-builder.sh run "build"` green; no behaviour
  change anywhere, because nothing calls the new object yet.

### Phase 2: unit-test the predicate in isolation [Must]

- **Goal**: `planFor` pinned per clause, before the rename can mask a defect behind a refusal.
- **Tasks**:
  - [ ] Create `src/test/kotlin/net/internetisalie/lunar/refactoring/rename/LuaColonMethodRenamePlanTest.kt`
        (`BasePlatformTestCase`, `@RunWith(JUnit4::class)`), one `configureByText` per test method —
        `LuaTypeManagerImpl` searches `GlobalSearchScope.allScope(project)`, so a sibling fixture
        binds a member to the wrong file.
  - [ ] One method per clause, asserting the **refusal message key's rendered text** and, where the
        plan is accepted, the exact `callSites` offsets: R1 (requirements cases 6, 16), R2 (case 15),
        R3 escape (cases 7, 8, 9, 12, 25, 27, 28, 29), R3 dotted (cases 11, 18), R4 (case 10),
        R5 (cases 13, 14, 24), accept (cases 1, 4, 5, 26, 31), and the malformed-sibling
        traversal (case 30).
  - [ ] Assert the *message*, never a bare "refusal happened": several clauses refuse the same
        fixture family and only the message separates which fired.
- **Exit criteria**: the new class is green; the rest of the suite is unchanged (nothing is wired).

### Phase 3: wire it into `LuaRenameProcessor` [Must]

- **Goal**: the rename performs and refuses as `design.md` specifies.
- **Tasks**:
  - [ ] Edit `LuaRenameProcessor.substituteElementToRename`: add `colonCallSiteDeclarationLeaf` to
        the `leaf` chain and replace the `METHOD_FUNCTION -> refuse(colonMethod)` arm with
        `colonMethodSubstitution` — realizes design §2.2.
  - [ ] Add the private `colonCallSiteDeclarationLeaf`, `colonMethodSubstitution` and `caretRefusal`
        members — realizes design §2.2, §3.7. Import `com.intellij.psi.util.PsiUtilBase`.
  - [ ] Add design §7.2's **Phase 3** key, `refactoring.rename.colonMethod.caretNotOnMethod`.
  - [ ] Edit `LuaRenameProcessor.findReferences`: add the `METHOD_FUNCTION` early return —
        realizes design §2.2.
  - [ ] Delete `refactoring.rename.colonMethod` from `LuaBundle.properties`; it now has no call site.
  - [ ] Update `LuaRenameProcessor`'s class KDoc: it currently says "**three** refusals are decided
        in `substituteElementToRename`" (`:46-51`). State the property — *every refusal that can be
        decided before the refactoring starts is decided there* — rather than a number, so the next
        edit cannot leave a stale count behind.
- **Exit criteria**: `LuaColonMethodRenameTest` (Phase 3's own end-to-end class, below) green; the
  full suite green **apart from** `LuaRenameTest.testColonMethodDeclarationIsRefused` and
  `testSelfInsideAMethodIsRefusedAsTheMethod`, which Phase 4 rewrites. Measured with the prototype
  applied to `128ba091`: 2 failures, 0 errors, and they are exactly those two. A third failure is a
  regression, not a known cost.

### Phase 4: repair the two REFACT-01 tests the replaced message breaks [Must]

- **Goal**: the suite is green and the two tests still assert what they were written to assert.
- **Tasks**:
  - [ ] `LuaRenameTest.testColonMethodDeclarationIsRefused` — the fixture is
        `Obj = {}` / `function Obj:<caret>m()` / `local o = Obj` / `o:m()`, a **global** receiver, so
        the refusal is now `receiverNotLocal`. Keep the fixture and keep every assertion it
        makes — that the refusal names its own reason, and that the file is byte-identical when the
        rename is driven end to end — and change only the expected message fragment, to
        `is not a file-local table`. Executed refusal text, on this exact fixture:
        `Cannot perform refactoring.\nThe receiver 'Obj' is not a file-local table, so call sites in
        other files cannot be found.`
  - [ ] `LuaRenameTest.testSelfInsideAMethodIsRefusedAsTheMethod` — its assertions about what `self` resolves to —
        the method-name leaf, whose `parent.parent` is a `LuaFuncNameMethod` — are unaffected and stay. Its refusal assertion calls `assertRefusedWith(…, null)` —
        a **null editor**, so design §3.7's guard cannot fire and the refusal comes from
        `receiverNotLocal` instead. Split it: keep this method asserting the global-receiver refusal
        on the null-editor path, and put the `self` guard in a new method that drives
        `myFixture.renameElementAtCaret` on a **file-local** receiver, which is requirements case 17.
  - [ ] Update the KDoc of both, and of `REFACT-01` requirements `REFACT-01-08`'s row, to say the
        colon form is renameable under [[REFACT-09]]'s predicate rather than refused outright.
- **Exit criteria**: `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"` reports
  0 failures, 0 errors.

### Phase 5: the member-name conflict [Should]

- **Goal**: `REFACT-09-07`, and removal of every false conflict the inherited global rules produce
  for this kind — design §5 names them by rule, not by count.
- **Tasks**:
  - [ ] Add `LuaColonMethodRename.memberDeclarationsNamed` — realizes design §2.1, §5.
  - [ ] Add the `METHOD_FUNCTION` arm and `memberNameTaken` to `LuaRenameConflictDetector.collisions`
        — realizes design §5. Add design §7.2's **Phase 5** key,
        `refactoring.rename.conflict.memberExists`, to `LuaBundle.properties`.
  - [ ] Re-read `collisions` before writing the arm and confirm design §5 still enumerates every
        rule it holds. §5 covers `shadows` explicitly even though `METHOD_FUNCTION.isFileLocal` is
        `false` and it therefore never ran for this kind — an omitted inert rule and an overlooked
        live one look identical in a list.
  - [ ] Update `LuaRenameConflictDetector`'s class KDoc: it says "Four rules"; state instead that the
        rule set is selected by `LuaDeclarationKind` and list the arms, so the sentence cannot go
        stale against a fifth rule.
  - [ ] Add `LuaColonMethodRenameConflictTest` covering requirements cases 21 and 22.
- **Exit criteria**: cases 21 and 22 green; `LuaRenameConflictTest` unchanged and green (its targets
  are locals, globals and dotted functions, none of which take the new arm).

## Requirement → Phase coverage

| Requirement | Priority | Delivered in |
| :-- | :-- | :-- |
| `REFACT-09-01` | M | Phase 1, Phase 3 |
| `REFACT-09-02` | M | Phase 1 (§3.8), Phase 3 |
| `REFACT-09-03` | M | Phase 1, Phase 2, Phase 3 |
| `REFACT-09-04` | M | Phase 3 (§3.7), Phase 4 (the test split) |
| `REFACT-09-05` | M | Phase 3 — verification only; no production change |
| `REFACT-09-06` | M | Phase 3 — inherited from `LuaRenameProcessor.renameElement`; verification only |
| `REFACT-09-07` | S | Phase 5 |
| `REFACT-09-08` | M | Phase 4, Phase 5 |

## Verification tasks

`LuaColonMethodRenameTest` is the end-to-end class (Phase 3); `LuaColonMethodRenamePlanTest` is the
unit class (Phase 2); `LuaColonMethodRenameConflictTest` is Phase 5's. Every row below names the
requirements test case it covers, and every mutation it names has already been **executed against
the prototype** — see requirements "Test Cases" for the observed output.

- [ ] `LuaColonMethodRenameTest.renamesADeclarationAndEveryCallSite` — case 1. Mutation: delete the
      `findReferences` arm.
- [ ] `…renamesFromACallSiteCaret` — case 2. Mutation: delete `colonCallSiteDeclarationLeaf`.
- [ ] `…renamesFromASelfCallSiteCaret` — case 3. Same mutation.
- [ ] `…renamesASelfCallAlongsideTheDeclaration` — case 4. Mutation: drop the `self` half of
      `receiverOccurrences`.
- [ ] `…leavesAnUnrelatedReceiverAlone` — case 5. Mutation: drop the `decided === declaration` test.
- [ ] `…refusesAGlobalReceiverAndLeavesBothFilesUnchanged` — case 6 (two files). Mutation: drop the
      `isFileLocal` test.
- [ ] `…refusesAnEscapingReceiver` — cases 7, 8, 9, one method each. Mutation: case 12's clause is
      falsified separately; these three are falsified by the bare-occurrence clause.
- [ ] `…refusesADuplicateDeclaration` — case 10. Mutation: drop `memberDeclarations.size != 1`.
- [ ] `…refusesADottedInvocationThatCanRebindSelf` — case 25. Mutation: delete the member-read
      escape; observed to rewrite `self:m()` into a call on a member the actual receiver lacks.
- [ ] `…renamesAlongsideAWriteToAnotherMember` — case 26. Mutation: delete the
      `isSoleAssignmentTarget` exception; observed to refuse ordinary field-writing OO.
- [ ] `…refusesBracketSpelledAccessToTheMember` — case 27, and `…refusesABracketWriteOnTheReceiver`
      — case 29. Mutation: delete the bracket-step escape.
- [ ] `…refusesAStoredMemberValue` — case 28. Same mutation as case 25; this fixture additionally
      falsifies the narrower rule that escapes only on a suffixed call head.
- [ ] `…refusesDottedAccessToTheSameMember` — cases 11 and 18. Mutation: delete the `DottedMember`
      verdict.
- [ ] `…refusesACallStepInTheReceiversSuffix` — case 12. Mutation: delete the
      `nameAndArgsList.isNotEmpty()` clause. The fixture's suffix is `.x`, **not** `.m`, or the
      `DottedMember` verdict would refuse under both the correct code and the mutant.
- [ ] `…refusesAChainsSecondSegment` — case 13. Mutation: delete the first-`nameAndArgs` guard.
- [ ] `…refusesAnUndecidableSameNamedCall` — case 14. Mutation: delete the undecided-site loop.
- [ ] `…refusesAnOccurrenceAboveTheBinding` — case 15. Mutation: delete R2.
- [ ] `…refusesAShadowedReceiverName` — case 16. Mutation: delete `bindings.size != 1`.
- [ ] `…caretOnSelfDoesNotRenameTheMethod` — case 17. **Must drive `myFixture.renameElementAtCaret`**,
      which supplies the fixture editor; `assertRefusedWith` passes `null` and the guard cannot fire.
      Mutation: delete `caretRefusal`; observed to rename the *enclosing* method.
- [ ] `…caretOnTheReceiverRenamesTheReceiver` — case 19. Mutation: **broaden the `findReferences`
      guard to every kind**. Mutations inside `declarationLeafOfCallSite` do not reach this row; they
      were executed proving it (requirements case 19).
- [ ] `…undoRestoresTheFileInOneStep` — case 20, via `UndoManager.getInstance(project).undo(...)`,
      copying `LuaRenameUndoTest`'s idiom rather than inventing one.
- [ ] `LuaColonMethodRenameConflictTest.reportsAMemberNameAlreadyOnTheReceiver` — case 21.
- [ ] `…doesNotReportAnIdenticalShapeInAnotherFile` — case 22.
- [ ] `LuaColonMethodRenameTest.dottedFormIsUnaffected` — case 23.
- [ ] `…refusesARequiredModuleReceiver` — case 24 (two files).
- [ ] Full suite: `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"`, 0 failures.
      `--rerun --no-build-cache` is not optional: without it Gradle serves `:test` FROM-CACHE and a
      green run asserts nothing.
- [ ] `tooling/gce-builder/gce-builder.sh run ktlintCheck` green (format on the VM and rsync back
      first; see the standing constraints above).
- [ ] `python3 scripts/lint_docs.py docs` and `python3 scripts/lint_planning.py docs`, 0 errors.
- [ ] Run `human-verification-checklists.md` — the dialog path is not exercised headlessly, and the
      refusal balloons are only visible in a real IDE.

## Task summary

| Phase | Status | Priority |
| :-- | :-- | :-- |
| Phase 1: `LuaColonMethodRename` — the predicate | todo | Must |
| Phase 2: unit-test the predicate in isolation | todo | Must |
| Phase 3: wire it into `LuaRenameProcessor` | todo | Must |
| Phase 4: repair the two REFACT-01 tests | todo | Must |
| Phase 5: the member-name conflict | todo | Should |
