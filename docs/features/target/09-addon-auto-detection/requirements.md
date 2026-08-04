---
id: TARGET-09
parent_id: TARGET
type: feature
folders:
  - "[[features/target/requirements|requirements]]"
title: "TARGET-09: Definition-library Auto-detection"
status: "planned"
priority: "medium"
vf_icon: 🔵
---

# TARGET-09: Definition-library Auto-detection

## 1. Problem

TARGET-08 ships **enablement**, not improvement: nothing changes for a project until a user finds
*Settings ▸ Languages & Frameworks ▸ Lua ▸ Definition Libraries* and ticks a box. The MAINT-33
corpus quantifies what that leaves on the table — **1507 of 1569 (96%)** `LuaUndeclaredVariable`
hits across luarocks and luacheck are busted globals (`describe`, `it`, `before_each`,
`after_each`, `finally`, `lazy_setup`, `lazy_teardown`, `pending`); per project, luarocks 937/954
and luacheck 570/615. Those are `level="ERROR"` and `enabledByDefault`, so any project with a
busted suite opens as a wall of red until someone happens to find a setting they have no reason to
look for.

Since 2026-08-04 the payoff for finding it is real — enabling `busted` removes those errors **and**
gives completion and types (TARGET-08-04, BUG-394/395/398/399). The remaining gap is purely one of
discovery.

## 2. Approach, and why this one

**Each addon already declares its own trigger.** Every entry in the bundled catalog is a LuaLS
addon, and each ships a `config.json` manifest whose `words` array is the pattern LuaLS itself
matches to decide the addon applies:

| Entry | Upstream `words` |
|---|---|
| luassert | `require[%s%(\"']+luassert[%)\"']` |
| love2d | `love%.%w+` |
| busted | *(none — declares only `settings`)* |

