---
id: INTENT-03-DESIGN
title: Name Suggestion Design
type: design
parent_id: INTENT-03
status: done
---

# Technical Design: Name Suggestion

All PSI types and platform APIs named below are grep-verified against this repo / the local
`intellij-community` checkout; file:line citations are inline.

## 1. Architecture Overview

- **New component**: `net.internetisalie.lunar.refactoring.rename.LuaNameSuggestionProvider`.
  - **Package note**: the package `net.internetisalie.lunar.lang.refactoring.rename` proposed
    by the original skeleton does **NOT exist** (`ls src/main/kotlin/.../lang/refactoring/` →
    "No such file or directory"). The existing refactoring code lives in
    `net.internetisalie.lunar.refactoring` (`LuaIntroduceVariableHandler.kt`,
    `LuaSafeDeleteProcessor.kt`). To keep refactoring code co-located, the provider is placed
    in a NEW sub-package `net.internetisalie.lunar.refactoring.rename`.
- **Implements**: `com.intellij.refactoring.rename.NameSuggestionProvider`
  (`intellij-community/platform/refactoring/src/com/intellij/refactoring/rename/NameSuggestionProvider.java:16`).
- **EP**: `com.intellij.nameSuggestionProvider`, registered as `<nameSuggestionProvider .../>`
  (declared at `intellij-community/platform/refactoring/resources/META-INF/RefactoringExtensionPoints.xml:16`;
  example consumer `intellij-community/java/java-backend/resources/META-INF/JavaPlugin.xml:770`).
- **Shared derivation**: the name-derivation logic is extracted into a stateless helper object
  `net.internetisalie.lunar.refactoring.rename.LuaNameDeriver` so BOTH the provider and the
  existing `LuaIntroduceVariableHandler.baseNameFor` consume one implementation (see §5).

## 2. Public API & Signatures

Exact signature (verified at `NameSuggestionProvider.java:29-30`):

```kotlin
package net.internetisalie.lunar.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.refactoring.rename.NameSuggestionProvider

class LuaNameSuggestionProvider : NameSuggestionProvider {
    override fun getSuggestedNames(
        element: PsiElement,
        nameSuggestionContext: PsiElement?,
        result: MutableSet<String>,
    ): SuggestedNameInfo?
}
```

- Return `null` when the provider is not applicable (element is not a Lua expression we can
  derive from, and no candidate is produced) — per the platform contract, `null` means
  "not applicable".
- When at least one candidate is produced, add it to `result` and return a non-null
  `SuggestedNameInfo`. Use `SuggestedNameInfo.NULL_INFO`
  (`intellij-community/.../psi/codeStyle/SuggestedNameInfo.java`, field `NULL_INFO`) — we keep
  no name-usage statistics, so the no-op `nameChosen` of `NULL_INFO` is correct.
- `result` is the shared accumulator across all providers; we ADD, never clear it.

### Helper signature

```kotlin
object LuaNameDeriver {
    /** Returns a single base-name candidate for [expr], or null if none can be derived. */
    fun baseName(expr: LuaExpr): String?
}
```

## 3. Derivation Algorithm (normative)

Input: a `LuaExpr` (`src/main/gen/net/internetisalie/lunar/lang/psi/LuaExpr.java`). The
provider first resolves the target expression from `element`/`nameSuggestionContext`
(see §4), then calls `LuaNameDeriver.baseName(expr)`.

### 3.1 Raw name extraction (by expr kind)

