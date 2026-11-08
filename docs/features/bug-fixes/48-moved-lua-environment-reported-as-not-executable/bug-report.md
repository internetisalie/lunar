---
id: "BUG-48"
title: "A moved Lua environment's tools are reported as 'Not executable' instead of naming the move"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-48: A moved Lua environment's tools are reported as "Not executable" instead of naming the move

## 1. Reproduction
1. Create a hererocks environment at one path (seen: built in the Docker container as
   `/home/lunar/test/.lua`), then use it from another path (`/home/mini/Documents/src/lua/test/.lua`).
2. Open the project. The editor banner reads *Lua tool 'LuaRocks' is unavailable: Not executable*.

The file is executable (`-rwxr-xr-x`). It cannot start because its first line,
`#!/home/lunar/test/.lua/bin/lua`, names an interpreter that does not exist on this host. Hand-editing
the paths cannot repair the environment either: the `lua` binary has the old `package.path` and
`package.cpath` compiled in, and `/home/lunar` also remains in `etc/luarocks/config-5.1.lua`,
`include/luaconf.h`, `bin/luarocks-admin` and the `activate*` scripts. Re-provisioning fixes it.

## 2. Expected vs Actual Behavior
- **Expected**: the health check says what is wrong and what to do. When the environment's
  `hererocks.manifest` `"location"` differs from where the manifest actually sits, it reports
  something like *"This Lua environment was created at /home/lunar/test/.lua and has been moved;
  re-create it here"*. Any other launch failure names its cause, for example a missing `#!`
  interpreter, rather than claiming the file is not executable.
- **Actual**: "Not executable", which is false. `LuaToolHealthChecker` even records
  `executable = true` alongside that reason.

## 3. Root Cause
- `LuaToolProbeImpl.kt:50-57`: a launch that fails (`LuaExecOutcome.START_FAILED`, here an ENOENT on
  the `#!` interpreter) is reported with the same `failure = "Not executable"` as the real
  permission check at `:21-28`.
- `LuaToolHealthChecker.kt:97` falls back to `"Not executable"` whenever a probe fails without a reason.
- Nothing reads `hererocks.manifest`'s `"location"`, so a moved environment cannot be told apart from
  a broken one.

## 4. Fix Strategy (to be confirmed by plan-bug)
- Give a launch failure its own reason. Where the target is a script whose `#!` interpreter does not
  exist, name that path.
- When the tool sits under a directory whose `hererocks.manifest` records a different `"location"`,
  report it as a moved environment, with both paths.
- Out of scope: re-provisioning from the IDE. The plugin no longer contains hererocks provisioning.

## 5. Test Strategy
Light tests with a temporary directory: a script whose `#!` interpreter is missing gives the
missing-interpreter reason, not "Not executable". A manifest whose `"location"` differs from its own
directory gives the moved-environment reason. A truly non-executable file still gives "Not executable".
