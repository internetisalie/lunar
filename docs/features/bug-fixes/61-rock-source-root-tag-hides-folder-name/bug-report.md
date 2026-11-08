---
id: "BUG-61"
title: "A rock source root folder in the Project view shows only its tag, not its name"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-61: The rock source root tag hides the folder name

## 1. Reproduction
Open a project whose rockspec maps modules into subfolders (the `test` project's `test-dev-1.rockspec`
maps modules into `cases/` and `examples/`) and look at those folders in the Project view (seen 2026-11-08).

## 2. Expected vs Actual Behavior
- **Expected**: `cases  rock source root`.
- **Actual**: a blank name followed by the gray ` rock source root` tag.

## 3. Root Cause
`LuaRockSourceRootDecorator.decorate` added the tag with `PresentationData.addText`. `NodeRenderer`
draws only `coloredText` once it is non-empty, and falls back to `presentableText` only when it is
empty, so on a directory node with no colored fragments the tag replaced the name. The project root
kept its name because its node already carries colored fragments.

## 4. Fix Strategy
When `coloredText` is empty, add the presentable text (and location string, if any) as fragments
before the tag.

## 5. Test Strategy
`LuaRockSourceRootDecoratorTest` now gives the node a name and asserts the fragments are the name
followed by the tag, not the tag alone.
