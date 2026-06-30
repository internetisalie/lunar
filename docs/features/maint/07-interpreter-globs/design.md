---
id: MAINT-07-DESIGN
title: Interpreter Globs Design
type: design
parent_id: MAINT-07
---

# Technical Design: Interpreter Globs

## 1. Architecture Overview
- **Component**: `net.internetisalie.lunar.settings.LuaSdkType`

## 2. Core Algorithms
1. When searching for SDKs in `suggestHomePaths()`, use Java NIO `Files.newDirectoryStream` with a glob matching `lua5.*` to locate possible interpreters automatically.
