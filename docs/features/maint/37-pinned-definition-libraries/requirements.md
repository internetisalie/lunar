---
id: "MAINT-37"
title: "37: Corpus Sweeps Run With Pinned Definition Libraries"
type: "feature"
status: "in_progress"
priority: "medium"
parent_id: "MAINT"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-37: Corpus Sweeps Run With Pinned Definition Libraries

## Overview

The [MAINT-33](../33-corpus-sweep/requirements.md) corpus sweep indexes each pinned project with
**no definition libraries**, although each pinned project is written against one: luacheck,
luarocks and penlight are busted test suites, and ZeroBrane Studio is a wxLua application. So the largest buckets in the
committed baselines are not Lunar defects but missing definitions — the `LuaUndeclaredVariable`
floor is dominated by them (measured from `src/test/resources/corpus/*.baseline` at `e762dc74`):

| corpus | dominant undeclared symbols (top-10 breakdown, capped) | sum |
| :-- | :-- | --: |
| luacheck | `it` 469, `describe` 96, `pending` 3, `before_each` 2 | 570 of 608 |
| luarocks | `it` 449, `finally` 186, `describe` 166, `before_each` 44, `after_each` 41, `lazy_setup` 29, `lazy_teardown` 19, `pending` 3 | 937 of 955 |
| penlight | `it` 235, `describe` 106, `pending` 9, `before_each` 6, `after_each` 2, `setup` 1 | 359 of 359 |
| zerobrane | `wx` 1548, `wxstc` 274, `wxaui` 62 | 1884 of 1952 |

This feature pins, per corpus entry, the [TARGET-08](../../target/08-on-demand-definition-libraries/requirements.md)
catalog libraries the project actually uses; provisions them reproducibly and offline; enables them
through the component the settings page uses (`LuaDefinitionLibraryEnabler`); records the pins in the baseline so a re-pin is an
identity change; and re-baselines with every movement **attributed** — to the library by a
classifier over a member-alone pair, to the settings refresh or a previous pin by a member-alone run
of the starting configuration, or to the invocation shape by a reconciliation of the committed
full-shape delta against those runs — or the re-baseline blocks. That is the reason
[BUG-417](../../bug-fixes/417-inspection-cross-contamination-via-resolve-cache/bug-report.md) had to
land first. Parent epic: [MAINT](../requirements.md).

## Scope

### In Scope

Everything in the Functional Requirements table below, including the fix for
[BUG-496](../../bug-fixes/496-corpus-sweep-leaks-module-root-into-later-members/bug-report.md)
(MAINT-37-09) and the existing CI `corpus` job's trigger and fixture check (MAINT-37-02).

### Out of Scope

- **New catalog entries.** `lfs` (luarocks, zerobrane), `bit`/`jit`/`winapi` (zerobrane) have no
  catalog entry and stay undeclared. Adding a library to the catalog is TARGET's business.
- **Authoring the `wxlua` library.** That is [TARGET-10](../../target/10-wxlua-definition-libraries/requirements.md);
  this feature only pins it once it is catalogued.
- **Fixing what the re-baseline reveals.** Every attributed rise is filed as a BUG, per MAINT-33's
  rule that the ratchet and the fixes stay reviewable apart.
- **Changing `net.internetisalie.lunar.definitions` production code.** The sweep consumes it as is.
- **A LuaCATS-annotated corpus** — [MAINT-39](../39-luacats-annotated-corpus/requirements.md).

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| MAINT-37-01 | **Manifest pin** | M | A corpus entry names the catalog libraries it uses; an id the bundled catalog does not carry fails loudly. |
| MAINT-37-02 | **Verified offline provisioning** | M | `fetch-corpus.py` downloads every pinned archive (transitive `requires` included) into `test/corpus/.definitions/`, refusing any byte mismatch against the catalog pin; CI's `corpus` job asserts the archives are present and verified before it sweeps. The sweep never touches the network. |
| MAINT-37-03 | **User-path enablement** | M | The sweep fetches and enables pinned libraries through `LuaDefinitionLibraryEnabler` — the class behind the settings page — so the settings-changed broadcast (library index reload, daemon restart) and the resolve-cache drop run as they do for a user; it replaces any stale cached tree, and leaves no library behind for the next member. The publish, the reload and the drop are each observed by their effect (TC 36, TC 37). |
| MAINT-37-04 | **Pins are baseline identity** | M | The baseline records each registered library's version; a pin change fails the ratchet with a re-record instruction rather than as a regression or an improvement. |
| MAINT-37-05 | **Attributed re-baseline** | M | Twin runs on one commit, given the same settings refresh (checked by its observed effects), produce uncapped tallies; a classifier attributes every delta to the library or blocks the re-baseline; a member-alone `before` run of the starting configuration lets a reconciliation compare each member's committed full-shape delta with its attributed delta, measure the refresh or previous-pin effect separately, check each shape offset against the bound, and block on a structural or crash residual or an out-of-bound offset. |
| MAINT-37-06 | **busted pins** | M | luacheck, luarocks and penlight pin `busted`; their baselines are re-recorded with an attribution record. |
| MAINT-37-07 | **wxlua pin** | M | zerobrane pins `wxlua`; its baseline is re-recorded with an attribution record. Blocked on TARGET-10 Phase 4. |
| MAINT-37-08 | **Documented workflow** | S | `tooling/corpus/README.md` and `corpus.json`'s `$comment` describe the field, the fetch, the identity line and the attribution procedure. |
| MAINT-37-09 | **Per-member module root (BUG-496)** | M | After each member, the sweep restores the project's `sourcePath` to the value it had before that member and, when it changed, refreshes roots — so no member's `moduleRoot` reaches a later member, as a search path or as an indexed library. Lands in its own commit and re-baseline, before any pin. |

## Detailed Specifications

### MAINT-37-01: Manifest pin

`definitionLibraries` is an optional JSON array of catalog ids. Absent means `[]`. Blank strings
are dropped and duplicates collapse, first occurrence kept. Ids name entries in
`src/main/resources/definitions/lunar-definitions-catalog.json`; the version is **not** repeated
in the manifest — the catalog's commit-SHA `version` is the pin (design §9 explains why). An id the
catalog lacks is an error at both ends: `fetch-corpus.py` exits non-zero naming it, and the sweep
throws naming it before measuring anything. `LuaDefinitionCatalog.withDependencies` silently drops
unknown ids, so the check must happen before that call.

