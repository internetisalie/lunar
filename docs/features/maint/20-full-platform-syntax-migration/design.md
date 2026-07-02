---
id: "MAINT-20-DESIGN"
title: "MAINT-20: Design — Headless Parser & Lexer Generation"
type: "design"
priority: "low"
parent_id: "MAINT-20"
folders:
  - "[[features/maint/20-full-platform-syntax-migration/requirements|requirements]]"
---

# MAINT-20: Technical Design

## Goal restated

Turn the lexer/parser regeneration workflow into a single **headless, no-IDE** command. Keep the
classic **Java** generated output (`%type IElementType`, `JavaParserGenerator`) unchanged in shape;
only remove the human right-click handoff. No `com.intellij.platform.syntax` migration (that premise
is abandoned — see requirements §Scope pivot).

## What already works (MAINT-19 + this feature's spike)

- **JFlex CLI** regenerates `_LuaLexer.java` / `_LuaCatsLexer.java` headless and byte-identically
  (MAINT-19). No skeleton flag needed — the default JFlex skeleton emits the Java `FlexLexer`
  Lunar's `FlexAdapter` wraps.
- **Grammar-Kit headless** is proven (2026-07-02): running
  `java -cp <grammar-kit.jar>:<goland-lib>/*:build/classes/kotlin/main org.intellij.grammar.Main
  <gen-dir> lua.bnf` produced the full Java parser + PSI and printed `lua.bnf parser generated to …`.
  The `LuaPsiImplUtil.getDocComment(...) method not found` lines are the expected stub-strip signal
  (those files are reverted post-gen), not errors.

The only reasons `generate.sh` did not "just work" before:
1. It resolves the Grammar-Kit jar **only** from `~/.gradle/caches/modules-2`
   (`generate.sh:48`), where it is absent (Grammar-Kit is not a declared Lunar dependency).
2. The version reachable via an installed IDE differs slightly from whatever produced the committed
   `src/main/gen/`, yielding a cosmetic diff (below).

## The binding constraint: circular dependency (unchanged from today)

The generated parser/PSI reference `LuaPsiImplUtil` (Kotlin), which itself references generated PSI
types — so neither compiles without the other. `generate.sh` already resolves this by staging:

1. `./gradlew compileKotlin` (produces `build/classes/kotlin/main`, incl. `LuaPsiImplUtil`).
2. Copy those classes onto the generator classpath (`out/production/lunar`, `build/classes/java/main`).
3. Run `org.intellij.grammar.Main` — it resolves `psiImplUtilClass` against the staged classes.
4. `git checkout --` the **14 hand-stubbed** generated files (`LuaBlock(Impl)`, `LuaFuncDecl(Impl)`,
   `LuaFuncDef(Impl)`, `LuaGenericForStatement(Impl)`, `LuaLocalFuncDecl(Impl)`,
   `LuaLocalVarDecl(Impl)`, `LuaNumericForStatement(Impl)`) to preserve custom inheritance.
5. `./gradlew compileKotlin` again to verify.

This design **keeps that staging** verbatim; it is the reason the Grammar-Kit *Gradle plugin* stays
unwired (the plugin cannot express the mid-generation revert).

## Design — the three changes

### 3.1 Deterministic Grammar-Kit jar resolution (MAINT-20-02)

Replace `generate.sh:48`'s single `find … modules-2` with an ordered resolver:

```bash
resolve_grammar_kit_jar() {
  # 1. explicit override
  [ -n "$GRAMMAR_KIT_JAR" ] && { echo "$GRAMMAR_KIT_JAR"; return; }
  # 2. installed JetBrains IDE bundled plugin (contains org.intellij.grammar.Main)
  local j
  j=$(find "$HOME/.local/share/JetBrains" -path '*grammar-kit/lib/grammar-kit-*.jar' 2>/dev/null | sort -V | tail -1)
  [ -n "$j" ] && { echo "$j"; return; }
  # 3. legacy: gradle cache
  find "$HOME/.gradle/caches/modules-2" -name 'grammar-kit-*.jar' 2>/dev/null | head -1
}
```

