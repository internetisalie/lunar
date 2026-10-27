---
id: "BUG-540"
title: "Import LuaCov Report accepts any file on GoLand 2026.2: the universal file chooser ignores the descriptor's filter"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-540: Import LuaCov Report accepts any file on 2026.2

Found by MAINT-44's HV-04 live check on GoLand 2026.2.3 (`runIde`).

## 1. Reproduction
1. In GoLand 2026.2, open a project that contains `main.lua`.
2. *Find Action* → *Import LuaCov Report…*.
3. Choose `main.lua` and click *OK*.

## 2. Expected vs Actual Behavior
- **Expected**: only LuaCov report files (`*.out`) can be imported.
- **Actual**: the chooser lists every file and *OK* is enabled for `main.lua` or a directory. The IDE
  log then reads `Coverage from file /…/main.lua loaded successfully.`

Evidence: `~/.cache/claude-scratch/lunar/maint-44/impl/hv/hv04-ok.png`.

## 3. Surface
`coverage/LuaCovReportImportAction.kt`.

## 4. Root Cause
GoLand 2026.2 enables `universal.file.chooser.is.enabled`, so `FileChooser.chooseFile` opens
`UniversalFileChooser` (`LocalFileChooserFactory`, the *Local / Docker* dialog). That dialog never
consults the descriptor: it calls none of `isFileVisible`, `isFileSelectable`, `matchesFilters` or
`validateSelectedFiles`, and returns whatever is selected. Both the old `isFileSelectable` override
and MAINT-44's `withFileFilter` are therefore ignored there; the classic `FileChooserDialogImpl`
honours them.

## 5. Fix Strategy
The action checks the chosen file itself: `LuaCovReportImportAction.importReport` refuses anything
that is not a non-directory `*.out` file with an error dialog, and imports only otherwise. The
descriptor keeps its filter for choosers that honour it.

## 6. Test Strategy
`LuaCoverageReportTest.testImportReportRejectsANonReportFileBUG540` calls `importReport` with
`main.lua` and asserts it returns false after showing `main.lua is not a LuaCov report file.`

## 7. Verification
- Red with the check disabled, green with it.
- Live on 2026.2.3: `main.lua` and a directory are refused with the error dialog;
  `luacov.stats.out` imports (`hv04-reject-crop.png`, `hv04-out2.png`).
