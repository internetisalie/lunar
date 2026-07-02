---
id: "MAINT-20"
title: "MAINT-20: Headless Parser & Lexer Generation (no IDE handoff)"
type: "feature"
status: "planned"
priority: "low"
parent_id: "MAINT"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-20: Headless Parser & Lexer Generation (no IDE handoff)

> **Scope pivot (2026-07-02).** This feature was originally "Full platform.syntax Migration"
> (emit `SyntaxElementType` Kotlin lexers/parser). That premise was abandoned: JFlex/Grammar-Kit
> natively emit **Java**, and the standard practice — including JetBrains' own IDEA-plugin work —
> is to let them generate Java into `src/main/gen` and call it from Kotlin. Forcing Kotlin emission
> (JetBrains-internal skeleton, `parser-api="syntax"`) buys nothing here. The genuinely valuable,
> now-**proven** goal is to make generation **fully automatic and headless** — eliminating the
> manual "human right-clicks `lua.bnf` → Generate Parser Code in the IDE" handoff documented in
> CLAUDE.md. See [risks-and-gaps.md](risks-and-gaps.md) §"Superseded" for the abandoned
> platform.syntax findings and §"Headless proof" for the evidence.

## Overview

Follow-up to MAINT-19 (done). Today, adding or changing a language feature requires a manual IDE
step: the agent edits `lua.bnf` / `lua.flex`, then **pauses and hands off to a human** who
right-clicks the grammar in IntelliJ to run *Generate Parser Code* / *Run JFlex Generator*, commits
`src/main/gen/`, and hands back (CLAUDE.md → "Note on Lexer/Parser Generation"). That handoff is the
only step in the whole lexer/parser workflow an agent cannot perform, and it blocks autonomous
grammar work.

MAINT-19 already proved the **JFlex** half runs headless (the JFlex CLI regenerated `_LuaLexer.java`
byte-identically). This feature closes the remaining gap — **headless Grammar-Kit parser
generation** — and wires both into a single reproducible command, so grammar changes need no IDE.

Parent epic: [[features/maint/requirements|MAINT]]. Predecessor:
[[features/maint/19-platform-syntax-migration/requirements|MAINT-19]].

## Scope

### In Scope
- Make `.claude/skills/generate-parser/scripts/generate.sh` locate a working Grammar-Kit jar
  **without relying on an ad-hoc Gradle-cache hit**. A usable jar ships with any installed
  JetBrains IDE's bundled Grammar-Kit plugin
  (`~/.local/share/JetBrains/<IDE>/grammar-kit/lib/grammar-kit-*.jar`, containing
  `org.intellij.grammar.Main`); the script must resolve it deterministically (explicit path /
  env override / documented fallback order) and fail loudly if none is found.
- Run `org.intellij.grammar.Main` **headless** to regenerate the classic **Java** parser + PSI
  (`LuaParser.java`, `Lua*` PSI/impl) for both `lua.bnf` and `luacats.bnf`, keeping the existing
  circular-dependency workaround (compile Kotlin → stage classes on the generator classpath →
  revert the 14 hand-stubbed generated files → recompile).
- Keep the JFlex lexer generation headless (already working) for `lua.flex` / `luacats.flex`.
- **Resolve generator-version output drift**: the bundled Grammar-Kit version must produce output
  matching (or an accepted, documented, wholesale-regenerated superset of) the committed
  `src/main/gen/`. The one known drift is a cosmetic `var` → `var_$` internal method-name escaping
  from a Grammar-Kit version difference (see risks). Either pin the version that reproduces the
  committed files, or adopt the new version and regenerate/commit **all** affected files in one go.