Then **verify** the jar actually contains `org/intellij/grammar/Main.class`
(`unzip -l "$jar" | grep -q 'org/intellij/grammar/Main.class'`) and **abort with a clear error**
otherwise — no soft "Skipping" for the parser step (MAINT-20 behavior rule).

Confirmed locally: `~/.local/share/JetBrains/IntelliJIdea2026.1/grammar-kit/lib/grammar-kit-2023.3.2.jar`.

### 3.2 Version parity / the `var_$` drift (MAINT-20-05)

A headless run with `grammar-kit-2023.3.2` vs the committed `LuaParser.java` diffs by **18 lines**,
all one change: the committed file names the `var` rule method `var(...)`, the 2023.3.2 output names
it `var_$(...)` (Grammar-Kit escaping the Java 10+ `var` soft keyword). It is a purely internal
method name — behavior and the produced PSI are identical, and both compile.

Two acceptable resolutions (the plan picks one after checking which the IDE actually uses):
- **(A) Pin the reproducing version.** Identify the Grammar-Kit version that regenerates the
  committed files unchanged and resolve *that* jar (e.g. from a specific IDE build, or `$GRAMMAR_KIT_JAR`).
- **(B) Adopt 2023.3.2.** Accept the `var_$` naming, regenerate **all** affected files, and commit
  them atomically as a one-time version bump — documented in the skill. Simpler and forward-looking.

Not acceptable: leaving the tree in a state where a routine regen silently rewrites `var`→`var_$`.

### 3.3 De-manualize the docs (MAINT-20-07)

CLAUDE.md currently reads, under "Add a language feature" → "Note on Lexer/Parser Generation":
*"Agent pauses and hands off to the human user. Human right-clicks `lua.bnf` → Generate Parser Code
… then commits `src/main/gen/`."* Replace with the headless invocation
(`.claude/skills/generate-parser/scripts/generate.sh`) and note the human step is no longer required.
Because the tool entry points (`CLAUDE.md`, `GEMINI.md`, …) symlink to `.agents/AGENTS.md`, the edit
lands once in the canonical guide.

## Integration points / registration

None. No `plugin.xml` change, no new EP, no PSI/token change. This feature touches only:
`.claude/skills/generate-parser/scripts/generate.sh`, the generate-parser skill docs, and the shared
`.agents/AGENTS.md` guide. (The `.agents/*` and `.claude/skills/*` trees are gitignored symlinks —
so **the user-facing, tracked deliverable is the corrected guidance under `docs/`** plus, if the
`var_$` route B is taken, the regenerated `src/main/gen/` files.)

> **Tracked-destination note.** The generate-parser skill + AGENTS.md live under gitignored
> agent-config. MAINT-20's *durable, reviewable* record is this `docs/features/maint/20-…` set and
> any committed `src/main/gen/` regeneration; the CLAUDE.md/skill edits are convenience surfaces, not
> the acceptance artifact.

## Threading / contract conformance

- No runtime code path changes; this is build-time tooling. No EDT interaction, no new refs to
  `Project`/`Editor`/`PsiFile`.
- The script's own `./gradlew compileKotlin` is the documented local-gradle exception (staging);
  the final verification gate still runs through `gce-builder`.

## What is explicitly NOT built
- No `SyntaxElementType` holders, no `LanguageSyntaxDefinition`, no `ElementTypeConverter`, no
  `parser-api="syntax"` — the entire platform.syntax migration is dropped.
- No Grammar-Kit Gradle-plugin task (circular-dependency staging keeps it script-only).

## Open Questions

None — the headless path is proven and the one bounded decision (Grammar-Kit version pin vs adopt for the `var_$` drift) is tracked in risks-and-gaps.md Risk 1.1 and resolved in Phase 1.
