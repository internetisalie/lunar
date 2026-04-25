# Refactoring & Intentions Requirements (`REFACT`, `INTENT`)

Lunar provides tools to safely restructure code and perform automated transformations.

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `REFACT-01` | **Rename Refactoring** | **M** | Safely rename symbols (variables, functions, fields, labels) and update all references across the project. |
| `REFACT-02` | **Introduce Variable** | **S** | Extract an expression into a local variable and replace all occurrences. |
| `REFACT-03` | **Safe Delete** | **S** | Verify if a symbol is unused before allowing its deletion. |
| `INTENT-01` | **String Style Conversion** | **C** | Intention to switch between different string quote styles (single, double, long brackets). |
| `INTENT-02` | **Invert If Statement** | **C** | Automatically flip an `if-else` block and its condition. |
| `INTENT-03` | **Name Suggestion** | **S** | Suggest idiomatic names when creating new variables or functions based on their type/context. |
