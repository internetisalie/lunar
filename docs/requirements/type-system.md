# Type System Requirements (`TYPE`)

Lunar aims to provide a robust, LuaCATS-first type system to enable advanced IDE features. An experimental exploration of the **Cubic Biunification** approach is available on the `inference` branch for reference.

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| [`TYPE-01`](spec/type/01-basic-type-inference.md) | **Basic Type Inference** | **M** | **Not Implemented** | Infer types from literal assignments and function return values. |
| [`TYPE-02`](spec/type/02-class-table-definitions.md) | **Class/Table Definitions** | **M** | **Partial** | Support `@class` and `@alias` tags to define complex structures. |
| [`TYPE-03`](spec/type/03-function-signature-matching.md) | **Function Signature Matching** | **M** | **Not Implemented** | Validate that arguments match `@param` definitions. |
| [`TYPE-04`](spec/type/04-union-types.md) | **Union Types** | **S** | **Not Implemented** | Support `type1 | type2` syntax for multi-type variables. |
| [`TYPE-05`](spec/type/05-generics-support.md) | **Generics Support** | **S** | **Not Implemented** | Parse and resolve `@generic` tags. |
| [`TYPE-06`](spec/type/06-return-type-checking.md) | **Return Type Checking** | **S** | **Not Implemented** | Validate that `return` statements match the `@return` tag. |
| [`TYPE-07`](spec/type/07-external-api-stubs.md) | **External API Stubs** | **S** | **Full** | Allow `.lua` files to define types for external modules. |
| `TYPE-08` | **Flow-Sensitive Analysis** | **C** | **Future Work** | Narrow types based on control flow. |

---

## Detailed Implementation Status

### TYPE-01: Basic Type Inference
- **Status**: **Not Implemented**
- **Strategy**: Cubic Biunification (Flow-based analysis)

### TYPE-02: Class/Table Definitions
- `TYPE-02-01` **@class Parsing**: **Implemented** (`LuaCatsParser`)
- `TYPE-02-02` **@alias Parsing**: **Implemented** (`LuaCatsParser`)
- `TYPE-02-03` **Inheritance Resolution**: **Not Implemented** (Will use graph reachability)

### TYPE-03: Function Signature Matching
- **Status**: **Not Implemented**
- **Strategy**: Polarized Flow (Contravariant argument matching)

### TYPE-04: Union Types
- **Status**: **Not Implemented**
- **Strategy**: Graph Disjunctions

### TYPE-05: Generics Support
- **Status**: **Not Implemented**
- **Strategy**: Let-Polymorphism (Generic instantiation at call sites)

### TYPE-06: Return Type Checking
- **Status**: **Not Implemented**
- **Strategy**: Polarized Flow (Return node constraints)

### TYPE-07: External API Stubs
- `TYPE-07-01` **Standard Library Definitions**: **Implemented** (Bundled in `resources/platform/`)
- `TYPE-07-02` **Library Provider**: **Implemented** (`PlatformLibraryProvider`)
- `TYPE-07-03` **Graph Injection**: **Not Implemented** (Inject stub ValueNodes on `require`)

