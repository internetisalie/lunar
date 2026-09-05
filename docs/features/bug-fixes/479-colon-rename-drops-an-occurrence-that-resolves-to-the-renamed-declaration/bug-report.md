---
id: "BUG-479"
title: "Colon rename silently drops an occurrence that resolves to the declaration being renamed"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-479: `colonCallVerdict` decides by resolving, not by resolving elsewhere

> ## Correction — the symptom this was filed on does not reproduce
>
> **Filed as "a colon-method rename half-applies, silently". That observation was an artifact of my
> own fixture handling, and the title above is the defect that survived it.**
>
> Re-verified live on a clean sandbox, fixture staged as `builder` **before** the IDE started so it
> was indexed at startup: caret `3:20`, <kbd>Shift+F6</kbd> → `withName`, **both occurrences
> renamed**, no conflict dialog. `human-verification-checklists.md` Scenario 1.3 **passes**.
>
> The two failing runs used a file written **into an already-running IDE**, in a directory where a
> sibling file had just raised `Failed to change read-only status` and been `chmod`-ed mid-session —
> the fixture was created by `ssh builder` (root-owned) rather than through the builder shell. What
> differs between the runs is fixture lifecycle, not plugin code.
>
> **Both premises the report rested on were also false, and were falsified by execution:**
>
> 1. *"No unit test drives a rename to completion and asserts the file text for an aliased
>    `---@class` receiver."* — `LuaColonMethodRenameTest.anAnnotatedReceiverRenamesThroughAnAliasedCallSite`
>    has asserted exactly that, on the same fixture, since Phase 2 (`aa76b275`).
> 2. *"`ReferencesSearch` misses the occurrence."* — measured across four shapes:
>    `projectScopeUsages=1`, `allScopeUsages=1`, `isReferenceTo=true`. Scope is not the variable, and
>    nothing in the searcher is measurably broken.
>
> **What remains real is the logic defect below**, which was latent rather than the cause of what I
> saw: `scanEmptyUsages=ACCEPTED` is deterministic across all four probed shapes. It is fixed at
> `778ec948`, and the fix is worth having on its own terms — it converts a would-be silent drop into
> a reported conflict. But it fixes a **latent** hazard, and this report must not be read as
> evidence that the hazard ever fired in the field.
>
> **The lesson is mine, not the code's: isolate the observation before filing it.** A control run in
> a clean environment would have cost three minutes and saved a critical-severity report. This is the
> same failure as [[BUG-478]]'s first draft (a description that did not survive contact) and the
> Quick Doc withdrawal (a conclusion generalized from two fixtures) — all three in one session, all
> three the same shape: **trusting what I saw without controlling how I produced it.**


**This is the [[BUG-457]] class that [[REFACT-09]] exists to prevent, occurring in REFACT-09's own
widest-reach shape.** Found by live verification of `human-verification-checklists.md`
**Scenario 1.3**, which fails.

## Reproduction

Sandbox GoLand 2026.1.3, plugin loaded, `feat-refact-09` at `56909120` + Phase 5. Reproduced twice,
the second time on a fully settled index after an undo.

```lua
---@class Builder
local Builder = {}
function Builder:setName(x) end
local b = Builder
b:setName("x")
```

Caret inside `setName` on line 3, <kbd>Shift+F6</kbd> → `withName`, Refactor.

| | Expected (Scenario 1.3) | Observed |
| :-- | :-- | :-- |
| the declaration | `withName` | `withName` |
| `b:setName("x")` | `withName` | **`setName` — unchanged** |
| conflicts dialog | none | **none** |

**The occurrence is neither renamed nor reported.** The user is told nothing.

## Resolution is not the problem — it works

<kbd>Ctrl+B</kbd> on the call site's `setName` (caret verified `5:6`) jumps to `3:18`, the
declaration. [[NAV-13]] resolves this shape correctly, and `requirements.md` case 4 pins it.

## Root cause

[`LuaColonMethodRename.colonCallVerdict`](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaColonMethodRename.kt):

```kotlin
if (nameRef in usages) return null
if (LuaColonCallResolution.declarationLeafOf(nameRef) != null) return null
return Undecided(nameRef, Spelling.COLON_CALL)
```

Clause (b) asks **"does it resolve?"** where `design.md` §3.4 specifies **"a colon call that resolves
*elsewhere*"**. It never compares the resolved leaf to the declaration being renamed. So an
occurrence that resolves to **this** declaration but is absent from `usages` satisfies clause (b),
is declared decided, and is dropped by both halves of the contract:

- not renamed, because the rename rewrites `usages`;
- not reported, because the scan only reports *undecided* occurrences.

The two nets are supposed to be complementary. This occurrence falls between them.

## The review round predicted this, and it was not executed

Round 3's non-blocking **N3**, recorded verbatim as a question rather than a finding:

> *clause (a) of §3.4 may be unreachable and nothing pins it. `usages` comes from `ReferencesSearch`
> over NAV-13's `isReferenceTo`, so every member also resolves through `declarationLeafOf` and clause
> (b) would decide it anyway. No test row names a mutation for `if (nameRef in usages) return null`,
> so **if the identity-set assumption is wrong, nothing goes red.**"

Nothing went red, and that part is exactly right: **no test named a mutation for the identity
assumption, so the clause was free to be wrong.** It was — clause (b) never compared identities at
all.

