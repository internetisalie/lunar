---
id: "BUG-538"
title: "Debugging in a wizard-provisioned environment fails: mobdebug requires luasocket, which provisioning does not install"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-538: Debug fails on `require 'socket'` in a provisioned environment

Found by MAINT-44's HV-02 live check on GoLand 2026.2.3 (`runIde`), after the sandbox assets were
injected by hand to get past [[BUG-536]].

## 1. Reproduction
1. *File → New → Project… → LuaRocks*, tick *Provision isolated environment*, and create the project.
   The wizard provisions Lua 5.4.8 and LuaRocks 3.13.0 into the project's `.lua/` tree and makes it
   the active environment (`lua 5.4` in the status bar).
2. Create `main.lua`:
   ```lua
   local x = 1
   local y = x + 1
   print("sum", x + y)
   print("done")
   ```
3. Set a breakpoint on line 3 and Debug a Lua run configuration for `main.lua` that uses the project
   environment.

## 2. Expected vs Actual Behavior
- **Expected**: the debuggee connects and stops on line 3, or, if debugging cannot work in this
  environment, Lunar says what is missing and how to add it.
- **Actual**: the process exits with code 1 before connecting. The Debug console shows `module 'socket'
  not found`, listing the searched paths (including `<project>/.lua/lib/lua/5.4/…` and `./socket.so`),
  then:
  ```
  stack traceback:
      [C]: in function 'require'
      .../plugins_runIde/lunar/lua/mobdebug/init.lua:107: in main chunk
      [C]: in function 'require'
      .../plugins_runIde/lunar/lua/lunar/debug.lua:10: in function 'lunar.debug.start'
      .../plugins_runIde/lunar/lua/debug.lua:11: in main chunk
      [C]: in ?
  Process finished with exit code 1
  ```
  Nothing tells the user that luasocket is the missing piece or how to install it.

In the same session, Debug against a Lua that has luasocket (`/usr/local/bin/lua`) stopped at the
breakpoint, with locals `x = 1`, `y = 2`.

Evidence, under `~/.cache/claude-scratch/lunar/maint-44/hv/`:
`hv02-runIde-injected-lua-socket.png` (the failure) and `hv02-runIde-session-with-injected-lua.png`
(the working session).

## 3. Context / Environment
- **Lua version**: 5.4.8, provisioned by the LuaRocks new-project wizard together with LuaRocks 3.13.0.
- **IDE**: GoLand 2026.2.3 (`runIde` sandbox).
- **How the debuggee loads socket** (cited, not diagnosed):
  - `run/LuaRunConfiguration.kt:321-335`: for the Debug executor, Lunar sets `LUA_INIT=@<plugin>/lua/debug.lua`,
    `LUNAR_LUA_PATH_TEMPLATE=<plugin>/lua/?/init.lua;<plugin>/lua/?.lua`, `LUNAR_DEBUGGER_PACKAGE=mobdebug`
    and `MOBDEBUG_PORT`. It adds only the plugin's `lua/` directory to `package.path`, and nothing to
    `package.cpath`.
  - `src/main/lua/debug.lua:9-11` prepends that template to `package.path` and calls
    `require("lunar.debug").start()`.
  - `src/main/lua/lunar/debug.lua:9-10` requires `mobdebug`; its retry `sleep` (line 3) also
    requires `socket`.
  - `src/main/lua/mobdebug/init.lua:107`: `local socket = require "socket"` at module load, so
    mobdebug cannot load without luasocket. Line 1583 requires it again.
  - The plugin ships mobdebug as Lua source only; no luasocket (a C module) is bundled.
  - `LuaExecutionEnvironmentBuilder.build(...).applyTo(commandLine)` (`LuaRunConfiguration.kt:338-343`)
    sets `LUA_PATH`/`LUA_CPATH` to the environment's tree, so `socket` is looked up only there and in
    the interpreter's defaults. A provisioned tree contains what the provision request listed: the
    runtime, optionally LuaRocks and the catalog tools. luasocket is not among them.
  - No pre-launch check in `run/` looks for `socket`; the first sign of the problem is the traceback.
- Earlier verification ran against interpreters that already had luasocket: MAINT-22's checklist uses
  "PUC 5.1 env (+luasocket)", and DEBUG-13 used "`lua 5.4.7` + luasocket".

## 4. Open design question (for planning, not decided here)
Which layer should own the dependency?
1. **Provisioning installs luasocket** into every environment it creates, or offers it as a catalog
   option that defaults on when LuaRocks is included. That needs LuaRocks and a C compiler, and it does
   not help environments Lunar did not provision.
2. **The debugger bundles luasocket**, or a socket shim, per platform and Lua ABI, and adds it to
   `package.cpath` alongside the existing `package.path` template. This works with any interpreter,
   but means shipping native binaries per OS, architecture and Lua version (5.1–5.5, LuaJIT).
3. **The error guides the user**: detect a failed `require "socket"` in the preloader (or probe before
   launch) and report "Debugging needs luasocket in this environment" with an action such as
   `luarocks install luasocket` into the active environment. This is the cheapest change, and it could
   be combined with either option above.
