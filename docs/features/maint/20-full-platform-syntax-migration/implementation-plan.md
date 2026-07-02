---
id: "MAINT-20-PLAN"
title: "Implementation Plan"
type: "plan"
priority: "low"
parent_id: "MAINT-20"
folders:
  - "[[features/maint/20-full-platform-syntax-migration/requirements|requirements]]"
---

# MAINT-20: Implementation Plan

> **Precondition (hard gate).** Phase 0 (the DR spikes in `risks-and-gaps.md`, DR-01…DR-04)
> MUST complete with all success criteria met before any of Phases 1–5 begin. If DR-01 or
> DR-02 fails (the toolchain cannot emit Kotlin syntax lexer/parser in this checkout), the
> feature stays `todo` and the finding is recorded — do NOT proceed to hand-write generated
> artifacts.

## Toolchain (reference, used by Phases 1–3)

All commands run from the repo root; generation is the manual human-in-the-loop step (grammar-kit
Gradle plugin stays unwired, per CLAUDE.md). Paths verified in `~/Documents/src/lua/intellij-community`.

- **Syntax lexer (Kotlin)**: JFlex with the Kotlin skeleton —
  `java -jar jflex-1.9.2.jar --skel ~/Documents/src/lua/intellij-community/tools/lexer/idea-flex-kotlin.skeleton -d <gen-dir> <flex-file>`.
  (DR-01 confirms 1.9.2 accepts this skeleton; if not, DR-01 pins the JFlex version that does.)
- **Syntax parser + holders + converter**: Grammar-Kit `org.intellij.grammar.Main` on `lua.bnf`
  carrying `generate=[parser-api="syntax"]` (as `.claude/skills/generate-parser/scripts/generate.sh`
  already invokes `org.intellij.grammar.Main`, extended per DR-02).
  (DR-02 confirms the resolved grammar-kit version supports `parser-api="syntax"`; if not, DR-02
  pins the version/jar that does.)

## Phases

### Phase 0: De-risking spikes [Must] — GATE
- **Goal**: prove the syntax-emitting toolchain works in *this* checkout and the platform.syntax EPs
  are available at Lunar's 261 runtime.
- **Tasks**:
  - [ ] Execute DR-01, DR-02, DR-03, DR-04 (risks-and-gaps.md) and record outcomes inline.
- **Exit criteria**: all four DR success criteria met; otherwise STOP and keep feature `todo`.

### Phase 1: Generated element-type holders + converter [Must]
- **Goal**: syntax holders and the converter exist and round-trip to the classic singletons.
- **Tasks**:
  - [ ] Add `generate=[parser-api="syntax"]`, `syntaxElementTypeHolderClass`,
        `elementTypeConverterFactoryClass` options to `lua.bnf` and `luacats.bnf` — realizes design §7.
  - [ ] Generate `LuaSyntaxElementTypes` / `LuaCatsSyntaxElementTypes` and
        `LuaElementTypeConverterFactory` (+ LuaCATS converter) — realizes design §2.1, §2.2, §2.7.
  - [ ] Verify name-string parity with classic holders — realizes design §3.3.
- **Exit criteria**: TC-2, TC-5; converter returns the same classic instance for each syntax type.

### Phase 2: Syntax lexers [Must]
- **Goal**: `_LuaLexer.kt` / `_LuaCatsLexer.kt` generated and wrapped in `LuaLexer`/`LuaCatsLexer`.
- **Tasks**:
  - [ ] Edit `lua.flex` / `luacats.flex` to `%type SyntaxElementType`, Kotlin header/imports
        (package + `com.intellij.platform.syntax.*`), returning `LuaSyntaxElementTypes` members —
        realizes design §2.3.
  - [ ] Regenerate `_LuaLexer.kt` / `_LuaCatsLexer.kt` (Kotlin skeleton); delete the `.java` lexers —
        realizes design §2.3.
  - [ ] Rewire `LuaLexer` innermost adapter to the syntax `FlexAdapter` + converter boundary
        (design §3.2 option A), preserving the merging chain — realizes design §2.4.
- **Exit criteria**: TC-1, TC-7, TC-8 (merged tokens identical to baseline).

### Phase 3: Syntax parser + language definition [Must]
- **Goal**: platform.syntax parser drives PSI construction.
- **Tasks**:
  - [ ] Generate `LuaSyntaxParser` (and any `LuaSyntaxParserUtil` if `parserUtilClass` is set);
        remove classic `LuaParser` — realizes design §2.5.
  - [ ] Hand-write `LuaLanguageDefinition` — realizes design §2.6.
- **Exit criteria**: TC-3; `LuaLanguageDefinition` compiles and overrides all four members.

### Phase 4: File-element-type + parser-definition re-wire [Must]
- **Goal**: parsing runs through platform.syntax while stubs stay intact.
- **Tasks**:
  - [ ] Edit `LuaFileElementType.doParseContents` to the §3.1 bridge; bump `getStubVersion` 2→3;
        keep `getBuilder`/`serialize`/`deserialize` — realizes design §2.8, §3.1.
  - [ ] Edit `LuaParserDefinition`: `createLexer`→`LuaLexer()`, `createParser`→throw, `FILE` unchanged —
        realizes design §2.9.
  - [ ] Register `<syntax.syntaxDefinition>` and `<syntax.elementTypeConverter>` in `plugin.xml` —
        realizes design §7.
- **Exit criteria**: TC-4, TC-9, TC-11; plugin loads in `runIde`.

### Phase 5: Equivalence verification + docs [Must/Should]
- **Goal**: prove byte-for-byte equivalence and document the toolchain.
- **Tasks**:
  - [ ] Add a PSI-tree snapshot test asserting the parsed tree for a representative corpus is
        identical to a pre-migration baseline (TC-6) — realizes MAINT-20-09.
  - [ ] Run full suite `gce-builder run test`; confirm 0 new failures, no test source edited (TC-10).
  - [ ] [Should] Update `.claude/skills/generate-parser/` to document the syntax-emitting path
        (Kotlin skeleton flag + `parser-api="syntax"`) — realizes MAINT-20-12.
- **Exit criteria**: TC-6, TC-10 green; skill updated.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-20-01 | M | Phase 2 |
| MAINT-20-02 | M | Phase 2 |
| MAINT-20-03 | M | Phase 1 |
| MAINT-20-04 | M | Phase 3 |
| MAINT-20-05 | M | Phase 3 |
| MAINT-20-06 | M | Phase 1 |
| MAINT-20-07 | M | Phase 4 |
| MAINT-20-08 | M | Phase 4 |
| MAINT-20-09 | M | Phase 5 |
| MAINT-20-10 | M | Phase 2 |
| MAINT-20-11 | S | Phase 1 |
| MAINT-20-12 | S | Phase 5 |

## Verification Tasks
- [ ] PSI-tree snapshot + token-stream tests — cover TC-6, TC-7.
- [ ] Stub/index regression (class-name index hit on a `return M` module) — covers TC-9.
- [ ] Full suite unmodified — covers TC-10.
- [ ] Sandbox load + parse smoke via `runIde` — covers TC-4, TC-11.
- [ ] Run [human-verification-checklists](../../../features/maint/20-full-platform-syntax-migration/requirements.md) items where applicable.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 0: De-risking spikes (GATE) | todo | Must |
| Phase 1: Holders + converter | todo | Must |
| Phase 2: Syntax lexers | todo | Must |
| Phase 3: Parser + language definition | todo | Must |
| Phase 4: File-element-type + parser-def re-wire | todo | Must |
| Phase 5: Equivalence verification + docs | todo | Must |
