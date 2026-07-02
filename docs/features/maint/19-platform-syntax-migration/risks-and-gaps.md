---
id: "MAINT-19-RISKS"
title: "MAINT-19: Risks & Gaps"
type: "risk"
priority: "low"
parent_id: "MAINT-19"
folders:
  - "[[features/maint/19-platform-syntax-migration/requirements|requirements]]"
---

# MAINT-19: Risks & Gaps

## Risks

| ID | Risk | Likelihood | Impact | Mitigation |
|----|------|-----------|--------|------------|
| R-1 | Grammar-Kit CLI strips stub inheritance during headless regen (known Catch-22). | Med | Med | This feature regenerates **lexers only** (JFlex), not the Grammar-Kit parser, so the stub-stripping bug (`.claude/skills/generate-parser/SKILL.md` step 6) does not apply. If the human uses the full `generate.sh`, verify `src/main/gen` PSI/stub files are unchanged and `git checkout` any spuriously-touched ones. |
| R-2 | Kotlin `object` `@JvmField` does not expose bare static fields to the generated Java lexer via `import static`. | Low | High | Established idiom (`lang/LuaIcons.kt`, `lang/format/LuaCodeStyleSettings.kt` use `@JvmField`). Compile in Phase 3 is the gate; if it fails, fall back to a top-level Kotlin `object` remains correct — `@JvmField` on an `object` member always emits `public static final` on the object class. |
| R-3 | Debug-name string drift changes `IElementType.toString()`, breaking string-asserting tests. | Low | High | Port strings byte-for-byte (design rule); Phase 3 runs the exhaustive lexer suites unmodified — any drift fails TC-05/06/07. |
| R-4 | Manual JFlex handoff produces a lexer with an unintended body diff. | Low | Med | Phase 2 verification requires the generated-file diff be limited to the class declaration + `import static` line; reject any transition-table change. |

## De-risking Tasks

| ID | Task | Priority | Notes |
|----|------|----------|-------|
| MAINT-19-00-1 | **Assess full `com.intellij.platform.syntax` migration for a future wave.** Determine whether a `SyntaxElementType`-emitting JFlex skeleton and a `SyntaxGeneratedParserRuntime`-emitting Grammar-Kit generator can be vendored into Lunar's build (the tooling JetBrains uses for `json/syntax`, `java/java-syntax`). If yes, scope a follow-up feature to introduce `LanguageSyntaxDefinition` (cf. `intellij-community/json/syntax/.../JsonLanguageDefinition.kt`), migrate `lua.flex`/`luacats.flex` to `%type SyntaxElementType`, and port the parser. **Deferred**: the required generators are not present in this checkout's Grammar-Kit (`org.intellij.grammar.Main`, classic options) and the gradle plugin is unwired (CLAUDE.md). | Should | Grounds the roadmap's "platform.syntax" title; this is the real migration, out of MAINT-19's achievable scope. |

## Grounding gaps / could-not-verify

- **JFlex header pass-through of `import static`**: verified indirectly — JFlex copies the pre-`%%`
  header verbatim (the current header already carries `import java.lang.reflect.Field;` etc. through to
  `_LuaLexer.java:9`), so a `import static` line will pass through identically. Not executed headlessly
  here (regeneration is the human-in-the-loop Phase 2b); Phase 3 compile is the definitive gate.
- **No syntax-emitting generator in Lunar's build**: confirmed by absence —
  `grep -rln 'SyntaxTreeBuilder\|generateFromSyntax' tools/grammar-kit*` in
  `~/Documents/src/lua/intellij-community` returned nothing, and Lunar's `lua.bnf` uses classic
  options only. This is the basis for scoping the full migration out.
