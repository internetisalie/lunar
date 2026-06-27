---
id: MAINT-17
title: "MAINT-17: Test Coverage - Utilities & Command Line"
type: feature
parent_id: MAINT
status: todo
priority: medium
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-17: Test Coverage - Utilities & Command Line

## Overview
Increase test coverage for background command processes, thread management utilities, classpath builder services, and terminal runner settings.

## Scope
* **In Scope**:
  * Unit tests for background commands execution and output readers in `LuaProcessUtil`.
  * Unit tests for project file path resolution and installation directory searches in `LuaFileUtil`.
  * Unit tests for background thread execution helpers in `LuaTaskUtil`.
  * Unit tests for environment variable mapping and runner classpath configuration in `LuaCommandLine` and `LuaRunProfile`.
* **Out of Scope**:
  * Testing OS-specific shell terminal configurations.

## Functional Requirements
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| MAINT-17-01 | **Process Capture Execution** | Must | planned | Verify that `LuaProcessUtil.capture()` correctly runs OS commands in background threads and yields stdout/stderr. |
| MAINT-17-02 | **Path Resolution Helpers** | Must | planned | Verify that `LuaFileUtil` maps project directories and resolves standard plugin resources. |
| MAINT-17-03 | **Task Background Workers** | Must | planned | Verify that `LuaTaskUtil` spawns background tasks safely without blocking the IDE event thread. |
| MAINT-17-04 | **CommandLine Builders** | Must | planned | Verify that `LuaCommandLine` correctly configures classpaths for jar runners and injects `LUA_PATH`. |

## Acceptance Criteria
* **AC-17-01**: A test case asserts that executing a command (e.g. `lua -v`) via `LuaProcessUtil.capture()` returns stdout containing the version info.
* **AC-17-02**: A test case asserts that `LuaFileUtil.getPluginPath()` returns a valid path to the installed plugin resource folder.
* **AC-17-03**: A test case asserts that task execution runs on a pooled background thread and invokes success callbacks upon termination.
* **AC-17-04**: A test case asserts that configuring a command line adds custom environment variables and format-escaped jar parameters.
