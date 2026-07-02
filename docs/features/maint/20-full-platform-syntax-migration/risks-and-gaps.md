---
id: "MAINT-20-RISKS"
title: "Risks & Gaps"
type: "risk"
priority: "low"
parent_id: "MAINT-20"
folders:
  - "[[features/maint/20-full-platform-syntax-migration/requirements|requirements]]"
---

# MAINT-20: Risks & Gaps

Scope pivoted 2026-07-02 from "full platform.syntax migration" to "headless parser/lexer
generation". The pivot removed the high-uncertainty toolchain risks; the remaining risks are small
and bounded.

## Current Risks

### Risk 1.1: Grammar-Kit version drift rewrites generated code
- **Impact**: a regen with a different Grammar-Kit version rewrites internal method names
  (observed: `var` → `var_$`, 18 lines in `LuaParser.java`), so a routine regen produces a noisy,
  confusing diff — or, worse, a silent partial rewrite committed by accident.
- **Likelihood**: certain (already observed) — but harmless behaviorally.
- **Mitigation**: Phase 1 decides one canonical version — pin the version that reproduces the
  committed files (route A), or adopt 2023.3.2 and regenerate **all** of `src/main/gen` atomically
  (route B). Either way the tree is left in a state where a regen over unchanged sources is a
  no-op (MAINT-20-05 / TC-5).

### Risk 1.2: Grammar-Kit jar not present on a given machine
- **Impact**: `org.intellij.grammar.Main` can't run.
- **Likelihood**: low on dev machines (any installed JetBrains IDE bundles the Grammar-Kit plugin);
  higher in a bare CI container.
- **Mitigation**: ordered resolver with an explicit `$GRAMMAR_KIT_JAR` override and a **loud abort**
  if none is found (MAINT-20-02). Document the override in the skill. The gce-builder VM has the
  GoLand SDK; sourcing the Grammar-Kit jar there (or vendoring one) is the CI story if needed.

### Risk 1.3: Circular-dependency staging regression
- **Impact**: if the compile-first / revert-14-stubbed-files staging is broken, generation emits
  parser code that doesn't compile, or clobbers the custom `*Impl` inheritance.
- **Likelihood**: low — the staging already exists and works in `generate.sh`.
- **Mitigation**: keep the staging verbatim (design §"binding constraint"); Phase 4 `gce-builder run
  build` is the regression gate (MAINT-20-06).

## Superseded — platform.syntax premise (kept for the record)

The original MAINT-20 aimed to emit **Kotlin** `SyntaxElementType` lexers/parser via a
JetBrains-internal JFlex skeleton and Grammar-Kit `parser-api="syntax"`. Findings that killed it:

- **JFlex/Grammar-Kit emit Java natively.** Standard practice (incl. JetBrains IDEA-plugin work) is
  to generate Java into `src/main/gen` and call it from Kotlin. Forcing Kotlin emission adds a
  fragile toolchain dependency for zero functional gain — Kotlin's Java interop already makes the
  generated Java a first-class callee (exactly the MAINT-19 arrangement).
- **The Kotlin skeleton half-works and is pointless.** `jflex-1.9.2 --skel idea-flex-kotlin.skeleton`
  applies the Kotlin frame but still emits the action dispatch as a Java `switch/case`, yielding a
  non-compiling Java/Kotlin hybrid. The Kotlin-emitting JFlex fork is JetBrains-internal. Irrelevant
  now — we keep Java output.
- **`SyntaxElementType` Java lexer is *not* actually blocked** (for the record): a Java
  `%type SyntaxElementType %implements FlexLexer` lexer compiled against `syntax-261` after only a
  skeleton tweak (drop `throws IOException`) and using `SyntaxTokenTypes` via getters. And the local
  `grammar-kit-2023.3.2.jar` *does* carry `syntaxElementTypeHolderClass` support. So platform.syntax
  was feasible — just not worth it. The pivot is a value decision, not a feasibility wall.

## Headless proof (evidence)

- `org.intellij.grammar.Main src/main/gen lua.bnf` with
  `grammar-kit-2023.3.2.jar:<goland-lib>/*:build/classes/kotlin/main` → `lua.bnf parser generated to
  /tmp/gktest`, 111 Java files emitted; `getDocComment … method not found` = expected stub-strip.
- Diff vs committed `LuaParser.java`: 18 lines, all `var` ↔ `var_$` (Grammar-Kit soft-keyword escaping).
- JFlex headless lexer regen: already proven byte-identical in MAINT-19.

## Technical Debt & Future Work
- **Grammar-Kit Gradle plugin** stays unwired (can't express the mid-generation stub revert). If the
  circular dependency is ever refactored away (e.g. `LuaPsiImplUtil` split so generated code needs
  no back-reference), the plugin's `generateParser`/`generateLexer` tasks could replace the script.
- **CI without an IDE**: vendor a known-good Grammar-Kit jar (or add a resolvable dependency) so the
  gce-builder / CI can regenerate without a full IDE install.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Predecessor findings: [[features/maint/19-platform-syntax-migration/risks-and-gaps|MAINT-19 Risks & Gaps]]
