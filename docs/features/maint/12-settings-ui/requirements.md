---
id: MAINT-12
title: "MAINT-12: Test Coverage - Settings & UI Configuration"
type: feature
parent_id: MAINT
status: todo
priority: medium
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-12: Test Coverage - Settings & UI Configuration

## Overview
Increase test coverage for state serialization, settings modification listeners, standard library indexing reloads, and Platform library registration.

## Scope
* **In Scope**:
  * Unit tests verifying `LuaProjectSettings` and `LuaApplicationSettings` persist and reload target language version, interpreters, and custom tool bindings.
  * Unit tests verifying that changing settings fires a `LuaSettingsChangedEvent` on the message bus.
  * Unit tests verifying that `LuaSettingsChangeListener` forces index rebuilds and updates open editor highlights.
  * Unit tests verifying that `PlatformLibraryProvider` registers standard libraries as synthetic roots when targets change.
* **Out of Scope**:
  * Testing Swing layout code inside panels (e.g. `LuaProjectSettingsPanel`, `LuaInterpretersTable`) except state bindings.

## Functional Requirements
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| MAINT-12-01 | **State Serialization** | Must | planned | Verify that application-level and project-level settings serialize to XML states and load back without losing fields. |
| MAINT-12-02 | **Settings Events Transmission** | Must | planned | Verify that calling setter actions (like `setTargetAndNotify`) triggers `LuaSettingsChangedEvent` on the topic channel. |
| MAINT-12-03 | **Index Invalidation Listener** | Must | planned | Verify that `LuaSettingsChangeListener` triggers index rebuilds and rescans project roots upon language level updates. |
| MAINT-12-04 | **Library Provider Registration** | Must | planned | Verify that `PlatformLibraryProvider` updates project synthetic library collections to match target platforms. |

## Acceptance Criteria
* **AC-12-01**: A test case asserts that setting an interpreter path, serialization, and deserialization preserves the configured path.
* **AC-12-02**: A test case asserts that a mock listener receives a topic notification when `settings.setTargetAndNotify()` is executed.
* **AC-12-03**: A test case asserts that changing language levels triggers a rescan of dependencies on `ProjectRootManagerEx`.
* **AC-12-04**: A test case asserts that when the project target is changed to Redis 7+, `PlatformLibraryProvider` returns a synthetic library containing the `cjson.lua` file.
