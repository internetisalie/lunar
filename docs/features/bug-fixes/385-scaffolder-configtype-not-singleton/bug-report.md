---
id: "BUG-385"
title: "LuaRocks scaffolder instantiates a fresh LuaRunConfigurationType instead of the registered singleton"
type: "bug"
parent_id: "BUG"
priority: "low"
status: "done"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-385: Scaffolder instantiates a fresh run-configuration type

> **RESOLVED 2026-07-17 (this commit)**: `LuaRocksScaffolder.patchRunConfigTemplate` now calls `ConfigurationTypeUtil.findConfigurationType(LuaRunConfigurationType::class.java)` to obtain the platform-registered singleton instead of constructing `LuaRunConfigurationType()`. Added `testScaffolderUsesRegisteredConfigTypeSingleton` to `LuaRocksScaffolderTest`.

*Source: codebase review [`docs/review.md`](../../../review.md) finding **#49** (still present
2026-07-17).*

## 1. Reproduction

1. Create a new rock via the LuaRocks project scaffolder (`rocks/init/LuaRocksScaffolder.kt`).
2. Inspect the run-configuration template the scaffolder patches.

## 2. Expected vs Actual Behavior

- **Expected**: the scaffolder patches the template of the **registered**
  `LuaRunConfigurationType` singleton (configuration types are platform singletons).
- **Actual**: `LuaRocksScaffolder.kt:73` constructs a fresh `LuaRunConfigurationType()`; template
  patching may operate on a divergent instance rather than the platform-registered one.

## 3. Notes

Fix per the review: `ConfigurationTypeUtil.findConfigurationType(LuaRunConfigurationType::class.java)`.
One-line fix + a scaffolder test asserting the patched template is the registered type's.
