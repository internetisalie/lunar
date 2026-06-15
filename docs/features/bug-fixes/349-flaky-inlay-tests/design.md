---
id: BUG-349-DESIGN
title: Flaky Inlay Tests Design
type: design
parent_id: BUG-349
status: planned
---

# Technical Design: Flaky Inlay Tests

## 1. Architecture Overview
- **Component**: `net.internetisalie.lunar.lang.hints.LuaInlayHintsTest`

## 2. Core Algorithms
1. Avoid initializing full editor fonts during inlay test verification. Use light fixtures.
