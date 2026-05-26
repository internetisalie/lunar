---
id: TARGET
type: epic
folders:
  - "[[features]]"
title: "TARGET: Runtime Environment Configuration"
priority: high
status: in_progress
vf_icon: 🚧
---
# Target Configuration Requirements (`TARGET`)

The Target Configuration feature manages the runtime environment for Lua code, allowing users to switch between different platforms (e.g., Standard Lua, Redis, LuaJIT) and their respective versions.

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :--- | :--- |
| [`TARGET-00`](00-preparatory-activities/requirements.md) | **Preparatory Activities** | **H** | **Completed** | Complete all prerequisite work, including design sign-off and risk mitigation. |
| [`TARGET-01`](01-data-model/requirements.md) | **Target Data Model** | **M** | **Not Implemented** | Define a `Target` configuration combining a `LuaPlatform` and a platform-specific Version string. |
| [`TARGET-02`](02-implicit-level/requirements.md) | **Implicit Language Level** | **M** | **Not Implemented** | Selecting a Target must automatically set the `LuaLanguageLevel` (e.g., Redis implies Lua 5.1). |
| [`TARGET-03`](03-contextual-ui/requirements.md) | **UI Contextual Versions** | **M** | **Not Implemented** | The Project Settings panel must dynamically update the available version options based on the selected Platform. |
| [`TARGET-04`](04-library-resolution/requirements.md) | **Library Root Resolution** | **M** | **Not Implemented** | `PlatformLibraryProvider` must inject the correct standard library and SDK stubs based on the target. |
| [`TARGET-05`](05-luacheck-integration/requirements.md) | **Luacheck Integration** | **M** | **Not Implemented** | Map each Target to its corresponding luacheck `--std` argument via explicit `VersionEntry.luacheckStd` values. |
| [`TARGET-06`](06-migration/requirements.md) | **Target Migration** | **C** | **Not Implemented** | Existing projects using `LuaLanguageLevel` should gracefully migrate to the `Target` model. |

---

## Target Mapping Table

| Platform | Version | Implicit Language Level | Library Root |
| :--- | :--- | :--- | :--- |
| **Standard** | 5.1 | Lua 5.1 | `runtime/standard/lua-5.1/` |
| **Standard** | 5.2 | Lua 5.2 | `runtime/standard/lua-5.2/` |
| **Standard** | 5.3 | Lua 5.3 | `runtime/standard/lua-5.3/` |
| **Standard** | 5.4 | Lua 5.4 | `runtime/standard/lua-5.4/` |
| **Standard** | 5.5 | Lua 5.4 † | `runtime/standard/lua-5.5/` |
| **LuaJIT**   | 2.0 | Lua 5.1 | `runtime/luajit/luajit-2.0/` |
| **LuaJIT**   | 2.1 | Lua 5.1 | `runtime/luajit/luajit-2.1/` |
| **Redis**    | 5   | Lua 5.1 | `runtime/redis/redis-5/` |
| **Redis**    | 6   | Lua 5.1 | `runtime/redis/redis-6/` |
| **Redis**    | 7+  | Lua 5.1 | `runtime/redis/redis-7/` |
| **Tarantool**| 2.10| Lua 5.1 | `runtime/tarantool/tarantool-2.10/` |
| **OpenResty**| latest | Lua 5.1 | `runtime/ngx/ngx-latest/` |
| **Pandoc**   | latest | Lua 5.4 | `runtime/pandoc/pandoc-latest/` |

† Lua 5.5 language level support is future work; maps to `LUA54` until `LUA55` is added.
