---
id: "BUG-528"
title: "PublishRockAction reads `VIRTUAL_FILE` on the EDT in `update()`, and the IDE reports it as an error"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-528: PublishRockAction reads `VIRTUAL_FILE` on the EDT

## 1. Reproduction

Seen in MAINT-21's Phase 3 live check, on GoLand 2026.2.3 and 2026.1.3 alike.

1. Open any Lua project in a sandbox IDE with Lunar installed.
2. Right-click a file in the Project view.

## 2. Expected

The context menu opens with no IDE error.

## 3. Actual

The IDE shows an "IDE error occurred" balloon. `idea.log` records:

```
SEVERE - #c.i.o.a.i.PreCachedDataContext - 'virtualFile' is requested on EDT by PublishRockAction#presentation@ProjectViewPopup (net.internetisalie.lunar.rocks.publish.PublishRockAction) "Lua.Rocks.Publish". See ActionUpdateThread javadoc.
	at net.internetisalie.lunar.rocks.publish.PublishRockAction.update(PublishRockAction.kt:34)
```

## 4. Notes

- `PublishRockAction` does not override `getActionUpdateThread()`, so `update()` runs on the EDT,
  where `CommonDataKeys.VIRTUAL_FILE` is not pre-cached.
- Four other classes override `update(` without `getActionUpdateThread()`: `RunMatrixAction`,
  `LuaCovReportImportAction`, `LuaConsoleAction` and `LuaHierarchyNodeDescriptor`, which is not an
  action. Whether each of the three actions reads slow data keys has not been checked.
- Root cause and fix strategy are not established here; plan with the `plan-bug` skill.
