**Lunar** is a Lua language plugin for the IntelliJ Platform — GoLand, IntelliJ IDEA, PyCharm,
CLion, WebStorm, and other JetBrains IDEs. It targets **Lua 5.1–5.5** and brings first-class
editor intelligence, static analysis, and remote debugging to Lua projects.

## Features

- **Syntax & editor support** — highlighting, folding, brace matching, breadcrumbs, and structural editing
- **Code completion** — keywords, symbols, cross-file, and type-driven suggestions
- **Navigation** — go to definition, find usages, structure view, gutter markers, and references
- **Type system** — LuaCATS/LuaDoc annotations, type inference, and function signatures
- **Documentation** — Quick Doc, LuaCATS/LuaDoc rendering, and parameter info
- **Inspections & diagnostics** — undeclared/unused variables, type mismatches, and quick fixes
- **Static analysis** — Luacheck integration via an external annotator
- **Formatting** — indentation, alignment, and spacing (StyLua-compatible)
- **Refactoring & intentions** — rename, introduce variable, label handling, and string conversions
- **Debugging & execution** — breakpoints, stack frames, remote (DBGp/MobDebug) debugging, and a REPL
- **Runtime targets** — select a platform/version target with target-aware standard-library
  resolution, stub-backed for Standard Lua (5.1–5.4), Redis (5/6/7), and Valkey (7.2/8)
- **Toolchain management** — discover, provision, and resolve Lua interpreters and tools
- **LuaRocks integration** — rockspec support, dependency management, package discovery, and multi-rock workspaces
- **Redis & Valkey** — server-side Lua scripting (`redis.*` / `server.*`), sandbox inspections, and connection-aware typing
- **Schema-driven data files** — JSON-schema-backed validation and completion for `.rockspec`, `.luacheckrc`, and other Lua config

Lunar is licensed under Apache-2.0.
