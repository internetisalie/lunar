---
folders:
  - "[[features/syntax/07-inlay-hints|hints]]"
title: "07: Method Chaining Hints"
priority: medium
status: todo
---

# Requirements: SYNTAX-07-07 Method Chaining Hints

## Overview
Surface the return types of intermediate method calls in fluent interface chains when they span multiple lines. This helps developers track the object type as it evolves through a series of transformations or configuration calls.

## Scope

### In Scope
- Method calls using the colon syntax (`obj:method()`).
- Implementation as a **separate** `InlayHintsProvider` registered in the standard IntelliJ `Method chains` group.
- Chained calls where the receiver and the call expression are on different lines.
- Resolution of return types from LuaCATS `---@return` annotations.
- Basic type inference for methods returning `self` or a known class instance.

### Out of Scope
- Function calls using dot syntax (`obj.method(obj)`), unless it's clearly part of a chain (though standard Lua convention uses `:` for chains).
- Single-line chains (visual noise outweighs benefit).
- Assignment expressions at the end of a chain (covered by local variable type hints).

## Requirements Table

| ID | Priority | Description | Status |
| :--- | :---: | :--- | :---: |
| **07-07-REQ-01** | [Must] | Identify method call chains where the call is on a different line from its receiver. | Pending |
| **07-07-REQ-02** | [Must] | Resolve the return type of each intermediate method call in the chain. | Pending |
| **07-07-REQ-03** | [Must] | Render the hint as `: <type>` at the end of the line containing the method call. | Pending |
| **07-07-REQ-04** | [Should] | Suppress hints if the return type cannot be resolved to a specific type (e.g., `any` or `unknown`). | Pending |
| **07-07-REQ-05** | [Should] | Suppress hints for calls where both the call and its receiver are on the same line. | Pending |
| **07-07-REQ-06** | [Should] | Support multiple return types in the hint (e.g., `: string, number`). | Pending |
| **07-07-REQ-07** | [Must] | Ensure hint computation does not trigger expensive recursive resolution for chains deeper than 10 calls. | Pending |
| **07-07-REQ-08** | [Must] | Resolve `self` return types to the concrete class of the receiver. | Pending |
| **07-07-REQ-09** | [Should] | Suppress hints for trivial primitive types (boolean, number) unless they are part of a multi-type return. | Pending |
| **07-07-REQ-10** | [Must] | Implement as a dedicated `LuaMethodChainInlayHintProvider` in the `METHOD_CHAINS_GROUP`. | Pending |

## Test Cases (TC)

| ID | Input | Action | Expected Output |
| :--- | :--- | :--- | :--- |
| **TC-01** | `builder:setName("foo")\n  :setAge(30)` | Multi-line chain | `:setName("foo") : Builder\n :setAge(30) : Builder` |
| **TC-02** | `obj:m1():m2()` | Single-line chain | No hints shown |
| **TC-03** | `db:query("...")\n  :first()` | Mixed return types | `:query(...) : ResultSet\n :first() : Row?` |
| **TC-04** | `str:lower()\n  :trim()` | Stdlib methods | `:lower() : string\n :trim() : string` |
| **TC-05** | `obj:unknown()` | Unresolvable method | No hint shown |
| **TC-06** | `---@return A, B\nfunction m() end\nobj:m()\n  :next()` | Multiple returns | `:m() : A, B` |
| **TC-07** | `---@return self\nfunction B:set() end\nB:set()\n  :set()` | Self resolution | `:set() : B\n :set() : B` |
| **TC-08** | `---@class G<T>\n---@return G<T>\nfunction G:m() end\nlocal x = G --[[@as G<string>]]\nx:m()\n :m()` | Generic instantiation | `:m() : G<string>\n :m() : G<string>` |
| **TC-09** | `long:chain():on():one():line()\n  :finally()` | Partial single-line | Only show hint on `:line()` and `:finally()` |

