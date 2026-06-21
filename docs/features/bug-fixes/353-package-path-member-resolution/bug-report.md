---
id: "BUG-353"
title: "package.path member resolution and hover bugs"
type: "bug"
status: "todo"
priority: "medium"
folders:
  - "[[features]]"
---

# BUG-353: package.path member resolution and hover bugs

## 1. Reproduction

Open a Lua file in the plugin environment and paste the following snippet:

```lua
-- Add source trees
package.path = package.path ..
    ";./?/init.lua;./?.lua" ..
    ";./luacheck/src/?/init.lua;./luacheck/src/?.lua"
```

1. Observe type annotations/errors on the second `package.path` (the one on the right-hand side of the assignment).
2. Hover over the base `package` name in `package.path`.
3. Hover over the member `path` in `package.path`.
4. Trigger "Go to Declaration/Definition" on the member `path` in `package.path`.

## 2. Expected vs Actual Behavior

- **Problem 1 (Assignment type warning)**:
  - **Expected**: `package.path` is inferred as a `string` (since it is declared as `package.path = ""` with type `string` in `package.lua`). The concatenation expression `package.path .. ...` returns a `string`, which is assignable to `package.path`. No errors should be shown.
  - **Actual**: The second `package.path` shows an error: `"nil value is not assignable to string"`.

- **Problem 2 (Base hover documentation)**:
  - **Expected**: Hovering over `package` should show the documentation for the `package` library/table class (as annotated by `---PACKAGE LIBRARY` / `@class package` in `package.lua`).
  - **Actual**: `package` hover shows the documentation for the function `package.loadlib`.

- **Problem 3 (Member hover documentation)**:
  - **Expected**: Hovering over `path` in `package.path` should show the documentation for the `path` field of the `package` class.
  - **Actual**: `path` hover shows the documentation for the first function in the separate `path` package/module (e.g. `path.exists` or `path.join`).

- **Problem 4 (Member Go to Definition)**:
  - **Expected**: "Go to Definition" on `path` in `package.path` should navigate to the declaration of the `path` field of the `package` table/class in the standard library.
  - **Actual**: "Go to Definition" lists all functions in the separate `path` package/module.

## 3. Context / Environment

- **Relevant Files**:
  - `src/main/resources/runtime/standard/lua-5.4/package.lua` (defines `package` and `package.path` standard library bindings)
  - `src/main/kotlin/net/internetisalie/lunar/lang/LuaNameReference.kt` (handles reference resolution)
  - `src/main/kotlin/net/internetisalie/lunar/lang/doc/LuaDocumentationTargetProvider.kt` (handles documentation resolution)

- **Other Notes (Diagnostic clues)**:
  - **Dotted indexing in stubs**: `LuaFuncStubElementType.indexStub` indexes dotted names like `package.loadlib` under the base name (`"package"`). When a reference to `"package"` is resolved, the search in `LuaGlobalDeclarationIndex` returns all such member functions, leading to the hover showing `package.loadlib`.
  - **Bare-name resolution for members**: When resolving `path` in `package.path`, reference resolution falls back to bare global index lookups for `"path"`. This matches functions in the unrelated `path` library.
  - **Member resolution via type engine**: Member references (like `path` in `package.path`) should ideally be resolved via the type of their receiver (`package`), mapping to their declaring `sourceElement` in the type engine, rather than through bare-name global index searches.
