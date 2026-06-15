---
id: ROCKS-08-DESIGN
title: Publishing Design
type: design
parent_id: ROCKS-08
status: planned
---

# Technical Design: Publishing

## 1. Architecture Overview
- **Component**: `net.internetisalie.lunar.lang.rocks.PublishRockAction`
- **Implements**: `com.intellij.openapi.actionSystem.AnAction`

## 2. Core Algorithms
1. Add an Action that appears in the context menu for `.rockspec` files.
2. When triggered, open a dialog to confirm the API key.
3. Use `GeneralCommandLine` to run `luarocks upload <file.rockspec> --api-key=<key>`.
