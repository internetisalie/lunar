---
id: "ROCKS-06-CHECKLIST"
title: "Verification Checklists"
type: "qa"
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
- **Result**: ✅ Pass — Settings → Tools → LuaRocks page present with executable and server URL fields

### Scenario 1.2: Project server override round-trips
- **Setup**: Open a Lua project; open Settings → Languages & Frameworks → Lua Project.
- **Steps**:
  1. Enter `http://localhost:8080` in "LuaRocks server URL (project override)".
  2. Click Apply, close, reopen Settings.
  3. Inspect `.idea/lunar.xml` on disk.
- **Expected**: The value persists across reopen, and `rocksServerUrl=http://localhost:8080`
  appears in `.idea/lunar.xml` (VCS-shared). No API key appears in that file.
- **Result**: ✅ Pass — Value persists across reopen; `rocksServerUrl=http://localhost:8080` in `.idea/lunar.xml`; no API key in file

## 2. Server Resolution & Search

### Scenario 2.1: Search targets the project server
- **Setup**: Project A with `rocksServerUrl = http://localhost:8080`; a reachable local
  rockserver (or observe the constructed command in the IDE log via a temporary `log.warn`).
- **Steps**:
  1. Open the LuaRocks package browser (ROCKS-02) and search `inspect`.
- **Expected**: The executed `luarocks search --porcelain inspect` includes
  `--server http://localhost:8080`.
- **Result**: ✅ Pass — `idea.log` confirmed `[--server, http://localhost:8080, search, --porcelain, ...]` (global-flag position)

### Scenario 2.2: Unset project uses no `--server`
- **Setup**: Project B with blank project override and blank app default.
- **Steps**:
  1. Search `inspect` in the browser.
- **Expected**: The executed command contains **no** `--server` token (default luarocks.org
  behavior, identical to pre-ROCKS-06).
- **Result**: ✅ Pass — `idea.log` confirmed no `--server` token when unset

### Scenario 2.3: Project override beats app default
- **Setup**: App default `serverUrl = https://reg.example`; Project A override
  `http://localhost:8080`.
- **Steps**:
  1. Search in Project A.
- **Expected**: Command uses `--server http://localhost:8080` (project wins).
- **Result**: ✅ Pass — `idea.log` confirmed project override (`http://localhost:8080`) beats app default (`https://reg.example`)

## 3. Executable Resolution (TOOL-02)

### Scenario 3.1: Bound LuaRocks tool is used
- **Setup**: Register a LuaRocks tool and bind it to the project (TOOL-02 project binding) at a
  non-default path.
- **Steps**:
  1. Trigger a search.
- **Expected**: The command uses the bound tool's path, not the bare `luarocks` default.
- **Result**: ✅ Pass — Bound tool path `/opt/custom-luarocks/bin/luarocks` used; TOOL-DIAG confirmed valid binding

### Scenario 3.2: Fallback to app executable
- **Setup**: No valid LuaRocks tool bound; app `executablePath = luarocks`.
- **Steps**:
  1. Trigger a search.
- **Expected**: The command uses `luarocks` (app fallback).
- **Result**: ✅ Pass — App `luarocks` fallback used when no tool bound

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
- **Result**: ⏭ Deferred — requires live rockserver; headless TC 5/6 cover command shape; TC 7/8 cover per-server key derivation

### Scenario 4.2: Different server prompts for its own key
- **Setup**: After 4.1, change the override to a different server URL.
- **Steps**:
  1. Publish again.
- **Expected**: A fresh key prompt appears (per-server keying); the first server's key is
  untouched.
- **Result**: ⏭ Deferred — same as 4.1

### Scenario 4.3: Legacy key still works
- **Setup**: A pre-existing credential stored under `"luarocks.org API key"`; project + app server
  both blank.
- **Steps**:
  1. Publish a `.rockspec`.
- **Expected**: No re-prompt — the legacy key is read via the blank-server fall-through; the
  command omits `--server`.
- **Result**: ⏭ Deferred — same as 4.1

## Verification Summary (2026-06-25)

- VNC environment: `lunar-ide` container (GoLand 2026.1.3), plugin `lunar-1.0.0-SNAPSHOT`
- Method: temporary `log.warn("ROCKS-06 search cmd: ...")` in `LuaRocksSearchService.search()` (removed after testing)
- Scenarios 1.1–3.2: **7 PASS** (all green)
- Scenarios 4.1–4.3: **Deferred** — require live rockserver; credential keying covered by headless `LuaRocksApiKeyStoreTest` TC 7/8
- Side finding: pre-existing ROCKS-02 `Alarm` threading warning on tool-window open (not a ROCKS-06 defect)
- Full audit log: `.agents/handoffs/ROCKS-06-vnc-verification.md`
