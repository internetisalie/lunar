---
id: "TYPE-21"
title: "21: Prefixed and Suffixed Backtick Captures"
type: "feature"
status: "todo"
priority: "low"
parent_id: "TYPE"
folders:
  - "[[features/type/requirements|requirements]]"
---

# TYPE-21: Prefixed and Suffixed Backtick Captures

## Overview
A backtick capture can carry text on either side, so a string argument names a class by joining it to a
fixed prefix or suffix: ``Object.`T` `` and `` `T`Color `` build the class name from the argument.
Today the whole parameter is the bare capture only. Parent: [[features/type/requirements|TYPE]].

## Example
```lua
---@class Vehicle
---@class VehicleColor
---@class Object.Cat

---@generic T
---@param class `T`Color
---@return T
local function getItemColor(class) end

---@generic T
---@param name Object.`T`
---@return T
local function make(name) end

local c = getItemColor("Vehicle")   -- VehicleColor
local o = make("Cat")               -- Object.Cat
```
LuaLS 3.13.6 added a definition after the capture, and optional, union and array forms
(`` `T`.Cat? `` with `f('Smile')` gives `(Smile.Cat)?`); the string-literal-with-suffix behaviour is
described in PR [#3149](https://github.com/LuaLS/lua-language-server/pull/3149)'s "currently" example.
The same PR's other half, capturing an argument's *type* (`getItemColor(someVehicle)`), is open and
unmerged; it is not part of this feature. Survey Q5.

## Current behaviour in Lunar
- The grammar's capture is the whole parameter: `parameterName ::= CODE` (`luacats.bnf:217`). Whether
  `Object.`T`` or `` `T`Color `` lexes to a single `CODE` or to `CODE` plus text is Not yet probed.
- `TypeParser.captureType` (`TypeParser.kt:124-131`) strips backticks from the parameter text and
  marks the generic as a capture.
- `LuaGenericInstantiation.capturedType` (lines 145-150) resolves the string literal's text as a class
  name unchanged; it applies no prefix or suffix.
- Probe first: the example in a light fixture; record syntax errors on the two `@param` lines and the
  inferred types of `c` and `o`.

## Requirements
| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :--- | :--- | :--- |
| TYPE-21-01 | **A suffixed capture composes the name** | M | Not Implemented | `` `T`Color `` with `"Vehicle"`: `c` is `VehicleColor`. |
| TYPE-21-02 | **A prefixed capture composes the name** | M | Not Implemented | ``Object.`T` `` with `"Cat"`: `o` is `Object.Cat`. |
| TYPE-21-03 | **Neither form is a syntax error** | M | Not Implemented | The two `@param` lines in the example produce no parse error. |
| TYPE-21-04 | **A suffix with an optional return** | S | Not Implemented | `` `T`.Cat? `` with `---@return T?` and `f("Smile")`: `Smile.Cat?`. |
| TYPE-21-05 | **An unresolvable composed name leaves `T` unbound** | S | Not Implemented | `getItemColor("Nope")` (no class `NopeColor`): the result is `undefined`, with no diagnostic, as for a bare capture today. |
| TYPE-21-06 | **The bare capture is unchanged** | S | Not Implemented | ``---@param name `T` `` with `"Vehicle"` is `Vehicle`, as in TYPE-14-13. |

## Depends on / Related
- Depends on: [[TYPE-14]] (done; TYPE-14-13 is the bare capture).
- Related: survey Q5 and PR #3149; issue #1322 (deriving a class from a prefixed name, closed upstream as not planned).

## Out of scope
- Capturing an argument's type (PR #3149's new behaviour); upstream has not merged it.
- Computed names beyond one prefix and one suffix (`"AceGUI"..T`, #1322).
