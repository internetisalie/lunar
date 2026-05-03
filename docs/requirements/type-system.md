# Type System Requirements (`TYPE`)

Lunar aims to provide a robust, LuaCATS-first type system to enable advanced IDE features.

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `TYPE-01` | **Basic Type Inference** | **M** | **None** | Infer types from literal assignments and function return values. |
| `TYPE-02` | **Class/Table Definitions** | **M** | **Partial** | Support `@class` and `@alias` tags to define complex structures. |
| `TYPE-03` | **Function Signature Matching** | **M** | **None** | Validate that arguments match `@param` definitions. |
| `TYPE-04` | **Union Types** | **S** | **None** | Support `type1 | type2` syntax for multi-type variables. |
| `TYPE-05` | **Generics Support** | **S** | **None** | Parse and resolve `@generic` tags. |
| `TYPE-06` | **Return Type Checking** | **S** | **None** | Validate that `return` statements match the `@return` tag. |
| `TYPE-07` | **External API Stubs** | **S** | **Full** | Allow `.lua` files to define types for external modules. |
| `TYPE-08` | **Flow-Sensitive Analysis** | **C** | **None** | Narrow types based on control flow. |
