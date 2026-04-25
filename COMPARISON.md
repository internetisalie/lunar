# Lua IntelliJ Plugin Feature Comparison Matrix

| | [lunar] | [lua-for-idea] | [IntelliJ-EmmyLua] | [EmmyLua2] | [Luanalysis] |
|---|:---:|:---:|:---:|:---:|:---:|
| **Status** | Early Dev | Active | Actively Maintained | Actively Maintained | Actively Maintained |
| **Version** | 1.0.0-SNAPSHOT | 1.0.96 | 1.4.15+ | 0.x (analyzer-rust) | 1.4.0 |
| **IDE Build Range** | 261+ | 145–171 | 253–261 | 252–261 | 221+ |
| **Language** | Kotlin | Java | Kotlin | Kotlin + Rust LSP | Kotlin/Java |
| **Architecture** | PSI/native | PSI/native | PSI/native | LSP (emmylua-analyzer-rust) | PSI/native |
| | | | | | |
| **SYNTAX** | | | | | |
| Syntax highlighting | ✔ | ✔ | ✔ | ✔ | ✔ |
| Semantic highlighting (global/local/param/upvalue) | ✔ | ✔ | ✔ | ✔ (LSP semantic tokens) | ✔ |
| Brace matching | ✔ | ✔ | ✔ | ✔ | ✔ |
| Comment in/out | ✔ | ✔ | ✔ | ✔ | ✔ |
| Code folding | ✔ | ✔ | ✔ | ✔ (LSP) | ✔ |
| Method separators | ✗ | ✗ | ✔ | ✗ | ✔ |
| | | | | | |
| **LUA VERSION SUPPORT** | | | | | |
| Lua 5.1 | ✔ | ✔ | ✔ | ✔ | ✔ |
| Lua 5.2 | ✔ | ✔ | ✔ | ✔ | ✔ |
| Lua 5.3 | ✔ | ✔ | ✔ | ✔ | ✔ |
| Lua 5.4 (`<const>`, `<close>`) | ✔ | ✗ | ✔ | ✔ | ✔ |
| LuaJIT | ✗ (planned) | ✔ | ✗ | ✔ | ✗ |
| Luau | ✗ (planned) | ✗ | ✗ | ✗ | ✗ |
| | | | | | |
| **CODE COMPLETION** | | | | | |
| Keyword completion | ✗ | ✔ | ✔ | ✔ (LSP) | ✔ |
| Basic symbol completion | ✗ | ✔ | ✔ | ✔ (LSP) | ✔ |
| Cross-file completion | ✗ | ✔ | ✔ | ✔ (LSP) | ✔ |
| Type-inference-based completion | ✗ | ✔ (exp.) | ✔ | ✔ (LSP) | ✔ |
| Parameter name hints | ✗ | ✗ | ✔ | ✔ (LSP inlay hints) | ✔ |
| Postfix completion templates | ✗ | ✗ | ✔ (14) | ✗ | ✔ |
| Live templates | ✗ | ✗ | ✔ | ✗ | ✔ |
| | | | | | |
| **CODE NAVIGATION** | | | | | |
| Go to definition | ✗ | ✔ | ✔ | ✔ (LSP) | ✔ |
| Find usages | ✔ (labels only) | ✔ | ✔ | ✔ (LSP) | ✔ |
| Go to symbol | ✗ | ✔ | ✔ | ✔ (LSP) | ✔ |
| Go to class | ✗ | ✗ | ✔ | ✔ (LSP) | ✔ |
| Go to file | ✗ | ✗ | ✔ | ✔ (LSP) | ✔ |
| Structure view / code outline | ✔ | ✔ | ✔ | ✗ | ✔ |
| Method override line markers | ✗ | ✗ | ✔ | ✔ (LSP gutter) | ✔ |
| | | | | | |
| **TYPE SYSTEM** | | | | | |
| Basic type inference | ✗ | ✔ (exp.) | ✔ | ✔ (LSP) | ✔ (Advanced) |
| Comment-based type annotations | LuaCATS | LuaDoc | EmmyDoc | EmmyDoc (enhanced) | EmmyDoc (enhanced) |
| Class/table type definitions | ✗ | ✗ | ✔ | ✔ | ✔ |
| Generics / union types | ✗ | ✗ | ✔ | ✔ | ✔ (Advanced) |
| Return type checking | ✗ | ✗ | ✔ | ✔ | ✔ |
| Function signature matching | ✗ | ✗ | ✔ | ✔ | ✔ |
| External API definition files | ✗ | ✔ | ✔ | ✔ | ✔ |
| Lua standard library stubs | ✔ | ✔ | ✔ | ✔ | ✔ |
| | | | | | |
| **DOCUMENTATION** | | | | | |
| Quick documentation (Ctrl+Q) | ✔ (partial) | ✔ | ✔ | ✔ (LSP hover) | ✔ |
| LuaDoc generation / highlighting | ✔ | ✔ | ✔ | ✔ | ✔ |
| LuaCATS support | ✔ | ✗ | ✗ | ✗ | ✗ |
| | | | | | |
| **INSPECTIONS / DIAGNOSTICS** | | | | | |
| Total inspections | 0 | 18+ | 13+ | LSP diagnostics | 25+ |
| Unused assignment | ✗ | ✔ | ✔ | ✔ | ✔ |
| Suspicious global creation | ✗ | ✔ | ✗ | ✔ | ✔ |
| Unbalanced assignment | ✗ | ✔ | ✗ | ✔ | ✔ |
| Undeclared variable | ✗ | ✗ | ✔ | ✔ | ✔ |
| Duplicate class declaration | ✗ | ✗ | ✔ | ✔ | ✔ |
| Local name shadowed | ✗ | ✗ | ✔ | ✔ | ✔ |
| Global can be local | ✗ | ✗ | ✔ | ✔ | ✔ |
| Assign type mismatch | ✗ | ✗ | ✔ | ✔ | ✔ (Error) |
| Deprecated API usage | ✗ | ✗ | ✔ | ✔ | ✔ |
| Language level compliance | ✗ | ✗ | ✔ | ✔ | ✔ |
| Divide by zero | ✗ | ✔ | ✗ | ✔ | ✗ |
| String concatenation in loops | ✗ | ✔ | ✗ | ✗ | ✗ |
| Unreachable statements | ✗ | ✔ | ✗ | ✔ | ✔ |
| Cyclomatic complexity | ✗ | ✔ | ✗ | ✗ | ✗ |
| Overly complex / long method | ✗ | ✔ | ✗ | ✗ | ✗ |
| Self parameter naming | ✗ | ✔ | ✗ | ✗ | ✗ |
| Redundant initialization | ✗ | ✔ | ✗ | ✗ | ✗ |
| Array element zero index | ✗ | ✔ | ✗ | ✗ | ✗ |
| | | | | | |
| **INTENTIONS / CODE ACTIONS** | | | | | |
| Code intentions | ✗ | ✔ | ✔ (9) | ✔ (LSP code actions) | ✔ |
| String method style conversion | ✗ | ✔ | ✔ | ✗ | ✔ |
| | | | | | |
| **REFACTORING** | | | | | |
| Rename identifier | ✔ (labels only) | ✔ | ✔ | ✔ (LSP) | ✔ |
| Safe delete | ✗ | ✔ | ✗ | ✗ | ✗ |
| Introduce variable | ✗ | ✔ (exp.) | ✗ | ✔ | ✗ |
| Name suggestion | ✗ | ✗ | ✔ | ✗ | ✔ |
| | | | | | |
| **FORMATTING** | | | | | |
| Code formatter | ✔ (partial) | ✔ | ✔ | ✔ (EmmyLuaCodeStyle) | ✔ |
| Configurable code style | ✔ | ✔ | ✔ | ✔ | ✔ |
| | | | | | |
| **DEBUGGING** | | | | | |
| Debugger | ✔ (in-progress) | ✔ (exp.) | ✔ | ✔ (EmmyLua debugger) | ✔ |
| Line breakpoints | ✔ | ✔ | ✔ | ✔ | ✔ |
| Stack frames | ✔ | ✔ | ✔ | ✔ | ✔ |
| Expression evaluation | ✔ | ✔ | ✔ | ✔ | ✔ |
| Remote debugger | ✗ | ✗ | ✔ (Mobdebug) | ✔ (Emmy protocol) | ✔ |
| Lua profiler | ✗ | ✗ | ✔ | ✗ | ✔ |
| | | | | | |
| **EXECUTION** | | | | | |
| Run configuration | ✔ | ✔ | ✔ | ✔ | ✔ |
| Lua SDK REPL console | ✗ | ✔ | ✗ | ✗ | ✗ |
| LuaJ run configuration | ✗ | ✔ | ✗ | ✗ | ✗ |
| | | | | | |
| **STATIC ANALYSIS** | | | | | |
| Luacheck integration | ✔ | ✗ | ✔ | ✗ | ✔ |
| Flow analysis | ✗ | ✔ | ✗ | ✔ (LSP) | ✔ |
| | | | | | |
| **FRAMEWORK / PLATFORM** | | | | | |
| World of Warcraft FrameXML injection | ✗ | ✔ | ✗ | ✗ | ✗ |
| Modules support | ✗ | ✔ (exp.) | ✔ | ✔ | ✔ |
| Love2D | ✗ | ✗ | ✗ | ✗ | ✗ |
| Defold | ✗ | ✗ | ✗ | ✗ | ✗ |
| | | | | | |
| **OTHER** | | | | | |
| Hierarchy view | ✗ | ✗ | ✔ | ✗ | ✔ |
| Spellchecker support | ✗ | ✗ | ✔ | ✔ | ✔ |
| Configurable color settings | ✔ | ✔ | ✔ | ✔ | ✔ |
| LSP support | ✗ | ✗ | ✗ | ✔ (is LSP-based) | ✗ |
| Test runner integration | ✗ | ✗ | ✗ | ✗ | ✗ |

