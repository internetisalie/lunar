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

> **Feasibility: mechanism proven, clean-checkout sequence NOT yet verified (2026-07-02).**
> Headless `org.intellij.grammar.Main` ran to completion against the local IDE-bundled
> `grammar-kit-2023.3.2.jar`, producing a correct Java parser + PSI **when the compiled Kotlin
> classes were already staged** on the classpath (the run reused pre-existing
> `build/classes/kotlin/main`). Diff vs committed = 18 cosmetic `var`→`var_$` lines.
>
> **Caveat (load-bearing):** the compiled classes are *required*. Re-running `Main` with **no**
> project classes on the classpath still prints `parser generated` but silently degrades — 66/111
> PSI files lose their `psiImplUtil` accessors (`//WARNING: getBlockList(...) is skipped … not found
> in LuaPsiImplUtil`) and would not compile. So `generate.sh` step 1 (`./gradlew compileKotlin`) is
> essential, and the **full clean-checkout sequence (compile-from-clean → stage → generate →
> recompile green) has NOT been run** — it needs `gce-builder`, not a local `./gradlew`. Phase 4
> is the gate that closes this. JFlex half is already headless (MAINT-19).

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

### Phase 1: Vendor the jar + deterministic resolution + version decision [Must]
- **Goal**: an IDE-independent, version-pinned generator; `generate.sh` resolves it deterministically;
  the `var_$` drift is resolved to a single committed state.
- **Tasks**:
  - [ ] **Create `tooling/parser-gen/`**: `git mv`/move `jflex-1.9.2.jar` from root into it
        (`git rm --cached` the root copy), add `grammar-kit-<ver>.jar`, add `.gitignore:
        tooling/parser-gen/*.jar`, and write a tracked `tooling/parser-gen/README.md` documenting how
        to obtain both jars. Repoint `generate.sh`'s `jflex-1.9.2.jar` reference — realizes MAINT-20-02.
  - [ ] Add `resolve_grammar_kit_jar()` (design §3.1): vendored → `$GRAMMAR_KIT_JAR` → IDE-bundled →
        gradle cache; verify `org/intellij/grammar/Main.class`; **abort with a clear error** if none.
  - [ ] Decide the pinned version / `var_$` route (design §3.2): pick the Grammar-Kit version to
        vendor — either the one that reproduces the committed `var(...)` (route A), or adopt a newer
        one (e.g. 2023.3.2) and regenerate **all** of `src/main/gen` in one commit (route B) —
        realizes MAINT-20-05. The vendored jar *is* the pin, so this choice is baked in once.
- **Exit criteria**: vendored jar present + synced; `generate.sh` resolves it or fails loudly; a
  regen over unchanged sources is byte-identical to the (possibly route-B-updated) committed tree.

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
- **Goal**: prove the regenerated tree builds and tests clean **from a clean checkout**.
- **Tasks**:
  - [ ] **Clean-checkout sequence** (the currently-unverified gap): from a tree with no
        `build/`/`out/` classes, run `generate.sh` end-to-end (its step-1 `./gradlew compileKotlin`
        bootstraps from the committed `gen/`, then it stages → regenerates → reverts stubs →
        recompiles). Confirm the compile-first staging actually resolves `LuaPsiImplUtil` — i.e. the
        regenerated PSI has the accessors, NOT the `//WARNING … skipped` degraded form.
  - [ ] `gce-builder run build` (compile + checkStatus + lintDocs) and `gce-builder run test` —
        realizes MAINT-20-06.
- **Exit criteria**: BUILD SUCCESSFUL from clean; regenerated PSI files carry their `psiImplUtil`
  accessors (no `skipped` warnings beyond the 14 intentionally-stubbed files); suite green, no test
  source edited.

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
