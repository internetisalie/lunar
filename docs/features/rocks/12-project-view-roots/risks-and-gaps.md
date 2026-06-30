---
id: "ROCKS-12-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "ROCKS-12"
folders:
  - "[[features/rocks/12-project-view-roots/requirements|requirements]]"
---

# ROCKS-12: Risks & Gaps

## Critical Risks

### Risk 1.1: Dependency on unshipped ROCKS-05 / ROCKS-09
- **Impact**: Piece B's source-root set comes from `PathConfiguration.getProjectSourcePathPatterns`,
  which ROCKS-05 augments with `build.modules`-derived roots, and ROCKS-05 itself consumes ROCKS-09
  discovery. If ROCKS-05/09 are not yet merged, `getProjectSourcePathPatterns` returns only the
  default/`$PROJECT_DIR$` patterns, so Piece B marks the project root rather than per-rock roots.
- **Likelihood**: medium
- **Mitigation**: Piece A (the Must) has **no** dependency on ROCKS-05/09 — it reads the installed
  tree directly via `LuaRocksTreeLocator`. Sequence Piece A first; Piece B (a Should) degrades
  gracefully (it still marks whatever absolute in-project leading paths exist) and gains precision
  automatically once ROCKS-05 lands. No code change in ROCKS-12 is needed when ROCKS-05 ships.

### Risk 1.2: `<X.Y>` derivation when no target is set
- **Impact**: A project with no explicit interpreter/target could resolve the wrong `share/lua`
  version directory and miss the installed tree.
- **Likelihood**: low
- **Mitigation**: `LuaProjectSettings...getTarget()` returns `Target.default()` (Standard 5.4) and
  `getImplicitLanguageLevel()` defaults to `LUA54` (`Target.kt:49`), so `<X.Y>` always resolves to a
  concrete value (`5.4`). The lookup is layout-driven: if the actual installed tree uses a different
  `<X.Y>`, only that directory is missed, never a crash. (Future enhancement: scan all
  `share/lua/*` children — deferred, see TBD below.)

## Design Gaps

### Gap 2.1: Piece-B marking mechanism choice
- **Question**: decorator (a) vs icon provider (b) vs true `SourceFolder` model marking (c)?
- **Options / leaning**: **Resolved → (a) `ProjectViewNodeDecorator`** (design §2.2, §9). It is
  non-destructive (presentation suffix only), dynamic (re-reads
  `getProjectSourcePathPatterns`, no persistence), and has a real platform precedent
  (`ScratchFileServiceImpl$FilePresentation`). (b) can only swap the icon, not add a label. (c)
  mutates and persists the module/workspace model and risks conflicts with the IDE's module config
  and with `additionalLibraryRootsProvider` scoping — over-scoped for a visual mark.
- **Resolved by**: folded into design §2.2 / §9; no open DR task.

## Technical Debt & Future Work

- **TBD: System/global LuaRocks trees** — Piece A only roots the project-local tree
  (`LuaRocksTreeLocator` v1 scope, ROCKS-03-G-01). Global trees are deferred.
- **TBD: Version-agnostic `share/lua/*` scan** — if a project's installed tree `<X.Y>` does not
  match the configured target, the directory is missed. A future enhancement could enumerate all
  `share/lua/*` / `lib/lua/*` children rather than the single derived `<X.Y>`. Deferred to keep v1
  deterministic and aligned with the run-side path derivation.
- **TBD: True source-root semantics (option c)** — if downstream tooling later needs real
  `SourceFolder` semantics (e.g. for refactoring scope), revisit content-entry marking with explicit
  persistence/conflict handling. Deferred.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| ROCKS-00-DR-12A | In a fixture project, confirm a file under `lua_modules/share/lua/5.4` reports `ProjectFileIndex.isInLibrary == true` once the new provider is registered (validates the exclusion claim, ROCKS-12-03). | Risk 1.1 (Piece A independence) | deferred (confirmed via implicit SyntheticLibrary behavior; explicit test blocked by platform index recursion bug #1) |

## Test Case Gaps

- First-party-vs-vendored boundary: a project that has BOTH a first-party `src/` root AND a
  `thirdparty/` vendored copy of the same module — assert only `src/` is badged (covered by TC #8,
  but worth a dedicated fixture combining both at once).
- A `.luarocks` tree (instead of `lua_modules`) with a populated `share/lua/<X.Y>` — assert Piece A
  resolves it (algorithm covers it via `treeRoot`; add an explicit case).

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
