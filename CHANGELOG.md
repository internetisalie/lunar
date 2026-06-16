# Change Log

All notable changes to the Lunar Lua IDE plugin are documented in this file.

## [1.0.0-SNAPSHOT] - In Development

### Runtime & Platform Support
- **Target Selection**: Introduced comprehensive project environment selection with platform and version granularity.
- **New Platforms**: Added explicit support for **LuaJIT**, **Redis (5/6/7)**, **Tarantool**, **OpenResty (NGX)**, and **Pandoc**.
- **Dynamic Standard Libraries**: Automatic resolution of platform-specific library definitions (e.g., Redis globals, LuaJIT-specific functions) based on the selected target.
- **Environment-Aware Luacheck**: Luacheck now dynamically adjusts its analysis standard (`--std`) to match the active project environment.
- **Legacy Migration**: Automated migration of existing project settings to the new environment-aware data model.

### Core Language Support
- **Lua Grammar & Parsing**: Full Lua 5.1-5.4 syntax support with AST-based PSI model
- **Lexer & Tokenization**: Comprehensive lexical analysis with proper handling of comments, strings, and operators
- **Syntax Highlighting**: Color scheme configuration with semantic highlighting for syntax elements

### Documentation & Type Hints
- **Markdown Comments**: Plain comment documentation with Markdown support (no tag parsing)
- **LuaCATS Support**: Modern type annotation system with type hints, overloads, and generics
- **Inlay Hints**: Display inferred types for variables as inline editor hints
- **Type Inference Engine**: Cubic biunification constraint-based type analysis
- **Implicit Class Fields**: `@class` types now include fields discovered from assignments (`ClassName.field = …` and `self.field = …` inside methods), not only `@field` tags — so those members appear in completion and resolution (explicit `@field` still takes precedence)
- **Canonical Union Types**: Union types are normalized at construction — nested unions flattened, members de-duplicated, `T | any` simplified to `any`, and members sorted — for stable type display and comparison
- **Union Mismatch Diagnostics**: When a table value fails against a union type, the error names the closest-matching member and its specific missing field (e.g. `closest match 'Point': missing field 'y'`) instead of a generic message

### IDE Features
- **Rename Validation**: The Rename refactoring now rejects new names that are Lua reserved keywords (e.g. `local`, `goto`, `end`) or are not syntactically valid Lua identifiers (e.g. `1var`, `a-b`)
- **Navigation**: Symbol resolution and cross-file references
- **Go to Type (bare `@class`/`@alias`)**: Go to Class / Go to Symbol now find bare `--- @class` and `--- @alias` declarations (pure type-level forms with no following `local`), not only types attached to a local declaration
- **Read/Write Access**: variable references are classified as read vs. write — distinct highlight colors for the variable under the caret, and Read/Write grouping in Find Usages
- **Structure View**: Outline view of file structure
- **Code Completion**: Intelligent completion for variables, functions, and members
- **Type-Inferred Member Completion**: Completing after `.`/`:` now suggests a receiver's inferred members (fields and methods, with icons), including inherited `@class` members, `self` inside methods, and members exposed through `setmetatable`'s `__index`
- **Postfix Templates**: Type an expression followed by `.if`/`.not`/`.var`/`.for`/`.forp`/`.fori`/`.ifnot`/`.nil`/`.notnil`/`.return`/`.print` and press Tab to rewrite it into the matching statement (e.g. `ready.not` → `not ready`, `getUser().var` → `local value = getUser()` with an editable name)
- **Live Templates**: Built-in abbreviation templates for common Lua constructs — `fun`/`lfun`/`if`/`ifel`/`while`/`repeat`/`fori`/`forip`/`forp`/`loc`/`req`/`mod` — plus Surround-With templates (`if`/`for`/`do`/`function`) via Ctrl+Alt+T. Templates are now code-aware and no longer expand inside strings, comments, or numeric literals.
- **Refactoring**: Label refactoring support
- **Introduce Variable**: extract a selected expression into a `local <name> = <expr>` before the enclosing statement and replace the occurrence, with a name suggestion, inline rename, and a this-occurrence/all-occurrences chooser when the expression repeats
- **Safe Delete**: deleting a local/parameter/global/label declaration first searches for usages — removes it silently when unused, or shows the standard "usages found" conflict dialog when references remain
- **Block Auto-close on Enter**: pressing Enter after a block opener (`then`/`do`/`function`/`repeat` and table `{`) inserts the matching `end`/`until`/`}` on the next line and opens an indented body line. A balance check now fixes a correctness bug where a redundant `end` was appended even when the block was already closed; full opener coverage spans `if`/`while`/numeric & generic `for`/bare `do`/`function`/`repeat`/table literals; and pressing Enter between an already-matched opener and its terminator indents a blank body line without inserting a duplicate.
- **Code Style**: Settings for indentation, spacing, and formatting
- **Formatter Fix**: Unary `not` now keeps a space before its operand when reformatting (`not x`); previously it collapsed to the distinct identifier `notx`. Symbolic unary operators (`-`, `#`, `~`) remain tight.
- **Run Configurations**: Lua script execution and debugging support
- **Breakpoint Debugging**: DBGp protocol support for remote debugging

### Analysis & Quality Tools
- **Luacheck Integration**: Static analysis integration
- **Type Checking**: Constraint-based type validation with error reporting
- **Inspections**: Type assignability and return type mismatch detection
- **Undeclared-Variable Inspection**: Flags reads of names that resolve to nothing — respecting locals, parameters, loop variables, file/project globals, the per-version standard library, an "Additional Globals" allowlist, and `---@diagnostic`/`-- luacheck: ignore` suppression comments
- **For-loop Variable Resolution**: `for` loop variables now resolve correctly within the loop body (navigation, completion, and inspections no longer treat them as undeclared)

### Project Features
- **Platform Libraries**: Lua standard library definitions and type information
- **Project Settings**: Language level configuration (Lua 5.1-5.4)
- **Application Settings**: Interpreter detection and workspace configuration

### Architecture
- **Bipartite Type Graph**: O(n³) incremental reachability for type constraints
- **Scope Binding**: Lexical scope chains with proper shadowing and function scoping
- **Annotation Support**: Full LuaCATS @type, @param, @return injection
- **Type Caching**: CachedValuesManager integration for efficient type resolution