- Update CLAUDE.md / `.agents/AGENTS.md` ("Add a language feature" + "Note on Lexer/Parser
  Generation") to replace the human right-click handoff with the headless command.

### Out of Scope
- **Any `com.intellij.platform.syntax` / `SyntaxElementType` migration** — abandoned (superseded
  premise; see the pivot note above). The classic `%type IElementType` + `JavaParserGenerator`
  path MAINT-19 delivered on stays the supported representation.
- Wiring the Grammar-Kit **Gradle plugin** as a build task — it stays unwired due to the Kotlin
  circular-dependency issue (`LuaPsiImplUtil` resolution) documented in CLAUDE.md; the staged
  script *is* the supported mechanism. (Revisiting the Gradle plugin is future work.)
- Any change to Lua/LuaCATS grammar rules, tokens, or the produced PSI tree — this is a tooling /
  developer-workflow change; generated output must stay functionally identical.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| MAINT-20-01 | **Headless parser generation** | M | `generate.sh` runs `org.intellij.grammar.Main <gen-dir> lua.bnf` (and `luacats.bnf`) to completion with **no IDE and no human interaction**, emitting the Java parser + PSI into `src/main/gen`. Mechanism proven 2026-07-02 **with compiled classes staged** (the run reused `build/classes/kotlin/main`); the classes are load-bearing (without them 66/111 PSI files lose their accessors), so the compile-first step is required and the clean-checkout end-to-end run is Phase 4's gate. |
| MAINT-20-02 | **Deterministic Grammar-Kit jar resolution** | M | The script resolves a Grammar-Kit jar via a documented order (explicit `$GRAMMAR_KIT_JAR` env → installed-IDE `grammar-kit/lib/grammar-kit-*.jar` → Gradle cache) and **exits with a clear error** if none contains `org.intellij.grammar.Main`. No silent skip. |
| MAINT-20-03 | **Headless lexer generation** | M | `generate.sh` regenerates `_LuaLexer.java` / `_LuaCatsLexer.java` via the JFlex CLI (already headless per MAINT-19), returning `IElementType` through the MAINT-19 `import static` holders. |
| MAINT-20-04 | **Circular-dependency staging preserved** | M | The script compiles Kotlin first, stages `build/classes/kotlin/main` onto the generator classpath, and reverts the 14 hand-stubbed generated files (`LuaBlock`, `LuaFuncDecl`, … + `*Impl`) so custom inheritance survives — matching current `generate.sh` behavior. |
| MAINT-20-05 | **Output parity with committed gen** | M | A headless run over unchanged `.bnf`/`.flex` produces `src/main/gen/` **byte-identical** to what is committed — OR any diff is a documented, version-attributable, wholesale-regenerated change (the known `var`→`var_$` escaping) committed atomically, never a silent mismatch. |
| MAINT-20-06 | **Verified clean compile after generation** | M | After generation the project compiles green via `gce-builder run build` (or `compileKotlin`), proving the regenerated Java + Kotlin call sites still align. No test source edited. |
| MAINT-20-07 | **Docs de-manualized** | M | CLAUDE.md / `.agents/AGENTS.md` "Add a language feature" no longer instruct the agent to pause for a human IDE step; they point to the headless command. The stale "grammar-kit plugin not used → generate manually via the IDE" note is corrected to describe the headless path. |
| MAINT-20-08 | **Skill documents the toolchain** | S | `.claude/skills/generate-parser/` documents jar resolution, the staging workaround, and the version-pinning requirement, so regeneration is reproducible on a fresh checkout. |

## Behavior Rules
- **No grammar/PSI change.** Any diff to the produced PSI tree, token stream, or stub keys from a
  generation run over unchanged sources is a defect. Method-name escaping internal to the parser
  (`var_$`) is acceptable only if version-attributed, documented, and applied wholesale.
- **Fail loud, never silent.** Missing generator jar, missing skeleton, or a non-clean post-gen
  compile must abort with a clear message — the old `generate.sh` "Warning: … Skipping lexer
  generation" soft-skip is not acceptable for the parser step.
- **Local `./gradlew` stays inside the script only.** The script's own `compileKotlin` staging is
  the documented exception; agents still use `gce-builder` for the final build/test gate.

## Test Cases

| # | Requirement | Given | When | Then |
|---|-------------|-------|------|------|
| 1 | MAINT-20-01 | unchanged `lua.bnf` + compiled Kotlin classes | run `org.intellij.grammar.Main` headless | exits 0, prints `lua.bnf parser generated`, emits `LuaParser.java` + PSI (verified 2026-07-02) |
| 2 | MAINT-20-02 | no `grammar-kit-*.jar` in the Gradle cache | run `generate.sh` | resolves the IDE-bundled jar and proceeds; if none anywhere, aborts with a clear "no Grammar-Kit jar found" error |
| 3 | MAINT-20-03 | edited `lua.flex` | run `generate.sh` | `_LuaLexer.java` regenerated via JFlex CLI, no `.java~` left behind |
| 4 | MAINT-20-04 | full `generate.sh` run | after generation | the 14 stubbed files match HEAD (reverted); custom `LuaBlockImpl` etc. inheritance intact |
| 5 | MAINT-20-05 | unchanged sources | `generate.sh` then `git diff src/main/gen` | empty diff (or only the documented `var_$` version delta, committed wholesale) |
| 6 | MAINT-20-06 | post-generation tree | `gce-builder run build` | BUILD SUCCESSFUL; suite unmodified and green |
| 7 | MAINT-20-07 | CLAUDE.md / AGENTS.md | read the "Add a language feature" / lexer-parser note | describe the headless command; no "pause and hand off to the human" step |

## Acceptance Criteria
- [ ] MAINT-20-01…04: one headless command regenerates both lexers and both parsers with the
      staging workaround, no IDE, no human step.
- [ ] MAINT-20-05/06: regeneration over unchanged sources is byte-identical (or a documented,
      wholesale, version-attributed diff) and the project builds + tests green.
- [ ] MAINT-20-07/08: the shared agent guide and generate-parser skill describe the headless path;
      the manual-IDE handoff is removed.

## Non-Functional Requirements
- **Reproducibility**: works on a fresh checkout given an installed JetBrains IDE (or an explicit
  `$GRAMMAR_KIT_JAR`); no reliance on a pre-warmed Gradle cache.
- **Contract**: hand-written script/docs follow `docs/engineering-contract.md`; generated Java is
  exempt from style but must compile.

## Dependencies
- **MAINT-19** (done) — headless JFlex regen + `import static` token holders this builds on.
- A resolvable Grammar-Kit jar. Confirmed present locally:
  `~/.local/share/JetBrains/IntelliJIdea2026.1/grammar-kit/lib/grammar-kit-2023.3.2.jar`
  (contains `org.intellij.grammar.Main` + `JavaParserGenerator`).
- GoLand platform libs (already resolved in the Gradle transforms cache) for the generator classpath.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
- Predecessor: [[features/maint/19-platform-syntax-migration/requirements|MAINT-19]]
