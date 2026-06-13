---
id: "RUN-03"
title: "03: Interactive Console (REPL)"
type: "feature"
parent_id: "DEBUG/RUN"
status: "planned"
priority: "low"
folders:
  - "[[features/debug/requirements|requirements]]"
---

# Specification: RUN-03 Interactive Console (REPL)

This document defines the requirements for providing an integrated Lua REPL (Read-Eval-Print Loop) within the Lunar editor.

## 1. Scope

The Interactive Console provides a Lua REPL embedded within the IDE, bound to the project's selected Lua SDK. Developers can evaluate expressions, run short scripts, and inspect results without leaving the editor.

## 2. Technical Strategy

The console implementation leverages IntelliJ's `LanguageConsoleView` API to provide a native-feeling terminal experience with full IDE support for the input field.

- **Console View**: Uses `LanguageConsoleView` for syntax-highlighted input and standard output rendering.
- **Process Management**: Launches the project's Lua SDK with a bundled [REPL bootstrap script](design/repl-bootstrap.lua) that manages the evaluation loop.
- **Input Detection**: Adopts the `lua-repl` approach of trial-compilation with `load`. If the error ends with `<eof>`, the console enters multi-line mode (`>>` prompt).
- **Backend Investigation**: The [lua-repl](https://github.com/hoelzro/lua-repl) library is under investigation as a potential structured backend for more advanced I/O.

## 3. Interaction Rules

### 3.1 Evaluation
Input is sent for evaluation when the user presses `Enter` on a complete Lua chunk. `Shift+Enter` is reserved for inserting literal newlines.

### 3.2 Multi-line Mode
If the user submits an incomplete block (e.g., an unclosed `if`), the console enters multi-line mode, showing a continuation prompt (`>>`) until the block is closed.

### 3.3 Persistence
Command history is persisted at the project level (`.idea/lua-console-history`) and remains available across IDE restarts.

## 4. Requirements

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `RUN-03-01` | **Native Console UI** | **M** | Embed the console using `LanguageConsoleView` with distinct input/output areas. |
| `RUN-03-02` | **SDK Integration** | **M** | Launch the REPL using the active project SDK interpreter. |
| `RUN-03-03` | **Incomplete Input Detection** | **M** | Detect incomplete Lua syntax and switch to multi-line mode automatically. |
| `RUN-03-04` | **Input Syntax Highlighting** | **M** | Apply full Lua syntax highlighting to the console input field. |
| `RUN-03-05` | **Command History** | **S** | Support history navigation (Up/Down) and persistence across sessions. |
| `RUN-03-06` | **Basic Completion** | **S** | Provide code completion for standard library and session-defined symbols. |
| `RUN-03-07` | **Stderr Differentiation** | **S** | Visually distinguish error output (stderr) from normal stdout. |
| `RUN-03-08` | **Unbuffered Output** | **C** | Ensure interpreter output is flushed immediately to the console. |

## 5. Examples

### 5.1 Simple Expression
```lua
> 1 + 1
2
```

### 5.2 Multi-line Block
```lua
> function greet(name)
>>  return "Hello, " .. name
>> end
> greet("Lunar")
"Hello, Lunar"
```

### 5.3 Error Handling
```lua
> error("oops")
[Error] stdin:1: oops
```
