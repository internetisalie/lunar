---
id: "BUG-500"
title: "A require of a module name without a dot resolves to any init.lua in the project, unrelated or not"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-500: `require "x"` falls back to the first `init.lua` anywhere

Found by the review of [[MAINT-37]] Phase 3b ([[BUG-496]]), while explaining why ZeroBrane's
`unresolvedRequires` went from 3 to 19 once penlight's module root stopped leaking into it.

## 1. What happens

`resolveModuleCandidates` (`src/main/kotlin/net/internetisalie/lunar/lang/path/LuaModuleFileResolver.kt`)
falls back to `FilenameIndex`. It collects every `<last segment>.lua` and **every `init.lua`** in
`GlobalSearchScope.allScope(project)`, and then keeps a file when:

```kotlin
path.endsWith(expectedPathPart) || path.endsWith(expectedInitPathPart) || !moduleName.contains('.')
```

For a module name with no dot, the third disjunct is always true. Every `init.lua` in the project
and its libraries is accepted, whatever directory it sits in, and so is every `<name>.lua` in any
directory. The path-tail check that the KDoc describes ("filtered by the expected path tail")
applies only to dotted names.

## 2. Evidence (read, not executed)

- In the corpus sweep, with penlight's tree indexed as a library through the BUG-496 leak, 16 of
  ZeroBrane's 19 `require`s resolved. All 16 are names without a dot, and the only `init.lua` in
  that scope was penlight's `lua/pl/init.lua`. The 3 that stayed unresolved are the dotted
  `luainspect.*` requires (`src/editor/inspect.lua:100-102` in the ZeroBrane checkout). Once the
  leak was fixed, nothing provided an `init.lua` and all 19 became unresolved.
- Two callers take the first candidate:
  - `LuaRequireReference.resolve()` (`firstOrNull()`): <kbd>Ctrl+B</kbd> on the string.
  - `LuaTypeManagerImpl` (`:298`): the module's inferred type.

  So a wrong match is both a wrong navigation target and wrong types for `local m = require "x"`.

## 3. Reproduction (to be executed by the fix)

A light fixture with `pl/init.lua` (`return { penlight = true }`), no `mobdebug.lua` anywhere, and
`local m = require "mobdebug"`:
- **Expected:** the require reference resolves to nothing.
- **Predicted actual:** it resolves to `pl/init.lua`, and `m` is typed as that table.

A second case without a dot but with a match by name, `lib/other/json.lua` and `require "json"`, is
worth asserting either way. Lua's default `package.path` (`./?.lua;./?/init.lua;…`) would not find
it.

## 4. Related

- The corpus `$comment` in `tooling/corpus/corpus.json` says each member's requires resolve relative
  to its checkout root. That is not true for ZeroBrane, whose `package.path` is
  `lualibs/?.lua;lualibs/?/?.lua;…` (`src/main.lua:46`) and whose `lualibs/` is outside the swept
  roots. So ZeroBrane's `unresolvedRequires` measures nothing. Fixing this bug is not expected to
  move it.
- How wide the effect is on the other corpus members' resolved counts has not been measured.
