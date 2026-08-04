---
id: TARGET-09-RISKS
parent_id: TARGET-09
type: risk
folders:
  - "[[features/target/09-addon-auto-detection/requirements|requirements]]"
title: "Risks & Gaps"
---

# TARGET-09: Risks & Gaps

## Critical Risks

### Risk 1.1: A false positive is worse than a missed detection
- **Question**: a banner offering to download something the project does not use is an
  interruption, and the cost is asymmetric — a missed suggestion costs a user nothing they did not
  already have, a wrong one costs attention and trust.
- **Assessment**: the patterns are upstream's own, chosen by the addon author to mean "this project
  uses my library", and LuaLS has run them at scale for years. `love%.%w+` will fire on any
  `love.anything`, including an unrelated local named `love` — rare, and the banner is dismissible
  per project.
- **Mitigation**: first match in catalog order, at most one banner, three exits including a
  persisted "never". Deliberately **no** fuzzy or additional heuristics of our own — if a pattern is
  wrong, that is a catalog-data fix, not code.

### Risk 1.2: The translator is the part most likely to be silently wrong
- **Question**: a mistranslated pattern does not throw; it just matches the wrong thing, in a code
  path with no visible output when it matches nothing.
- **Mitigation**: Phase 2 lands with tests and no wiring, table-driven over every row of design
  §3.2, plus the two real catalog patterns as worked examples with positive **and** negative cases.
  An untranslatable pattern returns null and is dropped with a `warn` naming the entry — a
  detection that silently never fires is otherwise indistinguishable from a project that does not
  use the library.

### Risk 1.3: Editor-path cost
- **Question**: `collectNotificationData` runs per file per notification refresh; a naive
  implementation would recompile patterns and rescan on every keystroke-driven refresh.
- **Mitigation**: patterns compile once per catalog load into a `CachedValuesManager` value (design
  §3.3); `detect` scans the text once and short-circuits on the first hit; enabled/dismissed
  exclusion happens before any matching. Worst case is one `find()` per unenabled entry over one
  file's text — three entries today.
- **Residual**: unbounded if the catalog grows to hundreds of entries. Revisit if it does; a
  first-hit-wins scan over a handful of patterns needs no index.

## Design Gaps

### Gap 2.1: Detection cannot see the manifest it comes from — RESOLVED
- **Resolution**: patterns are curated into the bundled catalog, not read from the fetched tree.
  Detection must work **before** the fetch, since its purpose is to suggest it; reading the trigger
  out of the artefact it triggers the download of is circular. Design §3.1.
- **Consequence to accept**: our copy can drift from upstream when a pin is bumped. See Curation.

### Gap 2.2: `busted` has no `words` — RESOLVED
- **Resolution**: not papered over. Upstream declares none, so `busted` is never detected directly;
  it arrives as `luassert`'s dependent through the existing `requires` edge. Inventing a pattern for
  it would be exactly the per-framework heuristic this feature exists to stop writing.
- **Note**: this means a project that uses `describe`/`it` but never requires luassert explicitly —
  the common busted case — is **not** detected. That is a real coverage gap against the 96% corpus
  finding and is the strongest argument for pairing this with the cheaper
  `LuaUndeclaredVariable` fix (requirements §2.1), not a reason to fake a trigger.

## Curation

`detectionPatterns` is copied verbatim from each addon's upstream `config.json` `words`, read at the
pinned commit already recorded in the catalog entry:

| Entry | Pinned commit | Source |
|---|---|---|
| luassert | `d3528bb679302cbfdedefabb37064515ab95f7b9` | `luassert-<sha>/config.json` |
| love2d | `c630dd883cda128a19d850bd5e3911110b271609` | `love2d-<sha>/config.json` |

**Rule for future pin bumps**: re-read `config.json` at the new commit and update
`detectionPatterns` in the same change. A stale pattern is a silent no-op, so nothing will fail.

## Technical Debt & Future Work

- **TBD: the rest of the manifest.** `config.json` also carries `Lua.runtime.version` (love2d
  declares `LuaJIT`, which we ignore despite having a first-class target concept),
  `Lua.workspace.library` (busted declares its luassert dependency there, which DR-01 re-derived by
  hand into `requires`), `Lua.runtime.special`, and `name`. Consuming those means retaining
  `config.json` at extraction time — worthwhile, but independent of detection and **not** a
  prerequisite for it (design §3.1).
- **TBD: deriving `requires` from the manifest** rather than hand-maintaining it, once the manifest
  is retained. Removes a drift surface.
- **TBD: measuring the fix.** As MAINT-33 records, the corpus sweep runs headless with no network
  and no enable list, so the `LuaUndeclaredVariable` floor will not move for this feature either
  unless the fixture pre-seeds a cached definition tree. Establish that before claiming a number.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| TARGET-09-00-DR-01 | Confirm `EditorNotificationProvider.collectNotificationData` is callable directly in a light fixture and that the returned `Function` can be applied to a `FileEditor` obtained from `myFixture`, so TC 12–14 need no live editor. If it is not, fall back to asserting `LuaAddonDetector` only and move banner coverage entirely to the live checklist. | Test approach for TARGET-09-04 | todo |
| TARGET-09-00-DR-02 | Confirm `FileDocumentManager.getDocument(file)` is non-null and safe on the notification-collection thread for a file open in an editor, and decide the fallback when it is null (design §3.4 step 2 currently returns null). | Design §3.4 | todo |
