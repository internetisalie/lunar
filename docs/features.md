# Lunar Project Features

This document serves as the index for the functional and technical requirements of the Lunar Lua plugin. Requirements are categorized into specialized sub-documents for better maintainability.

## Core Documentation
- [[TYPE] Type System Requirements](features/spec/type/requirements.md)
- [[COMP] Code Completion Requirements](features/spec/completion/requirements.md)
- [[NAV] Code Navigation Requirements](features/spec/navigation/requirements.md)
- [[INSP] Inspections & Diagnostics Requirements](features/spec/inspections/requirements.md)
- [[ANALYSIS] Static Analysis Requirements](features/spec/analysis/requirements.md)
- [[DEBUG/RUN] Debugging & Execution Requirements](features/spec/debug/requirements.md)
- [[SYNTAX] Syntax & Editor Requirements](features/spec/syntax/requirements.md)
- [[FORMAT] Formatting Requirements](features/spec/formatting/requirements.md)
- [[DOC] Documentation Requirements](features/spec/documentation/requirements.md)
- [[TOOL] Tool Inventory Management](features/spec/tool/requirements.md)
- [[ROCKS] LuaRocks Integration](features/spec/rocks/requirements.md)
- [[REFACT/INTENT] Refactoring & Intentions Requirements](features/spec/refactoring/requirements.md)
- [Technical Non-Functional Requirements](features/non-functional.md)

## Requirement Classification (MoSCoW)

To guide the development of Lunar, requirements are classified using the MoSCoW method:

- **Must Have (M):** Fundamental features required for a "Beta" release. These are non-negotiable for a functional Lua development experience.
- **Should Have (S):** Important features that significantly enhance the developer experience but are not strictly required for the initial launch.
- **Could Have (C):** Non-essential features for specialized use cases or future polish.
- **Won't Have (W):** Explicitly out of scope for the current development phase (e.g., features legacy plugins supported that are no longer relevant for modern IntelliJ).