1. **`LuaFuncCall`** (`src/main/gen/.../psi/LuaFuncCall.java:8`, `extends LuaExpr`):
   determine the callee's last identifier segment.
   - A `LuaFuncCall` has `getVarOrExp(): LuaVarOrExp` (`LuaFuncCall.java:14`) and
     `getNameAndArgsList(): List<LuaNameAndArgs>` (`LuaFuncCall.java:11`).
   - **Method call** `obj:getName()`: the method name lives in the LAST `LuaNameAndArgs`
     whose `getMethodExpr()` is non-null — `LuaNameAndArgs.getMethodExpr(): LuaMethodExpr?`
     (`LuaNameAndArgs.java`), and `LuaMethodExpr.getNameRef(): LuaNameRef`
     (`src/main/gen/.../psi/LuaMethodExpr.java:8-12`), `LuaNameRef.getIdentifier(): PsiElement`
     (`src/main/gen/.../psi/LuaNameRef.java`). Use this identifier's text → `getName`.
   - **Plain / dotted call** `getUser()` / `db.getUser()`: when no `LuaNameAndArgs` carries a
     `methodExpr`, take the LAST `LuaNameRef` under `getVarOrExp()` (so `db.getUser` →
     `getUser`, not `db`). Mirror the existing `calleeName` traversal
     (`LuaIntroduceVariableHandler.kt:150-153`: `PsiTreeUtil.findChildrenOfType(call.varOrExp,
     LuaNameRef::class.java).lastOrNull()?.identifier?.text`), but check `methodExpr` FIRST so
     method calls are handled (the existing handler does NOT — see §5 / Risk 1.2).
2. **`LuaIndexExpr`** field access `cfg.timeout`
   (`src/main/gen/.../psi/LuaIndexExpr.java:8`): `getNameRef(): LuaNameRef?`
   (`LuaIndexExpr.java:14`) → its identifier text → `timeout`. (Matches the existing
   `propertyName` helper at `LuaIntroduceVariableHandler.kt:155-158`.)
   - NOTE `LuaIndexExpr` does NOT `extend LuaExpr`; it is reached as a child of a `LuaVar`/
     `LuaVarOrExp` expression. The provider handles it when the resolved expression contains a
     trailing `LuaIndexExpr` (use `PsiTreeUtil.findChildrenOfType(expr, LuaIndexExpr).lastOrNull()`).
3. **`LuaNameRef`** bare name `foo`: the identifier text itself → `foo`.
4. **Anything else** (e.g. `LuaBinOpExpr`, literals): out of scope for the provider — return
   `null` (the Introduce Variable handler keeps its own `result`/`value` fallbacks; see §5).

If no raw name is extracted, return `null`.

### 3.2 Prefix stripping (INTENT-03-02)

Apply to the raw name `n`:

- **Prefix list (exact, ordered longest-first to avoid `new` vs `newCustomer` ambiguity is
  moot since all are whole-word matched by the uppercase rule):**
  `["create", "build", "find", "load", "make", "get", "set", "new"]`.
- **Rule:** for the first prefix `p` such that `n` starts with `p` AND `n.length > p.length`
  AND `n[p.length]` is an uppercase ASCII letter (`isUpperCase`):
  - strip `p`, then lowercase the first remaining character:
    `result = n[p.length].lowercaseChar() + n.substring(p.length + 1)`.
  - Example: `getUser` → `User` → `user`; `createOrder` → `order`.
- If no prefix matches the uppercase rule, return `n` unchanged. Examples that must NOT
  strip: `compute` (no prefix), `settings` (`set`+`t`, lowercase), `getter` (`get`+`t`),
  `newline` (`new`+`l`).
- Result must be non-empty and a valid Lua identifier start (already guaranteed: stripped
  result begins with a lowercased letter).

### 3.3 Output

- The provider adds the single derived candidate to `result` and returns `NULL_INFO`.
- It does **not** add the un-stripped form as an extra candidate (keeps the popup focused);
  this is a deliberate scope decision — see [risks-and-gaps.md](risks-and-gaps.md) TBD.

## 4. Element resolution inside `getSuggestedNames`

The platform passes `element` (the element being renamed) and `nameSuggestionContext` (the
declaration/usage context). For the Rename path on a `local x = getUser()`, `element` is the
name being renamed; the RHS is reached via the enclosing local declaration. Resolution order:

1. If `element` (or `nameSuggestionContext`) is itself a `LuaExpr` we can derive from, use it.
2. Otherwise walk to the enclosing local var declaration and take its initializer expression.
   (Detailed PSI walk is a de-risking task — see [risks-and-gaps.md](risks-and-gaps.md)
   INTENT-03-00-DR-02 — because the rename element shape is not yet pinned; the Introduce
   Variable path, which passes the RHS expr directly to `LuaNameDeriver`, is fully specified.)

## 5. Prior Art / Integration — `LuaIntroduceVariableHandler`