*Corrected by measurement:* the assumption that `usages` contains every occurrence resolving to the
declaration is **not** shown to be false for an aliased `---@class` receiver. In every shape
reachable from a fixture it holds (see *What was measured*). What was false is the inference N3 drew
from it — that clause (b) "would decide it anyway" *correctly*. Clause (b) decided it by the wrong
question, which is a defect whether or not clause (a) also happens to cover the site.

## Fix strategy

Compare identity in clause (b) — resolve *elsewhere*, not resolve *at all*:

```kotlin
val resolved =
    LuaColonCallResolution.declarationLeafOf(nameRef)
        ?: return Undecided(nameRef, Spelling.COLON_CALL)
if (resolved !== declarationLeaf) return null   // genuinely another method
return Undecided(nameRef, Spelling.COLON_CALL)  // ours, but not in the usage set
```

*As shipped:* [`LuaRenameTarget`](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameConflictDetector.kt)
has no `declarationLeaf` field — the declaration leaf is `target.identifier`. `colonCallVerdict`
takes it as a third parameter rather than taking the whole target, which keeps the verdict a
question about two elements and holds the function at the contract's three-argument cap.

This fails **safe**: an occurrence that resolves to the renamed declaration and is missing from
`usages` becomes a reported conflict rather than a silent drop, which is the direction
`requirements.md` "Behaviour Rules" fixes.

**The second question is why `ReferencesSearch` misses it at all**, given `resolve()` succeeds and
[[NAV-13]] case 7 pins the search returning the call site. Candidates, none verified: the rename's
`projectScope` against NAV-13's tested `allScope`; or `LuaNameReferenceSearcher`'s candidate-file
keying. **Execute this — do not reason about it.** The identity fix above makes the failure loud but
does not make the rename complete, and a user seeing a conflict on every aliased receiver is a
different defect from a silent half-rename.

## Test gap this exposes

~~No unit test drives a rename to completion and asserts the **file text** for an aliased
`---@class` receiver.~~ **This was wrong.**
`LuaColonMethodRenameTest.anAnnotatedReceiverRenamesThroughAnAliasedCallSite` has driven exactly
this fixture end to end through `myFixture.renameElementAtCaret("withName")` and asserted the
resulting buffer since Phase 2 (`aa76b275`), and it is green. The real gap was narrower and is what
the fix closes: nothing pinned the *premise* that fixture's sibling `c11` states in prose — that the
call site is in the usage set — because `assertAccepted` was satisfied either way, by clause (a) if
the search found it and by the old clause (b) if it did not. `theAliasedAnnotatedCallSiteIsInTheUsageSet`
now asserts it directly.

## What was measured (the report's second question, executed)

> *"The second question is why `ReferencesSearch` misses it at all. Candidates, none verified: the
> rename's `projectScope` against NAV-13's tested `allScope`; or `LuaNameReferenceSearcher`'s
> candidate-file keying. Execute this — do not reason about it."*

Executed, on the builder, with a throwaway probe over four shapes. **Both named candidates are
falsified, and no shape reproduces the search miss.** On the reported fixture:

| Measurement | Value |
| :-- | :-- |
| `ReferencesSearch` under `projectScope` | **1 usage**, and it *is* the call site |
| `ReferencesSearch` under `allScope` | **1 usage** — identical; the scope hypothesis is dead |
| `isReferenceTo(declarationLeaf)` | `true` |
| `resolve()` identity to the declaration leaf | `true` |
| `LuaColonCallResolution.declarationLeafOf` identity | `true` |
| `CacheManager.getFilesWithWord("setName", …)` | `[a.lua]` under **both** `IN_CODE` and `ANY` |

Three further shapes were probed for a resolve/search divergence and none produced one: the
declaration in one file with an `---@type`-annotated receiver in another; the same with the alias
chained through a second local; and a second file declaring the *same* class name. In every one:
`projectScopeUsages=1`, `callSiteInUsages=true`, `isReferenceTo=true`.

**So the searcher has nothing measurably wrong with it, and there was no contained searcher change
to prefer over the identity fix.** The fix applied is therefore the report's fail-safe.

**What is reproducible, deterministically, is the logic defect itself**, independent of the search:
scanning the reported fixture with a usage set that omits the occurrence gave
`scanEmptyUsages=ACCEPTED` in all four shapes — the occurrence declared decided, renamed by nobody
and reported by nobody. That is the condition the fix now reports, and it is what the regression
test pins.

## Residual: the live symptom is not explained

The live observation stands as recorded, and **its mechanism remains unestablished**. No shape
reachable from `BasePlatformTestCase` reproduces a `ReferencesSearch` that misses an occurrence
whose `resolve()` succeeds, so the live-only difference was not isolated. What the fix guarantees is
that if that condition does occur, the user is told: the occurrence becomes a conflict in the
dialog instead of a silent drop. **It does not make the rename complete in that condition.**
Re-running `human-verification-checklists.md` Scenario 1.3 live is the open item; if it still
half-applies, it will now do so *with* a conflict listing `b:setName("x")`, which is the diagnostic
the first live run did not have.

## Impact

`REFACT-09` must not ship as `done` while this stands. Annotated receivers are the shape the feature
reaches most — **121 of 268** declarations in the annotated substitute — so this is the common case,
not an edge.
