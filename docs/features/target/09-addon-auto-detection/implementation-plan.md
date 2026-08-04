---
id: TARGET-09-PLAN
parent_id: TARGET-09
type: plan
folders:
  - "[[features/target/09-addon-auto-detection/requirements|requirements]]"
title: "Implementation Plan"
---

# TARGET-09: Implementation Plan

Sequenced from [design.md](design.md). Each phase leaves the build green and is independently
testable. Precondition: TARGET-08 is **done** — the catalog, loader, enable list and
`LuaDefinitionLibraryEnabler` all exist and are the seams this feature plugs into.

Run the gate through `tooling/gce-builder/gce-builder.sh run test` (never `./gradlew` locally), and
gate on the **full** suite, not a filtered run — a filtered green has hidden a full-suite failure
three times in this area.

## Phase 1: Catalog carries the patterns [Must]

- **Goal**: `detectionPatterns` round-trips, and the loader stops coercing non-strings.
- **Tasks**:
  - [ ] Add `detectionPatterns: List<String> = emptyList()` to `LuaDefinitionEntry`
        (`LuaDefinitionCatalog.kt:55`) — design §2.1.
  - [ ] Parse it in `parseEntry` with the existing `optStringArray` (`LuaDefinitionCatalogLoader.kt:94`).
  - [ ] Tighten `requireStringArray` (`:129`) with the typed check `requireString` already uses at
        `:112` — design §2.1. Note this also tightens `urls` and `requires`, intentionally.
  - [ ] Populate the catalog JSON for `luassert` and `love2d`; leave `busted` without patterns.
- **Exit criteria**: TC 1, 2, 3 pass. The existing `LuaDefinitionCatalogLoaderTest` stays green —
  if any case there relies on numeric coercion, that case was asserting a bug and is updated.

## Phase 2: Pattern translation [Must]

- **Goal**: a pure, well-tested Lua-pattern → regex translator. **No wiring in this phase.**
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.definitions.detect.LuaPatternTranslator` with `toRegex` and
        `compile` — design §3.2, including the full class table, the escape sets, the `-` → `*?`
        rule, the anchor-position rules, and the unsupported list returning null.
  - [ ] Compile with `Pattern.DOTALL` and no case-insensitivity.
- **Exit criteria**: TC 4, 5, 6, 7 pass, plus a table-driven test over every row of design §3.2's
  class table. This phase is the one a weak implementer is most likely to get subtly wrong, so its
  tests are written before its wiring exists and must fail first.

## Phase 3: Detection [Must]

- **Goal**: text in, suggestible entry out; pure and cheap.
- **Tasks**:
  - [ ] Add `dismissedDefinitionLibraries` to `LuaProjectSettings.State` plus the accessor and
        `dismissDefinitionLibrary(id)` — design §2.2.
  - [ ] Create `net.internetisalie.lunar.definitions.detect.LuaAddonDetector` — design §3.3 — with
        the catalog-order walk, the enabled/dismissed/session-suppressed exclusions, and the
        `CachedValuesManager`-held compiled-pattern map.
  - [ ] Add `suppressForSession(id)` backed by a concurrent set — design §3.5.
- **Exit criteria**: TC 8, 9, 10, 11 pass. Assert directly that `detect` performs no I/O by
  constructing the detector against a project with no definitions cache on disk at all.

## Phase 4: The banner [Must]

- **Goal**: the suggestion reaches the user, and acting on it routes through TARGET-08.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.definitions.ui.LuaAddonNotificationProvider` — design §3.4.
  - [ ] Register `<editorNotificationProvider>` in `plugin.xml` beside the existing one at `:640`.
  - [ ] Wire the three action labels to `LuaDefinitionLibraryEnabler.apply`, `suppressForSession`
        and `dismissDefinitionLibrary` respectively.
- **Exit criteria**: TC 12, 13, 14, 15 pass.

## Phase 5: Verification [Must]

- **Tasks**:
  - [ ] Full suite green via `gce-builder`, plus `ktlintFormat ktlintCheck`.
  - [ ] `@reviewer` pass over the diff against [requirements.md](requirements.md) and the
        engineering contract.
  - [ ] Run [human-verification-checklists.md](human-verification-checklists.md) live per the
        `verify-in-ide` skill. **This gate is not optional here.** TARGET-08 shipped three times
        looking finished on green unit suites while the running IDE did nothing useful, because a
        light fixture's project sits entirely inside `projectScope` and cannot observe a
        library-scope defect. A banner is a registration-and-presentation feature, which is exactly
        the class unit tests do not prove.
- **Exit criteria**: checklist scenarios pass; requirement statuses updated to `Full`.

## Testing Notes

- Phases 1–4 are all light-fixture testable; none needs `LibraryRootTestCase`, because detection
  deliberately never consults library content.
- `EditorNotificationProvider` is testable headlessly by calling `collectNotificationData(project, file)`
  directly and asserting on the returned `Function` — no editor needed for TC 12–14.
- A single-match completion auto-inserts and returns null from `completeBasic`; irrelevant here, but
  the same trap shape applies to any assertion on "exactly one" result.
