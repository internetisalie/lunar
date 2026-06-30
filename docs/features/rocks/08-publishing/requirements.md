---
id: ROCKS-08
title: Publishing Requirements
type: feature
parent_id: ROCKS
status: done
vf_icon: ✅
folders:
  - "[[features/rocks/requirements|requirements]]"
---

# Publishing Requirements

Publish the current project's `.rockspec` to luarocks.org from the IDE by driving
`luarocks upload`, reusing the ROCKS-04 binary path and storing the API key in the
platform credential store.

## Requirements Table
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| ROCKS-08-01 | Publish Action | Could | Full | Context-menu action on a `.rockspec` to publish the project to LuaRocks.org. Registered in the project-view and editor popup groups. |
| ROCKS-08-02 | API key in PasswordSafe | Could | Full | The upload API key is stored via `PasswordSafe` under a stable credential key, never in persisted settings XML. Prompted on first use. |
| ROCKS-08-03 | Reuse luarocks binary | Could | Full | The upload invokes `luarocks upload` using `LuaRocksSettings.executablePath` (ROCKS-04); no second binary-path setting. |
| ROCKS-08-04 | Background + notify | Could | Full | The upload runs on a `Task.Backgroundable` (off the EDT); success/failure is reported via the `notification.group.lunar.luarocks` group. |
| ROCKS-08-05 | Live upload to LuaRocks.org | Could | Partial | End-to-end publish to the live registry. Verified manually — requires a real API key + network; not exercised in the headless suite. |
