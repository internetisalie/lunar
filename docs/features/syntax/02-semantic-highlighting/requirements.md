---
id: SYNTAX-02
title: "02: Semantic Highlighting"
type: feature
parent_id: SYNTAX
status: "done"
vf_icon: ✅
priority: "medium"
folders:
  - "[[features/syntax/requirements|requirements]]"
---
# Specification: SYNTAX-02 Semantic Highlighting

This document defines the requirements for semantic highlighting in the Lunar editor, which differentiates variables based on their scope and declaration type (locals, globals, parameters, and upvalues).

## 1. Scope

This specification applies to variable references and declarations in Lua files across all supported Lua versions (5.1 - 5.4). It requires semantic analysis of the abstract syntax tree (AST) beyond basic lexical tokenization.

## 2. Highlight Categories

The editor must identify and apply distinct text attributes (colors/styles) to the following categories of variables:

### 2.1 Global Variables
Variables that are declared in the global scope or referenced without a prior local declaration in the current or enclosing scopes.
- **Example**: `math`, `print`, `_G`, or implicitly created globals like `myGlobal = 1`.
- **Styling Intent**: Typically a distinct color indicating broad scope or potential side-effects.

### 2.2 Local Variables
Variables declared within a specific block using the `local` keyword.
- **Example**: `local x = 10`
- **Styling Intent**: Standard variable color, distinct from globals.

### 2.3 Parameters
Variables declared as part of a function's parameter list, including the implicit `self` parameter in method definitions.
- **Example**: `function add(a, b)` -> `a` and `b` are parameters.
- **Example**: `function obj:method()` -> `self` is an implicit parameter.
- **Styling Intent**: Usually a specific color to differentiate inputs from local state.

### 2.4 Upvalues (Closures)
Variables declared in an outer local scope but referenced from within a nested function (closure).
- **Example**:
  ```lua
  local counter = 0
  local function increment()
      counter = counter + 1 -- 'counter' here is an upvalue
      return counter
  end
  ```
- **Styling Intent**: Often italicized or colored distinctly to indicate captured state.

## 3. Rules & Behavior

### 3.1 Resolution Precedence
The semantic highlighter must resolve variable references by searching outward through lexical scopes:
1. Current block locals.
2. Function parameters (if inside a function).
3. Outer block locals (treated as upvalues if crossing a function boundary).
4. Globals (if no local declaration is found in any enclosing scope).

### 3.2 Shadowing
If a local variable or parameter shadows an outer variable (or global), the highlighting must reflect the most immediate scope correctly.
- **Example**:
  ```lua
  local x = 1 -- 'x' is local
  function foo(x) -- 'x' is parameter
      print(x) -- highlights as parameter
  end
  ```

### 3.3 Syntax Highlighting Hierarchy
Semantic highlighting takes precedence over basic lexical highlighting for identifiers, but should not override highlighting for keywords, numbers, strings, or operators.

### 3.4 Fallback/Default Highlighting
If semantic analysis cannot resolve a variable (e.g., due to severe syntax errors or incomplete code structure), the editor should fall back to a default "identifier" or "global" style.

## 4. IDE Integration
- Users must be able to customize the text attributes (foreground, background, font type like bold/italic) for Globals, Locals, Parameters, and Upvalues in the IDE's **Color Scheme** settings under the Lua language section.
- The highlighting annotator should run asynchronously (e.g., using `ExternalAnnotator` or IntelliJ's highlighting passes) to ensure typing remains responsive and the UI thread is not blocked during semantic analysis.