### MAINT-37-02: Verified offline provisioning

Archive file name: `<id>-<version>.tar.gz` (the `.tar.gz` suffix is what
`LuaArchiveExtractor.decompressorFor` dispatches on). A file already present whose `sha256` and
byte size equal the catalog's is not re-downloaded. Any other outcome — download error, digest or
size mismatch on every URL — exits non-zero, leaves no `.part` file, and never renames a mismatched
download into place. Production fetches the same archives under `ArtifactVerification.ADVISORY`;
the sweep deliberately does **not**: a baseline is only comparable to a byte-identical input.

`definition_pins.py --verify DIR` re-checks that every archive the manifest's pins resolve to is in
`DIR` with the catalog's `size` and `sha256`, and exits `1` naming each one that is not (design
§3.14). CI's `corpus` job runs it as part of its fixture step's verdict, because that step cannot
see `fetch-corpus.py`'s exit status.

### MAINT-37-03: User-path enablement

The user's path is the settings page, `LuaDefinitionLibrariesConfigurable`, a shell over
`LuaDefinitionLibraryEnabler` (`LuaDefinitionLibrariesConfigurable.kt:30`), whose `apply(ids)` writes
the enable list, publishes the settings broadcast (library index reload and daemon restart,
`LuaSettingsChangeListener.kt:31-40`) and drops resolve caches (`LuaProjectSettings.kt:198-204`). Per member the sweep stages each pinned library through the enabler from the local
verified archive, replacing any cached tree (TC 10, TC 11), then calls `apply(ids)` and asserts the
EP-registered `LuaDefinitionLibraryProvider` watches exactly those trees (TC 8, TC 9, TC 13); after
the member it unticks every library and deletes the trees (TC 12). The algorithm is design §3.3 and
§3.4; the settings subscriber is the plugin's own, checked at `setUp` (design §3.13).

**The refresh is asserted by its effects, not by its publish**: the publishes, the roots change the
reload makes, and the PSI events the reload and the cache drop cause (design §2.6a). The enable and
release windows each read exactly `RefreshObservation(1, 1, 4)` (TC 36).

### MAINT-37-04: Pins are baseline identity

`library.<id>=<version>` lines, sorted by id, one per library in the dependency closure, written
after `unresolvedRequires=`. Identity-checked like `commit`: any difference — added, removed or
moved version — fails with a message containing `Definition-library pins changed`. A baseline with
no `library.` lines (every baseline today) parses to an empty map and stays valid for an unpinned
member.

### MAINT-37-05: Attributed re-baseline

`-PcorpusAttribution=bare|pinned` makes `LuaCorpusSweepTest` write
`build/corpus-attribution/<mode>/<name>.tally` (design §4.4) instead of ratcheting or recording;
`bare` skips provisioning and gives the arm the same settings broadcast without a library (design
§3.12; TC 37). Combining it with `-PrecordCorpusBaseline` is an error (TC 48). A bare arm that
registered a library or recorded no symbol hits fails the run and leaves no tally (TC 49; design
§2.7 step 4). Both runs are **member-alone** with the same test filter (BUG-418: one invocation
shape is byte-deterministic). `tooling/corpus/attribute_delta.py` classifies every non-zero delta
(design §3.5; TC 20–27, TC 32) and exits `1` when any row is `UNEXPLAINED`, `2` when the inputs are
not comparable — including a pair whose observed refresh differs (TC 24, TC 38).

The committed baselines are recorded in the **full** shape, so each member's committed delta is
reconciled with the delta from a member-alone `before` run of the starting configuration to the
pinned arm (design §3.11; TC 39–46). The refresh effect (first pin) or previous-pin effect (re-pin)
is measured and labelled separately; each shape offset is checked against ±2 on both sides; and a
structural, crash or `highlightFailures` residual, an `inspection.*` residual beyond 4, an offset
beyond 2, or a start-effect row a refresh or library cannot cause stops the change. A smaller
`inspection.*` residual is recorded in `rebaseline-attribution.md` with its values.

### MAINT-37-06 / -07: Which corpus pins what

Measured 2026-10-10 from the local checkouts under `test/corpus/`, whose `.corpus-sha` stamps equal
the manifest commits (file counts are as of those commits):

| corpus | evidence | pin |
| :-- | :-- | :-- |
| luacheck | `.busted` at the checkout root; 36 files have a line starting `describe(` or `it(` | `busted` |
| luarocks | `.busted` (`helper = "spec.util.test_env"`); 43 such files | `busted` |
| penlight | `.busted` (`lpath = "./lua/?.lua;…"`); 17 such files | `busted` |
| zerobrane | `src/main.lua:48` `require("wx")`; no `.busted`, no such file | `wxlua` |

`busted` pulls in `luassert` through its catalog `requires`. The globals those two archives declare
were extracted from the real pinned archives with design §3.6's rule (executed 2026-10-10):

```
busted:   after_each assert async before_each context describe done expose file finally insulate it
          lazy_setup lazy_teardown mock pending randomize setup spec spy strict_setup strict_teardown
          stub teardown test
luassert: match mock stub
```

Every busted symbol in the table under Overview is in that set.

**When MAINT-37-06 is Full.** luacheck, luarocks and penlight each keep their `busted` pin
(classifier exit 0 on each pair) and TC 28/29 hold for each of them. A member whose pair exits 1 has its pin withheld and a BUG
filed; the other members' pins still land. MAINT-37-06 is then **Partial** in the MAINT epic table,
with the BUG ID in the row's description, and Phase 4 stays open until that BUG is closed and the
withheld member's pin lands through the record sequence (implementation-plan → Phase 4) with
`PAIRED` = that member.

### MAINT-37-09: Per-member module root (BUG-496)

`CorpusSweep.applyModuleRoot` (`CorpusSweep.kt:139-147`) overwrites
`LuaProjectSettings.State.sourcePath` for penlight, the only entry with a `moduleRoot`, and nothing
restores it. Two effects reach every member swept after penlight in the same JVM:

- The written value **replaces** the whole default `package.path`
  (`PathConfiguration.getStaticSourcePathPatterns`, `SourcePathPattern.kt:37-44`, falls back to
  `DEFAULT_SOURCE_PATH` only when the setting is empty), including `$PROJECT_DIR$/?.lua`.
