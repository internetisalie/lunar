---
id: "BUG-117"
title: "A method's call signature lists its parameters in @param tag order, so arguments bind to the wrong parameter"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-117: `---@param` tags out of signature order make `TaskOptions is not assignable to fun(p)`

## Reproduction
Light fixture, reduced from lazy.nvim `lua/lazy/manage/task/init.lua` (the finding is at line 39,
`self:_run(task)`; the call is `Task.new(plugin, def[2], task_def.run, opts)` in `runner.lua:173`):

```lua
---@class TO
---@field on_done? fun(x: string)
---@alias TFn fun(x: string)
local Task = {}
function Task:_run(task) task('a') end

---@param name string
---@param opts? TO
---@param task TFn
function Task.new(name, task, opts)
  local self = setmetatable({}, { __index = Task })
  self:_run(task)
  return self
end
```

Rows on `bug-114-fix` (`883a3066`): `ERROR task | TO is not assignable to fun(p)`, plus HYPOTHESIS
`.new | fun(name, opts?, task) | fun(name, task, opts?) is not assignable to fun(name, opts?, task)`. On main
(`269b57cb`): the same `TO is not assignable to fun(p)` as HYPOTHESIS (at `task`, `task('a')`), no ERROR.
Control: put the tags in signature order (`name`, `task`, `opts`) and the `TO` rows disappear.

## Expected vs Actual
- **Expected:** LuaCATS binds `@param` by name; the tag order is irrelevant to which argument meets which parameter.
- **Actual:** the function type recorded as the member's declared signature is built from the tags in the order
  written, so the call's third argument meets `opts?` and its fourth, the `TaskOptions` value, meets `task`.
  Lazy finding: `TaskOptions is not assignable to fun(p, p)` (`lua/lazy/manage/task/init.lua` 39). True defect in
  the plugin's model, false report for the user: the source's signature and call agree.

## Root Cause
- `LuaTypeManagerImpl.declaredSignatureOf` (main `:706`) returns `LuaCatsDeclarations.params(cats)`, in tag order, and
  `methodType` (main `:781-786`) turns it into `LuaFunctionType(params…)` with no reference to the declaration's own
  parameter list. The member and the in-file function type then disagree, which is the second type in the
  HYPOTHESIS row above.
- The graph side binds by name already (`LuaTypeGraphBridge.injectParamAnnotations`, `astIndex = paramNames.indexOf`),
  so only the member type is wrong. A comment at main `:743` records the same hazard ("a real signature in `@param`
  order against the in-file function", Gap 2.9).
- Why the fix adds an ERROR: the pair is now judged against the final graph where the declared `@param task`
  use is reachable (BUG-116). The wrong binding is present on main.

## Fix Strategy
Order the member's parameters by the declaration's parameter list (its AST names), looking each
tag up by name, and keep a tag with no matching parameter by position as before. Keep `declaredSignature` as is.

## Fix
`LuaCatsDeclarations.params(comment, parameterNames)` orders the tags by the declaration's own parameter list
(`LuaPsiImplUtil.getParameters`, `...` included), matching each tag to its parameter by name. The AST-backed
`declaredSignatureOf` path and both func stub element types (`LuaFuncStubElementType`, `LuaLocalFuncStubElementType`)
call it, so a stub-backed member reads the same order without loading the AST; the stub version is bumped. A tag
naming no parameter takes the position of the parameter at its own index, as the graph side binds it; tags still
unplaced follow in tag order. `LuaParamTagOrderTest` pins the binding with differently typed parameters, because
the lazy `TO` row does not surface on this main.

## Test Strategy
`LuaParamTagOrderTest`: the fixture above gives no `is not assignable` row; a call with swapped argument types
(`Task.new('n', 'not a function', {})`) still reports on the argument of `task`.
