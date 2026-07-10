---
id: "FEATURES"
title: "Lunar Project Features"
type: "spec"
priority: "medium"
folders:
  - "[[features]]"
---

# Lunar Project Features

This document serves as the index for the functional and technical requirements of the Lunar Lua plugin. Requirements are categorized into specialized sub-documents for better maintainability.


## Core Documentation
- [[TYPE] Type System Requirements](features/type/requirements.md)
- [[COMP] Code Completion Requirements](features/completion/requirements.md)
- [[NAV] Code Navigation Requirements](features/navigation/requirements.md)
- [[INSP] Inspections & Diagnostics Requirements](features/inspections/requirements.md)
- [[ANALYSIS] Static Analysis Requirements](features/analysis/requirements.md)
- [[DEBUG/RUN] Debugging & Execution Requirements](features/debug/requirements.md)
- [[SYNTAX] Syntax & Editor Requirements](features/syntax/requirements.md)
- [[EDITOR] Editor Ergonomics & Structural Editing](features/editor/requirements.md)
- [[FORMAT] Formatting Requirements](features/formatting/requirements.md)
- [[DOC] Documentation Requirements](features/documentation/requirements.md)
- [[TOOL] Tool Inventory Management](features/tool/requirements.md)
- [[TOOLING] Unified Lua Toolchain Management](features/tooling/requirements.md)
- [[ROCKS] LuaRocks Integration](features/rocks/requirements.md)
- [[SCHEMA] Schema-Driven Data Files](features/schema/requirements.md)
- [[REFACT/INTENT] Refactoring & Intentions Requirements](features/refactoring/requirements.md)
- [[MAINT] Maintenance & Refactoring](features/maint/requirements.md)
- [[TARGET] Target Configuration](features/target/requirements.md)
- [[REDIS] Redis & Valkey Integration](features/redis/requirements.md)
- [[AI] AI-Assisted Development](features/ai/ai-product-requirements.md)
- [[BUG] Bugfixes & Stability](features/bug-fixes.md)
- [Technical Non-Functional Requirements](features/non-functional.md)

## Requirement Classification (MoSCoW)

To guide the development of Lunar, requirements are classified using the MoSCoW method:

- **Must Have (M):** Fundamental features required for a "Beta" release. These are non-negotiable for a functional Lua development experience.
- **Should Have (S):** Important features that significantly enhance the developer experience but are not strictly required for the initial launch.
- **Could Have (C):** Non-essential features for specialized use cases or future polish.
- **Won't Have (W):** Explicitly out of scope for the current development phase (e.g., features legacy plugins supported that are no longer relevant for modern IntelliJ).
