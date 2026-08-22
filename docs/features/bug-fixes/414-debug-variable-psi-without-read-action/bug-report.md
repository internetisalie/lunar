---
id: "BUG-414"
title: "Debugger variable navigation walks PSI with no read action"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-414: `LuaDebugVariable.computeSourcePosition` reads PSI without a read action

*Source: codebase review [`docs/review.md`](../../../review.md) finding **#73** (P1, second pass
2026-08-06).*

## 1. Reproduction

1. Start a Lua debug session and pause at a breakpoint.
2. In the Variables pane, invoke **Jump to Source** on a local.
3. While the position is being computed, edit the source file (any write action races the walk).

The window is small, so this reproduces intermittently; the defect is visible by inspection
regardless.

## 2. Expected vs Actual Behavior

- **Expected**: all PSI access happens inside `runReadAction { }` (engineering contract rule 1 —
  "READING PSI: Use `runReadAction { }`").
- **Actual**: `run/LuaDebugVariable.kt:84-130` performs, with no read action anywhere in the
  method:
  - `XDebuggerUtil.getInstance().findContextElement(...)` (`:93`),
  - a full `processDeclarations` scope walk up the PSI tree, block by block (`:104-125`),
  - `XDebuggerUtil.getInstance().createPositionByElement(processor.result)` (`:128`).

  `computeSourcePosition` is a platform callback invoked off the EDT and is not guaranteed to
  hold a read lock. Under a concurrent write the walk can see an inconsistent tree or throw.

## 3. Scope — one defect, not the three the review implied

This report originally carried two further items from the same review pass. Both were examined on
2026-08-22 before implementation; **neither survives as filed**, and the reasons are recorded here
because both would have been shipped as "obvious" cleanups.

### 3a. Review #87 (`LuaStackFrame.kt:56-85`) is wrong — do NOT remove that read action

The review claimed the `runReadAction` wrapping the `LuaDebugVariable`/`LuaDebugValue` construction
in `LuaStackFrame.computeChildren` is superfluous because "the block touches no PSI or VFS at all",
making the subsystem's read actions look exactly inverted.

**The premise is false.** The accessor chain is pure Kotlin — `entry.locals` → `LuaRemoteScope` →
`LuaRemoteVariable.value` all walk an already-materialized `LuaTable` — but the *constructor at the
end of it* is not:

```kotlin
// run/LuaDebugValue.kt:40-46
constructor(luaValue: LuaValue, identityValue: String?, icon: Icon?) {
    ...
    this.displayValue = luaValue.psiElement?.text ?: luaValue.toDisplayString()   // <- PSI deref
}
```

`LuaValue` carries a `psiElement` (`run/LuaValue.kt:32`), and `LuaDebugValueParser` populates it for
numbers, booleans, tables and function definitions, so `psiElement` is non-null for most stack
variables and `.text` runs on nearly every one. The read action there is load-bearing.

The tidy "inverted pattern" story is therefore not true: the subsystem has one missing read action,
not one missing and one misplaced. **Leave `LuaStackFrame` alone.**

### 3b. Review #82 (the dead `isIndex` / `parent` fields) is carved out as [[BUG-447]]

Deciding it needs a live debug session, and the answer turns a field deletion into a feature
restoration — the platform discards a null evaluation expression silently, so **Add to Watches** is
predicted to be a no-op for *every* variable, not just the nested table children the review named.
That is a behavioural fork, and holding this read-action fix behind a VNC run to settle it would be
the wrong trade. See [[BUG-447]] for the platform-source grounding and the live check.

## 4. Fix strategy

Wrap the walk, compute the position inside the read action, and hand it to the navigatable outside:

```kotlin
override fun computeSourcePosition(navigatable: XNavigatable) {
    val project: Project =
        targetProject ?: run {
            super.computeSourcePosition(navigatable)
            return
        }
    val position: XSourcePosition? = runReadAction { resolveDeclarationPosition(project) }
    if (position != null) navigatable.setSourcePosition(position)
}
```

Three constraints on the shape:

- **The method must be split.** `computeSourcePosition` is ~45 logic lines today, already past the
  engineering contract's 30-line tripwire; adding a nesting level makes it worse. Extract the
  session/context lookup and the scope walk into private helpers. **Each helper takes at most the
  contract's 3 arguments** — `resolveDeclarationPosition(project)` is one, and the walk needs only
  the context element, since `name` is a property of the receiver. Do not thread `processor`,
  `state` and `contextElement` through a 4-argument helper to avoid a nested `when`.
- **`createPositionByElement` stays inside the read action.** It is at `:128` today, after the walk,
  and it dereferences the resolved element. Only `navigatable.setSourcePosition` moves out.
- **Preserve the null path exactly.** When `processor.result` is null the method currently returns
  without touching `navigatable` — it does *not* fall back to `super`. Keep that; changing it alters
  what the Variables pane does on an unresolvable name, which is out of scope and untested.

## 5. Test strategy

`TestLuaDebugVariable` already has the harness — a fake `XNavigatable` recording calls, and
`testComputeSourcePositionNullProjectFallsBackToSuper` as the pattern to follow.

| test | asserts |
| :-- | :-- |
| `computeSourcePosition` from a pooled thread with no read lock yields the declaration's position | the fix, functionally |
| **control**: a bare PSI walk on that same thread throws | that the thread genuinely lacks read access |

**The control is what stops this being tautological**, and it is not optional. Under
`BasePlatformTestCase` the test thread frequently already permits read access, in which case the
first test passes identically with and without the fix — a green that asserts nothing, which is the
failure mode this repo has been bitten by twice (`--rerun`, `ktlintFormat`). The control proves the
absence of a read lock in the context where the first assertion is made.

**Gate with `mutation-proof` before calling this done**: strip the `runReadAction` back out and
confirm the first test goes red. If it stays green even with a passing control, the platform is not
asserting on this path — in that case say so in the commit and keep the test as a contract guard,
rather than reporting coverage the run did not demonstrate.

## 6. Notes

- Sequencing: this is independent of [[BUG-447]] and needs no debug session. The full-suite gate
  applies (`--rerun`), not the corpus sweep — no type or index behaviour is touched.
