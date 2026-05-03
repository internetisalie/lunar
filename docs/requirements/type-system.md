# Type System Requirements (`TYPE`)

Lunar aims to provide a robust, LuaCATS-first type system to enable advanced IDE features.

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `TYPE-01` | **Basic Type Inference** | **M** | **Not Implemented** | Infer types from literal assignments and function return values. |
| `TYPE-02` | **Class/Table Definitions** | **M** | **Partial** | Support `@class` and `@alias` tags to define complex structures. |
| `TYPE-03` | **Function Signature Matching** | **M** | **Not Implemented** | Validate that arguments match `@param` definitions. |
| `TYPE-04` | **Union Types** | **S** | **Not Implemented** | Support `type1 | type2` syntax for multi-type variables. |
| `TYPE-05` | **Generics Support** | **S** | **Not Implemented** | Parse and resolve `@generic` tags. |
| `TYPE-06` | **Return Type Checking** | **S** | **Not Implemented** | Validate that `return` statements match the `@return` tag. |
| `TYPE-07` | **External API Stubs** | **S** | **Full** | Allow `.lua` files to define types for external modules. |
| `TYPE-08` | **Flow-Sensitive Analysis** | **C** | **Future Work** | Narrow types based on control flow. |

---

## Detailed Implementation Status

### TYPE-01: Basic Type Inference
- **Status**: **Not Implemented**

### TYPE-02: Class/Table Definitions
- `TYPE-02-01` **@class Parsing**: **Implemented** (`LuaCatsParser`)
- `TYPE-02-02` **@alias Parsing**: **Implemented** (`LuaCatsParser`)
- `TYPE-02-03` **Inheritance Resolution**: **Not Implemented**

### TYPE-07: External API Stubs
- `TYPE-07-01` **Standard Library Definitions**: **Implemented** (Bundled in `resources/platform/`)
- `TYPE-07-02` **Library Provider**: **Implemented** (`PlatformLibraryProvider`)

