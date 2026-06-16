---
id: ROCKS-08-DESIGN
title: Publishing Design
type: design
parent_id: ROCKS-08
status: planned
---

# Technical Design: Publishing

> **⚠ Grounding correction (2026-06-16):** this 9-line design is too thin to implement. Before coding:
> rename the package `net.internetisalie.lunar.lang.rocks` → `net.internetisalie.lunar.rocks.publish`
> (track convention), store the API key via `PasswordSafe` (not a plain field), reuse
> `LuaRocksSettings.executablePath` for the binary, register the `<action>` in `plugin.xml`, and run the
> upload under `Task.Backgroundable`. `Could`-priority — defer to last.
> See [planning-gaps.md](../../../planning-gaps.md#wave-10-grounding-audit-2026-06-16).

## 1. Architecture Overview
- **Component**: `net.internetisalie.lunar.rocks.publish.PublishRockAction`
- **Implements**: `com.intellij.openapi.actionSystem.AnAction`
- **Registration**: `<action>` in `plugin.xml`, in the `.rockspec` context-menu group.
- **Binary**: reuse `LuaRocksSettings.executablePath` (ROCKS-04) for the `luarocks` executable.
- **API key**: stored via `PasswordSafe` (credential store), never a plain settings field.

## 2. Core Algorithms
1. Add an Action that appears in the context menu for `.rockspec` files.
2. When triggered, confirm/prompt for the API key (read from `PasswordSafe`).
3. Under `Task.Backgroundable` (off the EDT), use `GeneralCommandLine` to run
   `luarocks upload <file.rockspec> --api-key=<key>`; report the result via a notification.
