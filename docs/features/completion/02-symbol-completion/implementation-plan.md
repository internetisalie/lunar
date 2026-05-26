---
id: "COMP-02-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "COMP-02"
status: "done"
priority: "high"
folders:
  - "[[features/completion/02-symbol-completion/requirements|requirements]]"
---

# COMP-02: Basic Symbol Completion Implementation Plan

## Phases

### Phase 1: Scope Processing [Must]
- Implement `LuaScopeProcessor` to collect local variables and parameters.
- Handle block-based scoping rules.
- **Verification**: Unit tests for scope resolution in various nested block scenarios.

### Phase 2: Completion Provider [Must]
- Implement `LuaSymbolCompletionProvider`.
- Register provider in `plugin.xml`.
- Integrate `LuaScopeProcessor` into the provider's `addCompletions` method.
- **Verification**: Integration tests triggering completion in the editor.

### Phase 3: Global Symbols (File-level) [Should]
- Extend processor to include top-level (global) declarations within the same file.
- **Verification**: Test case for global variables in the same file.

### Phase 4: Iconography & UI [Could]
- Add distinctive icons for different symbol types.
- Add type information (if easily available from PSI) to the lookup element tail text.
- **Verification**: Manual UI check.

## Verification Tasks

- [ ] [Must] Implement `LuaScopeProcessorTests`.
- [ ] [Must] Implement `LuaSymbolCompletionContributorTests`.
- [ ] [Should] Manual verification of shadowing behavior.
- [ ] [Could] Verify icons in the IntelliJ completion popup.
