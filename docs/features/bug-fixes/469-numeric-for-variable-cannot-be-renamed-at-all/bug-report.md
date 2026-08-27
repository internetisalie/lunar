---
id: "BUG-469"
title: "Shift+F6 on a numeric-`for` variable reaches no rename handler at all"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-469: a numeric-`for` control variable cannot be renamed

Found 2026-08-25 by [[REFACT-07]]'s `REFACT-07-00-05` (DR-05) data-context probe. Not caused by
REFACT-07; a shipped defect the probe surfaced while measuring something else.

## Reproduction

```lua
for i = 1, 10 do
    print(i)
end
```

Place the caret on `i` in the `for` header and press <kbd>Shift+F6</kbd>.

## Expected

The rename dialog opens and renaming `i` updates the control variable and its uses in the loop
body, as it does for any other Lua local.

## Actual

Nothing happens. No dialog, no error, no balloon.

## Measured

DR-05 probes **f / f2 / f3**, read from a real editor data context
(`DataManager.getInstance().getDataContext(myFixture.editor.contentComponent)` →
`PsiElementRenameHandler.getElement(context)`) with nothing injected:

- `supplied` is **null**
- `RenameHandlerRegistry.getRenameHandlers(context)` returns an **empty list** — not the default
  `PsiElementRenameHandler`, which every other non-label caret falls back to

Every other declaration and usage caret measured in the same run returned a non-null element and a
handler. Evidence: `docs/features/refactoring/07-inplace-rename/dr-05-evidence/measured-rows.txt`.

## Scope note

The probe measured the **data context only**; the end-to-end Shift+F6 flow was not driven. The
symptom is inferred from the empty handler list — a handler list with no entries cannot rename —
but the user-visible behaviour has not itself been observed. Confirm before fixing.

## Why it is separate from REFACT-07

[[REFACT-07]] delivers in-place rename for declarations the data context already supplies. This
caret supplies nothing, so no in-place work reaches it — the gap is upstream, in what the platform
resolves at a numeric-`for` control variable, and is equally a defect for the ordinary dialog path
that [[REFACT-01]] ships. Fixing it likely means a `LuaDeclarationSite` classification or a
reference at that position, not a rename change.
