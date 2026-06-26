---
id: "BUG-353"
title: "package.path member resolution and hover bugs"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-353: package.path member resolution and hover bugs

> **Resolution status (2026-06-24):** Problems 2, 3, & 4 are **fixed**;
> Problem 1 (assignment type warning) remains **open** (requires reproduction). See §4 Resolution.

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

- **Problem 2 (Base hover documentation)** — ✅ **FIXED**:
  - **Expected**: Hovering over `package` should show the documentation for the `package` library/table class (as annotated by `---PACKAGE LIBRARY` / `@class package` in `package.lua`).
  - **Actual**: `package` hover shows the documentation for the function `package.loadlib`.
  - **Note**: The member-segment fix below does *not* cover this — the base `package` is not a dotted
    member segment, so the doc provider still does a bare-name `getElements(KEY, "package")` lookup and
    returns the first `package.*` member function.

- **Problem 3 (Member hover documentation)** — ✅ **FIXED**:
  - **Expected**: Hovering over `path` in `package.path` should show the documentation for the `path` field of the `package` class.
  - **Actual (was)**: `path` hover shows the documentation for the first function in the separate `path` package/module (e.g. `path.exists` or `path.join`).
  - **Now**: shows `package.path : string` + the field's description (NAV-12).

- **Problem 4 (Member Go to Definition)** — ✅ **FIXED**:
  - **Expected**: "Go to Definition" on `path` in `package.path` should navigate to the declaration of the `path` field of the `package` table/class in the standard library.
  - **Actual (was)**: "Go to Definition" lists all functions in the separate `path` package/module.
  - **Now**: navigates to the `package.path = ""` field declaration (NAV-12).

## 3. Context / Environment

- **Relevant Files**:
  - `src/main/resources/runtime/standard/lua-5.4/package.lua` (defines `package` and `package.path` standard library bindings)
  - `src/main/kotlin/net/internetisalie/lunar/lang/LuaNameReference.kt` (handles reference resolution)
  - `src/main/kotlin/net/internetisalie/lunar/lang/doc/LuaDocumentationTargetProvider.kt` (handles documentation resolution)

- **Other Notes (Diagnostic clues)**:
  - **Dotted indexing in stubs**: `LuaFuncStubElementType.indexStub` indexes dotted names like `package.loadlib` under the base name (`"package"`). When a reference to `"package"` is resolved, the search in `LuaGlobalDeclarationIndex` returns all such member functions, leading to the hover showing `package.loadlib`.
  - **Bare-name resolution for members**: When resolving `path` in `package.path`, reference resolution falls back to bare global index lookups for `"path"`. This matches functions in the unrelated `path` library.
  - **Member resolution via type engine**: Member references (like `path` in `package.path`) should ideally be resolved via the type of their receiver (`package`), mapping to their declaring `sourceElement` in the type engine, rather than through bare-name global index searches.

## 4. Resolution

### Fixed (2026-06-24)
- **Problem 4 — member Go-to** (`925a694e`, then `6909b9df`): member segments now resolve
  only through the receiver-qualified name; a qualified-name **member-field index** (`LuaMemberFieldIndex`,
  NAV-12) navigates `package.path` to its `package.path = ""` declaration instead of every `path.*` function.
- **Problem 3 — member hover** (`07aa8dec`, then NAV-12 `b9a81d79`): the doc provider stopped grabbing an
  arbitrary same-short-name symbol and now renders the field's own `---@type`/doc (`package.path : string`).

This was scoped and built as feature **NAV-12 Member Field Resolution**
(`docs/features/navigation/12-member-field-resolution/`), elevating `NAV-01-03` (Table Fields) to **Full**.
Verified live in the containerized GoLand.

- **Problem 2 — base `package` hover** (`f5c5bb1b`): base identifiers that are not member segments now resolve to their matching class/type declarations using `LuaCatsTypeNameIndex` lookup.

### Still open
- **Problem 1 — `nil value is not assignable to string`** false positive. Untouched here (a separate fix
  removed its *duplicate* reporting; the inference itself is unchanged). Did not reproduce in the
  containerized GoLand, where the bundled stub types `package.path` as `string` — likely environment- or
  stdlib-load-order-dependent; needs its own reproduction before planning.
