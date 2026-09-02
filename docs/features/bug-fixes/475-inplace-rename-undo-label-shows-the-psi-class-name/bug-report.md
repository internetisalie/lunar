---
id: "BUG-475"
title: "The in-place rename's undo entry names the PSI implementation class, not the symbol"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-475: `Undo Renaming Lua Name Ref Impl con…`

Carved out of [[BUG-471]], whose restore-failure claim did not reproduce and is closed. This half
did reproduce, on the supported VM-native path.

## Reproduction

In a Lua file in a running IDE:

```lua
local config = 2
print(config)
```

1. Caret on the `config` declaration, <kbd>Shift+F6</kbd> (the **in-place** route), type `renamed`,
   <kbd>Enter</kbd>.
2. Open **Edit**.

## Expected

The undo entry names the symbol the user renamed, the way the dialog route already does — e.g.
`Undo Renaming local variable config…`.

## Actual

`Undo Renaming Lua Name Ref Impl con…` — the PSI implementation class name (`LuaNameRefImpl`),
de-camel-cased by the platform.

Measured 2026-09-02, VM-native `runIde`, GoLand `GO-2026.1.3`, plugin load confirmed fresh.
The **dialog route is unaffected** and read `Undo Renaming global variable gconf…` in the same
session, which is the control that makes this specific to the in-place path.

## Root cause

The platform derives that string from `ElementDescriptionUtil.getElementDescription(element,
UsageViewTypeLocation)`. Nothing in this plugin supplies an `ElementDescriptionProvider` for the
`LuaNameRef` composite, so the platform falls back to de-camel-casing the implementation class name.
`grep -rn ElementDescriptionProvider src/main` returns no registration — only a comment in
`LuaRenameProcessor.kt:314`.

The dialog route escapes it because it renames through a different element whose description the
platform can already produce.

## Fix strategy

Register an `ElementDescriptionProvider` that, for a `LuaNameRef`, returns the declaration kind
`LuaDeclarationSite.kindOf` already computes — the same vocabulary the conflict messages use
(`local variable`, `parameter`, `global variable`, `for variable`, `local function`) — for
`UsageViewTypeLocation`, and the identifier text for `UsageViewShortNameLocation`.

`LuaDeclarationKind` already carries a display string (`LuaDeclarationSite.kt:27`,
`METHOD_FUNCTION("global function", false)`), so the wording exists and should not be re-invented.

## Verification

A unit test can assert `ElementDescriptionUtil.getElementDescription(nameRef, UsageViewTypeLocation.INSTANCE)`
directly — no IDE needed for the logic. **The undo-entry text itself is a UI surface**, so the
`verify-in-ide` gate applies before closing: rename in-place, open Edit, read the entry.

Falsifying mutation: unregister the provider in `plugin.xml` and the assertion returns
`Lua Name Ref Impl`.

## Fixed and verified 2026-09-02

`LuaElementDescriptionProvider` (`refactoring/`) answers `UsageViewTypeLocation` with
`LuaDeclarationKind.usageViewType` and `UsageViewShortNameLocation` with the identifier text,
returning null for anything that is not a declaration site so the platform keeps its default.
Registered as `<elementDescriptionProvider>` in `plugin.xml`.

**Unit gate:** `LuaElementDescriptionProviderTest` — local/global/parameter wording, plus an
assertion that no description contains `Lua Name Ref`. Mutation-proved: commenting out the
`plugin.xml` registration turns **all four** red, including the leak assertion.

**Live gate** (the undo entry is a UI surface, so the unit test is not sufficient), VM-native
`runIde`, GoLand `GO-2026.1.3`, sandbox log **truncated before launch** so the plugin-load line is
unambiguously this run's:

| | Edit ▸ Undo entry |
| :-- | :-- |
| before | `Undo Renaming Lua Name Ref Impl con…` |
| after | **`Undo Renaming local variable config…`** |

The dialog route was already correct and is unchanged.

Full suite 2896/0/0/1 across 465 files.
