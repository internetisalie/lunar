# Parser / Lexer generator jars (local-only)

This directory holds the two CLI jars that `.claude/skills/generate-parser/scripts/generate.sh`
uses to regenerate the Lua lexer and parser from `lua.flex` / `lua.bnf` (and the LuaCATS pair):

| Jar | Purpose | Provides |
|-----|---------|----------|
| `jflex-1.9.2.jar` | Lexer generation (`.flex` → `_Lua*Lexer.java`) | JFlex CLI |
| `grammar-kit-<ver>.jar` | Parser + PSI generation (`.bnf` → `LuaParser.java` + PSI) | `org.intellij.grammar.Main` |

## Why the jars are git-ignored

The jars are **not committed** (`.gitignore: /tooling/parser-gen/*.jar`). Regeneration is an
infrequent, **local** developer step — it is **never run in CI**. The build/test gate (including the
gce-builder VM) compiles the already-committed `src/main/gen/`, so it needs no jar. Keeping ~2.8 MB
of binaries out of version control is the deliberate trade-off; the cost is that a fresh checkout
must repopulate this directory before it can regenerate.

## How to obtain them

### `jflex-1.9.2.jar`
Download from the JFlex releases (<https://github.com/jflex-de/jflex/releases/tag/v1.9.2>) — the
`jflex-1.9.2.jar` (or `jflex-full-1.9.2.jar`) artifact — and drop it here.

### `grammar-kit-<ver>.jar`
Grammar-Kit is **not** bundled with GoLand. Two sources:
1. **An installed IntelliJ IDEA with the Grammar-Kit plugin** — copy from
   `~/.local/share/JetBrains/<IDE>/grammar-kit/lib/grammar-kit-*.jar`
   (e.g. `~/.local/share/JetBrains/IntelliJIdea2026.1/grammar-kit/lib/grammar-kit-2023.3.2.jar`).
2. **JetBrains/Grammar-Kit releases** (<https://github.com/JetBrains/Grammar-Kit/releases>) — prefer
   the release jar for clean provenance.

Verify the jar is usable: `unzip -l grammar-kit-*.jar | grep org/intellij/grammar/Main.class`.

## Version pinning

The Grammar-Kit **version is the pin** for generated output. **Pinned version: 2023.3.2** (the
newest release; the committed `src/main/gen/` was generated with it — commit `d53adcbb`). A regen
with 2023.3.2 over unchanged sources is a no-op; other versions will produce a diff (e.g. an older
one un-escapes `var_$` back to `var`). `generate.sh` resolves the jar from here first, then
`$GRAMMAR_KIT_JAR`, then an installed IDE, then the Gradle cache — and aborts if none contains
`org.intellij.grammar.Main`. Obtain 2023.3.2 from an installed IntelliJ IDEA's `grammar-kit/lib/`
or the JetBrains/Grammar-Kit `v2023.3.2` release.
