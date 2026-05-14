# Implementation Plan: SYNTAX-07-09 Per-Category Settings

## Phase 1: Data Model & Persistence
- [ ] Create `LuaInlayHintsSettings.kt` as an application-level service.
- [ ] Implement `State` with all flags (Local Var, Parameter, Return Type, Method Chain, Respect Annotations, Large File Threshold).
- [ ] Register the service in `plugin.xml`.

## Phase 2: UI Implementation
- [ ] Create `LuaInlayHintsConfigurable` with a Swing panel for checkboxes and the threshold input.
- [ ] Register the configurable as an `inlayHintsConfigurable` for Lua in `plugin.xml`.
- [ ] Implement the `isModified`, `apply`, and `reset` logic.
- [ ] Implement the editor refresh trigger in `apply`.

## Phase 3: Provider Integration
- [ ] Add file size check in `LuaTypeInlayHintProvider.collectFromElement`.
- [ ] Update `LuaTypeInlayHintProvider` to check category flags for each hint type.
- [ ] Ensure `hasExplicitAnnotation` logic respects the `respectAnnotations` toggle.

## Phase 4: Verification
- [ ] Test each toggle independently in the settings UI.
- [ ] Verify large file threshold prevents hints on files exceeding the limit.
- [ ] Verify persistence across IDE restarts.
- [ ] Verify that toggling a setting immediately updates the editor.
