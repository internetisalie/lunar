# Change Log

All notable changes to the Lunar Lua IDE plugin are documented in this file.

## [1.0.0-SNAPSHOT] - In Development

### Core Language Support
- **Lua Grammar & Parsing**: Full Lua 5.1-5.4 syntax support with AST-based PSI model
- **Lexer & Tokenization**: Comprehensive lexical analysis with proper handling of comments, strings, and operators
- **Syntax Highlighting**: Color scheme configuration with semantic highlighting for syntax elements

### Documentation & Type Hints
- **Markdown Comments**: Plain comment documentation with Markdown support (no tag parsing)
- **LuaCATS Support**: Modern type annotation system with type hints, overloads, and generics
- **Inlay Hints**: Display inferred types for variables as inline editor hints
- **Type Inference Engine**: Cubic biunification constraint-based type analysis

### IDE Features
- **Navigation**: Symbol resolution and cross-file references
- **Structure View**: Outline view of file structure
- **Code Completion**: Intelligent completion for variables, functions, and members
- **Refactoring**: Label refactoring support
- **Code Style**: Settings for indentation, spacing, and formatting
- **Run Configurations**: Lua script execution and debugging support
- **Breakpoint Debugging**: DBGp protocol support for remote debugging

### Analysis & Quality Tools
- **Luacheck Integration**: Static analysis integration
- **Type Checking**: Constraint-based type validation with error reporting
- **Inspections**: Type assignability and return type mismatch detection

### Project Features
- **Platform Libraries**: Lua standard library definitions and type information
- **Project Settings**: Language level configuration (Lua 5.1-5.4)
- **Application Settings**: Interpreter detection and workspace configuration

### Architecture
- **Bipartite Type Graph**: O(n³) incremental reachability for type constraints
- **Scope Binding**: Lexical scope chains with proper shadowing and function scoping
- **Annotation Support**: Full LuaCATS @type, @param, @return injection
- **Type Caching**: CachedValuesManager integration for efficient type resolution
