---
id: TARGET-10
parent_id: TARGET
type: feature
folders:
  - "[[features/target/requirements|requirements]]"
title: "TARGET-10: wxLua (wx / wxstc / wxaui) Definition Libraries"
status: "in_progress"
priority: "low"
vf_icon: 🔵
---

# TARGET-10: wxLua (`wx` / `wxstc` / `wxaui`) Definition Libraries

## Overview

Give Lunar type definitions for wxLua's global tables — `wx`, `wxstc`, `wxaui` (and `wxwebview`) —
so completion, go-to-definition and type inference work in wxLua applications and ZeroBrane Studio
configs. [Research](research.md) established that **no upstream LuaCATS or LuaLS definition library
for wxLua exists**, so this feature generates one from wxLua's own `.i` binding-interface files,
publishes it as a standalone LuaCATS-shaped repository, and adds a single pinned entry to the
[TARGET-08](../08-on-demand-definition-libraries/requirements.md) catalog.

Parent epic: [TARGET](../requirements.md). Unblocks **MAINT-37** (corpus sweeps run with pinned
definition libraries), whose ZeroBrane baseline is currently dominated by unresolved `wx` globals.

## Scope

### In Scope

- An offline generator under `tooling/definitions/wxlua/` that turns a commit-pinned wxLua checkout
  into a LuaCATS definition tree.
- A hand-curated supplement for the a handful of Lua-visible names that no `.i` file declares (global object
  instances such as `wx.wxDefaultPosition`, and `%override`-region constants).
- The published artefact's layout: `library/`, `config.json`, `LICENCE`, and a provenance file
  naming the wxLua commit the tree was generated from.
- One catalog entry `wxlua` in `src/main/resources/definitions/lunar-definitions-catalog.json`,
  pinned by commit SHA with `sha256` + `size`, attributed to the wxWindows Library Licence.
- A `THIRD-PARTY.md` registry entry for the derived work.
- Automated acceptance tests that exercise the emitted shape through
  `LibraryRootTestCase.registerLibraryRoot` — no network, no real tarball.

### Out of Scope

- **Changing TARGET-08's fetch/extract/registration machinery.** This feature is a catalog-data
  change on the plugin side. Any code change to `net.internetisalie.lunar.definitions` is a defect
  in this plan, not a task in it.
- **Auto-detection.** `detectionPatterns` is written into the catalog entry forward-compatibly
  (TARGET-10-11) but the detection mechanism itself is [TARGET-09](../09-addon-auto-detection/requirements.md).
- **Re-baselining the ZeroBrane corpus.** That is MAINT-37's job; TARGET-10 only makes it possible.
- **Tracking upstream wxLua.** The tree is pinned to one commit. Refreshing it is a future
  maintenance item, not a scheduled job (see [risks-and-gaps.md](risks-and-gaps.md) → Technical Debt).
- **Documentation prose.** wxLua's `///` comments are sparse; the generator carries them through
  when present but does not author descriptions.
- **wxWidgets C++ semantics beyond the binding.** Only what wxLua exposes to Lua is described.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| TARGET-10-01 | **Binding generator** | M | An offline Python tool reads a pinned wxLua checkout's `bindings/wxwidgets/` and emits a LuaCATS tree. |
| TARGET-10-02 | **Namespace fidelity** | M | Each `.i` file's target Lua table is taken from its `<cppns>_rules.lua` `hook_lua_namespace`, never hard-coded. |
| TARGET-10-03 | **Resolvable emission shape** | M | Emitted classes, methods, constructors, statics and constants use only LuaCATS constructs Lunar's type engine consumes. |
| TARGET-10-04 | **Deterministic output** | M | Re-running the generator on the same pin produces byte-identical files. |
| TARGET-10-05 | **Curated supplement** | M | Lua-visible names absent from every `.i` file are supplied from a separate, human-maintained file. |
| TARGET-10-06 | **Published tree layout** | M | The artefact is LuaCATS-shaped: `library/` holds the definitions; `config.json`, `LICENCE` and provenance sit beside it. |
| TARGET-10-07 | **Catalog entry** | M | One `wxlua` entry, pinned and verifiable, satisfying every `LuaDefinitionCatalogLoader` invariant. |
| TARGET-10-08 | **`require("wx")` resolves** | M | `require("wx")` from project code resolves into the library root. |
| TARGET-10-09 | **Coverage ratchet** | S | The generator reports, and a test enforces, coverage of the wx surface the ZeroBrane corpus actually uses. |
| TARGET-10-10 | **Licence attribution** | S | `THIRD-PARTY.md` records the derived work, its upstream, and the wxWindows Library Licence. |
| TARGET-10-11 | **Forward-compatible detection patterns** | C | The catalog entry carries `detectionPatterns` for TARGET-09 to pick up without a data change. |