So detection is not a heuristic to invent per framework; it is data to consume. This removes the
question the roadmap flagged as needing investigation ("unresolved `require`? a `spec/` tree? a
`.busted` config? the rockspec's `test_dependencies`?").

**What it does when it fires is a product decision, and it is made here: suggest, never fetch.**
A detection that silently enabled a library would trigger a network download the user did not ask
for. Instead a Lua-file editor banner offers the action, modelled directly on the existing
`LuaToolEditorNotificationProvider` (`src/main/kotlin/net/internetisalie/lunar/toolchain/health/LuaToolEditorNotificationProvider.kt:37`).

### 2.1 The cheaper alternative, and why it is not chosen

The roadmap asks that "teach `LuaUndeclaredVariable` about test-framework globals directly" be
priced first. It is cheaper — no catalog, fetch or UI — and removes the same 96%. It is **not**
chosen because it hard-codes a framework list into an inspection and delivers *only* silence: no
completion, no signatures, no types, and nothing for the next framework. TARGET-08 already carries
the definitions; this feature is the last mile to them. The two are not mutually exclusive, and if
a fast partial fix is wanted before this lands, that is the one to take.

## 3. Scope

### In Scope
- A `detectionPatterns` field on catalog entries, populated from upstream `words` at curation time.
- Translating Lua patterns to Java regex, with an explicit supported subset.
- Per-file detection in an editor-notification provider, matched against the file being edited.
- A banner offering **Enable**, **Not now**, and **Never for this project**, with the last persisted.
- Detection suppressed for an entry already enabled, already dismissed, or fetched.

### Out of Scope
- **Any automatic fetch or enable.** The user always clicks (see §2).
- Reading `config.json` from the fetched tree at runtime — see the chicken-and-egg note in
  [design.md](design.md) §3.1.
- Consuming the manifest's other fields (`Lua.runtime.version`, `Lua.workspace.library`) — recorded
  as future work in [risks-and-gaps.md](risks-and-gaps.md).
- Project-wide scanning, indexing, or a background detection pass.

## 4. Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| TARGET-09-01 | **Catalog detection patterns** | M | Not Implemented | Each catalog entry may carry `detectionPatterns`: a list of Lua patterns copied verbatim from the upstream addon's `config.json` `words`. Absent or empty means the entry is never auto-detected. The loader rejects a non-string element as a corrupt catalog, consistent with every other field. |
| TARGET-09-02 | **Lua-pattern → regex translation** | M | Not Implemented | A pure translator converting the supported Lua-pattern subset to an equivalent `java.util.regex` pattern. An unsupported construct makes the pattern unusable rather than wrong: it is skipped and logged once. The supported and unsupported sets are enumerated in [design.md](design.md) §3.2. |
| TARGET-09-03 | **Per-file detection** | M | Not Implemented | For a Lua file, the set of catalog entries whose translated patterns match that file's text, excluding entries already enabled. Pure and side-effect free: no I/O, no network, no index access. |
| TARGET-09-04 | **Suggestion banner** | M | Not Implemented | A Lua-file editor banner naming the detected library and offering **Enable**, **Not now**, **Never for this project**. Enable routes through the existing `LuaDefinitionLibraryEnabler.apply`, so the fetch stays on its background task with its existing failure balloon. At most one banner; the first detected entry in catalog order wins. |
| TARGET-09-05 | **Dismissal is persisted per project** | M | Not Implemented | "Never for this project" adds the entry id to a persisted list in `lunar.xml`, and that entry is never suggested again for that project. "Not now" suppresses for the session only. |
| TARGET-09-06 | **No suggestion without a payoff** | S | Not Implemented | An entry that is already enabled is never suggested, whether or not its cache exists. |
| TARGET-09-07 | **Cheap on the editor path** | M | Not Implemented | Detection runs on the editor-notification path and must not perform I/O, network or index access, and must not scan the file more than once per notification collection. Translated patterns are compiled once per catalog load, not per file. |

## 5. Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | TARGET-09-01 | A catalog JSON entry with `"detectionPatterns": ["love%.%w+"]` | `LuaDefinitionCatalogLoader.parse(json)` | The parsed `LuaDefinitionEntry.detectionPatterns` is `["love%.%w+"]`. |
| 2 | TARGET-09-01 | A catalog JSON entry with no `detectionPatterns` key | `LuaDefinitionCatalogLoader.parse(json)` | `detectionPatterns` is empty; no exception. |
| 3 | TARGET-09-01 | A catalog JSON entry with `"detectionPatterns": [42]` | `LuaDefinitionCatalogLoader.parse(json)` | Throws `LuaProvisionException` naming `detectionPatterns`. |
| 4 | TARGET-09-02 | `love%.%w+` | `LuaPatternTranslator.toRegex(...)` | `love\.[A-Za-z0-9]+`, and the compiled regex matches `love.graphics` and does not match `loveX`. |
| 5 | TARGET-09-02 | luassert's pattern `require[%s%("']+luassert[%)"']` | `LuaPatternTranslator.compile(...)` then `find()` | Finds in `require("luassert")` and `require 'luassert'`; does **not** find in `requireluassert`, nor in `require[[luassert]]` — upstream's class contains no `[`, and design §3.2 preserves that fidelity rather than improving it. |
| 6 | TARGET-09-02 | `%bxy` (unsupported balanced match) | `LuaPatternTranslator.toRegex(...)` | Returns null (pattern unusable); no exception. |
| 7 | TARGET-09-02 | `a|b` (a Lua literal, a regex metacharacter) | `LuaPatternTranslator.toRegex(...)` | A regex matching the literal `a|b` and not matching a bare `a`. |
| 8 | TARGET-09-03 | Catalog with love2d; a file containing `love.graphics.draw()`; nothing enabled | `LuaAddonDetector.detect(file text)` | Returns the `love2d` entry. |
| 9 | TARGET-09-03 | Same, but `love2d` is in `enabledDefinitionLibraries` | `LuaAddonDetector.detect(...)` | Returns nothing (TARGET-09-06). |
| 10 | TARGET-09-03 | A file containing no catalog pattern | `LuaAddonDetector.detect(...)` | Returns nothing. |
| 11 | TARGET-09-05 | `love2d` in the persisted dismissed list; a file using `love.graphics` | `LuaAddonDetector.detect(...)` | Returns nothing. |
| 12 | TARGET-09-04 | A detected entry | `LuaAddonNotificationProvider.collectNotificationData(project, file)` | Returns a non-null panel whose text names the entry's `displayName`, with action labels `Enable`, `Not now`, `Never for this project`. |
| 13 | TARGET-09-04 | A non-Lua file | `collectNotificationData(project, file)` | Returns null. |
| 14 | TARGET-09-04 | Two entries both matching | `collectNotificationData(...)` | Exactly one panel, for the entry earliest in catalog order. |
| 15 | TARGET-09-05 | The banner's **Never for this project** is invoked | The action runs | `LuaProjectSettings.state.dismissedDefinitionLibraries` contains the id, and a subsequent `detect` returns nothing for it. |

## 6. Acceptance Criteria

- [ ] `detectionPatterns` round-trips through the catalog loader, and a malformed value is a corrupt-catalog error (TC 1–3).
- [ ] The translator handles the catalog's real patterns and rejects unsupported constructs without throwing (TC 4–7).
- [ ] Detection returns the right entries and is suppressed by enablement and dismissal (TC 8–11).
- [ ] The banner appears only on Lua files, names the library, and offers the three actions (TC 12–14).
- [ ] Dismissal persists in `lunar.xml` and suppresses future suggestions (TC 15).
- [ ] Real-flow DoD: opening a `.lua` file that uses `love.graphics` in GoLand shows the banner; **Enable** fetches the library and the API resolves afterwards (VNC-verified).
