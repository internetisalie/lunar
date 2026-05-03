# Code Navigation Requirements (`NAV`)

Lunar provides powerful tools to explore and navigate the Lua codebase.

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `NAV-01` | **Go to Definition (Symbols)** | **M** | **Full** | Resolve and navigate to the declaration of variables, functions, and table fields. |
| `NAV-02` | **Find Usages (Symbols)** | **M** | **Partial** | Search for all references to a specific symbol across the project. |
| `NAV-03` | **Go to Class/File/Symbol** | **S** | **None** | Implement `ChooseByName` contributors for quick navigation. |
| [`NAV-04`](spec/navigation/04-structure-view.md) | **Structure View** | **M** | **Full** | Provide a hierarchical outline of the current file's functions, classes, and variables, including anonymous functions. |
| `NAV-05` | **Method Override Markers** | **S** | **None** | Show gutter icons for methods that override or implement a parent class/interface method. |
| `NAV-06` | **Hierarchy View** | **C** | **None** | Show the inheritance hierarchy for classes and tables. |
| `NAV-07` | **Reference Contributors** | **S** | **Full** | Register custom reference providers to enable PSI-based reference resolution via `PsiReferenceContributor`. |
| `NAV-08` | **Line Markers** | **S** | **None** | Display gutter markers for special call types: recursive calls and tail calls. |
| `NAV-09` | **Return Highlighter** | **C** | **None** | Highlight `return` statements and their corresponding function definitions for visual clarity. |
| `NAV-10` | **Access Detector** | **S** | **None** | Detect and highlight variable access patterns (read vs. write) for semantic analysis. |
| `NAV-11` | **Bindings Caching** | **M** | **Full** | Use `CachedValuesManager` to cache `getReferences` and `getFileGlobals` results, invalidating on PSI changes. |
