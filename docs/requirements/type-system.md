# Type System Requirements (`TYPE`)

Lunar aims to provide a robust, LuaCATS-first type system to enable advanced IDE features.

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `TYPE-01` | **Basic Type Inference** | **M** | Infer types from literal assignments (`local x = 10`), table constructors, and function return values. |
| `TYPE-02` | **Class/Table Definitions** | **M** | Support `@class` and `@alias` tags to define complex structures and object-oriented patterns. |
| `TYPE-03` | **Function Signature Matching** | **M** | Validate that arguments passed to a function match the `@param` definitions in its LuaCATS block. |
| `TYPE-04` | **Union Types** | **S** | Support `type1 | type2` syntax for variables that can hold multiple types. |
| `TYPE-05` | **Generics Support** | **S** | Parse and resolve `@generic` tags for reusable components (e.g., list wrappers). |
| `TYPE-06` | **Return Type Checking** | **S** | Validate that `return` statements match the `@return` tag defined for the function. |
| `TYPE-07` | **External API Stubs** | **S** | Allow users to provide `.lua` files (without logic) to define types for external C-modules. |
| `TYPE-08` | **Flow-Sensitive Analysis** | **C** | Narrow types based on control flow (e.g., `if type(x) == "string" then ...`). |
