---
id: TARGET-QA
parent_id: TARGET
type: qa
folders:
  - "[[features/target/requirements|requirements]]"
title: "Verification Checklists"
status: not_implemented
---

# QA Verification Scenarios: TARGET - Runtime Environment Configuration

This document outlines the formal verification scenarios for the TARGET feature.

## 1. Settings Panel & UI

### Scenario 1.1: Platform and Version Selection
1. Open **Settings | Languages & Frameworks | Lua | Project Settings**.
2. Change **Platform** from `Standard` to `Redis`.
3. Verify **Version** dropdown updates to show Redis versions (`5`, `6`, `7+`).
4. Select version `7+`.
5. Verify **Language Level** label displays `Lua 5.1`.
6. Click **Apply**.

### Scenario 1.2: Settings Reset
1. Change **Platform** to `LuaJIT`.
2. Click **Reset**.
3. Verify selection reverts to previous saved state.

## 2. Platform Libraries & Completion

### Scenario 2.1: Redis Completion
1. Set Project Target to `Redis / 7+`.
2. Open a Lua file.
3. Type `redis.`
4. Verify completion suggestions include `redis.call`, `redis.pcall`, `redis.log`, etc.

### Scenario 2.2: LuaJIT Completion
1. Set Project Target to `LuaJIT / 2.1`.
2. Open a Lua file.
3. Type `bit.`
4. Verify completion suggestions include LuaJIT-specific bitop functions like `bit.bor`, `bit.band`.

## 3. Luacheck Integration

### Scenario 3.1: Standard Lua 5.1 vs 5.4
1. Set Project Target to `Standard / 5.1`.
2. In a Lua file, use `table.unpack({})`.
3. Verify Luacheck flags `table.unpack` as an undefined global (since it was added in 5.2).
4. Set Project Target to `Standard / 5.4`.
5. Verify the error disappears.

### Scenario 3.2: Redis Built-ins
1. Set Project Target to `Redis / 7+`.
2. In a Lua file, use `redis.call("GET", "key")`.
3. Verify no Luacheck "undefined global" error for `redis`.

## 4. Legacy Migration

### Scenario 4.1: Automated Migration
1. Open a project created with an older version of the plugin (having only `languageLevel` set to `LUA51`).
2. Open **Project Settings**.
3. Verify **Platform** is automatically set to `Standard` and **Version** to `5.1`.
4. Verify `.idea/lunar.xml` now contains a `<target>` entry.
