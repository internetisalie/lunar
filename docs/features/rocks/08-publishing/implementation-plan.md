---
id: ROCKS-08-PLAN
title: Publishing Plan
type: plan
parent_id: ROCKS-08
status: done
---

# Implementation Plan

`Could`-priority — defer to last; keep it small.

## Phase 1: Action [Must]
- [x] Implement `net.internetisalie.lunar.rocks.publish.PublishRockAction : AnAction`
      (package `rocks.publish`, **not** `lang.rocks`); visible in the context menu for `.rockspec` files.
- [x] Register the `<action>` in `plugin.xml` (group it under the `.rockspec` context menu).
- [x] Store the LuaRocks.org API key via `PasswordSafe` (credential store), not a plain settings field.
- [x] Reuse `LuaRocksSettings.executablePath` (ROCKS-04) for the `luarocks` binary — do not re-add a path field.
- [x] On invoke: confirm/prompt for the API key, then run `luarocks upload <file.rockspec> --api-key=<key>`
      via `GeneralCommandLine` under `Task.Backgroundable` (off the EDT); surface result via a notification.
- **Verification**: `PublishRockActionTest`.