- `PlatformLibraryProvider.getExternalLibraries` (`PlatformLibraryProvider.kt:54-73`, registered at
  `plugin.xml:537`) turns every absolute leading path outside the project base into an indexed
  "Search Trees" library on the next roots change. Once MAINT-37 pins penlight, its `release` fires
  such a roots change directly after penlight's sweep.

The rule: `LuaCorpusSweepTest.setUp` snapshots `sourcePath`; `tearDown` restores it **before**
releasing libraries, and when the restored value differs from the one penlight left, fires a roots
change if `release` will not. Whether the second effect occurs in a light fixture is DR-06, executed
by TC 35's control half.

## Behavior Rules

- **The catalog is the version authority.** Re-pinning a catalog entry re-pins every sweep that
  uses it. The identity line keeps the re-pin's PR red until the re-record is in the same change:
  its CI `corpus` job runs on any PR that touches `src/main/resources/definitions/` (added to the
  job's trigger in Phase 3, design §7 "CI") and fails with `Definition-library pins changed`
  otherwise. A catalog change pushed to `main` without a PR turns `main`'s `corpus` job red, and the
  next record sequence's pre-gate stops on it; the commit that resolves that stop reverts the change
  (implementation-plan → S1).
- **A library may only move what a library can explain.** It cannot change `requires`, `files`,
  `parseErrors`, oracle or lexer counters, or crashes: those deltas are `UNEXPLAINED` by rule.
- **An `UNEXPLAINED` row blocks that corpus's pin.** When the pin is a manifest edit, the pin is
  left out of the commit and the row is filed as a BUG; other corpora proceed. The requirement that
  pin belongs to is Partial until the BUG closes and the pin lands (MAINT-37-06 above; MAINT-37-07
  likewise). When the change is a catalog re-pin, the whole re-pin stops and none of it lands: the
  old version leaves the catalog with the change, so one member cannot be held back on it.
- **A member's project state does not outlive it.** Libraries and `sourcePath` are both restored
  in `tearDown` (MAINT-37-03, MAINT-37-09).
- **Both arms of a pair receive the same refresh, and every committed delta is reconciled**
  (MAINT-37-05).
- **Every re-record runs the one record sequence** (implementation-plan → Phase 4) — the first pins,
  a withheld member's later pin, the `wxlua` pin and any catalog re-pin alike.
- **A rise is recorded, not suppressed.** `TRIAGE-*` rows are re-baselined as measured; each
  `TRIAGE-CONTRACT` inspection id with a positive delta gets one BUG.
- **Attribution runs never write a baseline**, and recording runs never attribute.

## Test Cases

"Mutation" names the code change that must turn the case red; every mutation is reachable from that
case's own fixture.

Each Kotlin case (TC 1–3, 8–19, 33–37, 48, 49) is the test method design §2.16 names, and none of
those names contains `Corpus`, so each runs in the routine suite (design §2.16).

TC 20–26 and TC 32 share two parsed tallies (`dict[str, str]`, as `parse_tally` returns). Each case
states only its additions or replacements:

```
B (bare):   commit=c  files=2  settingsBroadcasts=1  rootsTicks=1  physicalPsiChanges=2  unresolvedRequires=1
            inspection.LuaUndeclaredVariable=10  allsymbol.LuaUndeclaredVariable.describe=6
            allsymbol.LuaUndeclaredVariable.lfs=4
            inspection.LuaShadowingVariable=3    allsymbol.LuaShadowingVariable.file=1
P (pinned): commit=c  files=2  settingsBroadcasts=1  rootsTicks=1  physicalPsiChanges=2  library.busted=v
            unresolvedRequires=0
            inspection.LuaUndeclaredVariable=4   allsymbol.LuaUndeclaredVariable.lfs=4
            inspection.LuaShadowingVariable=5    allsymbol.LuaShadowingVariable.file=3
```

The classifier cases assert the rows `attribute` returns and the value of `exit_code(rows)`; the
mapping of `NotComparable` to exit `2` belongs to `main` and is TC 32.

| # | Requirement | Given (input) | When (action) | Then (expected) | Mutation that turns it red |
|---|-------------|---------------|---------------|-----------------|----------------------------|
| 1 | MAINT-37-01 | Manifest rows `{"name":"a",…,"roots":["src"],"definitionLibraries":["busted","busted"," "]}` and `{"name":"b",…,"roots":["src"]}` | `CorpusManifest.entry(root, "a")`, `entry(root, "b")` | `a.definitionLibraries == ["busted"]`; `b.definitionLibraries == []` | `parseEntry` ignores the key (always `[]`); or drops `.distinct()` |
| 2 | MAINT-37-01 | `CorpusEntry` with `definitionLibraries = ["nosuch"]` | `CorpusDefinitionLibraries.resolve(entry)` | `IllegalStateException` whose message contains `nosuch` and `lunar-definitions-catalog.json` | delete the unknown-id check (`withDependencies` then returns `[]` silently) |
| 3 | MAINT-37-01 | `CorpusEntry` with `["busted"]` | `resolve(entry).map { it.id }` | `["busted", "luassert"]` | call `catalog.entry(id)` per id instead of `withDependencies` |
| 4 | MAINT-37-02 | manifest `{"corpora":[{"name":"a","definitionLibraries":["busted"]},{"name":"b"}]}`; catalog `busted` (`requires:["luassert"]`), `luassert`, `love2d` | `definition_pins.resolve_libraries(manifest, catalog)` | ids `["busted", "luassert"]` | no `requires` closure (→ `["busted"]`); or iterate the whole catalog (→ `love2d` present) |
| 5 | MAINT-37-02 | same catalog; manifest pins `["nosuch"]` | `resolve_libraries` | raises `PinError` whose text contains `nosuch` | drop the unknown-id check |
| 6 | MAINT-37-02 | a local archive; `lib` whose `sha256`/`size` match it and whose `urls` is `[file://<archive>]` | `fetch_library(lib, dest)` twice, the second time with `urls` = `[file://<missing>]` | both calls return `dest/<id>-<version>.tar.gz`, equal bytes | remove the "already verified" skip (second call raises) |
| 7 | MAINT-37-02 | same archive; `lib.sha256 = "00…0"` | `fetch_library` | raises `PinError` containing both `00…0` and the real digest; `dest` has no `.tar.gz` and no `.part` | skip the digest comparison; or leave the `.part` file |
| 8 | MAINT-37-03 | temp repo root with synthetic `busted-<v>.tar.gz` (`busted-<v>/library/busted.lua` = `---@meta`+`function describe(name, block) end`+`function it(name, block) end`) and `luassert-<v>.tar.gz` (`luassert-<v>/library/luassert.lua` = `---@meta`); entry pins `["busted"]`; `LuaUndeclaredVariableInspection` enabled | `provision(project, entry, root)`, then highlight `x_spec.lua` = `describe("a", function() it("b", function() end) end)` | no highlight description starts with `Undeclared variable '` | `enable` skips `apply(ids)` (the enable list stays empty, so the provider registers nothing and `assertRegistered` throws) |
| 9 | MAINT-37-03 | same fixture, **no** `provision` | highlight the same file | descriptions include `Undeclared variable 'describe'` and `Undeclared variable 'it'` | (control — proves TC 8's fixture would report without the library) |
| 10 | MAINT-37-03 | TC 8's fixture, plus `defaultCacheRoot()/busted-<v>/busted.lua` pre-seeded with `---@meta` only | `provision`, highlight | no `Undeclared variable 'describe'` | drop the pre-delete (`missingEntries` sees `isCached` and never extracts, so the stale tree stays) |
| 11 | MAINT-37-03 | entry pins `["busted"]`; repo root with **no** archives | `provision` | `IllegalStateException` whose message contains `tooling/corpus/fetch-corpus.py` and `busted-<v>.tar.gz` | drop the archive check (`fetchAll` then reports both ids failed → the "did not extract" message, which names no fetch script) |
| 12 | MAINT-37-03 | after TC 8's `provision` | `release(project, dirs)` | `enabledDefinitionLibraries` is empty; no returned dir exists on disk | omit `apply(emptyList())`; or omit the delete |
| 13 | MAINT-37-04 | after TC 8's `provision` | `enabledPins(project)` | `{busted=<catalog version>, luassert=<catalog version>}` | read the enable list without `withDependencies` (→ no `luassert`) |
| 14 | MAINT-37-04 | `CorpusMetrics` with `libraries = {luassert=d352…, busted=5ed8…}` | `parse(render(m))` | equals `m`; rendered text has `library.busted=` before `library.luassert=`, both after `unresolvedRequires=` | `render` omits libraries; or `parseLibraries` keeps the `library.` prefix (keys become `library.busted`, so `parse(render(m)) != m`) |
| 15 | MAINT-37-04 | a baseline text with no `library.` line | `parse` | `libraries == {}` | `parseLibraries` starts with `check(rows.any { it.first.startsWith(LIBRARY_PREFIX) }) { "no library rows" }` (throws `IllegalStateException`) |
| 16 | MAINT-37-04 | baseline `libraries={busted=A}`; observed `{busted=B}`; then baseline `{}` vs observed `{busted=A}` | `CorpusGuards.assertRatchet` | both fail with a message containing `Definition-library pins changed` | delete the identity assertion |
| 17 | MAINT-37-04 | baseline and observed both `libraries={}`, otherwise equal | `assertRatchet` | no failure | assert `libraries` non-empty |
| 18 | MAINT-37-05 | property values `null`, `""`, `"bare"`, `"pinned"`, `"Pinned"` | `CorpusAttributionMode.fromProperty` | `null`, `null`, `BARE`, `PINNED`, `IllegalStateException` | map unknown values to `null` |
| 19 | MAINT-37-05 | metrics with `allSymbolHits = {LuaUndeclaredVariable.describe=6}` | `CorpusBaseline.render(metrics)`, `CorpusAttribution.renderTally(metrics, RefreshObservation(1, 2L, 3))` | baseline text contains no `allsymbol.`, `settingsBroadcasts`, `rootsTicks` or `physicalPsiChanges`; tally contains `settingsBroadcasts=1`, `rootsTicks=2`, `physicalPsiChanges=3` and `allsymbol.LuaUndeclaredVariable.describe=6`, in that order | `render` emits `allSymbolHits`; or `renderTally` omits them; or `renderTally` omits any one refresh line; or writes `rootsTicks` from `settingsBroadcasts` (→ `rootsTicks=1`) |
| 20 | MAINT-37-05 | **B** and **P** as given above the table; `G = {describe, file}` | `rows = attribute(B, P, G)`; `exit_code(rows)` | exactly three rows: `inspection.LuaUndeclaredVariable` `EXPLAINED` (evidence `describe:-6`), `unresolvedRequires` `EXPECTED`, `inspection.LuaShadowingVariable` `TRIAGE-GLOBAL` (evidence `file:+2`); `exit_code(rows) == 0` | treat `LuaShadowingVariable` like Undeclared (→ `UNEXPLAINED`); or make every non-Undeclared inspection `TRIAGE-CONTRACT` |
| 21 | MAINT-37-05 | **B**; **P** with `allsymbol.LuaUndeclaredVariable.lfs=3` and `inspection.LuaUndeclaredVariable=3`; `G` as TC 20 | `attribute`, `exit_code` | Undeclared row `UNEXPLAINED`, evidence `lfs:-1`; `exit_code(rows) == 1` | classify Undeclared by its total alone; or `exit_code` returns `0` unconditionally |
| 22 | MAINT-37-05 | **B** plus `requires=5`; **P** plus `requires=6`; `G` as TC 20 | `attribute`, `exit_code` | a `requires` row `UNEXPLAINED` (delta `+1`); `exit_code(rows) == 1` | omit `requires` from the numeric key set |
| 23 | MAINT-37-05 | **B**; **P** with `allsymbol.LuaUndeclaredVariable.describe=7` and `inspection.LuaUndeclaredVariable=11`; `G` as TC 20 | `attribute`, `exit_code` | Undeclared row `UNEXPLAINED`, evidence `describe:+1` (a library global rose); `exit_code(rows) == 1` | accept any delta on a library global |
| 24 | MAINT-37-05 | (a) **B** plus `library.busted=v`, **P**; (b) **B**, **P** with `commit=d`; (c) **B**, **P** with `settingsBroadcasts=0`; (d) **B** without its `settingsBroadcasts` line, **P**; (e) **B**, **P** with `files=3`; (f) **B**, **P** with `rootsTicks=0`; (g) **B**, **P** with `physicalPsiChanges=1`; (h) **B**, **P** without its `rootsTicks` line | `attribute` each | `NotComparable` whose text equals (a) `the bare run carried libraries`; (b) `not the same corpus commit (bare c, pinned d)`; (c) `refresh observable settingsBroadcasts differs (bare 1, pinned 0): the arms did not receive the same refresh`; (d) `the bare tally has no settingsBroadcasts line`; (e) `file counts differ (bare 2, pinned 3)`; (f) `refresh observable rootsTicks differs (bare 1, pinned 0): the arms did not receive the same refresh`; (g) `refresh observable physicalPsiChanges differs (bare 2, pinned 1): the arms did not receive the same refresh`; (h) `the pinned tally has no rootsTicks line` | skip the comparability checks; for (c) alone, drop `settingsBroadcasts` from `REFRESH_KEYS`; for (f) and (g), `REFRESH_KEYS = ("settingsBroadcasts",)` (the pair is then classified, no raise); for (d) and (h), read a missing line as equal to the other side |
| 25 | MAINT-37-05 | **B**; **P** plus `inspection.highlightFailures=1` | `attribute` | raises `NotComparable` whose text contains `BUG-390` | skip the check |
| 26 | MAINT-37-05 | **B** plus `inspection.LuaTypeAssignability=0`; **P** plus `inspection.LuaTypeAssignability=3` and `allsymbol.LuaTypeAssignability.x=3`; `G` as TC 20 | `attribute`, `exit_code` | TC 20's three rows plus `inspection.LuaTypeAssignability` `TRIAGE-CONTRACT`, evidence `x:+3`; `exit_code(rows) == 0` | classify every rising TRIAGE row `TRIAGE-GLOBAL` |
| 27 | MAINT-37-05 | synthetic archive: `p/library/a.lua` = `function describe(n) end`, `context = describe`, `local x = 1`, `  function nested() end`, `function M.f() end`, `x == y`; `p/spec/b.lua` = `function outside() end`; `p/config.json` | `library_globals(archive, "p/library")` | `{"describe", "context"}` | unanchored regex (→ `nested`); ignore the prefix (→ `outside`) |
| 28 | MAINT-37-06 | the re-recorded baselines | `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache --tests '*Corpus*' -PwithCorpus"` | green; every member whose `busted` pin was kept carries `library.busted=5ed85d0e016a5eb5eca097aa52905eedf1b180f1` and `library.luassert=d3528bb679302cbfdedefabb37064515ab95f7b9`; zerobrane's baseline, and any withheld member's, carries no `library.` line | remove the `libraries` copy in `CorpusSweep.run` → red with `Definition-library pins changed` |
| 29 | MAINT-37-06 | each kept member's `pinned` tally | grep `allsymbol.LuaUndeclaredVariable.`; `attribute_delta.py` | no key whose symbol is in the busted ∪ luassert global set above; `attribute_delta.py` exits 0 for each kept member | pin nothing (→ every `describe`/`it` key present) |
| 30 | MAINT-37-07 | zerobrane's `bare` and `pinned` tallies | grep, `attribute_delta.py` | pinned has no `allsymbol.LuaUndeclaredVariable.wx`, `.wxstc` or `.wxaui` key; exit 0; baseline carries `library.wxlua=<catalog version>` | drop zerobrane's `definitionLibraries` (→ the `wx` keys stay, and `stage` returns empty so `enable` fires no broadcast, so the pinned tally reads `settingsBroadcasts=0` against the bare arm's `1` and the classifier exits 2 at design §3.5 row d, before row f's "no library" check is reached) |
| 31 | MAINT-37-08 | `tooling/corpus/README.md`, `corpus.json` | `grep -c definitionLibraries`, `grep -c corpusAttribution` | each ≥ 1 in the README; `definitionLibraries` ≥ 1 in `$comment` | (documentation check) |
| 32 | MAINT-37-05 | a temp dir holding `bare/a.tally` = **B** and `pinned/a.tally` = **P**, one `key=value` per line; `catalog.json` = `{"catalogVersion":1,"libraries":[{"id":"busted","version":"w"}]}`; an empty `defs/` | `main(["--corpus","a","--bare",<bare>,"--pinned",<pinned>,"--definitions",<defs>,"--catalog",<catalog.json>])` | returns `2`; stderr contains `attribute_delta: not comparable: library busted: the pinned tally carries v, the catalog has w` | let `NotComparable` propagate out of `main` (the test errors instead of reading `2`); or skip the version check (G-building then opens `defs/busted-v.tar.gz` and the message is the missing-archive one) |
| 33 | MAINT-37-09 | `saved = CorpusSourcePath.snapshot(project)`; then `state.sourcePath = "/nonexistent/?.lua"` | `CorpusSourcePath.restore(project, saved)` | returns `true`; `state.sourcePath == saved` | `restore` returns without writing |
| 34 | MAINT-37-09 | `saved = CorpusSourcePath.snapshot(project)`; no change | `restore(project, saved)` | returns `false` | return `true` unconditionally (a needless roots change per member) |
| 35 | MAINT-37-09 | temp dir `D` from `Files.createTempDirectory("lunar-module-root")` (outside `project.basePath`) holding `leakprobe.lua` = `return {}`, root access allowed, refreshed into the VFS; `saved = snapshot(project)`; `state.sourcePath = "$D/?.lua"`; `CorpusDefinitionLibraries.refreshRoots(project)` | **control**: `runReadAction { FilenameIndex.getVirtualFilesByName("leakprobe.lua", GlobalSearchScope.allScope(project)) }`; then `restore(project, saved)`, `refreshRoots(project)`, the same query | control: non-empty (the module root became an indexed "Search Trees" library — DR-06); after: empty | skip the `restore` call (→ the second query is still non-empty) |
| 36 | MAINT-37-03 | TC 8's fixture | `staged = stage(project, entry, root)`; `e = observeRefresh(project, testRootDisposable) { enable(project, entry, root, staged) }`; `r = observeRefresh(project, testRootDisposable) { release(project, e.cacheDirs); emptyList() }` | `e.observation == RefreshObservation(1, 1L, 4)`; `e.cacheDirs == staged`; `r.observation == RefreshObservation(1, 1L, 4)`. Both tuples are measured: the enable window by DR-10 (pinned, waited; runs 1–3), the release window by DR-07b (TC 36's `o2 − o1`, three invocations) | Mutant tuples marked "predicted" are read from source, not measured; the assertion is on the whole tuple, so any value other than `(1, 1, 4)` kills the mutant. `enable` writes `state.enabledDefinitionLibraries` directly instead of calling `apply` (→ `e.observation.settingsBroadcasts == 0`, predicted); or writes it directly **and** publishes the topic directly, as `LuaProjectConfigurable.kt:277` does (→ `(1, 1, 3)`, predicted: the drop's event missing, design §3.12); or `observeRefresh` omits its step-1 `settle` (→ `e.observation == (1, 1, 5)` (measured, DR-11); DR-10's no-wait control read the same); or `release` clears the state directly instead of calling `apply(emptyList())` (→ `r.observation == (0, 0, 0)`, predicted); or `observeRefresh` returns `emptyList()` in place of `refresh()`'s result (→ `e.cacheDirs == []`, and the sweep's `tearDown` would leave the trees on disk) |
| 37 | MAINT-37-05 | enable list empty (as `setUp` leaves it); **no** `stage` | `o = observeRefresh(project, testRootDisposable) { refreshUnpinned(project); emptyList() }`; `enabledPins(project)` | `o.observation == RefreshObservation(1, 1L, 4)` — DR-10's bare value, equal to TC 36's enable window; `o.cacheDirs == []`; `enabledPins(project) == {}` | Mutant tuples marked "predicted" are read from source, not measured; any value other than `(1, 1, 4)` kills the mutant. `refreshUnpinned` omits `notifyDefinitionRootsChanged()` (→ `(0, 0, 0)`, predicted); or omits its `settle` (the `invokeLater` is never drained before the read → `(0, 0, 0)`, predicted; the failure `LuaDefinitionEnableListTest.kt:64-65` records); or design §3.12's direct-publish mutant (→ `o.observation == (1, 1, 3)` (measured, DR-11)) |
| 38 | MAINT-37-05 | DR-04's luacheck tallies, runs 1–2 bare and runs 3–4 pinned | `grep -E '^(settingsBroadcasts\|rootsTicks\|physicalPsiChanges)=' <tally>` on each; `attribute_delta.py` on run 1 bare vs run 3 pinned | every one of runs 1–4 carries `settingsBroadcasts=1` and `rootsTicks=1` (one publish, one roots change per refresh — the composition DR-07b measured); `physicalPsiChanges` carries one value across runs 1–4; classifier exit is `0` or `1`, never `2` | each mutant below, applied alone with the `temporary-edits` skill, re-running only the arm it names (a fresh JVM, luacheck member-alone) and classifying it against the other arm's unmutated tally (a bare mutant against run 3 pinned, a pinned mutant against run 1 bare), gives exit `2` with the stderr prefix shown. **Bare-arm mutants:** **(i)** `refreshFor`'s bare branch passes `{ emptyList() }` to `observeRefresh` instead of calling `refreshUnpinned` (design §2.7) → `attribute_delta: not comparable: refresh observable settingsBroadcasts differs (bare 0, pinned 1)`; **(ii)** design §3.12's direct-publish mutant in `refreshUnpinned` → `… refresh observable physicalPsiChanges differs (bare `. **Pinned-arm mutant:** **(iii)** `enable` writes `state.enabledDefinitionLibraries` directly and publishes `LuaSettingsChangedListener.TOPIC` directly in place of `apply` (TC 36's second mutant) → `… refresh observable physicalPsiChanges differs (bare `. A surviving mutant stops Phase 4 and returns design §2.6a to Step 9 review: no mutant here has an equivalence rule |
| 39 | MAINT-37-05 | `old` = `requires=5 unresolvedRequires=1 inspection.LuaUndeclaredVariable=10 inspection.LuaShadowingVariable=3`; `new` = `requires=5 unresolvedRequires=0 inspection.LuaUndeclaredVariable=4 inspection.LuaShadowingVariable=6`; **B** standing in for the `before` tally, **P** | `rows = reconcile(key_delta(old, new), key_delta(B, P))` | exactly one row: `Residual(key="inspection.LuaShadowingVariable", committed=3, attributed=2, residual=1, blocking=False)` | `reconcile` returns the committed delta without subtracting (→ rows also for `inspection.LuaUndeclaredVariable` −6 and `unresolvedRequires` −1) |
| 40 | MAINT-37-05 | TC 39's inputs with `new` carrying `requires=6` | `reconcile(...)`; `residual_exit(rows)` | a `requires` row with `committed=1`, `attributed=0`, `residual=1`, `blocking=True`; `residual_exit(rows) == 1` | `blocking` true only for `crash.*` (→ `requires` non-blocking, exit `0`) |
| 41 | MAINT-37-05 | temp dir: `old.baseline` = `requires=5 inspection.LuaShadowingVariable=3`; `new.baseline` = `requires=5 inspection.LuaShadowingVariable=5 crash.parse:NullPointerException=1`; no tallies | (a) `reconcile_baseline.main(["--corpus","zerobrane","--old",<old>,"--new",<new>])`; (b) the same plus `--bare <dir>` without `--before` or `--pinned`; (c) (a) plus `--offset` and `--pinned <dir>` | (a) returns `1`; stdout has a `crash.parse:NullPointerException` row with `blocking` `yes` and an `inspection.LuaShadowingVariable` row with residual `+2` and `blocking` `no`; (b) returns `2`, stderr contains `reconcile_baseline: --before, --bare and --pinned go together`; (c) returns `2`, stderr contains `reconcile_baseline: --offset compares two records and takes no --before, --bare or --pinned` | for (a): `main` returns `0` when no pair is given (an unpinned member skipped); or `key_delta` omits `crash.*` keys (→ exit `0`). For (b): `main` treats a partial set of tally directories as none (→ it reconciles against `{}` and returns `1` from (a)'s rows, printing them, instead of `2`). For (c): `main` ignores the tally directories under `--offset` (→ it prints offset rows and returns `1`, instead of `2`) |
| 42 | MAINT-37-05 | `committed = {"inspection.LuaShadowingVariable": 4, "inspection.LuaTypeAssignability": 5, "inspection.LuaUnusedLocal": -5, "inspection.highlightFailures": 1}`; `attributed = {}` | `rows = reconcile(committed, attributed)`; `residual_exit(rows)` | rows in this order: `LuaShadowingVariable` residual `4` `blocking=False`; `LuaTypeAssignability` `5` `True`; `LuaUnusedLocal` `-5` `True`; `highlightFailures` `1` `True` (each key with its `inspection.` prefix); `residual_exit(rows) == 1` | `abs(r) >= SHAPE_RESIDUAL_BOUND` (→ Shadowing `True`); or drop the bound clause (→ TypeAssignability and UnusedLocal `False`); or `r > SHAPE_RESIDUAL_BOUND` without `abs` (→ UnusedLocal `False`); or drop the `HIGHLIGHT_FAILURES` clause (→ highlightFailures `False`) |
| 43 | MAINT-37-05 | (a) `before` = `requires=5 unresolvedRequires=1 inspection.LuaShadowingVariable=3`; `bare` = `requires=5 unresolvedRequires=2 inspection.LuaShadowingVariable=4 settingsBroadcasts=1`. (b) `before` = (a)'s plus `library.busted=u` and with `requires=6`; `bare` as (a) | `start_effect(before, bare)` for each | (a) `("refresh effect", [StartRow("inspection.LuaShadowingVariable", 3, 4, 1, False), StartRow("unresolvedRequires", 1, 2, 1, True)])`; (b) `("previous pin effect (busted=u)", [StartRow("inspection.LuaShadowingVariable", 3, 4, -1, False), StartRow("requires", 6, 5, 1, True), StartRow("unresolvedRequires", 1, 2, -1, False)])` | ignore the `library.` check (→ (b) titled `refresh effect`, its `unresolvedRequires` effect `+1`); compute `before − bare` in the refresh branch (→ (a) effects `-1`); refresh rows block only on `crash.*` (→ (a) `unresolvedRequires` `False`); include untracked keys (→ a `settingsBroadcasts` row in (a)); previous-pin `unresolvedRequires` blocks at any non-zero effect (→ (b) `True`); previous-pin rows use `bare − before` (→ (b) `requires` `-1`, `unresolvedRequires` `+1` `True`) |
| 44 | MAINT-37-05 | first pin, temp dir: `old.baseline` = `requires=5 inspection.LuaShadowingVariable=3`; `new.baseline` = `requires=5 library.busted=v inspection.LuaShadowingVariable=6`; `before/a.tally` = `requires=5 settingsBroadcasts=0 inspection.LuaShadowingVariable=3`; `bare/a.tally` = `requires=5 settingsBroadcasts=1 inspection.LuaShadowingVariable=4`; `pinned/a.tally` = `requires=5 settingsBroadcasts=1 library.busted=v inspection.LuaShadowingVariable=6` | `main(["--corpus","a","--old",<old>,"--new",<new>,"--before",<before>,"--bare",<bare>,"--pinned",<pinned>])` | returns `0`; stdout's `## a — shape residual` table is the single row `\| — \| 0 \| 0 \| 0 \| no \|`; stdout's `## a — refresh effect` table has the row `\| inspection.LuaShadowingVariable \| 3 \| 4 \| +1 \| no \|` | take `attributed` from `key_delta(bare, pinned)` (→ a residual row `inspection.LuaShadowingVariable` `+3`/`+2`/`+1`: the refresh effect mislabelled as shape); or `main` omits `render_start` (→ no refresh-effect heading) |
| 45 | MAINT-37-05 | catalog re-pin, temp dir: `old.baseline` = `unresolvedRequires=0 library.busted=u inspection.LuaShadowingVariable=5`; `new.baseline` = `unresolvedRequires=0 library.busted=v inspection.LuaShadowingVariable=6`; `before/a.tally` = `settingsBroadcasts=1 unresolvedRequires=0 library.busted=u inspection.LuaShadowingVariable=5`; `bare/a.tally` = `settingsBroadcasts=1 unresolvedRequires=2 inspection.LuaShadowingVariable=4`; `pinned/a.tally` = `settingsBroadcasts=1 unresolvedRequires=0 library.busted=v inspection.LuaShadowingVariable=6` | `main` as TC 44 | returns `0`; the shape-residual table is the single empty row; stdout has the heading `## a — previous pin effect (busted=u)` with rows `inspection.LuaShadowingVariable` `5`/`4`/`+1`/`no` and `unresolvedRequires` `0`/`2`/`-2`/`no` | take `attributed` from `key_delta(bare, pinned)` (→ an `unresolvedRequires` residual `+2` with `blocking` `yes`, and `main` returns `1`: a re-pin stopped by the old library's own effect); or `start_effect` ignores the `library.` check (→ a `refresh effect` table whose `unresolvedRequires` row is `+2` `yes`, return `1`) |
| 46 | MAINT-37-05 | temp dir: `before/a.tally` = `inspection.LuaShadowingVariable=3 inspection.LuaTypeAssignability=10`; `new.baseline` = `inspection.LuaShadowingVariable=5 inspection.LuaTypeAssignability=13` | `main(["--corpus","a","--offset","--old",<before/a.tally>,"--new",<new.baseline>])` | returns `1`; stdout heading `## a — shape offset`; rows `inspection.LuaShadowingVariable` residual `+2` `no` and `inspection.LuaTypeAssignability` residual `+3` `yes` | `--offset` passes `SHAPE_RESIDUAL_BOUND` (→ `+3` is `no`, returns `0`); or `--offset` renders the default title (→ heading `shape residual`) |
| 47 | MAINT-37-02 | temp dir: `defs/` holding `busted-v.tar.gz` and `luassert-w.tar.gz`, two distinct archives written with `tarfile`; `catalog.json` = `{"catalogVersion":1,"libraries":[…]}` with `busted` (`version` `v`, `requires` `["luassert"]`, `size`/`sha256` of its archive) and `luassert` (`version` `w`, `size`/`sha256` of its archive); `manifest.json` = `{"corpora":[{"name":"a","definitionLibraries":["busted"]}]}` | `definition_pins.main(["--verify",<defs>,"--manifest",<manifest.json>,"--catalog",<catalog.json>])` in three states: (a) as given; (b) `luassert-w.tar.gz` deleted; (c) `luassert-w.tar.gz` restored and `busted-v.tar.gz` overwritten with as many zero bytes as it had | (a) returns `0`, stdout `definitions ok (2 archives verified in <defs>)`; (b) returns `1`, stderr contains `definition_pins: not provisioned: luassert-w.tar.gz`; (c) returns `1`, stderr contains `definition_pins: not provisioned: busted-v.tar.gz` | for (b): `main` verifies the manifest's declared ids without `resolve_libraries`' `requires` closure (→ returns `0`); or `main` returns `0` whatever `verify_archives` returns. For (c): `verify_archives` checks existence and size only, without the digest (→ returns `0`, since the size is unchanged) |
| 48 | MAINT-37-05 | `(attribution, record)` = (a) `("bare", "true")`; (b) `("pinned", "true")`; (c) `("bare", null)`; (d) `("bare", "false")`; (e) `(null, "true")` | `CorpusAttributionMode.forRun(attribution, record)`, each inside `runCatching { … }` | (a) and (b) fail with `IllegalStateException` whose message contains `mutually exclusive`; (c) and (d) return `BARE`; (e) returns `null` (a record run) | delete the `check` (→ (a) returns `BARE`, (b) `PINNED`, no failure); or `check(mode == null)`, dropping the record clause (→ (c) fails); or `check(record != "true")`, dropping `mode == null` (→ (e) fails: every record run would be refused) |
| 49 | MAINT-37-05 | `hits = mapOf("LuaUndeclaredVariable.describe" to 6)`, `lib = mapOf("busted" to "v")`; (a) `BARE`, `metrics().copy(allSymbolHits = hits)`; (b) `BARE`, `metrics().copy(allSymbolHits = hits, libraries = lib)`; (c) `BARE`, `metrics()`; (d) `PINNED`, `metrics().copy(libraries = lib)` | `CorpusAttribution.checkArm(mode, m)`, each inside `runCatching { … }` | (a) and (d) return normally; (b) fails with `IllegalStateException` whose message contains `the bare arm registered libraries`; (c) fails with `IllegalStateException` whose message contains `no symbol hits` | delete the library check (→ (b) returns normally); or delete the symbol-hit check (→ (c) returns normally); or drop the `PINNED` early return (→ (d) fails on its libraries) |

## Acceptance Criteria

- [ ] MAINT-37-01 — TC 1–3 pass.
- [ ] MAINT-37-02 — TC 4–7 and TC 47 pass; CI's `corpus` job carries the `--verify` check and the catalog trigger path (design §7 "CI"); `fetch-corpus.py` places `busted-5ed85d0e….tar.gz` and `luassert-d3528bb6….tar.gz` under `test/corpus/.definitions/`.
- [ ] MAINT-37-03 — TC 8–12 and TC 36 pass, in the routine suite (DR-01 and DR-10, executed, are their premises).
- [ ] MAINT-37-04 — TC 13–17 pass.
- [ ] MAINT-37-05 — TC 18–27, TC 32, TC 37, TC 39–46, TC 48 and TC 49 pass; DR-04 reads an empty diff for both the bare and the pinned arm; DR-08 confirms; TC 38 holds and each of its listed mutations is proven red.
- [ ] MAINT-37-06 — **Full**: luacheck, luarocks and penlight all keep the pin, TC 28–29 hold for each, every offset and reconciliation invocation exits 0, and `rebaseline-attribution.md` records every classifier, offset and reconciliation output verbatim. **Partial**: TC 28–29 hold for every kept member, each withheld member has a filed BUG named in the MAINT epic row, and Phase 4 stays open.
- [ ] MAINT-37-07 — TC 30 holds; every offset and reconciliation invocation exits 0; the zerobrane classifier output and every offset and reconciliation output are appended to `rebaseline-attribution.md`.
- [ ] MAINT-37-08 — TC 31.
- [ ] MAINT-37-09 — TC 33–35 pass in the routine suite; the Phase 3b re-baseline (or "no change") is recorded in BUG-496's report.
- [ ] No file under `src/main/` is modified.

## Non-Functional Requirements

- **No network at sweep time.** Only `fetch-corpus.py` downloads; the Gradle test JVM reads local
  archives.
- **Threading.** All new Kotlin runs in test code on the test's EDT, as the existing sweep does.
  The enabler marshals its refresh with `invokeLater`; the sweep drains it with
  `PlatformTestUtil.dispatchAllEventsInIdeEventQueue()` before measuring. The one direct roots change
  (`refreshRoots`) runs inside `runWriteAction`, as `PlatformLibraryIndex.reload` does.
- **Sweep cost.** `elapsedMs` stays advisory (MAINT-33-09); `wxlua`'s cost is measured at Phase 5
  (DR-05), never gated. A pinned member pays a user's cost: a stub-index rebuild in `enable` and in
  `release`. A record sequence adds one member-alone `before` run per member (S2a).
- **Licensing.** Archives are fetched into the out-of-repo `test/` tree and never committed, so no
  third-party file enters this repository; each archive's licence is its catalog entry's `license`
  (MIT for busted/luassert; wxWindows Library Licence for wxlua per TARGET-10-10).
- **Engineering contract.** ≤ 30 logic lines and ≤ 3 parameters per function, no `!!`, no wildcard
  imports — including private helpers.

## Dependencies

- **MAINT-33** (`done`) — the sweep, manifest, baseline and ratchet this extends.
- **TARGET-08** (`done`) — catalog, fetcher, extractor, provider.
- **BUG-417** (`done`) — inspection independence; without it a delta is not attributable to the
  library.
- **TARGET-10** (`in_progress`) — **only MAINT-37-07 / Phase 5 depends on it**, and only on
  TARGET-10 **Phase 4 (Publish and catalogue)**, checked by this feature itself
  (implementation-plan → Phase 5 precondition). The roadmap row's `Depends on` cell deliberately
  omits TARGET-10, because a listed edge would make the whole row not-ready and hide the startable
  phases; TARGET-10's `Unblocks` cell and this section carry the edge.
- **BUG-496** — delivered here as MAINT-37-09 (Phase 3b), in its own commit before any pin.

## Human Verification

There is no `human-verification-checklists.md` for this feature. It has no UI surface: everything it
adds is test code, Python tooling under `tooling/corpus/` and a manifest field, and every behaviour is asserted by an
automated test case or by the corpus gate. The settings page it reuses is TARGET-08's, verified
there.

## See Also

- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
