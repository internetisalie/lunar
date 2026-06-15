---
id: NAV-03-DESIGN
title: "Technical Design"
type: design
parent_id: NAV-03
status: "planned"
priority: "medium"
folders:
  - "[[features/navigation/03-go-to-class-file-symbol/requirements|requirements]]"
---

# Technical Design: NAV-03 Go to Class / File / Symbol

## 1. Architecture Overview

### Current State
No `ChooseByNameContributor` exists. The stub indexes are already populated:
`net.internetisalie.lunar.lang.indexing.LuaClassNameIndex`
(`KEY: StubIndexKey<String, LuaLocalVarDecl>`), `LuaAliasIndex`
(`StubIndexKey<String, LuaLocalVarDecl>`), and `LuaGlobalDeclarationIndex`
(`KEY: StubIndexKey<String, LuaFuncDecl>`). Go to File already works via the registered
`LuaFileType` (NAV-03-03 = Full).

### Target State
Two index-backed contributors expose classes/aliases (Go to Class) and global symbols (Go to
Symbol) to *Navigate ▸ Class/Symbol* and Search Everywhere. No file parsing — all lookups go
through `StubIndex`.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.navigation.LuaGotoClassContributor`
- **Responsibility**: classes + aliases for Go to Class.
- **Key API** (`GotoClassContributor` + `ChooseByNameContributorEx` for index efficiency):
  ```kotlin
  class LuaGotoClassContributor : GotoClassContributor, ChooseByNameContributorEx {
      override fun processNames(p: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
          val idx = StubIndex.getInstance()
          idx.processAllKeys(LuaClassNameIndex.KEY, p, scope, filter)
          idx.processAllKeys(LuaAliasIndex.KEY, p, scope, filter)
      }
      override fun processElementsWithName(name: String, p: Processor<in NavigationItem>,
                                           params: FindSymbolParameters) {
          val scope = params.searchScope; val project = params.project
          StubIndex.getElements(LuaClassNameIndex.KEY, name, project, scope, LuaLocalVarDecl::class.java)
              .forEach { if (!p.process(it)) return }
          StubIndex.getElements(LuaAliasIndex.KEY, name, project, scope, LuaLocalVarDecl::class.java)
              .forEach { if (!p.process(it)) return }
      }
      override fun getQualifiedName(item: NavigationItem): String? = (item as? PsiNamedElement)?.name
      override fun getQualifiedNameSeparator(): String = "."
  }
  ```

### 2.2 `net.internetisalie.lunar.lang.navigation.LuaGotoSymbolContributor`
- **Responsibility**: global functions/variables (+ classes/aliases) for Go to Symbol.
- **Key API** (`ChooseByNameContributorEx`): same shape over `LuaGlobalDeclarationIndex.KEY`
  (`LuaFuncDecl`) plus the two class/alias keys, so any named top-level symbol is reachable.

### 2.3 Presentation
Items are the indexed PSI (`LuaLocalVarDecl` / `LuaFuncDecl`), which implement
`NavigationItem`/`ItemPresentation`. Icons come from each element's `getIcon` — `@class`/`@alias`
→ class/alias icon, `LuaFuncDecl` → function icon (extend the elements' presentation if an icon
is missing). The location string is the containing file path.

## 3. Algorithms
No non-trivial algorithm — the contributors are thin adapters over `StubIndex.processAllKeys`
(name enumeration) and `StubIndex.getElements` (name → elements). Sorting/matching is the
platform's `ChooseByName` responsibility.

## 4. External Data & Parsing
None — `StubIndex` only.

## 5. Data Flow

### Example: Go to Class "MyClass" (NAV-03-01)
*Navigate ▸ Class* → platform calls `processNames` (lists keys incl. `MyClass`) → user picks it
→ `processElementsWithName("MyClass", …)` yields the `@class` `LuaLocalVarDecl` → navigates.

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| Same class name in two files | both elements returned; the popup disambiguates by location. |
| Alias and class with same name | both keys processed; both appear. |
| Non-project items toggle | honoured via `params.searchScope`/`isSearchInLibraries`. |
| Go to File | platform-handled via `LuaFileType` (no code). |

## 7. Integration Points

```xml
<!-- META-INF/plugin.xml, inside <extensions defaultExtensionNs="com.intellij"> -->
<gotoClassContributor  implementation="net.internetisalie.lunar.lang.navigation.LuaGotoClassContributor"/>
<gotoSymbolContributor implementation="net.internetisalie.lunar.lang.navigation.LuaGotoSymbolContributor"/>
```
- Reuses `LuaClassNameIndex`, `LuaAliasIndex`, `LuaGlobalDeclarationIndex`. No new index.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| NAV-03-01 Go to Class | M | §2.1 |
| NAV-03-02 Go to Symbol | M | §2.2 |
| NAV-03-03 Go to File | S | §6 (platform/`LuaFileType`) |
| NAV-03-04 Go to Alias | S | §2.1 (`LuaAliasIndex`) |

## 9. Alternatives Considered
- **`ChooseByNameContributorEx` vs the legacy `getNames`/`getItemsByName`**: the Ex variant
  streams from the index without materialising a `String[]` of all names — required by the
  performance constraint.

## 10. Open Questions

_None — feature has cleared the planning bar._
