# Change Log

## Unreleased

- **MAINT-04-07 (Complete)**: Removed LuaBindingsVisitor anti-pattern
  - Eliminated eager full-file PSI traversal for symbol resolution
  - Replaced with lazy, modern IntelliJ patterns (PsiReference, PsiScopeProcessor)
  - Removed ~420 lines of technical debt
  - Updated LuaParameterInfoHandler to use modern reference resolution
  - All reference, documentation, and annotation providers now use lazy resolution
- Add full Markdown support in Lua documentation (Quick Documentation popup)
- Support syntax highlighting for Lua code blocks in documentation
- Improve documentation comment detection and handling of blank lines
- Initial work
