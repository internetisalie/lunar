---
id: REFACT-05-DESIGN
title: "Technical Design"
type: design
parent_id: REFACT-05
status: done
priority: "medium"
folders:
  - "[[features/refactoring/05-name-validator/requirements|requirements]]"
---

# Technical Design: REFACT-05 Rename Names Validator

## 1. Architecture Overview

- **New component:** `net.internetisalie.lunar.refactoring.LuaNamesValidator`
  - The package `net.internetisalie.lunar.refactoring` already exists and holds the other
    rename/refactoring support classes — `LuaIntroduceVariableHandler.kt`,
    `LuaSafeDeleteProcessor.kt`
    (`src/main/kotlin/net/internetisalie/lunar/refactoring/`). The skeleton originally proposed
    `net.internetisalie.lunar.lang.refactoring.rename.LuaNamesValidator`; that sub-package does
    **not** exist, and creating it would split refactoring code across two near-identical
    package names. **Decision:** place the validator in the existing
    `net.internetisalie.lunar.refactoring` package. (No code currently lives in any
    `lang.refactoring.rename` package.)
- **Implements:** `com.intellij.lang.refactoring.NamesValidator`
  (`platform/analysis-api/src/com/intellij/lang/refactoring/NamesValidator.java`).
- **Class shape (Kotlin):**
  ```kotlin
  package net.internetisalie.lunar.refactoring

  import com.intellij.lang.refactoring.NamesValidator
  import com.intellij.openapi.project.Project
  import net.internetisalie.lunar.lang.LuaKeywords

  class LuaNamesValidator : NamesValidator {
      override fun isKeyword(name: String, project: Project?): Boolean =
          LuaKeywords.isReserved(name)

      override fun isIdentifier(name: String, project: Project?): Boolean =
          IDENTIFIER_PATTERN.matches(name) && !LuaKeywords.isReserved(name)

      private companion object {
          private val IDENTIFIER_PATTERN = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
      }
  }
  ```

### Exact platform interface signatures (verified)

From `NamesValidator.java` (lines 22, 31):
- `boolean isKeyword(@NotNull String name, Project project)`
- `boolean isIdentifier(@NotNull String name, Project project)`

`name` is `@NotNull`; `project` has **no** nullability annotation and the platform may pass
`null`, so the Kotlin override declares `project: Project?`. Parameter **order is**
`(name, project)`. This matches the platform's own Kotlin validators, e.g.
`KotlinNamesValidator` overrides `isKeyword(name: String, project: Project?)` /
`isIdentifier(name: String, project: Project?)`
(`plugins/kotlin/.../refactoring/KotlinNamesValidator.kt`).

## 2. Core Algorithms

### 2.1 Keyword set source — `LuaKeywords.RESERVED` (grounded)

The keyword set is `net.internetisalie.lunar.lang.LuaKeywords.RESERVED`
(`src/main/kotlin/net/internetisalie/lunar/lang/LuaKeywords.kt:8`), a `Set<String>` of all 22
Lua reserved words for Lua 5.1–5.4:

```
and break do else elseif end false for function goto if in
local nil not or repeat return then true until while
```

`isKeyword(name)` delegates to `LuaKeywords.isReserved(name)`
(`LuaKeywords.kt:15`), which is `word in RESERVED` — an exact, case-sensitive string-set
membership test.

> **Do NOT use `LuaSyntax.KeywordTokens`** (`lang/syntax/LuaSyntax.kt:46`). That `TokenSet` is
> a *highlighting* set, not a reserved-word source: it omits `nil`/`true`/`false` (which live in
> `PredefinedConstantTokens`, `LuaSyntax.kt:79`) and includes non-reserved tokens
> `LuaTokenTypes.WITH` and `LuaTokenTypes.CONTINUE` (`LuaSyntax.kt:53,64`). Deriving the keyword
> set from it would both miss real keywords and falsely reject `with`/`continue`.
> `LuaKeywords.RESERVED` is the single authoritative, complete list.

### 2.2 Identifier grammar — ASCII regex

`isIdentifier(name)` returns `true` iff:
1. `name` matches `^[A-Za-z_][A-Za-z0-9_]*$` (a letter or underscore, then letters/digits/
   underscores), **and**
2. `!isKeyword(name)`.

The regex is intentionally **ASCII-only** — Lua identifiers are ASCII (no Unicode letter
classes), so `\w`/Unicode categories must not be used. The empty string fails clause (1).
Clause (2) enforces the platform convention that a keyword is not a valid identifier, so the
rename UI rejects a keyword name via either predicate.

This mirrors the Lua identifier grammar already used in the lexer (`lang/lexer/LuaLexer.flex`,
identifier rule `[a-zA-Z_][a-zA-Z0-9_]*`). The regex is preferred over invoking the lexer: it is
allocation-light, EDT-safe, and has no PSI/VFS dependency, matching the pattern in
`KotlinNamesValidator` (a pure string check).

## 3. Integration Points

Registered declaratively in `src/main/resources/META-INF/plugin.xml`, next to the existing
`<lang.refactoringSupport language="Lua" .../>` block (around line 206):

```xml
<lang.namesValidator
        language="Lua"
        implementationClass="net.internetisalie.lunar.refactoring.LuaNamesValidator"/>
```

EP confirmed: `lang.namesValidator` is declared in
`platform/refactoring/resources/META-INF/RefactoringExtensionPoints.xml:7` as a
`LanguageExtensionPoint` (attributes `language` + `implementationClass`); reference usages
include `JavaPlugin.xml:1288`, `refactorings.xml:23` (Kotlin), and
`intellij.properties.backend.xml:68` (Properties). The default when unregistered is
`DefaultNamesValidator` (`LanguageNamesValidation.java:13`).

## 4. Prior Art (this repo)

- **No existing `NamesValidator`.** `grep -rn "NamesValidator" src` finds no Lua implementation
  and no `<lang.namesValidator>` in `plugin.xml`; Lua currently falls back to the platform
  `DefaultNamesValidator`, which does not know Lua keywords. This feature is **new**.
- **Rename wiring exists.** `LuaRefactoringSupportProvider`
  (`src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaRefactoringSupportProvider.kt:17`,
  registered at `plugin.xml:206`) already provides in-place rename for labels (REFACT-01). The
  names validator is a **separate, orthogonal** extension point — it does not modify the support
  provider; it is consulted by the platform rename pipeline independently.
- **`LuaKeywords` already exists and is reused** for import-name collision avoidance, so this
  feature adds zero new keyword data.

## 5. Open Questions

None — the language-level-vs-union decision for `goto` is resolved in favour of the union (resolved in risks-and-gaps Gap 2.1).
