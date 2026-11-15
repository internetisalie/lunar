---
id: "BUG-94"
title: "A class method annotated `@generic T: SomeClass` / `@param self T` reports missing fields and too many arguments, and not the undefined field"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-94: A class method annotated `@generic T: SomeClass` / `@param self T` reports missing fields and too many arguments, and not the undefined field

Found by the MAINT-55 conformance suite: 1 case (rule `field.undefined`, observed `false-report`) disagrees with LuaLS's expected findings. Cases are in `src/test/resources/conformance/luacats/cases/`, and the recorded outcomes in `results.json`.

## 1. Reproduction
Conformance case `undefined-field-013` (`rule=field.undefined source=undefined-field.lua#13`); lines marked `-- E` are the ones LuaLS reports.

```lua
---@class SomeClass
---@field someExistingVar integer
local SomeClass = {}

---@generic T: SomeClass
---@param self T
---@return T
function SomeClass:xyz()
    print(self.someExistingVar)
    print(self.doesNotExist) -- E
    return self
end
```

Cases in this group: `undefined-field-013`.

## 2. Expected vs Actual Behavior
- **Expected**: Only `self.doesNotExist` (line 10) is reported.
- **Actual**: `Missing required field 'someExistingVar'` (line 3), `{ ... } | SomeClass is not assignable to SomeClass` (lines 8-12) and `Too many arguments: expected at most 0, got 1` (line 8); line 10 is not reported.
- **Recorded for `undefined-field-013`**: findings `3-3:Missing required field 'someExistingVar'`, `8-12:{ ... } | SomeClass is not assignable to SomeClass`, `8-8:Too many arguments: expected at most 0, got 1`; conformance lines [10] must be reported.

## 3. Related
[[BUG-74]] is a likely mechanism for the too-many-arguments report; not confirmed.

## 4. Root Cause
The three false reports and the missing one come from one seam: a `---@param self T` tag on a `:`
method, whose receiver is implicit and has no entry in the function's parameter list.

1. **`Too many arguments: expected at most 0, got 1`** (8-8). `LuaTypeManagerImpl.methodType`
   (`LuaTypeManagerImpl.kt`, `val params = signature.params.map ...`) turns every `@param` tag into a
   parameter of the class member, including the `self` tag, and also sets `implicitSelf = true`. The
   member is one parameter longer than the in-file function `LuaTypesVisitor.visitFunctionBody`
   builds from the same declaration (an empty `parList`), so `LuaTypeGraph.reportArity` counts the
   receiver twice. It reproduces with a non-generic `---@param self SomeClass`, so generics are not
   involved ([[BUG-74]] is not the mechanism).
2. **`Missing required field 'someExistingVar'`** (3-3) and **`{ ... } | SomeClass is not assignable
   to SomeClass`** (8-12). `LuaTypeGraphBridge.injectParamAnnotations` finds no parameter node for the
   `self` tag (`paramNodes.getOrNull(astIndex) ?: return`), so `T` is never attached to `self`.
   `visitFunctionBody` gives `self` only the receiver edge, the union of the `{}` literal and
   `SomeClass`. `return self` then flows that union into the `@return T` demand. A bounded `Generic`
   use means "the value must satisfy the bound" (design 5.7), so the `{}` arm is checked against the
   exact class and reported.
3. **No report for `self.doesNotExist`.** The engine has no undefined-field report at all: a member
   read adds a non-exact `Table` use (`LuaTypesVisitor.visitIndexExpr`), and
   `LuaTypeGraph.checkTableCompatibility` forgives any member the value lacks unless the use is exact
   ([[BUG-93]] tracks the general case; `v.field3` on a `@type` class is also unreported). Fixing 1
   and 2 alone would turn `undefined-field-013` from `false-report` into `missed`, not `agree`.
4. **Related defect on the same seam.** With `---@param self X` followed by `---@param a string` on
   `function C:m(a)`, `injectParamAnnotations` falls back to the tag's index when the name is not
   found, so the `self` tag (index 0) typed `a` as `X`: `X is not assignable to string`.

## 5. Fix Strategy
- `LuaTypeManagerImpl.funcTypeFromStub`: drop a parameter named `self` from a `:` method's declared
  signature (`DeclaredSignature.withoutImplicitSelf`). The call-site template (`genericTemplateOf`)
  is left alone: the type a caller sees is [[TYPE-19]].
- `LuaTypeGraphBridge.injectGenericSelf`, called from `visitFunctionBody`: when the `self` tag names
  one of the comment's `@generic`s, `self` takes `Generic(T, bound)` in place of the receiver edge, so
  it reads as its bound (TYPE-14 5.7) and `return self` matches `@return T` as `Generic == Generic`.
  A `self` tag naming a plain class keeps today's receiver typing.
- `LuaTypeGraphBridge.injectParamAnnotations`: a `self` tag is not positional when the function has
  no `self` parameter.
- `LuaTypeGraph.reportUndefinedFields`, in the `Generic` arm of `checkCompatibility`: a member read
  through a `Generic` whose bound is a named class table, where the bound (with its supers) lacks the
  member, is `Undefined field 'k'` at warning tier. A write (`declaresMember`) is exempt. A union
  bound (`undefined-field-014`) and a general class-typed value stay with [[BUG-93]].
- No stub field changes, so no stub version bump.

## 6. Test Strategy
`LuaGenericSelfMethodTest` (light fixture, reads `LuaTypesSnapshot` errors with their lines):
- the conformance shape reports exactly `10:Undefined field 'doesNotExist'` (red before: the three
  findings above);
- a non-generic `@param self SomeClass` method reports nothing (red before: `Too many arguments`);
- a field inherited by the bound and a write to a new field are not undefined (controls);
- a method with `@param self` and `@param a string` still reports a call with too many arguments,
  and no longer types `a` as the class.

`undefined-field-013` reaches `agree` in the MAINT-55 conformance record and leaves `bugs.json`.
