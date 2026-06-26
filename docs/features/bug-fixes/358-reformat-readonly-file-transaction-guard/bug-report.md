---
id: "BUG-358"
title: "TransactionGuard write-unsafe context exception when reformating a read-only file"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-358: TransactionGuard write-unsafe context exception when reformating a read-only file

## 1. Reproduction

1. Open a read-only `.lua` file in the IDE.
2. Trigger the "Reformat Code" action (with "Optimize Imports" option checked) or trigger the action on a read-only file where code style settings computation is triggered asynchronously.
3. The IDE prompts a "Clear Read-Only Status" modal dialog.
4. During or after the dialog wrapper presentation, a VFS refresh triggers inside the event pumping loop.
5. A `SEVERE - #c.i.o.a.TransactionGuardImpl - Write-unsafe context!` exception is logged.

## 2. Expected vs Actual Behavior

- **Expected**: The file's read-only status is cleared or the prompt is handled gracefully without throwing `SEVERE` TransactionGuard exceptions in the IDE log.
- **Actual**: The modal dialog is shown from a write-unsafe context (`CodeStyleCachedValueProvider$AsyncComputation.notifyCachedValueComputed`), causing any subsequent nested event loop tasks (such as VFS refresh on application/frame activation) to throw TransactionGuard assertions.

## 3. Context / Environment

- **IDE version**: GoLand 2026.1.3 Build #GO-261.25134.147
- **Relevant Files**: This is an upstream IntelliJ Platform issue, not local to Lunar. It involves:
  - `com.intellij.application.options.codeStyle.cache.CodeStyleCachedValueProvider`
  - `com.intellij.codeInsight.actions.FileInEditorProcessor`
  - `com.intellij.openapi.vfs.newvfs.RefreshSessionImpl`
  - `com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl`
- **Other Notes**:
  - The stack trace indicates `CodeStyleCachedValueProvider` dispatches `notifyCachedValueComputed` to the EDT using `ModalityState.any()` but outside a TransactionGuard write-safe context, which executes the scheduled `OptimizeImportsProcessor` in a write-unsafe context.
