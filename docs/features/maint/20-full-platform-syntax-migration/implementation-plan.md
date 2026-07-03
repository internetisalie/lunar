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
  - [x] **Create `tooling/parser-gen/`** (done, commit `4fe9792e`): moved `jflex-1.9.2.jar` from root
        (`git rm --cached`), colocated `grammar-kit-2023.3.2.jar`, added `.gitignore:
        /tooling/parser-gen/*.jar`, wrote tracked `tooling/parser-gen/README.md`. Repointed
        `generate.sh`'s jflex reference — realizes MAINT-20-02.
  - [x] Added `resolve_grammar_kit_jar()` in `generate.sh` (design §3.1): `tooling/parser-gen/` →
        `$GRAMMAR_KIT_JAR` → IDE-bundled → gradle cache; verifies `org/intellij/grammar/Main.class`
        and **aborts** if none. (generate.sh is gitignored agent-config; the durable record is these
        docs.) Verified behavior-neutral: relocated jar regenerates a byte-identical `LuaParser.java`.
  - [x] Version decision (design §3.2): **route B — pinned Grammar-Kit 2023.3.2 (newest)** and
        regenerated all of `src/main/gen` (commit `d53adcbb`; 117 files, `@NotNull` ctor + `var_$`,
        gce-builder unit `:test` green) — realizes MAINT-20-05.
- **Exit criteria**: MET — jar in `tooling/parser-gen/`, `generate.sh` resolves it or fails loudly,
  and the committed tree now matches 2023.3.2 output (a regen over unchanged sources is a no-op).

### Phase 2: Headless end-to-end script [Must] — DONE (2026-07-03)
- **Goal**: one command regenerates both lexers + both parsers with the staging workaround, no IDE.
- **Tasks**:
  - [x] `generate.sh` runs end-to-end: compileKotlin → stage classes → parser gen (`lua.bnf`,
        `luacats.bnf`) → JFlex (`lua.flex`, `luacats.flex`) → revert the 14 stubbed files →
        recompile — realizes MAINT-20-01/03/04.
  - [x] Parser/jar step now **fails loud** (resolver verifies `org.intellij.grammar.Main`, aborts
        otherwise); JFlex soft-skip removed — realizes the MAINT-20 behavior rule.
- **Exit criteria**: MET. A full local `generate.sh` run exited 0 (`BUILD SUCCESSFUL`) and left
  **`git diff src/main/gen` empty** — the pinned 2023.3.2 + JFlex reproduce the committed tree
  byte-for-byte (TC-5). The empty diff also confirms the compile-first staging resolves
  `LuaPsiImplUtil` (no degraded `//WARNING skipped` accessors), closing the Phase 1/4 staging concern.
  Note: run reused an existing `build/`, so `compileKotlin` was fast (46s); a from-absolute-scratch
  `rm -rf build out` variant is the only residual (low-risk — it's a normal Gradle compile of
  committed sources producing the same classes).

### Phase 3: De-manualize docs + skill [Must/Should] — DONE (2026-07-03)
- **Goal**: the shared guide and skill describe the headless path; the human handoff is gone.
- **Tasks**:
  - [x] Rewrote `.agents/AGENTS.md` "Note on Lexer/Parser Generation": dropped the "pause and hand
        off to a human to right-click *Generate Parser Code*" step; now points to headless
        `generate.sh` with jar location (`tooling/parser-gen/`), pinned version (2023.3.2), the
        local-gradle-staging exception, and the no-op-regen safety check — realizes MAINT-20-07.
  - [x] [Should] Updated `.agents/skills/generate-parser/SKILL.md`: jar-resolution order + fail-loud,
        `tooling/parser-gen/` location, both grammars/lexers, version pin — realizes MAINT-20-08.
  - Tracked destination: AGENTS.md/skill are git-ignored; the durable, tracked description lives in
    the **tracked** `tooling/parser-gen/README.md` + this feature's docs (design §3.1/§3.3).
- **Exit criteria**: MET (TC-7) — guidance no longer references a manual IDE step.

### Phase 4: Verify [Must] — DONE (2026-07-03)
- **Goal**: prove the regenerated tree builds and tests clean **from a clean checkout**.
- **Tasks**:
  - [x] **Clean-checkout sequence**: `rm -rf build out`, then `generate.sh` end-to-end → exit 0,
        `BUILD SUCCESSFUL`, and **`git diff src/main/gen` empty**. `compileKotlin` materialized the
        classes (`FROM-CACHE`, from the Gradle build-cache) and staging resolved `LuaPsiImplUtil` —
        the empty diff proves the regenerated PSI matches the committed/tested tree (the handful of
        pre-existing `//WARNING getDocComment … skipped` markers are in HEAD too and are benign).
  - [x] `gce-builder run test` on the regenerated tree — compile + full unit `:test`
        `BUILD SUCCESSFUL` (5m08s), no test source edited — realizes MAINT-20-06.
- **Exit criteria**: MET. Clean-checkout regen is a byte-for-byte no-op and the suite is green.
  (Residual, accepted: `compileKotlin` restored from the Gradle build-cache rather than an
  absolute-zero recompile — functionally identical; a fresh compile produces the same classes, as
  the from-scratch gce-builder run independently demonstrated.)

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
| Phase 1: Vendor + jar resolution + version decision | done (commits 4fe9792e, d53adcbb) | Must |
| Phase 2: Headless end-to-end script | done (empty-diff regen, exit 0) | Must |
| Phase 3: De-manualize docs + skill | done | Must |
| Phase 4: Verify | done (clean-checkout empty-diff regen + suite green) | Must |