## Detailed Specifications

### TARGET-10-01: Binding generator

Input: a wxLua checkout at a pinned commit (default `4d83c8d44eeccf88683ca0146a13b16d0b0d4264`,
`pkulchenko/wxlua` `master`). Output: a directory tree of `.lua` files.

The tool is Python 3, stdlib-only, and follows `tooling/corpus/fetch-corpus.py`'s conventions
(JSON manifest, pinned commit, no shell string parsing — the BUG-407 lesson). It must run offline
against an already-fetched checkout, and must **not** be wired into the Gradle build: it is run by a
maintainer when the pin changes, and its output is committed to the published repository.

Every declaration form counted in [design §4.1](design.md) must be recognised — the four
`#define_*` variants, value-less `#define`, `%wxEventType`, `enum` blocks, `class` with optional
base, `%rename`/`%override_name`/`%member_func`, negated `!%` guards, and file-scope free
functions. The form list is a *measured* inventory, not a reading: a re-pin re-counts it. `//`-commented lines are discarded (wxLua uses them to mark
*deliberately unbound* API); `///` runs immediately preceding a declaration become its description.
`#if`/`#endif` and `%wxchkver_*` guards are **ignored** and the union is emitted — see
[design §3.2](design.md).

### TARGET-10-03: Resolvable emission shape

Constrained by what `LuaTypeGraphBridge` consumes (`@class`, `@type`, `@param`, `@return`,
`@generic`) and by the fact that `@overload` is parsed but **not** typed
(`lang/psi/types/LuaTypeGraphBridge.kt:66-92` vs `lang/insight/hint/LuaParameterInfoHandler.kt:174`).
`@overload` may still be emitted — it drives parameter-info hints — but no inference may depend on
it. The exact emission grammar is [design §3.5](design.md); the static-method encoding is
[design §3.6](design.md) and is gated on DR-02.

### TARGET-10-04: Deterministic output

A generator whose output ordering follows `os.listdir` or a `set` produces a different diff every
run, which makes the published repository's history unreadable and makes "did the pin change
anything?" unanswerable. Every iteration order is explicitly sorted; see [design §3.7](design.md).

### TARGET-10-07: Catalog entry

Must satisfy, all enforced by existing code or tests:

- `rootPrefix` ends with `/library` (`LuaDefinitionCatalogLoaderTest.everyBundledEntryIsPinnedAndAttributed`).
- Every URL contains the `version` string (`LuaDefinitionCatalogLoader.kt:69-73`).
- `sha256` non-blank, `size > 0`, `license` and `attributionUrl` non-blank.
- `id` unique; `requires` names only known ids (here: `[]`).

### TARGET-10-09: Coverage ratchet

The generator emits a machine-readable coverage report: for each namespace, the members the
ZeroBrane corpus references versus the members emitted. The ratchet is a floor, checked in as data,
raised when it improves and never silently lowered — the same discipline MAINT-33 applies to its
corpus baselines.

## Behavior Rules

