# Refactoring & Intentions Requirements (`REFACT`, `INTENT`)

Lunar provides tools to safely restructure code and perform automated transformations.

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `REFACT-01` | **Rename Refactoring** | **M** | **Full** | Safely rename symbols and update all references across the project. |
| `REFACT-02` | **Introduce Variable** | **S** | **None** | Extract an expression into a local variable and replace all occurrences. |
| `REFACT-03` | **Safe Delete** | **S** | **None** | Verify if a symbol is unused before allowing its deletion. |
| `REFACT-04` | **Label Refactoring** | **C** | **Full** | Support renaming and refactoring of `goto` labels and their references. |
| `REFACT-05` | **Name Validator** | **S** | **None** | Validate names for idiomatic conventions and suggest corrections. |
| `REFACT-06` | **Stubs for Declaring Identifiers** | **C** | **None** | Generate `.lua` stub files for declaring external APIs. |
| `INTENT-01` | **String Style Conversion** | **C** | **None** | Switch between different string quote styles. |
| `INTENT-02` | **Invert If Statement** | **C** | **None** | Automatically flip an `if-else` block and its condition. |
| `INTENT-03` | **Name Suggestion** | **S** | **None** | Suggest idiomatic names when creating new variables or functions. |
