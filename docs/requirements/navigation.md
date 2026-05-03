# Code Navigation Requirements (`NAV`)

Lunar provides powerful tools to explore and navigate the Lua codebase.

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `NAV-01` | **Go to Definition (Symbols)** | **M** | Resolve and navigate to the declaration of variables, functions, and table fields. |
| `NAV-02` | **Find Usages (Symbols)** | **M** | Search for all references to a specific symbol across the project. |
| `NAV-03` | **Go to Class/File/Symbol** | **S** | Implement `ChooseByName` contributors for quick navigation. |
| [`NAV-04`](spec/navigation/04-structure-view.md) | **Structure View** | **M** | Provide a hierarchical outline of the current file's functions, classes, and variables, including anonymous functions. |
| `NAV-05` | **Method Override Markers** | **S** | Show gutter icons for methods that override or implement a parent class/interface method. |
| `NAV-06` | **Hierarchy View** | **C** | Show the inheritance hierarchy for classes and tables. |
| `NAV-07` | **Reference Contributors** | **S** | Register custom reference providers to enable PSI-based reference resolution via `PsiReferenceContributor`. |
| `NAV-08` | **Line Markers** | **S** | Display gutter markers for special call types: recursive calls and tail calls. |
| `NAV-09` | **Return Highlighter** | **C** | Highlight `return` statements and their corresponding function definitions for visual clarity. |
| `NAV-10` | **Access Detector** | **S** | Detect and highlight variable access patterns (read vs. write) for semantic analysis. |
| `NAV-11` | **Bindings Caching** | **M** | Use `CachedValuesManager` to cache `getReferences` and `getFileGlobals` results, invalidating on PSI changes. |