- **Surplus beats absence.** Where a guard or a build option makes a symbol's presence
  build-dependent, emit it. A missing definition produces a false "undefined"; a surplus one
  produces at worst a stale completion.
- **Generated and curated never mix in one file.** The supplement (TARGET-10-05) lives in its own
  file so a regeneration can never clobber hand-written content.
- **Type names are flat.** `wxFrame`, not `wx.wxFrame` — wxLua class names are unique across all
  four namespaces (measured: 0 collisions over 758 class names), and flatness keeps
  `wxstc.wxStyledTextCtrl : wxControl` resolvable across files.
- **Map to the widest type that is still true.** A fetched, read-only library's false positives are
  unactionable — the user can only disable the inspection or the library. Ties resolve toward the
  wider type; unmapped C++ types become `any`, never a guess. This is why integral types map to
  `number` rather than `integer` (design §3.4): 7,469 sites, and Lua 5.1 — the corpus's level — has
  no integer subtype.
- **Defining members opens a new error surface.** Today `wx.*` is undeclared, so nothing about it is
  type-checked. Every emitted `@param`/`@return` becomes a contract the engine will enforce across
  ~1,900 ZeroBrane call sites that are currently unchecked. `LuaTypeAssignability` and
  `LuaReturnTypeMismatch` may therefore *rise* even as `LuaUndeclaredVariable` collapses; DR-08
  measures this before the tree is published.
- **The plugin's Kotlin is untouched.** If a requirement here appears to need a code change in
  `net.internetisalie.lunar.definitions`, stop — the emission shape is wrong, not the plugin.

## Test Cases

