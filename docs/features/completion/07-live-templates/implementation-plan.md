---
id: COMP-07-PLAN
title: Live Templates Plan
type: plan
status: done
parent_id: COMP-07
folders:
  - "[[features/completion/07-live-templates/requirements|requirements]]"
---

# COMP-07: Implementation Plan

Sequence the COMP-07 live-template work into shippable, verifiable phases. Design reference:
[design.md](design.md) (sections cited per task). All XML edits land in
`src/main/resources/liveTemplates/lua.xml`; all context types live in
`src/main/kotlin/net/internetisalie/lunar/lang/completion/templates/`; registrations in `plugin.xml`.

## Phases

### Phase 1: Must insertion templates [Must]
- **Goal**: ship the four Must abbreviations (`if`, `ifel`, `lfun`, `while`) as pure-XML templates in
  the existing `LUA` context (no code changes), so the Must set is complete before the context split.
- **Tasks**:
  - [x] Add `<template>` blocks `if` / `ifel` / `lfun` / `while` to `lua.xml` per design §2.2.2
    (exact `value`/tab stops in the table) — realizes design §2.2.2. Bind each to the **current**
    context option `LUA` for now (re-pointed to `LUA_CODE` in Phase 2).
  - [x] Confirm each carries `description`, `toReformat="true"`, `toShortenFQNames="true"` and
    `<variable … alwaysStopAt="true"/>` stops matching design §2.2.2.
- **Exit criteria**: `LuaLiveTemplateTest` loaded-set assertion extended to list
  `if`/`ifel`/`lfun`/`while`; manual expansion of each in a `.lua` scratch matches TC 2–5.

### Phase 2: `LuaCodeContextType` + migrate existing templates [Should — defect fix]
- **Goal**: fix the COMP-07-10 string/comment defect (the `LUA`-only templates fire inside strings
  and comments) by introducing the code-aware context and re-pointing all bundled templates to it.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.lang.completion.templates.LuaCodeContextType` — realizes
    design §2.1.2: extends `TemplateContextType` with id `"LUA_CODE"`, name `"Lua (code)"`, parent
    `LuaTemplateContextType::class.java` (3-arg super).
  - [x] Implement `isInContext` — realizes design §3.1: companion `SUPPRESS` `TokenSet` =
    `TokenSet.orSet(LuaSyntax.CommentTokens, LuaSyntax.StringLiteralTokens, TokenSet.create(
    LuaTokenTypes.LONGSTRING, LuaTokenTypes.LONGSTRING_BEGIN, LuaTokenTypes.LONGSTRING_END,
    LuaTokenTypes.NUMBER, LuaElementTypes.NUMBER))`; `findElementAt(offset)` with `offset-1`
    left-fallback; `PsiUtilCore.getElementType`; leaf-and-ancestor membership test against `SUPPRESS`.
  - [x] Register `<liveTemplateContext implementation="…LuaCodeContextType"/>` in `plugin.xml` —
    realizes design §7 (keep the existing `LuaTemplateContextType` registration).
  - [x] Migrate **all** bundled templates' context option from `LUA` to `LUA_CODE` in `lua.xml`
    (the four shipped + the four Phase-1 additions) — realizes design §2.2.1.
- **Exit criteria**: a new test asserts a template does **not** expand with the caret inside a string
  literal and inside a `--` comment (TC 6), and **does** expand in code; suite green.

### Phase 3: Should insertion templates [Should]
- **Goal**: add the remaining Should abbreviations.
- **Tasks**:
  - [x] Add `repeat` / `forip` / `req` / `mod` `<template>` blocks to `lua.xml`, each bound to
    `LUA_CODE` — realizes design §2.2.2. Use literal default names for `req`/`mod` (no smart macros —
    see risks-and-gaps DR-04).
- **Exit criteria**: loaded-set test lists `repeat`/`forip`/`req`/`mod`; manual expansion matches the
  design §2.2.2 table.

### Phase 4: Surround templates [Should]
- **Goal**: deliver COMP-07-11 surround-with templates.
- **Tasks**:
  - [x] Create `LuaSurroundContextType` — realizes design §2.3: id `"LUA_SURROUND"`, name
    `"Lua (surround)"`, parent `LuaTemplateContextType::class.java`; `isInContext` reuses the §3.1
    code check at the selection start. Factor the §3.1 predicate into a shared private helper / top-
    level function used by both `LuaCodeContextType` and `LuaSurroundContextType` (avoid duplication).
  - [x] Register `<liveTemplateContext implementation="…LuaSurroundContextType"/>` in `plugin.xml` —
    realizes design §7.
  - [x] Add surround `<template>` blocks `surr_if` / `surr_for` / `surr_do` / `surr_fn` to `lua.xml`
    using `$SELECTION$`, each bound to `LUA_SURROUND` with a `description` — realizes design §2.3.
- **Exit criteria**: surround templates appear in Surround-With for a selection; a test asserts the
  four surround templates load in group `Lua`.

### Phase 5 (Optional): `LuaIfContextType` + `elseif` [Could — parked]
- **Goal**: only if the parked `elseif` backlog item is pulled in.
- **Tasks**:
  - [ ] Create `LuaIfContextType` — realizes design §2.1.3: gates on a `LuaIfStatement`
    (`net.internetisalie.lunar.lang.psi.LuaIfStatement`) ancestor; register it; add an `elseif`
    template bound to `LUA_IF`.
- **Exit criteria**: deferred — not part of the Must/Should delivery.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| COMP-07-01 (`fun`/`fori`/`forp`/`loc`) | M | Phase 2 (context migration; templates already shipped) |
| COMP-07-02 `if` | M | Phase 1 |
| COMP-07-03 `ifel` | M | Phase 1 |
| COMP-07-04 `lfun` | M | Phase 1 |
| COMP-07-05 `while` | M | Phase 1 |
| COMP-07-06 `repeat` | S | Phase 3 |
| COMP-07-07 `forip` | S | Phase 3 |
| COMP-07-08 `req` | S | Phase 3 |
| COMP-07-09 `mod` | S | Phase 3 |
| COMP-07-10 Context refinement | S | Phase 2 (+ optional Phase 5 for `LuaIfContextType`) |
| COMP-07-11 Surround templates | S | Phase 4 |

## Verification Tasks
- [x] Extend `LuaLiveTemplateTest` to assert all new abbreviations load in group `Lua` — covers TC 1–5
  (loaded-set portion).
- [x] Add a behavioural test expanding `if`/`while` in real code (TC 2, 5) via the fixture.
- [x] Add a suppression test: caret inside `"…"` and inside `-- …`, assert no Lua template offered —
  covers TC 6 (the COMP-07-10 defect fix).
- [ ] Manual: Surround a selection with `if`/`for`/`do`/`function` via Ctrl+Alt+T (COMP-07-11).
- [x] Run `python3 scripts/lint_planning.py` and
  `python3 scripts/lint_docs.py docs/features/completion/07-live-templates` — no new errors.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Must insertion templates | done | Must |
| Phase 2: LuaCodeContextType + migrate | done | Should |
| Phase 3: Should insertion templates | done | Should |
| Phase 4: Surround templates | done | Should |
| Phase 5: LuaIfContextType + elseif | todo | Could |
