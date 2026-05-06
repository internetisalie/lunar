# Specification: DOC-02 LuaCATS Syntax Highlighting

This document defines the requirements for syntax highlighting of LuaCATS (Lua Code Annotation Toolset) tags and types within Lua comments.

## 1. Scope

LuaCATS uses a specific syntax within Lua comments (starting with `---`) to provide type annotations. This specification covers the visual representation of these annotations in the editor.

## 2. Highlighted Elements

### 2.1 Tags
Tags identify the type of information being provided.
- **Examples**: `@param`, `@return`, `@class`, `@type`, `@field`, `@alias`, `@generic`, `@overload`, `@see`, `@since`, `@deprecated`.
- **Formatting**: Typically a distinct color (e.g., keyword color).

### 2.2 Types
LuaCATS supports a rich type system.
- **Primitive Types**: `string`, `number`, `boolean`, `table`, `function`, `thread`, `userdata`, `nil`, `any`, `void`.
- **Literal Types**: `"string literal"`, `123`, `true`.
- **Union Types**: `string|number`.
- **Array Types**: `string[]`.
- **Table Types**: `table<string, number>`, `{ name: string, age: number }`.
- **Function Types**: `fun(name: string): boolean`.
- **User-defined Types**: Custom class or alias names.
- **Formatting**: Typically a distinct color (e.g., type or class color).

### 2.3 Identifiers
Names of parameters, fields, or generics.
- **Formatting**: Distinct from normal comment text.

### 2.4 Brackets and Delimiters
Punctuation used in type definitions.
- **Examples**: `<`, `>`, `[`, `]`, `{`, `}`, `:`, `|`, `,`.
- **Formatting**: Standard punctuation or operator color.

## 3. Editor Requirements

| ID | Requirement | Priority | Status |
| :--- | :--- | :---: | :---: |
| `DOC-02-01` | **Comment Detection** | **M** | **Full** |
| `DOC-02-02` | **Tag Highlighting** | **M** | **Full** |
| `DOC-02-03` | **Type Parsing and Highlighting** | **M** | **Full** |
| `DOC-02-04` | **Parameter Alignment** | **S** | **Full** |
| `DOC-02-05` | **Field Identification** | **S** | **Full** |
| `DOC-02-06` | **Deprecated/Since Tags** | **S** | **Full** |

## 4. Examples

### Example: Basic Type Annotation
```lua
--- @type string|number
local x
```
- `@type`: Tag color
- `string`, `number`: Type color
- `|`: Operator color

### Example: Function Signature
```lua
--- @param name string The name to greet.
--- @param options { silent?: boolean, count: number }
--- @return boolean success
function greet(name, options)
```
- `@param`, `@return`: Tag color
- `name`, `options`, `success`: Identifier color
- `string`, `boolean`, `number`: Type color
- `{`, `}`, `?`, `:`, `,`: Punctuation color

### Example: Complex Generic Type
```lua
--- @alias MyMap table<string, fun(a: any): void>
```
- `@alias`: Tag color
- `MyMap`: Identifier/Class color
- `table`, `string`, `any`, `void`: Type color
- `fun`: Keyword/Type color
- `<`, `>`, `(`, `)`, `:`, `,`: Punctuation color
