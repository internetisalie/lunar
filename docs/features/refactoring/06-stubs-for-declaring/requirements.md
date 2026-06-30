---
id: REFACT-06
title: Create from Usage Requirements
type: feature
parent_id: REFACT/INTENT
status: done
vf_icon: ✅
folders:
  - "[[features/refactoring/requirements|requirements]]"
---

# Create from Usage Requirements

## Overview

Two **standalone `IntentionAction`s** (Alt+Enter on the identifier, available anywhere on the
name — not bound to an inspection highlight) that generate a missing declaration from a usage:

- **REFACT-06-01 — Create local variable**: when an undeclared name is the **write target** of
  an assignment (`x = expr`), turn it into a `local` declaration (`local x = expr`).
- **REFACT-06-02 — Create function**: when an undeclared name is **called** (`myFunc(...)`),
  generate a `local function myFunc(arg1, …, argN) end` stub above the enclosing top-level
  statement, with N params matching the call's positional argument count.

"Undeclared" is determined identically to `LuaUndeclaredVariableInspection` — the name's
`LuaNameReference` resolves to nothing and the name is not an exempt global (standard global,
allowlisted, underscore-suppressed). See design §"Prior Art / Integration".

## Requirements Table

| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| REFACT-06-01 | Create Local Variable | Must | done | Intention to declare a `local` when an undeclared name is the simple write target of an assignment (`x = expr` → `local x = expr`). Offered only on a write target, never on a read or an already-declared name. |
| REFACT-06-02 | Create Function | Must | done | Intention to generate a `local function name(arg1..argN) end` stub above the enclosing top-level statement when an undeclared name is the callee of a call; N = the call's positional argument count. |

## Test Cases

All cases use `myFixture.configureByText(LuaFileType, …)` with `<caret>` on the named identifier,
`myFixture.findSingleIntention(<text>)` / `launchAction(…)`, then `myFixture.checkResult(…)`.
Negative cases assert `myFixture.filterAvailableIntentions(<text>).isEmpty()`.

### TC1 — Create local variable on a simple write target (REFACT-06-01)
**Intention:** `Create local variable 'x'`
**Input:**
```lua
x<caret> = 1
```
**Expected output:**
```lua
local x = 1
```

### TC2 — Create local NOT offered on a read use (REFACT-06-01, negative)
A bare read of an undeclared name is not a declaration site; "create local" must not appear.
(The undeclared-variable inspection still flags it for its own "Add to globals" fix.)
**Intention:** `Create local variable 'y'` — **must be absent**.
**Input:**
```lua
print(y<caret>)
```
**Expected:** no `Create local variable` intention is offered.

### TC3 — Create local NOT offered on an already-declared name (REFACT-06-01, negative)
**Intention:** `Create local variable 'x'` — **must be absent**.
**Input:**
```lua
local x = 0
x<caret> = 1
```
**Expected:** no `Create local variable` intention is offered (the LHS `x` resolves to the
existing `local x`).

### TC4 — Create function with two positional args (REFACT-06-02)
**Intention:** `Create function 'myFunc'`
**Input:**
```lua
myFunc<caret>(1, 2)
```
**Expected output:**
```lua
local function myFunc(arg1, arg2)
end

myFunc(1, 2)
```

### TC5 — Create function with zero args (REFACT-06-02)
**Intention:** `Create function 'f'`
**Input:**
```lua
f<caret>()
```
**Expected output:**
```lua
local function f()
end

f()
```

### TC6 — Create function NOT offered on an already-declared callee (REFACT-06-02, negative)
**Intention:** `Create function 'f'` — **must be absent**.
**Input:**
```lua
local function f() end
f<caret>()
```
**Expected:** no `Create function` intention is offered (callee resolves to the existing `f`).

### TC7 — Create function NOT offered when callee is a member access (REFACT-06-02, negative)
The callee is not a single undeclared `LuaNameRef` (it has a `.field` suffix), so the intention
does not apply.
**Intention:** `Create function 'method'` — **must be absent**.
**Input:**
```lua
obj.method<caret>(1)
```
**Expected:** no `Create function` intention is offered.

## Traceability

| Requirement | Positive TC | Negative TC |
|---|---|---|
| REFACT-06-01 | TC1 | TC2, TC3 |
| REFACT-06-02 | TC4, TC5 | TC6, TC7 |
