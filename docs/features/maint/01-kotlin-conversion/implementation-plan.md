---
id: MAINT-01-PLAN
title: Kotlin Conversion Plan
type: plan
parent_id: MAINT-01
---

# Implementation Plan

> **Scope:** four files (`LuaTokenType`, `LuaCatsElementType`, `LuaPsiUtils`, `LuaPluginDisposable`).
> The two token-constant interfaces (`LuaTokenTypes`, `LuaCatsTokenTypes`) are **deferred to
> MAINT-19** — the generated lexers `implements` them; see requirements.md §Out of Scope.

## Phase 1: Convert leaf `IElementType` subclasses [Must]
- **Tasks**:
  - Convert `src/main/java/net/internetisalie/lunar/lang/psi/LuaTokenType.java` →
    `src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaTokenType.kt` (MAINT-01-03), as a normal
    public Kotlin `class` (not `object`, not `internal`). Delete the `.java` original.
  - Convert `src/main/java/net/internetisalie/lunar/luacats/lang/lexer/LuaCatsElementType.java` →
    `src/main/kotlin/net/internetisalie/lunar/luacats/lang/lexer/LuaCatsElementType.kt`
    (MAINT-01-04), as a normal public Kotlin `class`. Delete the `.java` original.
  - No dependency between these two files — convert in either order.
- **Verification**:
  - `tooling/gce-builder/gce-builder.sh run build` — clean compile (proves the generated
    `LuaElementTypes.java` / `LuaCatsElementTypes.java` still construct these via `new` unchanged).
  - `tooling/gce-builder/gce-builder.sh run test` — full suite green (no new failures).
  - `tooling/gce-builder/gce-builder.sh run "ktlintFormat ktlintCheck"` — zero new violations on the two touched files.
  - TC 1 (requirements.md): `LuaTokenType("(").toString()` == `"LuaTokenType.("`.
  - TC 2 (requirements.md): `LuaCatsElementType("LCATS_NAME").toString()` == `"LCATS_NAME"`.
  - TC 7: existing `TestLuaLexer.kt` / `TestLuaLexerExhaustive.kt` / `TestLuaCatsLexer.kt` pass unchanged.

## Phase 2: Convert `LuaPsiUtils` [Must]
- **Tasks**:
  - Convert `src/main/java/net/internetisalie/lunar/lang/psi/LuaPsiUtils.java` →
    `src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaPsiUtils.kt` (MAINT-01-02), as a Kotlin
    `object` with every method `@JvmStatic`. Delete the `.java` original.
  - Drop the wildcard import (`import com.intellij.psi.*;`) in favor of explicit imports; drop the
    now-unused `PsiTreeUtil` / `Condition` imports (used only in deleted comment blocks).
  - Delete the **three** commented dead-code blocks — lines **183-198**, **283-349**, **377-400** —
    not ported even as comments.
  - ⚠ **Preserve the LIVE methods at lines 350-375** — `toPsiElementArray` (350-359) and
    `hasDirectChildErrorElements` (361-375). They sit *inside* the old "283-401" span but are part
    of the public API (design.md §2.2). Do **not** delete a contiguous 283-401 range.
  - Make `nodeType()`'s return type explicitly `IElementType?`.
- **Verification**:
  - `run build`, `run test`, `run "ktlintFormat ktlintCheck"` (zero new violations on the touched file).
  - TC 3: `nodeType()` on a node-less element returns `null`, not an NPE.
  - TC 4: `findNextSibling` skips a matching-type sibling and returns the next one after it.
  - TC 5: manual diff confirms the three comment blocks are absent **and** `toPsiElementArray` /
    `hasDirectChildErrorElements` are present and unchanged.

## Phase 3: Convert `LuaPluginDisposable` [Must]
- **Tasks**:
  - Convert `src/main/java/net/internetisalie/lunar/LuaPluginDisposable.java` →
    `src/main/kotlin/net/internetisalie/lunar/LuaPluginDisposable.kt` (MAINT-01-06), as a Kotlin
    `class` (not `object`) with both `getInstance()` overloads as `@JvmStatic` companion functions.
    Delete the `.java` original.
  - Independent of Phases 1-2; may be done in parallel.
- **Verification**:
  - `run build`, `run test`, `run "ktlintFormat ktlintCheck"` (zero new violations on the touched file).
  - TC 6: both `getInstance()` (no-arg) and `getInstance(project)` resolve without ambiguity and
    return a non-null `Disposable`.

## Phase 4: Documentation cleanup [Should]
- **Tasks**:
  - Update `CLAUDE.md`'s "Key Pending TODOs" Kotlin-conversion line to record the
    **4-converted / 2-deferred (→ MAINT-19)** split (MAINT-01-10).
  - Update `docs/status-detail.md`'s MAINT-01 row to the same split.
- **Verification**:
  - `grep` confirms neither file still cites a bare/stale "4 Java files" count without the
    deferral note.

## Out of plan (deferred → MAINT-19)
`LuaTokenTypes.java` and `LuaCatsTokenTypes.java` are **not** converted by this plan. The generated
`_LuaLexer.java` (`implements FlexLexer, LuaTokenTypes`) and `_LuaCatsLexer.java`
(`implements FlexLexer, LuaCatsTokenTypes`) inherit their bare constants; converting them needs
`.flex` edits + a manual JFlex regeneration, owned by the MAINT-19 platform.syntax migration.
