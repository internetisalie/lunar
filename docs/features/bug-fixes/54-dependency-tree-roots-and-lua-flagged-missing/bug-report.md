---
id: "BUG-54"
title: "The dependency tree flags each project rock and the `lua` interpreter constraint as missing packages, and its roots cannot be told apart"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-54: The dependency tree flags each project rock and the `lua` constraint as missing packages

## 1. Reproduction
Open `~/Documents/src/lua/test` and the *LuaRocks Dependencies* tool window. Select a root
`luacheck` node; the inspector shows *Version: not installed*, *Scope: direct*, and the conflict
"'luacheck' is required but not installed". Its child `lua (missing)` comes from the rockspec's
`"lua >= 5.1"` entry.

## 2. Expected vs Actual Behavior
- **Expected**:
  1. A root is a rock being developed in the project. It shows the rockspec's own version and its
     path, and it is never flagged as missing or as needing installation.
  2. The `lua` entry is checked against the configured runtime's version (satisfied, violated, or
     no runtime configured), not looked up as an installed rock.
  3. Roots can be told apart: each shows its version and its rockspec path.
- **Actual**: all three fail.

## 3. Root Cause
- `LuaRocksDependencyResolver.resolveOne` (`LuaRocksDependencyResolver.kt:37-45`) builds the root as
  `DependencyNode(rootData.packageName, isTransitive = false)`, with no `resolvedVersion`, although
  `rootData` carries the rockspec's version.
- `VersionConflictEngine.annotate` visits the root too, and `annotateNode`
  (`VersionConflictEngine.kt:20-32`) treats any node with a null `resolvedVersion` as
  `MISSING_DEPENDENCY`.
- `expand` passes `lua` through `DependencySpec.parse` like any other package, so it is looked up
  among installed rocks.

## 4. Test Strategy
- A project rockspec declaring `dependencies = { "lua >= 5.1", "x" }`, with `x` not installed. The
  root carries the rockspec's version and no conflict. `x` is missing. `lua` is evaluated against
  the runtime.
- Two roots in different directories render distinguishable labels.
