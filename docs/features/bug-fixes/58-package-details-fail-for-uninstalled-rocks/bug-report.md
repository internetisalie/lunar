---
id: "BUG-58"
title: "The packages browser shows \"Could not load metadata\" for every rock that is not installed"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-58: Package details fail for rocks that are not installed

## 1. Reproduction
In the *LuaRocks Packages* tool window, Marketplace tab, select any package that is not installed in
the project tree, from the popular list or from a search.

## 2. Expected vs Actual Behavior
- **Expected** (ROCKS-16 detail pane): the summary, description, license, homepage and dependencies
  of the selected package.
- **Actual**: the description reads "Could not load metadata.". `PackageDetailPane.fetchMetadata`
  calls `LuaRocksMetadataService.show`, which runs `luarocks show --porcelain <name>`. `luarocks show`
  reads only installed rocks: on 2026-11-08, LuaRocks 3.8.0 answered `luarocks show --porcelain argparse`
  with "Error: cannot find package argparse" and exit 1.

## 3. Context / Environment
- Found fixing BUG-56. A source for an uninstalled rock's metadata is its rockspec on luarocks.org
  (for example `luarocks download --rockspec <name>`, or the server's rockspec URL), which the browser
  does not yet use.
- Popular rows are also always shown as not installed (`LuaRocksBrowserModel.publishPopular` builds
  them with `installed = false`), so an installed popular package offers Install rather than
  Uninstall.
