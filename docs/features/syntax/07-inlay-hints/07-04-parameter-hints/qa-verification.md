---
folders:
  - "[[features/syntax/07-inlay-hints/07-04-parameter-hints/requirements|requirements]]"
title: QA Verification
---
# QA Verification: SYNTAX-07-04 Parameter Name Hints

## Overview
This document outlines the verification steps for the Parameter Name Hints feature.

## Automated Verification
The following unit test suites cover the core logic and edge cases:
- `net.internetisalie.lunar.lang.insight.hint.LuaParameterInlayHintsTest`: Specifically targets parameter hint rendering and suppression rules.
- `net.internetisalie.lunar.lang.insight.hint.LuaTypeInlayHintsTest`: Verifies that parameter hints don't regress type hint rendering.

**Execution:**
```bash
./gradlew test --tests net.internetisalie.lunar.lang.insight.hint.LuaParameterInlayHintsTest
./gradlew test --tests net.internetisalie.lunar.lang.insight.hint.LuaTypeInlayHintsTest
```

## Manual Verification Scenarios

### 1. Basic Positional Arguments
- **Input:**
  ```lua
  local function greet(first, last) end
  greet("John", "Doe")
  ```
- **Expected:** `greet(first: "John", last: "Doe")`

### 2. Method (Colon) Calls
- **Input:**
  ```lua
  local obj = {}
  function obj:set(val1, val2) end
  obj:set(1, 2)
  ```
- **Expected:** `obj:set(val1: 1, val2: 2)`. The implicit `self` parameter MUST NOT have a hint.

### 3. Suppression: Name Match
- **Input:**
  ```lua
  local function move(posX, posY) end
  local posX, posY = 10, 20
  move(posX, posY)
  ```
- **Expected:** No hints. Hints are suppressed when argument name matches parameter name.

### 4. Suppression: Single Parameter
- **Input:**
  ```lua
  local function print_log(msg) end
  print_log("hello")
  ```
- **Expected:** No hints. Hints are suppressed for functions with a single parameter to reduce noise.

### 5. Suppression: Trivial Names
- **Input:**
  ```lua
  local function solve(x, y) end
  solve(1, 2)
  ```
- **Expected:** No hints. Single-character names (x, y, p, _) are considered trivial and suppressed unless they match specific descriptors.

### 6. Cross-File Resolution
- **Setup:** Function defined in `utils.lua`, called in `main.lua`.
- **Input (`utils.lua`):**
  ```lua
  function setup_config(timeout, retry_count) end
  ```
- **Input (`main.lua`):**
  ```lua
  setup_config(5000, 3)
  ```
- **Expected:** `setup_config(timeout: 5000, retry_count: 3)`

### 7. LuaCATS Annotations
- **Input:**
  ```lua
  ---@param speed number
  ---@param force number
  local function apply(s, f) end
  apply(10, 20)
  ```
- **Expected:** `apply(speed: 10, force: 20)`. Names should be pulled from `@param` tags if available.