All fixture-based cases use `LibraryRootTestCase.registerLibraryRoot(files)`
(`src/test/kotlin/net/internetisalie/lunar/definitions/LibraryRootTestCase.kt:55`), which registers
an in-memory library root. Generator cases run against a checked-in `.i` fixture, not the network.

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | TARGET-10-01 | Fixture `.i` with `#define wxTEST_FLAG` and, on separate lines matching the real corpus, `class wxPoint : public wxObject` / `{` / `wxPoint(int x, int y);` / `int GetX() const;` / `};` | Run the generator | Output contains `---@class wxPoint : wxObject`, `local wxPoint = {}`, `---@return integer` above `function wxPoint:GetX() end`, `---@param x integer` + `---@param y integer` + `---@return wxPoint` above `function wx.wxPoint(x, y) end`, and `---@type number` above `wx.wxTEST_FLAG = nil` |
| 2 | TARGET-10-01 | Fixture line `// void OnAssertFailure(const wxChar *file);` | Run the generator | No `OnAssertFailure` appears anywhere in the output |
| 3 | TARGET-10-01 | Fixture `/// The frame's title.` immediately above `wxString GetTitle() const;` inside `class wxFrame` | Run the generator | The emitted `function wxFrame:GetTitle()` is preceded by `---The frame's title.` and `---@return string` |
| 4 | TARGET-10-02 | Fixture `zz_rules.lua` with `hook_lua_namespace = "zz"` and `zz_a.i` declaring `#define wxZZ_ONE` | Run the generator | `wxZZ_ONE` is emitted under table `zz`, in `library/zz/zz.lua` (one file per C++ group, design §3.5.2) — never under `wx` |
| 5 | TARGET-10-02 | Fixture `qq_rules.lua` with `hook_lua_namespace = ""` | Run the generator | `qq_*.i` contributes nothing; no `library/qq*` file is written |
| 6 | TARGET-10-03 | Library root `wx.lua` declaring `---@class wxFrame`, `local wxFrame = {}`, `---@return boolean` + `function wxFrame:Show(show) end`, and `---@return wxFrame` + `function wx.wxFrame(parent) end`; consumer `local f = wx.wxFrame(nil)\nf:<caret>` | `completeBasic()` | Lookup strings contain `Show` |
| 7 | TARGET-10-03 | Same root; consumer `wx.wxID_<caret>` with `---@type number` + `wx.wxID_ANY = nil` in the root | `completeBasic()` | Lookup strings contain `wxID_ANY` |
| 7a | TARGET-10-03 | **The layout the generator actually emits** (DR-06): `wx.lua` holding only `---@class wx` + `wx = {}` + `return wx`, and a *separate* `wx/wxcore.lua` holding `---@type number` + `wx.wxID_ANY = nil` and `---@return wxFrame` + `function wx.wxFrame(p) end` with no `---@class wx` re-anchor; consumer `wx.wxID_<caret>` | `completeBasic()` | Lookup strings contain `wxID_ANY`. **This is DR-06's test** — TC 6 and TC 7 both put everything in one file and would pass even if the split layout resolved nothing. If this fails, design §3.5.2's `single` fallback applies and TC 7a is re-specified against it |
| 8 | TARGET-10-03 | Root declares `---@class wxStyledTextCtrl : wxControl` in `wxstc/stc.lua` and `---@class wxControl` with `function wxControl:Enable(e) end` in `wx/core.lua`; consumer `local c = wxstc.wxStyledTextCtrl(nil)\nc:<caret>` | `completeBasic()` | Lookup strings contain `Enable` (cross-file, cross-namespace inheritance resolves) |
| 9 | TARGET-10-04 | The same pinned fixture checkout | Run the generator twice into two output dirs | `diff -r` reports no differences |
| 9a | TARGET-10-01 | Fixture lines `%rename GetPositionXY void GetPosition() const;` and `%rename wxBLACK #define_pointer wxLua_wxBLACK`, both inside/at the scope the real corpus uses | Run the generator | Output declares `function wxFoo:GetPositionXY()` and `wx.wxBLACK`; it contains **no** `GetPosition` and **no** `wxLua_wxBLACK` |
| 9b | TARGET-10-01 | Fixture lines `#define_object wxPoint wxDefaultPosition`, `#define_object wxDefaultValidator`, `#define_wxstring wxTestStr wxT("x");`, `!%wxchkver_3_1_1 #define wxTEST_NEGATED` | Run the generator | Emits `---@type wxPoint` + `wx.wxDefaultPosition`, `---@type any` + `wx.wxDefaultValidator`, `---@type string` + `wx.wxTestStr`, and `---@type number` + `wx.wxTEST_NEGATED` (the negated guard is stripped, not a reason to skip) |
| 9f | TARGET-10-01 | Fixture `%wxchkver_3_0_0 && %gtk wxString GetInstallPrefix() const;` inside a class, and `struct %delete wxThingParams` / `{` / `static bool IsCompatible(int v);` / `};` | Run the generator | `GetInstallPrefix` is an instance method of its class; `---@class wxThingParams` is emitted and `IsCompatible` belongs to it. The output contains **no** namespace-level `wx.IsCompatible` and **no** line beginning `&&`. Both forms were absent from an earlier grammar and both produced *invented* API, not merely missing declarations |
| 9d | TARGET-10-01 | Fixture `%wxEventType wxEVT_TEST_THING` and `class %delete wxThing : public wxObject` with a member `bool Ping();` | Run the generator | `wx.wxEVT_TEST_THING` is emitted as `---@type number`; `---@class wxThing : wxObject` is emitted and `Ping` is an **instance method** of it — **not** a namespace-level `wx.Ping()`. These are the two forms whose omission is silent: an unrestricted `%`-strip makes `%wxEventType` unreachable, and a line-anchored one leaves `%delete` in place so the class misparses and every member escapes to file scope |
| 9e | TARGET-10-01 | Fixture file whose first line is `// see include/aui/*.h` followed by a normal declaration; and a fixture with a `/** prose with an unmatched ( */` block above a class | Run the generator | Both fixtures' declarations are emitted. These pin design §3.2 steps 1 and 3 — reversing them empties a whole namespace, and dropping step 3 loses every class after the prose block |
| 9c | TARGET-10-01 | Fixture `wxBitmap(LuaTable t, int w, int h, int depth /* = 1 */);` and `void Draw(const wxString& text, int x = 0, int y = 0);` | Run the generator | The block comment is removed and `depth` is a normal 4th parameter; `Draw` renders `---@param x? integer` and `---@param y? integer` (optional cascade), `text` non-optional |
| 10 | TARGET-10-05 | Supplement file listing `wxAUI_TB_PLAIN_BACKGROUND` as `---@type number`; consumer `wxaui.wxAUI_TB_PLAIN_<caret>` | `completeBasic()` | Lookup strings contain `wxAUI_TB_PLAIN_BACKGROUND` |
| 11 | TARGET-10-05 | Regenerate over an output dir that already contains the supplement file | Run the generator | The supplement file is byte-identical afterwards |
| 11a | TARGET-10-05 | The generated tree plus the supplement | Diff their namespace-level name sets | The intersection is **empty** — no name is both generated and curated (design §4.5) |
| 12 | TARGET-10-06 | The published tree | Inspect | `library/` exists; `library/wx.lua` exists; `config.json`, `LICENCE`, `PROVENANCE.md` sit at the root beside `library/`, not inside it |
| 13 | TARGET-10-07 | The shipped `lunar-definitions-catalog.json` | `LuaDefinitionCatalogLoader.load()` | An entry with `id == "wxlua"` exists; its `rootPrefix` ends `/library`; every URL contains its `version`; `sha256` non-blank; `size > 0`; `license == "WxWindows-exception-3.1"`; `requires == []` |
| 14 | TARGET-10-08 | Library root containing `wx.lua`; consumer `local wx = require("wx")` | Resolve the reference at `"wx"` | Resolution lands in a file under the library root |
| 15 | TARGET-10-09 | The generated tree plus the pinned ZeroBrane checkout | Run the coverage report | Reported coverage is ≥ the checked-in floor for each of `wx`, `wxstc`, `wxaui` |
| 16 | TARGET-10-11 | The `wxlua` catalog entry | `LuaDefinitionCatalogLoader.load()` | Loading succeeds (the unknown `detectionPatterns` field is ignored by `parseEntry`), and the raw JSON contains `["wx%.%w+", "wxstc%.%w+", "wxaui%.%w+"]` |

