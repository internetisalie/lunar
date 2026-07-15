# Third-Party Notices

Lunar is licensed under the [Apache License, Version 2.0](LICENSE). It embeds,
derives from, or vendors source code from the third-party works listed below.
Each component is redistributed under its own license; the short-form
attribution required by Apache-2.0 §4(d) lives in [NOTICE](NOTICE).

This file is the authoritative registry — when code is copied in from, or
derived from, an upstream project, add a row here and a per-file header to the
affected files.

> **Scope:** this covers *source in the tree*. Binary/compiled build and runtime
> dependencies resolved by Gradle (the IntelliJ Platform, `lua-socket`, etc.) are
> not enumerated here; their licenses travel with those artifacts.

---

## Apache License 2.0

The following components are licensed under the same license as Lunar itself
(Apache-2.0). Their original copyright notices are preserved in each affected
file's header.

### Sylvanaar — "Lua for IDEA" plugin

- **Copyright:** 2010, 2011, 2016 Jon S Akhtar (Sylvanaar)
- **License:** Apache-2.0
- **Usage:** Derived source — lexer/token support, PSI utilities, syntax
  highlighting, code formatting, run/debug (DBGp) adapters, settings, and
  bundled resources.
- **Files:**
  - `src/main/kotlin/net/internetisalie/lunar/lang/format/LuaFormatBlock.kt`
  - `src/main/kotlin/net/internetisalie/lunar/lang/lexer/ExtendedSyntaxStrCommentHandler.kt`
  - `src/main/kotlin/net/internetisalie/lunar/lang/lexer/LuaTokenTypes.kt`
  - `src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaPsiUtils.kt`
  - `src/main/kotlin/net/internetisalie/lunar/lang/syntax/LuaColorSettingsPage.kt`
  - `src/main/kotlin/net/internetisalie/lunar/lang/syntax/LuaSyntax.kt`
  - `src/main/kotlin/net/internetisalie/lunar/luacats/lang/lexer/LuaCatsElementType.kt`
  - `src/main/kotlin/net/internetisalie/lunar/luacats/lang/syntax/LuaCatsSyntaxHighlighter.kt`
  - `src/main/kotlin/net/internetisalie/lunar/run/LuaCodeFragment.kt`
  - `src/main/kotlin/net/internetisalie/lunar/run/LuaDebugProcess.kt`
  - `src/main/kotlin/net/internetisalie/lunar/run/LuaDebugRunner.kt`
  - `src/main/kotlin/net/internetisalie/lunar/run/LuaDebugValue.kt`
  - `src/main/kotlin/net/internetisalie/lunar/run/LuaDebugVariable.kt`
  - `src/main/kotlin/net/internetisalie/lunar/run/LuaDebuggerController.kt`
  - `src/main/kotlin/net/internetisalie/lunar/run/LuaDebuggerEditorsProvider.kt`
  - `src/main/kotlin/net/internetisalie/lunar/run/LuaDebuggerEvaluator.kt`
  - `src/main/kotlin/net/internetisalie/lunar/run/LuaExecutionStack.kt`
  - `src/main/kotlin/net/internetisalie/lunar/run/LuaLineBreakpointType.kt`
  - `src/main/kotlin/net/internetisalie/lunar/run/LuaPosition.kt`
  - `src/main/kotlin/net/internetisalie/lunar/run/LuaStackFrame.kt`
  - `src/main/kotlin/net/internetisalie/lunar/run/LuaSuspendContext.kt`
  - `src/main/kotlin/net/internetisalie/lunar/settings/LuaApplicationSettings.kt`
  - `src/main/kotlin/net/internetisalie/lunar/settings/LuaApplicationSettingsPanel.kt`
  - `src/main/resources/codeStyle/preview/codeStyle.lua`
  - `src/main/resources/net/internetisalie/lunar/LuaBundle.properties`

### JetBrains — IntelliJ Platform / IntelliJ IDEA Community Edition

