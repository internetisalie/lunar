---
id: "BUG-522"
title: "Both checkboxes on the Lua settings page do nothing: remove them"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-522: "Enable type inference" and "Add additional completions" are inert

Found in the third-pass review (`docs/review.md` §6, findings #106 and #107). Verified by grep over `src/`;
**not run**.

## 1. Reproduction (read, not run)

Open Settings, Languages & Frameworks, Lua. Toggle "Enable type inference" or "Add additional completions" and
apply. Predicted: the persisted XML changes and no behaviour does.

Evidence: a repo-wide, case-insensitive `git grep` for `enableTypeInference` and `includeAllFieldsInCompletions`
finds them only in `LuaApplicationSettings.State` (`LuaApplicationSettings.kt:35-36`), `LuaApplicationSettingsPanel.kt`
and settings round-trip tests. No completion, type-engine, inlay or annotator code reads either.

## 2. Expected vs actual

- **Expected:** no setting is offered that does not change behaviour.
- **Actual (by reading):** both checkboxes are persisted and read by nothing. MAINT-12, TOOLING-06
  ("none (unchanged behavior)") and TOOLING-08 carried the fields forward and tested serialization only, so none
  could catch it.

## 3. Root cause (read, not executed)

The consumers were never wired, or were removed, while the settings and their serialization tests stayed.
Related P3 (#107): `LuaApplicationSettingsPanel.getData` is a getter-named method with a side effect, calling
`dropResolveCaches()` on every open project on every Apply while the box is ticked. It disappears with the panel content.

## 4. Decision

**Remove both settings.** The user believes the behaviour is implemented elsewhere, so the expected outcome is
removal, not wiring. No migration is needed: there is no external install base.

## 5. Fix strategy

1. Delete the two checkboxes and their UI in `LuaApplicationSettingsPanel.kt`.
2. Delete `enableTypeInference` and `includeAllFieldsInCompletions` from `LuaApplicationSettings.State` and any
   accessors.
3. Delete the serialization tests that exercise only these two fields.
4. Verify by case-insensitive `git grep` over `src/` and `META-INF/` that nothing reads either name afterwards,
   and that no resource bundle key is left orphaned.
5. The configurable is still the parent of two child pages (`plugin.xml:588,593,599`). Keep it, with an empty
   landing page (a Kotlin UI DSL `BoundConfigurable` with no state of its own, the idiom of the TOOLING-06 and
   TOOLING-08 pages).

MAINT-41's proposed "every persisted setting has a reader outside `settings/`" check guards the class.