## Acceptance Criteria

- [ ] TARGET-10-01 — the generator runs offline against a pinned checkout and emits a tree, honouring `%rename`, the four `#define_*` forms, `%wxEventType`, attributed classes, negated guards, comment ordering and default values (TC 1–3, 9a–9f), and reproducing design §3.3's measured firing counts **and name set**, in both directions.
- [ ] TARGET-10-02 — namespaces come from `hook_lua_namespace`; the empty namespace emits nothing (TC 4–5).
- [ ] TARGET-10-03 — instance methods, constructors, constants and cross-namespace inheritance all resolve in-IDE, **in the layout the generator actually emits** (TC 6–8, 7a).
- [ ] TARGET-10-04 — two consecutive runs are byte-identical (TC 9).
- [ ] TARGET-10-05 — the supplement contributes resolvable symbols, survives regeneration, and never overlaps generated names (TC 10–11, 11a).
- [ ] TARGET-10-06 — the published tree is LuaCATS-shaped (TC 12).
- [ ] TARGET-10-07 — the catalog entry loads and satisfies every loader invariant (TC 13).
- [ ] TARGET-10-08 — `require("wx")` resolves into the root (TC 14).
- [ ] TARGET-10-09 — the coverage floor holds (TC 15).
- [ ] TARGET-10-10 — `THIRD-PARTY.md` names the derived work and its licence.
- [ ] TARGET-10-11 — the catalog still loads with `detectionPatterns` present (TC 16).
- [ ] DR-02, DR-04 and DR-06 are closed, and their answers are folded back into `design.md`.
- [ ] [human-verification-checklists.md](human-verification-checklists.md) run against a real IDE.
- [ ] No file under `src/main/kotlin/net/internetisalie/lunar/definitions/` is modified.

