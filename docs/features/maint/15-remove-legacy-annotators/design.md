---
id: MAINT-15-DESIGN
title: "Technical Design"
type: design
parent_id: MAINT-15
folders:
  - "[[features/maint/15-remove-legacy-annotators/requirements|requirements]]"
---

# Technical Design: MAINT-15 — Remove Legacy Annotators

## 1. Architecture Overview

### Current State
The target package is **`net.internetisalie.lunar.lang.syntax`** (single file
`src/main/kotlin/net/internetisalie/lunar/lang/syntax/LuaAnnotators.kt`). There is **no**
`net.internetisalie.lunar.lang.annotator` package in the repo — the earlier stub named a
non-existent package, corrected here.

`LuaAnnotators.kt` holds seven `Annotator` classes: four live (emit annotations) and three
dead (no-op). The three dead ones are registered in `META-INF/plugin.xml` and dispatched by
the platform on every element for no effect.

### Why removal is behavior-preserving
The three targets' `annotate(...)` methods contain only comments — no `holder` call, no
side effect. `annotate` returning early with an empty body is indistinguishable, to the
platform, from the extension not being registered at all. Therefore deleting both the
classes and their registrations produces zero observable change. This is dead-code deletion,
**not** a functional replacement; the in-code comments record that the real work already
moved elsewhere (completion contributor, `LuaLabelReference`, semantic/LuaCATS analysis).

## 2. Removal Map

### 2.1 Classes to delete — `lang/syntax/LuaAnnotators.kt`
| Class | Decl line | Full block to delete | Disabling comment (quoted) |
|---|---|---|---|
| `LuaLocalBindingsAnnotator` | `:151` | `:151–:157` | `// Note: Bindings annotation disabled after removing LuaBindingsVisitor` … `// This class retained for future enhancement` |
| `LuaGotoAnnotator` | `:159` | `:159–:165` | `// Note: Goto annotation disabled after removing LuaBindingsVisitor` … `// This class retained for future enhancement` |
| `LuaGlobalBindingsAnnotator` | `:167` | `:167–:172` | `// Note: Global bindings annotation disabled after removing LuaBindingsVisitor` … `// This class retained for future enhancement using semantic analysis` |

These are the last three classes in the file. Delete the contiguous span `:151–:172`
(including the blank separator line `:150`/`:158`/`:166` between blocks). No other edits to
the file are needed: the imports at `:1–:10` are all still used by the four retained
annotators (`AnnotationHolder`, `Annotator`, `HighlightSeverity`, `TextRange`, `PsiElement`,
`PsiTreeUtil`, `elementType`, and the `lang.psi.*` wildcard used by `LuaAttribName` /
`LuaLocalVarDecl` / `LuaElementTypes` / `LuaHighlight`), so no import cleanup is required.

### 2.2 Registrations to delete — `META-INF/plugin.xml`
| Class | `<annotator>` block lines |
|---|---|
| `LuaLocalBindingsAnnotator` | `:142–:144` |
| `LuaGlobalBindingsAnnotator` | `:145–:147` |
| `LuaGotoAnnotator` | `:148–:150` |

Delete these three contiguous `<annotator language="Lua" implementationClass="…"/>` blocks
(span `:142–:150`).

### 2.3 Explicitly retained (out of scope)
Do NOT touch the four live annotators or their registrations:

| Class | Class line | Emits at | plugin.xml |
|---|---|---|---|
| `LuaNumeralAnnotator` | `:12` | `:29`,`:40`,`:48`,`:54`,`:65` | `:130–:132` |
| `LuaLongStringAnnotator` | `:72` | `:85`,`:89` | `:133–:135` |
| `LuaLongCommentAnnotator` | `:96` | `:109`,`:113` | `:136–:138` |
| `LuaAttribNameAnnotator` | `:120` | `:132`,`:144` | `:139–:141` |

## 3. Reference Audit
`grep -rn` across `src/main`, `src/test`, `src/integrationTest` for the three dead class
names returns matches **only** in the two files being edited:
`LuaAnnotators.kt` (`:151`, `:159`, `:167`) and `plugin.xml` (`:144`, `:147`, `:150`).

No test, no other production source references them. `TestLuaNumeralAnnotator.kt` and
`LuaInferredTypeAnnotatorTest.kt` reference only the retained/live annotators and are
untouched. Therefore removal cannot break compilation elsewhere.

**Docs scope (not zero everywhere):** the `src/`-scope audit above is clean, but a
`grep -rn … docs/` finds **four** live-doc references that will dangle once the classes are
deleted — `docs/features/inspections/requirements.md:38`,
`docs/features/syntax/17-inferred-type-highlighting/design.md:17–18`,
`docs/features/syntax/17-inferred-type-highlighting/risks-and-gaps.md:19`, and the
hand-maintained `docs/status-detail.md:280`. These are documentation-only (no compilation
impact) and are handled by implementation-plan **Phase 3** / requirement **MAINT-15-05**;
do not read this audit as "zero references anywhere". (Matches in `docs/.obsidian/` session
logs are immutable agent-history JSON and are left as-is.)

## 4. Open Questions
None.