[lunar]: lunar
[lua-for-idea]: lua-for-idea
[IntelliJ-EmmyLua]: IntelliJ-EmmyLua
[EmmyLua2]: Intellij-EmmyLua2
[Luanalysis]: IntelliJ-Luanalysis

---

## Summary

- **lunar** is a modern ground-up rewrite in Kotlin targeting IntelliJ 2026.1+. It has the most
  current IDE compatibility (build 261+), Lua 5.4 syntax support, LuaCATS annotation support,
  Luacheck integration, and a debugger under active development, but many features are still
  incomplete or unimplemented.

- **lua-for-idea** (a fork of IDLua) targets older IDE builds (145–171) and is notable for its 18+
  inspections including flow/metrics analysis, WoW FrameXML injection, and a Lua SDK REPL console.

- **IntelliJ-EmmyLua** is the most feature-complete PSI-native plugin with advanced type inference,
  EmmyDoc annotations (generics, union types, class definitions), 14 postfix templates, remote
  debugging via Mobdebug, a Lua profiler, and Go to Class/File navigation. Targets IDE builds 231–232.

- **EmmyLua2** is the spiritual successor to IntelliJ-EmmyLua. It replaces the PSI-based type
  engine with a Rust language server (`emmylua-analyzer-rust`) accessed via LSP4IJ, delivering
  real-time semantic diagnostics, enhanced EmmyDoc annotations, EmmyLuaCodeStyle formatting, and
  EmmyLua remote debugging. The LSP architecture means analysis quality improves independently of
  the IDE plugin. Targets builds 252–261.
