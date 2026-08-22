---
id: "BUG-447"
title: "Add to Watches is a silent no-op for every debugger variable — the evaluation expression was left commented out"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-447: nothing overrides `getEvaluationExpression`, so **Add to Watches** does nothing

Carved out of [[BUG-414]] on 2026-08-22. BUG-414 bundled three changes to the same class; two are
read-action corrections with no open question, and this one is a behavioural fork that needs a live
check. Splitting it keeps BUG-414 from blocking on a VNC session.

BUG-414's Notes framed this as *"either delete the three dead fields or restore the feature — check
first whether Add to Watches on a nested table child currently works"*. The platform source answers
the enabling half of that question without a debug session, and the answer is worse than the framing
assumed: it is not nested table children that are affected, it is **every variable**.

## 1. Reproduction

1. Start a Lua debug session and pause at a breakpoint.
2. Right-click any variable in the Variables pane — a plain local will do.
3. Invoke **Add to Watches**.

## 2. Expected vs actual

- **Expected**: the variable appears in the Watches pane.
- **Actual** (predicted from platform source; §5 is the live confirmation): the menu item is
  **enabled**, the click is accepted, and nothing happens. No watch is added, no error is shown, and
  nothing is logged.

## 3. Root cause

`LuaDebugVariable` never overrides `getEvaluationExpression()`. The only implementation in the repo
is **commented out**, at
[`run/LuaDebugVariable.kt:158-165`](../../../../src/main/kotlin/net/internetisalie/lunar/run/LuaDebugVariable.kt):

```kotlin
//    val evaluationExpression: String?
//        get() {
//            if (isIndex) {
//                return parent.getEvaluationExpression() + "[" + getName() + "]"
//            }
//            return if (parent != null) parent.getEvaluationExpression() + "[\"" + getName() + "\"]" else getName()
//        }
```

`grep -rn 'getEvaluationExpression\|calculateEvaluationExpression' src/main` returns those three
comment lines and nothing else, so the platform default is in force:

```java
// platform/xdebugger-api/.../XValue.java:33
public @Nullable String getEvaluationExpression() { return null; }
```

`calculateEvaluationExpression()` wraps it and resolves to a `null` `XExpression`. The consuming end
discards a null without any user-visible signal —
`platform/xdebugger-impl/ui/.../DebuggerUIUtil.java:565`:

```java
public static void addToWatches(@NotNull XWatchesView watchesView, @NotNull XValue value) {
  value.calculateEvaluationExpression().onSuccess(expression -> {
    if (expression != null) {                                  // <- always null for Lunar
      invokeLater(() -> watchesView.addWatchExpression(expression, -1, false));
    }
  });
}
```

Nothing suppresses the action, because `XAddToWatchesTreeAction.isEnabled` tests only that the node
has a name and a watches view exists — neither of which involves the expression. So the action
presents as available and silently does nothing.

**This is why the two fields look dead.** `isIndex` is `false` at all three construction sites and
`parent` is written at `:67` and never read; both exist *only* to serve the commented-out block.
They are not leftovers from a removed feature — they are the surviving half of one that was never
switched on.

## 4. Fix strategy

**Restore the block rather than delete the fields**, assuming §5 confirms the prediction. Deleting
them would cement a menu item that does nothing, and would throw away the parent-chain plumbing that
a correct expression needs — `computeChildren` at `:65` already threads `parent = this` through every
nested child for exactly this purpose.

Restoring it as-is is not sufficient; three defects are visible in the commented code:

- **It is a `val`, not an override.** `XValue.getEvaluationExpression()` is a Java method; the
  Kotlin property as written would not override it. It needs `override fun getEvaluationExpression(): String?`.
- **`parent.getEvaluationExpression()` is called on a nullable receiver** in the `isIndex` branch —
  it would not compile.
- **`isIndex` is never `true`.** `computeChildren` passes `isIndex = false` unconditionally at
  `:69`, even though it has just decided the key's kind at `:56-61`: `LuaValueKind.Number` produces
  a `[n]` name, everything else a bare or bracketed name. The index/name distinction the field
  exists to carry is computed and then discarded. Set it from that same `when`.

The name a child carries is already bracket-decorated for non-string keys (`"[1]"`, `"[foo]"`), so
the expression builder must not bracket a second time. Whether to fix that in the name or in the
expression is the one design choice here; prefer keeping the display name as-is and deriving the
expression from `isIndex` + the raw key.

## 5. How to settle it — do this before writing code

The platform source predicts the symptom; it does not prove the user-visible behaviour, and the
BUG-443 caret trap is a standing reminder that a live "nothing happened" has cheap explanations.

1. Sandbox IDE, real debug session, paused at a breakpoint (`verify-in-ide` skill).
2. **Add to Watches** on a plain local. Confirm from a cropped screenshot that the Watches pane
   stays empty and no balloon appears.
3. Repeat on a nested table child, which is the case BUG-414 originally called out.
4. If a watch *is* added, this bug is wrong and the fields really are dead — delete all three and
   close it as a cleanup.

## 6. Test strategy

| test | asserts | where |
| :-- | :-- | :-- |
| a top-level local's expression is its bare name | the non-nested case | `TestLuaDebugVariable` |
| a string-keyed child yields `parent["key"]` | the `parent` chain is read | `TestLuaDebugVariable` |
| a numeric-keyed child yields `parent[1]` | `isIndex` is set, not hardcoded `false` | `TestLuaDebugVariable` |
| a two-level nest yields `a["b"]["c"]` | the chain recurses | `TestLuaDebugVariable` |

The numeric-key case is the one that fails against a naive restoration, because it is the one
`isIndex = false` currently breaks. Mutation-proof it: restore `isIndex = false` at the construction
site and confirm that test alone goes red.

## 7. Notes

- **Severity.** A menu item that is enabled and does nothing is worse than a missing one — there is
  no signal to the user that the feature is absent. Against that, the whole Watches surface is
  reachable by typing the expression by hand, so it degrades rather than blocks.
- `LuaDebugValue` also leaves `getModifier()` at its default, so watches would be read-only. That is
  a separate absence and is **not** in scope here.
