---
id: DEBUG-02
title: "02: Stack Frames & Variables"
type: feature
status: "done"
vf_icon: ✅
priority: "medium"
parent_id: DEBUG/RUN
folders: ["[[features/debug/requirements|requirements]]"]
---

# 02: Stack Frames & Variables

Display the call stack and each frame's locals and upvalues when paused at a breakpoint, and let the
user act on a value — navigate to it, watch it, expand it.

## How these requirements were derived

**Not from Lunar's code.** A specification read off its own implementation cannot fail, which is the
defect that left [[DEBUG-07]] marked shipped for months (see [[BUG-450]] §4). The rows below come
from three external sources:

1. **The IntelliJ platform contract** — every capability `XValue`, `XNamedValue` and `XStackFrame`
   expose is a question this feature must answer, either "we provide it" or "we deliberately do
   not". That list exists independently of Lunar and is the backbone of the table.
2. **[[plugin-feature-comparison]]** — what lua-for-idea, IntelliJ-EmmyLua and EmmyLua2 ship.
3. **Live observation** — a real MobDebug session paused in `test/debug.lua`, 2026-08-22.

Where a capability is unimplemented, the row distinguishes **Lunar does not implement it** from
**the user cannot do it**: the platform supplies working defaults for several, and conflating the
two would manufacture gaps that are not there.

## Requirements & Status

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `DEBUG-02-01` | **Call stack listed** | **M** | **Full** | `LuaExecutionStack.computeStackFrames` builds one `LuaStackFrame` per remote entry, `=[C]` frames included without a source position. |
| `DEBUG-02-02` | **Frame presentation** | **M** | **Full** | `LuaStackFrame.customizePresentation` renders position plus the context name in italics. |
| `DEBUG-02-03` | **Locals and upvalues grouped** | **M** | **Full** | Two auto-expanding `XValueGroup`s. Empty groups are omitted rather than shown empty. |
| `DEBUG-02-04` | **Values presented by type** | **M** | **Full** | `LuaDebugValue.computePresentation` selects numeric/string/regular presentations from the Lua kind. |
| `DEBUG-02-05` | **Tables expand to members** | **M** | **Full** | `computeChildren` enumerates `LuaTable.pairs()`; string, numeric and unrepresentable keys each render. |
| `DEBUG-02-06` | **Jump to Source on a variable** | **S** | **Full** | `computeSourcePosition` resolves the declaration by scope walk. Was unsafe off the EDT until [[BUG-414]] (fixed 2026-08-22). |
| `DEBUG-02-07` | **Add to Watches** | **S** | **Full** | `getEvaluationExpression` yields `count`, `cfg["name"]`, `items[1]`. Silently inert until [[BUG-447]] (fixed 2026-08-22). |
| `DEBUG-02-08` | **Set a value while paused** | **S** | **Not Implemented** | `XValue.getModifier` is never overridden, so no `XValueModifier` exists and **Set Value…** is greyed out — confirmed in the live context menu, 2026-08-22. mobdebug supports `EXEC`, so the protocol is not the obstacle. |
| `DEBUG-02-09` | **Tree state survives a step** | **S** | **Not Implemented** | `XStackFrame.getEqualityObject` returns the platform default `null`, so no frame is recognised as the same frame after stepping and the variables tree cannot retain expansion or selection. `LuaStackFrame` already carries `index` + position, which is the natural key. |
| `DEBUG-02-10` | **Full value for a truncated one** | **C** | **Partial** | Nothing calls `setFullValueEvaluator`. A **View** affordance does appear on long values, but that is the platform's own truncated-text handling, not Lua-aware retrieval — and mobdebug has already truncated upstream, so a real implementation needs a protocol round-trip, not just a UI hook. |
| `DEBUG-02-11` | **Inline values in the editor** | **C** | **Full** | *Not* implemented by Lunar — `computeInlineDebuggerData` is left at `ThreeState.UNSURE`, and the platform's name-matching fallback supplies it. Verified rendering live. Recorded as Full because the requirement is about the user, and as a caveat because a future override would replace working behaviour. |
| `DEBUG-02-12` | **Population is bounded** | **S** | **Not Implemented** | The `STACK` payload is serialized to unbounded depth across up to 100 frames including `_ENV`, then realized eagerly before a node renders. [[BUG-450]]; cost unmeasured, which is that bug's first task. |
| `DEBUG-02-13` | **Navigate to a value's type** | **C** | **Won't** | `computeTypeSourcePosition` is meaningful where a runtime value carries a declared type. A Lua value does not; the LuaCATS annotation that describes it belongs to the *variable*, which `DEBUG-02-06` already reaches. |
| `DEBUG-02-14` | **Referrers — what holds this value** | **W** | **Won't** | `getReferrersProvider` needs heap traversal. mobdebug exposes no such query and adding one means patching the vendored debuggee. |
| `DEBUG-02-15` | **Instance evaluator** | **W** | **Won't** | `getInstanceEvaluator` models evaluating against an object instance, a JVM-debugger concept with no Lua equivalent. |

## Verification

`TestLuaStackFrame`, `TestLuaDebugVariable`, `TestLuaDebugValue`, `TestLuaExecutionStack` and
`TestLuaRemoteStackFrames` cover `-01` through `-07` headlessly. `-06` and `-07` additionally have
live evidence from the 2026-08-22 VNC session, which is what the unit tests could not give: both
defects were in registration and presentation, not logic.

**`-08` and `-09` were found by writing this table** and are recorded nowhere else in the repo.
Neither has a bug report yet.
