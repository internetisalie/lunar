---
id: "BUG-58"
title: "The packages browser shows \"Could not load metadata\" for every rock that is not installed"
type: "bug"
parent_id: "BUG"
status: "done"
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

## 4. Root Cause
`PackageDetailPane.fetchMetadata` had one source of metadata, `luarocks show`, which answers only for
installed rocks. On its failure `applyMetadata` set the description alone, so License and Homepage
kept "(loading)".

## 5. Fix
- `LuaRocksMetadataService.describe` tries `luarocks show`, then the rocks server: it runs
  `luarocks download --rockspec <name> [<version>]` in a temporary directory with the resolved
  LuaRocks and reads the downloaded rockspec with `RockspecMetadataReader`.
- `RockspecMetadataReader` evaluates the rockspec with `LuaDebugValueParser`, which reads literal
  assignments over the syntax tree and executes nothing. A downloaded rockspec is untrusted Lua, so
  it is never run, and `RockspecBridge` (which runs Lua, BUG-50) is not used.
- When neither source answers, License and Homepage read "(unknown)".
- Popular rows are marked installed when the project tree holds the rock, so they offer Uninstall.

## 6. Test
- `RockspecMetadataReaderTest`: a server rockspec's summary, license, homepage, dependencies and
  modules; a long-bracket description; a rockspec that calls `os.execute` and `io.open` is read
  without running them; text with no `package` and `version` is rejected.
- `RockspecDownloadArgsTest`: a blank version downloads the latest.
- `LuaRocksBrowserModelTest`: a popular rock already in the tree is shown installed.
