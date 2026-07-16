---
id: "REDIS-06-CHECKLIST"
title: "Verification Checklists"
type: "qa"
status: "planned"
parent_id: "REDIS-06"
folders:
  - "[[features/redis/06-sandbox-quickdoc-refinements/requirements|requirements]]"
---

# Verification Checklists: REDIS-06 — Sandbox & Quick-Doc Gating Refinements

Run in the sandbox IDE (`tooling/gce-builder/gce-builder.sh run runIde`) or the containerized
GoLand over VNC, on a project whose Lunar target is set to **Redis 7+**.

## 1. Sandbox Inspection (REDIS-06-01)

### Scenario 1.1: Shadowing local suppresses the false positive
- **Setup**: Redis 7+ target; open a `.lua` file.
- **Steps**:
  1. Type `local print = redis.log` then on the next line `print("hi")`.
- **Expected**: No "Redis script sandbox" warning underlines the second `print`.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.2: Genuine global still flagged
- **Setup**: Redis 7+ target; open a fresh `.lua` file (no shadowing local).
- **Steps**:
  1. Type `print("hi")` on its own.
- **Expected**: `print` is underlined at WARNING with "'print' is not available in the Redis
     script sandbox".
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.3: Parameter shadow suppresses the warning
- **Setup**: Redis 7+ target.
- **Steps**:
  1. Type `local function f(io) io.read() end`.
- **Expected**: No sandbox warning on the `io` use inside `f`.
- **Result**: ⬜ Pass / ⬜ Fail

## 2. Command Quick-Doc (REDIS-06-02)

### Scenario 2.1: Quick-doc on the command string
- **Setup**: Redis 7+ target.
- **Steps**:
  1. Type `redis.call("GET", KEYS[1])`.
  2. Place the caret inside `"GET"` and invoke Quick Documentation (Ctrl+Q / F1).
- **Expected**: The GET command doc popup appears (name, summary, Since, Arity).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.2: No quick-doc off the command string
- **Setup**: same file as 2.1.
- **Steps**:
  1. Place the caret on `redis`, then on `call`, then inside `KEYS[1]`, invoking Quick
     Documentation at each position.
- **Expected**: No Redis command doc popup for any of these positions (either nothing, or the
     normal Lua doc for that element — never the GET command card).
- **Result**: ⬜ Pass / ⬜ Fail
