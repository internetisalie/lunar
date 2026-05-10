# Change Log

## Unreleased

### Added

- **Type Inference Engine (Foundation)**: Implemented cubic biunification type graph engine for constraint-based, flow-sensitive type analysis over Lua PSI trees
  - Bipartite flow graph architecture with ValueNode, UseNode, and VariableNode for bidirectional type propagation
  - O(n³) incremental reachability algorithm for maintaining transitively closed flow relations
  - Type caching via `CachedValuesManager` to prevent stale types after edits
  - Support for `@type`, `@param`, and `@return` LuaCATS annotations

- **Type Inlay Hints**: Display inferred types for variables as inline editor hints
  - Correctly shows union types when annotated variables are assigned to other variables
  - Skips hints for variables with explicit type annotations
  - Comprehensive integration with IntelliJ's declarative inlay hint provider

- **Type Annotations Support**: Full integration of LuaCATS type annotations in type inference
  - `@type` annotations inject both value and constraint edges for proper type flow and validation
  - `@param` annotations properly scope parameter types within function bodies
  - `@return` annotations track function return types across multiple return statements

- **Comprehensive Type Tests**: 444 test cases covering type inference and annotations
  - Literal type inference (nil, boolean, number, string)
  - Annotation seeding and injection
  - Parameter and return type handling
  - Name-ref data flow and variable scoping
  - Function scope binding and nested scopes
  - Union type rendering and compatibility checking

### Fixed

- Type annotation union hint rendering for inlay hints
  - Variables receiving values from annotated variables now show correct union types
  - Type annotations properly flow as values to dependent expressions
  - Type checking validates assignments against declared types
