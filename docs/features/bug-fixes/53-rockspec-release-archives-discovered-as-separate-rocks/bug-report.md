---
id: "BUG-53"
title: "Every historical rockspec of one rock is discovered as a separate project rock"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-53: Every historical rockspec of one rock is discovered as a separate project rock

## 1. Reproduction
Open `~/Documents/src/lua/test`. Its `corpus/luacheck/` holds `luacheck-dev-1.rockspec` plus a
`rockspecs/` directory of 47 released versions (`luacheck-0.1.0-1.rockspec` … ). The *LuaRocks
Dependencies* tool window shows 48 roots, every one labelled `luacheck`.

## 2. Expected vs Actual Behavior
- **Expected**: one root per rock. Several rockspecs for the same package in one project are versions
  of one rock: use the development rockspec (`scm`/`dev`), or else the highest version, and ignore a
  release archive such as `rockspecs/`.
- **Actual**: `LuaRockspecDiscoveryService` returns every included `.rockspec`
  (`LuaRockspecDiscoveryService.kt:85`), and `LuaRocksDependencyResolver.resolveAll` builds one root
  per rockspec.

## 3. Context / Environment
- ROCKS-09-01 specifies discovering "every source `.rockspec`". Same-package grouping was never
  specified, so this is a gap in that requirement as well as in the code.
- Discovery feeds module resolution (ROCKS-05) and the rocks browser as well. Check each consumer's
  expectation before changing the service, rather than only the tree.
