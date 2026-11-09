---
id: "BUG-54"
title: "The dependency tree flags each project rock and the `lua` interpreter constraint as missing packages, and its roots cannot be told apart"
type: "bug"
parent_id: "BUG"
status: "done"
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

## 4. Fix
- `DependencyNode.kind` (`DependencyKind`) distinguishes `PROJECT`, `ROCK` and `RUNTIME` nodes.
- `LuaRocksDependencyResolver` builds a root with its rockspec's version and its path relative to
  the content root (`location`). It resolves `lua` to the configured runtime's `luaVersion`.
- `VersionConflictEngine` never checks a project rock. It checks `lua` only when a runtime is
  configured.
- `DependencyLabels` gives the tree and the inspector one wording:
  - a root shows `name version — path`;
  - `lua` without a runtime shows *no runtime configured*.

## 5. Test
- `LuaRocksDependencyResolverForestTest` covers a rockspec declaring `lua >= 5.1` and `ghost`:
  - the root carries `1.2-1` and its path, with no conflict;
  - `lua` resolves to the runtime's `5.4` and holds;
  - `ghost` is still missing.
- `DependencyKindTest` covers:
  - `lua` with no runtime is not missing, and `< 5.4` against 5.4 is a conflict;
  - an unversioned project rock is not missing;
  - two same-named roots render differently.
