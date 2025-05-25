# lunar

<!-- Plugin description -->
Lua support for IntelliJ Platform.
<!-- Plugin description end -->

### TODO

#### General

- Finish conversion to Kotlin

#### Files

- [x] Template file

#### Language

- [x] Statement structure from IDLua
- [x] Lua 5.4 syntax
  - `<const>`, `<close>` local variable attributes
- [ ] Luau syntax?
- Formatter
  - [Expressions](https://github.com/JetBrains/intellij-community/blob/6319a70ded4aa13b0f3544aa762392afe28461ae/plugins/groovy/src/org/jetbrains/plugins/groovy/formatter/blocks/GroovyBlock.java)
  - [x] Port Basic Spacing
  - [ ] Review `stylua` formatter
- Refactoring
  - [ ] Labels
  - Name Validator
- Insight
  - [x] Folding Builder
  - [x] Code Block Support
  - [x] Find Usages (Labels)
  - Bindings
    - Locals are early-bound
    - Globals are late-bound
    ```lua
      >   function hello() callit() end
      >   hello()
      stdin:1: attempt to call a nil value (global 'callit')
      stack traceback:
      stdin:1: in function 'hello'
      (...tail calls...)
      [C]: in ?
      >   local function callit () end
      >   hello()
      stdin:1: attempt to call a nil value (global 'callit')
      stack traceback:
      stdin:1: in function 'hello'
      (...tail calls...)
      [C]: in ?
      >   function callit() end
      >   hello()
      >
    ```
- [ ] Auto-complete
- [ ] Enter Handler
  - [x] Lua
  - [ ] LuaDOC
  - [x] LuaCATS

#### Project Tree

- [x] External Libraries
  - Lua Runtime SDK

#### Application Settings

- [x] Interpreter detection

#### Project Settings

- [ ] Language Level
  - [x] Lua 5.1
  - [x] Lua 5.2
  - [x] Lua 5.3
  - [x] Lua 5.4
  - [ ] Luau
- [ ] Runtime
  - [x] Lua PUC-RIO
  - [ ] LuaJIT
  - [ ] Luau?

#### Static Analysis

- [x] Luacheck
  - [x] Settings panel integration
  - [x] External Annotator

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
- [ ] Line markers
  - Recursive call
  - Tail call
- [ ] Return highlighter
- [ ] Access detector
- [ ] Go to symbol

#### Structure View

- [ ] Anonymous functions

#### Inline Documentation

- [ ] Documentation provider
  - [x] Plain 
  - [x] LuaDoc
  - [x] LuaCATS
- [ ] Documentation indexing
  - [ ] LuaDoc
  - [ ] LuaCATS
- [ ] Parameter Info

#### Execution

- [x] Run target
- [ ] Debug target
- [ ] Run Configuration validation
  - Script name
  - Interpreter
- [x] Interpreter arguments
- [x] Script arguments