## Non-Functional Requirements

- **Indexing budget.** The tree is roughly an order of magnitude larger than any existing catalog
  entry (love2d's tarball is 97 KB). Enabling `wxlua` must not make project open unusable;
  **DR-04** measures first-index wall-clock on the ZeroBrane corpus and sets the budget. If the
  measured cost exceeds it, the fallback is splitting rarely-used namespaces
  (`wxrichtext`, `wxpropgrid`, `wxwebview`) into a second catalog entry that `wxlua` does not
  `require` — see [risks-and-gaps.md](risks-and-gaps.md) Risk 1.3.
- **Threading.** No new runtime code, therefore no new EDT obligation. Fetch and extraction reuse
  TARGET-08's existing `Task.Backgroundable` path unchanged.
- **Generator runtime.** Parsing ~1.6 MB of `.i` text is trivially fast; no budget is set, but the
  generator must be interruptible and must not require network access.
- **Reproducibility.** Byte-identical output for a given pin (TARGET-10-04) is what makes the
  published repository's history reviewable.

## Dependencies

- **TARGET-08** (`done`) — the catalog, fetcher, provider and settings UI this entry rides on.
- **BUG-394 / BUG-395 / BUG-398 / BUG-399** (`done`) — library-root completion and module
  resolution; without them a correct tree would still resolve nothing.
- **BUG-425** (`high`, untraced) — out-of-file signatures never reach the type graph. Nothing this
  feature *delivers* depends on it: completion, navigation, `require` resolution and the 1,877
  undeclared-variable hits are unaffected. But it means this feature's `@param`/`@return` contracts
  are **inert until BUG-425 is fixed**, and then activate as a population. It reshapes DR-08 (a
  corpus delta measures zero) and is the reason Phase 4 carries a publication note.
- **BUG-419** (`in_progress`; defect 3 landed in `31d9c761`, verification closed in `1a5fd807`) — **not a blocker.** The corpus
  baselines DR-08 measures against (zerobrane `LuaTypeAssignability=358`,
  `LuaReturnTypeMismatch=65`) are already post-fix, so Phase 3b can run immediately. Its landed rule
  makes a **stub signature** a declared contract and propagates declaredness transitively through
  call sites, so it does not shield this feature's `@param`s — it is what makes them enforceable.
  See [risks-and-gaps.md](risks-and-gaps.md) Risk 1.6.
- **TARGET-09** (`planned`) — consumes TARGET-10-11's `detectionPatterns`. Not a blocker in either
  direction.
> **Enforcement caveat (2026-08-07, after BUG-425/427 shipped).** Contracts are live, but checked
> only on **exact-arity, vararg-free** calls and only for **scalar** parameter types — both limits
> measured into existence (a looser arity rule put 244 false positives on the corpus; structural
> demands corrupt the shared signature). Optional-parameter signatures and `wxWindow`-typed
> parameters this feature emits are therefore **unenforced**. See DR-08 and `risks-and-gaps.md`.

- **BUG-423, BUG-424, BUG-425** — all land **before** this feature (decided 2026-08-07). 425 makes
  this feature's contracts reachable at all; 423 and 424 clear the two largest false-positive classes
  from the ERROR tier this feature is about to load. Together they make DR-08 a real measurement
  rather than a vacuous zero, and keep any regression attributable to TARGET-10 alone.
- **MAINT-37** — dependant, and *ongoing* regression protection once this ships: a pinned `wxlua` in
  the sweep is what catches a future re-pin or type-engine change degrading the map. Not load-bearing
  for the initial ship, given the ordering above.
- **wxLua upstream** — `pkulchenko/wxlua` @ `4d83c8d44eeccf88683ca0146a13b16d0b0d4264`.

## See Also

- Research: [research.md](research.md)
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
- Checklists: [human-verification-checklists.md](human-verification-checklists.md)