**Finding (grep-verified):** `LuaIntroduceVariableHandler`
(`src/main/kotlin/net/internetisalie/lunar/refactoring/LuaIntroduceVariableHandler.kt:37`)
**ALREADY computes a suggested base name** via a private heuristic:

- `suggestName(expr, block)` (`:139-142`) → `baseNameFor(expr)` (`:144-148`) → `uniquify(...)`
  (`:160-170`).
- `baseNameFor` (`:144-148`): `LuaFuncCall` → `calleeName` (`:150-153`); `LuaBinOpExpr` →
  `"result"`; else → `propertyName` (`:155-158`) ?: `"value"`.
- It does **NOT** consult any `NameSuggestionProvider`; it is wired in
  `LuaRefactoringSupportProvider.getIntroduceVariableHandler()`
  (`src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaRefactoringSupportProvider.kt:27-29`)
  and invoked directly (`LuaIntroduceVariableTest.kt:29`). So adding a
  `NameSuggestionProvider` alone would NOT change Introduce Variable behavior — the two paths
  would silently diverge.

**Decision: EXTEND, do not duplicate or replace.**

- Extract the raw-name + prefix-strip logic into `LuaNameDeriver.baseName(expr)`.
- The new `LuaNameSuggestionProvider` calls `LuaNameDeriver.baseName`.
- Refactor `LuaIntroduceVariableHandler.baseNameFor` to delegate to `LuaNameDeriver.baseName`,
  preserving its existing fallbacks: `LuaNameDeriver.baseName(expr) ?: when (expr) {
  is LuaBinOpExpr -> "result"; else -> "value" }`. The handler keeps `uniquify` (`:160-170`)
  for collision handling — the provider must NOT duplicate uniquification.
- **Behavioral consequence — INTENT-03-02 newly applies to Introduce Variable too.** Today
  `compute()` introduces `local compute = compute()` (`LuaIntroduceVariableTest.kt:60-63`,
  unchanged — no prefix). But `getUser()` today introduces `local getUser = getUser()`;
  after this change it becomes `local user = getUser()`. This is an intended improvement
  (TC1). The existing test `testCalleeNameSuggestion` (non-prefixed `compute`) still passes;
  a new prefix-stripping assertion is added (see implementation-plan.md).
- **Method-call gap is fixed as a side effect**: the existing `calleeName` (`:150-153`) only
  inspects `varOrExp`, so it misses `obj:getName()` (method name lives under
  `LuaNameAndArgs.methodExpr`). `LuaNameDeriver` handles `methodExpr` (§3.1), so both paths
  gain method-call support.

**Why two entry points remain:** `NameSuggestionProvider` feeds the platform **Rename**
popup (where the hand-rolled handler is not involved at all) and any future platform-driven
introduce UI. The hand-rolled handler still owns the headless/deterministic Introduce Variable
flow. Sharing `LuaNameDeriver` is what keeps them consistent.

## 6. Integration Points (registration)

In `src/main/resources/META-INF/plugin.xml`, alongside the existing refactoring registrations
(`plugin.xml:206-210`):

```xml
<nameSuggestionProvider implementation="net.internetisalie.lunar.refactoring.rename.LuaNameSuggestionProvider"/>
```

No `id`/`order` is required (Lua is the only provider for this language). If ordering relative
to a default provider ever matters, add `id="lua"` (cf. `JavaPlugin.xml:770` `id="java"`).

## 7. Threading

`getSuggestedNames` is called by the platform inside a read action during rename/introduce; it
performs PSI reads only (no I/O, no writes), so no extra `runReadAction` wrapping is needed.

## Open Questions

None.

<!-- The one unpinned item — the exact PSI walk from a Rename `element` to its RHS — is tracked
     as de-risking task INTENT-03-00-DR-02 in risks-and-gaps.md, to be resolved before
     implementation. The Introduce Variable path and the derivation algorithm are fully
     specified above. -->

## See Also
- Requirements: [requirements.md](requirements.md)
- Implementation Plan: [implementation-plan.md](implementation-plan.md)
- Risks & Gaps: [risks-and-gaps.md](risks-and-gaps.md)
</content>
