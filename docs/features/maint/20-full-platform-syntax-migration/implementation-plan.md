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

> **Feasibility: PROVEN (2026-07-02).** Headless `org.intellij.grammar.Main` generation ran to
> completion against the local IDE-bundled `grammar-kit-2023.3.2.jar`, producing the Java parser +
> PSI (`lua.bnf parser generated to …`) with only an 18-line cosmetic `var`→`var_$` diff vs the
> committed files. The JFlex half is already headless (MAINT-19). No further de-risking gate — this
> plan executes.

## Toolchain (reference)

All from repo root. `$JH=/home/mini/.jdks/corretto-21.0.10`.

- **Grammar-Kit jar** (contains `org.intellij.grammar.Main`):
  `~/.local/share/JetBrains/IntelliJIdea2026.1/grammar-kit/lib/grammar-kit-2023.3.2.jar`
  (resolver added in Phase 1; overridable via `$GRAMMAR_KIT_JAR`).
- **GoLand platform libs**: the transforms-cache `goland-*/lib` dir (already used by `generate.sh`).
- **Parser gen**: `$JH/bin/java -cp "<gk-jar>:<goland-lib>/*:build/classes/kotlin/main"
  org.intellij.grammar.Main src/main/gen <bnf-file>`.
- **Lexer gen**: `$JH/bin/java -jar jflex-1.9.2.jar -d <gen-dir> <flex-file>` (MAINT-19, unchanged).

## Phases

### Phase 1: Deterministic jar resolution + version decision [Must]
- **Goal**: `generate.sh` finds a valid Grammar-Kit jar deterministically and the `var_$` drift is
  resolved to a single committed state.
- **Tasks**:
  - [ ] Add `resolve_grammar_kit_jar()` (design §3.1): `$GRAMMAR_KIT_JAR` → IDE-bundled → gradle
        cache; verify `org/intellij/grammar/Main.class` in the jar; **abort with a clear error** if
        none — realizes MAINT-20-02.
  - [ ] Decide `var_$` route (design §3.2): check whether GoLand's own bundled Grammar-Kit
        reproduces the committed `var(...)`; if a version reproduces it, pin it (route A); else adopt
        2023.3.2 and regenerate **all** of `src/main/gen` in one commit (route B) — realizes MAINT-20-05.
- **Exit criteria**: `generate.sh` resolves a jar or fails loudly; a regen over unchanged sources is
  byte-identical to the (possibly route-B-updated) committed tree.

### Phase 2: Headless end-to-end script [Must]
- **Goal**: one command regenerates both lexers + both parsers with the staging workaround, no IDE.
- **Tasks**:
  - [ ] Ensure `generate.sh` runs: compileKotlin → stage classes → parser gen (`lua.bnf`,
        `luacats.bnf`) → JFlex (`lua.flex`, `luacats.flex`) → revert the 14 stubbed files →
        recompile — realizes MAINT-20-01/03/04. (Most of this already exists; harden error handling
        and the parser-step soft-skip.)
  - [ ] Make the parser step **fail loud** (no silent skip) — realizes the MAINT-20 behavior rule.
- **Exit criteria**: `generate.sh` exits 0 on a clean checkout; `git diff src/main/gen` empty (TC-5).

### Phase 3: De-manualize docs + skill [Must/Should]
- **Goal**: the shared guide and skill describe the headless path; the human handoff is gone.
- **Tasks**:
  - [ ] Edit `.agents/AGENTS.md` "Add a language feature" + "Note on Lexer/Parser Generation" to the
        headless command; drop the "pause and hand off to the human" step — realizes MAINT-20-07.
  - [ ] [Should] Document jar resolution + staging + version pin in
        `.claude/skills/generate-parser/` — realizes MAINT-20-08.
  - [ ] Mirror the corrected workflow into this `docs/` feature set (tracked destination), since the
        AGENTS.md/skill edits are gitignored.
- **Exit criteria**: TC-7; guidance no longer references a manual IDE step.

### Phase 4: Verify [Must]
- **Goal**: prove the regenerated tree builds and tests clean.
- **Tasks**:
  - [ ] `gce-builder run build` (compile + checkStatus + lintDocs) and `gce-builder run test` —
        realizes MAINT-20-06.
- **Exit criteria**: BUILD SUCCESSFUL; suite green, no test source edited.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-20-01 | M | Phase 2 |
| MAINT-20-02 | M | Phase 1 |
| MAINT-20-03 | M | Phase 2 |
| MAINT-20-04 | M | Phase 2 |
| MAINT-20-05 | M | Phase 1 |
| MAINT-20-06 | M | Phase 4 |
| MAINT-20-07 | M | Phase 3 |
| MAINT-20-08 | S | Phase 3 |

## Verification Tasks
- [ ] Headless regen over unchanged sources → empty `src/main/gen` diff (TC-5) or documented route-B commit.
- [ ] `gce-builder run build` + `run test` green (TC-6).
- [ ] Fresh-shell run with no gradle-cache grammar-kit jar resolves the IDE jar (TC-2).

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Jar resolution + version decision | todo | Must |
| Phase 2: Headless end-to-end script | todo | Must |
| Phase 3: De-manualize docs + skill | todo | Must |
| Phase 4: Verify | todo | Must |
