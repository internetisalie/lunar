# Code Navigation Requirements (`NAV`)

Lunar provides powerful tools to explore and navigate the Lua codebase.

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `NAV-01` | **Go to Definition (Symbols)** | **M** | Resolve and navigate to the declaration of variables, functions, and table fields. |
| `NAV-02` | **Find Usages (Symbols)** | **M** | Search for all references to a specific symbol across the project. |
| `NAV-03` | **Go to Class/File/Symbol** | **S** | Implement `ChooseByName` contributors for quick navigation. |
| [`NAV-04`](spec/nav-04-structure-view.md) | **Structure View** | **M** | Provide a hierarchical outline of the current file's functions, classes, and variables. |
| `NAV-05` | **Method Override Markers** | **S** | Show gutter icons for methods that override or implement a parent class/interface method. |
| `NAV-06` | **Hierarchy View** | **C** | Show the inheritance hierarchy for classes and tables. |
