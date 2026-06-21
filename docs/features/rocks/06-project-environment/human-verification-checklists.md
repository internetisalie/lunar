---
id: "ROCKS-06-CHECKLIST"
title: "Verification Checklists"
type: "qa"
status: "planned"
parent_id: "ROCKS-06"
folders:
  - "[[features/rocks/06-project-environment/requirements|requirements]]"
---

# Verification Checklists: ROCKS-06 — Project LuaRocks Environment

## 1. Settings UI

### Scenario 1.1: LuaRocks application Configurable exists
- **Setup**: Launch the sandbox IDE (`./gradlew runIde`); open Settings.
- **Steps**:
  1. Navigate to Settings → Tools (group `tools`).
  2. Find the "LuaRocks" page.
- **Expected**: A "LuaRocks" page shows an executable-path field (defaulting to `luarocks`) and a
  "Default server URL" field (empty by default).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.2: Project server override round-trips
- **Setup**: Open a Lua project; open Settings → Languages & Frameworks → Lua Project.
- **Steps**:
  1. Enter `http://localhost:8080` in "LuaRocks server URL (project override)".
  2. Click Apply, close, reopen Settings.
  3. Inspect `.idea/lunar.xml` on disk.
- **Expected**: The value persists across reopen, and `rocksServerUrl=http://localhost:8080`
  appears in `.idea/lunar.xml` (VCS-shared). No API key appears in that file.
- **Result**: ⬜ Pass / ⬜ Fail

## 2. Server Resolution & Search

### Scenario 2.1: Search targets the project server
- **Setup**: Project A with `rocksServerUrl = http://localhost:8080`; a reachable local
  rockserver (or observe the constructed command in the IDE log via a temporary `log.warn`).
- **Steps**:
  1. Open the LuaRocks package browser (ROCKS-02) and search `inspect`.
- **Expected**: The executed `luarocks search --porcelain inspect` includes
  `--server http://localhost:8080`.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.2: Unset project uses no `--server`
- **Setup**: Project B with blank project override and blank app default.
- **Steps**:
  1. Search `inspect` in the browser.
- **Expected**: The executed command contains **no** `--server` token (default luarocks.org
  behavior, identical to pre-ROCKS-06).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.3: Project override beats app default
- **Setup**: App default `serverUrl = https://reg.example`; Project A override
  `http://localhost:8080`.
- **Steps**:
  1. Search in Project A.
- **Expected**: Command uses `--server http://localhost:8080` (project wins).
- **Result**: ⬜ Pass / ⬜ Fail

## 3. Executable Resolution (TOOL-02)

### Scenario 3.1: Bound LuaRocks tool is used
- **Setup**: Register a LuaRocks tool and bind it to the project (TOOL-02 project binding) at a
  non-default path.
- **Steps**:
  1. Trigger a search.
- **Expected**: The command uses the bound tool's path, not the bare `luarocks` default.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.2: Fallback to app executable
- **Setup**: No valid LuaRocks tool bound; app `executablePath = luarocks`.
- **Steps**:
  1. Trigger a search.
- **Expected**: The command uses `luarocks` (app fallback).
- **Result**: ⬜ Pass / ⬜ Fail

## 4. Publish & Credentials

### Scenario 4.1: Upload targets the resolved server with a per-server key
- **Setup**: Project A (`rocksServerUrl = http://localhost:8080`); a `.rockspec` present; no key
  stored yet for that server.
- **Steps**:
  1. Right-click the `.rockspec` → "Publish Rock to LuaRocks…".
  2. Enter an API key when prompted.
- **Expected**: The upload command includes `--api-key=…` and `--server http://localhost:8080`.
  A second publish to the same server does **not** re-prompt; the key is stored in PasswordSafe
  (not in any XML).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 4.2: Different server prompts for its own key
- **Setup**: After 4.1, change the override to a different server URL.
- **Steps**:
  1. Publish again.
- **Expected**: A fresh key prompt appears (per-server keying); the first server's key is
  untouched.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 4.3: Legacy key still works
- **Setup**: A pre-existing credential stored under `"luarocks.org API key"`; project + app server
  both blank.
- **Steps**:
  1. Publish a `.rockspec`.
- **Expected**: No re-prompt — the legacy key is read via the blank-server fall-through; the
  command omits `--server`.
- **Result**: ⬜ Pass / ⬜ Fail
