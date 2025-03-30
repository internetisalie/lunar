# lunar

### TODO

#### Language

- [ ] Statement structure from IDLua
- [ ] Lua 5.4 syntax
  - `<const>`, `<close>` local variable attributes
- [ ] Luau syntax?

#### Project Tree

- [ ] External Libraries
  - Lua Runtime SDK

#### Application Settings

- [x] Interpreter detection

#### Project Settings

- [ ] Language
  - Lua 5.1
  - Lua 5.2
  - Lua 5.3
  - Lua 5.4
  - Luau
- [ ] Runtime
  - Redis
  - LOVE
  - Tarantool

```xml
<projectConfigurable
      parentId="tools"
      instance="com.example.ProjectSettingsConfigurable"
      id="com.example.ProjectSettingsConfigurable"
      displayName="My Project Settings"
      nonDefaultProject="true"/>
```

#### Static Analysis

- Luacheck

#### Refactoring

- [ ] Stubs for declaring identifiers

#### Navigation
- [ ] Reference Contributors
  - From `com.intellij.psi.PsiReferenceContributor`: 
  - The contributed references may then be obtained via 
    `PsiReferenceService.getReferences(PsiElement, PsiReferenceService.Hints)`, 
    which is the preferred way. Some elements return them from `PsiElement.getReferences()`
    directly, though, but one should not rely on that behavior since it may be 
    changed in the future. 
  - Note that, if you're implementing a custom language, it won't by default 
    support references registered through PsiReferenceContributor. If you want
    to support that, you need to call 
    `com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry.getReferencesFromProviders(PsiElement)`
    from your implementation of PsiElement.getReferences(). 
- [ ] Bindings
  - Use CachedValuesManager for:
    - `getReferences`
    - `getFileGlobals`
    
    Can automatically invalidate cache on PsiElement change in project.

#### Structure View

- [ ] Anonymous functions

#### LuaDoc

- [ ] Documentation provider

#### Execution

- [x] Run target
- [ ] Debug target
- [ ] Run Configuration validation
  - Script name
  - Interpreter
  - 