- **Copyright:** 2000-2024 JetBrains s.r.o. and contributors
- **License:** Apache-2.0
- **Usage:** Derived source — formatting model and the LuaCATS lexer.
- **Files:**
  - `src/main/kotlin/net/internetisalie/lunar/lang/format/LuaFormatBlock.kt`
    *(also carries the Sylvanaar copyright above)*
  - `src/main/kotlin/net/internetisalie/lunar/lang/syntax/LuaEditorHighlighter.kt`
  - `src/main/kotlin/net/internetisalie/lunar/luacats/lang/lexer/luacats.flex`
  - `src/main/gen/net/internetisalie/lunar/luacats/lang/lexer/_LuaCatsLexer.java`
    *(generated from `luacats.flex`)*

### EmmyLua

- **Copyright:** (c) 2017 tangzx (love.tangzx@qq.com)
- **License:** Apache-2.0
- **Usage:** Derived source — Lua language-level model and platform library
  provisioning.
- **Files:**
  - `src/main/kotlin/net/internetisalie/lunar/lang/LuaLanguageLevel.kt`
  - `src/main/kotlin/net/internetisalie/lunar/project/PlatformLibraryProvider.kt`

### Max Ishchenko

- **Copyright:** 2009 Max Ishchenko
- **License:** Apache-2.0
- **Usage:** Derived source — syntax highlighter.
- **Files:**
  - `src/main/kotlin/net/internetisalie/lunar/lang/syntax/LuaSyntaxHighlighter.kt`

---

## MIT License

### MobDebug

- **Copyright:** 2011-2023 Paul Kulchenko. Based on RemDebug 1.0, Copyright
  Kepler Project 2005.
- **License:** MIT
- **Version:** 0.805
- **Usage:** Vendored verbatim — the remote Lua debugger runtime shipped with
  the plugin.
- **Files:**
  - `src/main/lua/mobdebug/init.lua`
- **Upstream:** https://github.com/pkulchenko/MobDebug

### `lua.l` flex lexer

- **Copyright:** "Same as Lua" (the Lua/MIT license)
- **License:** MIT
- **Usage:** Derived source — the JFlex lexer grammar for the Lua language is
  adapted from the `lua.l` flex lexer for Lua 5.1.
- **Files:**
  - `src/main/kotlin/net/internetisalie/lunar/lang/lexer/lua.flex`
  - `src/main/gen/net/internetisalie/lunar/lang/lexer/_LuaLexer.java`
    *(generated from `lua.flex`)*

### Lua standard-library API stubs (Lua.org, PUC-Rio)

- **Copyright:** (c) 1994-2025 Lua.org, PUC-Rio
- **License:** MIT (the Lua license)
- **Usage:** Type-annotation stubs describing the Lua 5.1-5.4 standard-library
  API surface, used for completion, navigation, and type inference. Each file
  carries the full MIT license header.
- **Files:** `src/main/resources/runtime/standard/lua-5.{1,2,3,4}/*.lua`
  (39 files: `builtin`, `coroutine`, `debug`, `io`, `math`, `os`, `package`,
  `string`, `table`, plus `bit32` (5.2) and `utf8` (5.3, 5.4)).
- **Upstream:** https://www.lua.org/

---

## License texts

- **Apache-2.0** — see [LICENSE](LICENSE).
- **MIT (MobDebug)** — the notice is preserved inline at the top of
  `src/main/lua/mobdebug/init.lua`.
- **MIT (Lua)** — the full notice is preserved at the top of each stub file
  under `src/main/resources/runtime/standard/`.

## Maintaining this file

Run the provenance sweep to find copyright holders that may be missing a row:

```bash
git grep -hiE 'copyright' -- 'src/main/**' \
  | sed -E 's/^[[:space:]#*/-]+//' | sort | uniq -c | sort -rn
```

Ignore matches that are version-banner *test fixtures* (e.g.
`"Lua 5.4.6  Copyright (C) … PUC-Rio"`, `"LuaJIT … Mike Pall"`) under
`toolchain/` tests and docs — those are parsed data, not incorporated code.
