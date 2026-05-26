---
id: "TYPE"
title: "TYPE: Type System"
type: "epic"
status: "planned"
priority: "high"
folders:
  - "[[features]]"
---

# Type System Requirements (`TYPE`)

Lunar aims to provide a robust, LuaCATS-first type system to enable advanced IDE features.

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| [`TYPE-01`](./01-basic-type-inference.md) | **Basic Type Inference** | **M** | Infer types from literal assignments and function return values. |
| [`TYPE-02`](./02-class-table-definitions.md) | **Class/Table Definitions** | **M** | Support `@class` and `@alias` tags to define complex structures. |
| [`TYPE-03`](./03-function-signature-matching.md) | **Function Signature Matching** | **M** | Validate that arguments match `@param` definitions. |
| [`TYPE-04`](./04-union-types.md) | **Union Types** | **S** | Support `type1 | type2` syntax for multi-type variables. |
| [`TYPE-05`](./05-generics-support.md) | **Generics Support** | **S** | Parse and resolve `@generic` tags. |
| [`TYPE-06`](./06-return-type-checking.md) | **Return Type Checking** | **S** | Validate that `return` statements match the `@return` tag. |
| [`TYPE-07`](./07-external-api-stubs.md) | **External API Stubs** | **S** | Allow `.lua` files to define types for external modules. |
| [`TYPE-09`](./09-union-distribution-logic/requirements.md) | **Union Distribution Logic** | **M** | Distributive checking for union types. |
| `TYPE-08` | **Flow-Sensitive Analysis** | **C** | Narrow types based on control flow. |

---

## Detailed Implementation Status

### TYPE-01: Basic Type Inference
- **Status**: **✅ Implemented** (Phase 1 + Phase 5 enhancement)
- **Strategy**: Cubic Biunification (Flow-based analysis)
- **Phase 1 Components**:
  - Type graph construction (ValueNode, UseNode, VariableNode)
  - O(n³) transitive closure algorithm
  - Primitive type inference (nil, bool, number, string)
- **Phase 1 Test Status**: ✅ 29/29 tests passing
- **Phase 5 Components** (Future): Generic type support

### TYPE-02: Class/Table Definitions
- `TYPE-02-01` **@class Parsing**: **Implemented** (`LuaCatsParser`)
- `TYPE-02-02` **@alias Parsing**: **Implemented** (`LuaCatsParser`)
- `TYPE-02-03` **Inheritance Resolution**: **Not Implemented** (Will use graph reachability)

### TYPE-03: Function Signature Matching
- **Status**: **✅ Implemented** (See [`phase-3-implementation.md`](spec/type/phase-3-implementation.md))
- **Strategy**: Polarized Flow (Contravariant argument matching)
- **Components**:
  - `visitFuncCall()` - Function call visitor in LuaTypesVisitor
  - `checkFunctionCompatibility()` - Type checking with arity and contravariance validation
  - `LuaGraphType.Function` - Graph representation with parameters and return types
  - Test coverage: 7 comprehensive tests in TestLuaTypeCheckPhase3
- **Test Status**: ✅ 7/7 tests passing

### TYPE-04: Union Types
- **Status**: **✅ Implemented** (See [`phase-5-implementation-plan.md`](spec/type/design/phase-5-implementation-plan.md))
- **Strategy**: Graph Disjunctions

### TYPE-05: Generics Support
- **Status**: **✅ Implemented** (See [`phase-5-implementation-plan.md`](spec/type/design/phase-5-implementation-plan.md))
- **Strategy**: Let-Polymorphism (Generic instantiation at call sites)

### TYPE-06: Return Type Checking
- **Status**: **✅ Implemented** (Phase 2 + Phase 4 enhancement)
- **Strategy**: Polarized Flow (Return node constraints)
- **Phase 2 Components**:
  - Return type validation via @return annotations
  - Type constraint checking
  - Error reporting for type mismatches
- **Phase 2 Test Status**: ✅ 14/14 tests passing
- **Phase 4 Components** (Future): Multi-return values, tail call optimization

### TYPE-07: External API Stubs
- `TYPE-07-01` **Standard Library Definitions**: **Implemented** (Bundled in `resources/platform/`)
- `TYPE-07-02` **Library Provider**: **Implemented** (`PlatformLibraryProvider`)
- `TYPE-07-03` **Graph Injection**: **Not Implemented** (Inject stub ValueNodes on `require`)

### TYPE-09: Union Distribution Logic
- **Status**: **Planned**
- **Strategy**: Type Algebra (Distributive laws)
- **Detailed Specification**: [`09-union-distribution-logic/requirements.md`](./09-union-distribution-logic/requirements.md)

