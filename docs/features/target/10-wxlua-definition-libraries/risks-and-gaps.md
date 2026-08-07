---
id: TARGET-10-RISKS
parent_id: TARGET-10
type: risk
folders:
  - "[[features/target/10-wxlua-definition-libraries/requirements|requirements]]"
title: "Risks & Gaps"
---

# TARGET-10: Risks & Gaps

## Premises examined

The third axis of the planning bar: which constraints does this plan treat as fixed, and is each one
actually fixed?

| Constraint treated as fixed | Verdict |
|---|---|
| "Definition libraries are fetched tarballs pinned in the catalog" | **Chosen, not forced.** Lunar also bundles stubs in the jar via `PlatformLibraryProvider`, which would remove hosting, sha256 pinning and the network path entirely. Weighed against keeping a wxWindows-Licence derivative out of an Apache-2.0 distribution and the jar small; the maintainer chose the catalog path on 2026-08-07. Recorded in design §9. |
| "The roadmap names three libraries, so there are three catalog entries" | **Removed.** The roadmap names three *namespaces* — a fact about wxLua's binding layout, not a specification of three rows. One entry ships all four namespaces (design §9). |
| "Scope is what the ZeroBrane corpus uses" | **Removed.** ZeroBrane's 479 members are MAINT-37's coverage *floor*, not the feature's scope. A partial library turns "unknown global" into "this class has no such method" — a worse failure — and generation makes full coverage nearly free. |
| "A generator must replicate wxLua's preprocessor" | **Removed.** `#if` and `%wxchkver_*` resolve against the user's wxWidgets build, which the plugin cannot observe. Emitting the union is *more* correct here, and deletes the entire conditional-evaluation machinery (design §3.2). |
| "The Gitea mirror should be a second URL, like every other multi-mirror artefact" | **Removed on a contract, not taste.** Gitea's archive prefix differs from GitHub's and `LuaArchiveExtractor` strips exactly one `rootPrefix`; two mirrors with different prefixes cannot share an entry (design §4.2). Copying the mirror convention would have shipped a mirror that fails on use. |
| "wxLua is the pinned upstream" | **Genuinely fixed.** wxLua *is* the binding; there is no alternative source of its Lua surface. |
| "`rootPrefix` must end `/library`" | **Genuinely fixed.** Asserted for every bundled entry by `LuaDefinitionCatalogLoaderTest.everyBundledEntryIsPinnedAndAttributed`; the published repo's layout follows from it. |
| "`@overload` cannot carry a type" | **Genuinely fixed** for now — `LuaTypeGraphBridge` reads `@class`/`@type`/`@param`/`@return`/`@generic` and never `overloadTagList`. Making it typed is a type-engine feature, not this feature's to take on; it is filed below as debt. |
| "The `.i` grammar is the six forms an initial reading found" | **Removed — it was false, and it was the single riskiest premise in the plan.** Counting rather than sampling found four more `#define_*` forms (287 declarations), `%rename` (84) and `%override_name` (24) — which *bind the exported name* and so cannot be stripped as decoration — `%member_func` (14), and 457 negated `!%guard` lines an attribute regex without a leading `!` drops silently. Design §4.1 now carries the measured table, and the coverage ratchet exists because §3.3's catch-all (rule 17) is silent. |
| "A namespace table declared in one file can be extended from sibling files" | **Not fixed, and not assumable.** Neither love2d nor Lunar's own stdlib stub does this; `LuaMemberFieldIndex`/`LuaGlobalAssignmentIndex` suggest it should work, but that is a reading. Tracked as Gap 2.4 / **DR-06** with a specified single-file fallback (design §3.5.2). |
| "One output file per C++ group" | **Inherited on precedent, and it is what created the premise above.** It was chosen for stable filenames when upstream splits a header — a real but minor benefit. The alternative (one file per *namespace*) matches both reference layouts and would delete DR-06 entirely. DR-06 therefore decides the layout, not just validates it. |
| "A bad `sha256` fails the fetch closed" | **Removed — false.** Definition libraries fetch under `ArtifactVerification.ADVISORY` (`LuaDefinitionLibraryFetcher.kt:148-160`), which logs a mismatch and keeps the file (`LuaArtifactDownloader.kt:22,31,123`). Integrity rests on the URL embedding the immutable commit SHA, which is why the loader rejects a URL not containing `version`. Phase 4's hash read-back is provenance, not a gate. |

## Critical Risks

### Risk 1.1: The `.i` grammar drifts and the generator silently emits less

- **Impact**: a future re-pin quietly drops classes or constants. Because output is deterministic
  and committed, the loss appears as a large deletion diff — but only if someone reads it.
- **Likelihood**: raised to **high** by the premise audit above — this is not hypothetical drift, it
  is the failure mode that already occurred once during planning. An initial reading of the format
  found six declaration forms; counting found ten, covering 866 declarations the six would have
  dropped.
- **Mitigation**: the coverage ratchet (TARGET-10-09) fails the run rather than the review;
  `coverage-floor.json` must be *edited* to lower it, which is visible. Rule 17 of design §3.3
  silently ignores unrecognised lines, so the ratchet is the only backstop — this is why it is a
  requirement and not a nicety. Design §4.1's table is measured with reproducible commands so a
  re-pin can be re-counted rather than re-read.

### Risk 1.1b: The coverage ratchet is blind to *invented* API

- **Impact**: a grammar hole that misfiles class members as namespace functions adds names
  (`wx.IsCompatible`) rather than removing them. Coverage measures `used ∩ emitted`, so a surplus
  name can only ever raise it. Two such holes have already occurred — `class %delete` and `struct`.
- **Likelihood**: high, on the evidence: both were found by *sampling the parse residue*, not by any
  metric.
- **Mitigation**: the Phase 1 gate diffs the emitted **name set** against the probe's `names2.json`
  in **both** directions, and Phase 1 must sample the ~406 unrecognised `;`-terminated lines. The
  ratchet stays, but it is explicitly not the control for this failure mode.

### Risk 1.6: Typing the members opens a new false-positive surface

- **Impact**: this is the inverse of the feature's headline win. Removing `wx` from
  `LuaUndeclaredVariable` needs only the three namespace globals (1,877 of ZeroBrane's 1,945 hits,
  `src/test/resources/corpus/zerobrane.baseline`). Everything beyond that — the members — exists to
  serve completion and inference, and **inference cuts both ways**: ~1,900 ZeroBrane call sites that
  are unchecked today become checked, against ~10,000 emitted `@param`/`@return` contracts derived
  by a ~40-row hand-written C++→LuaCATS map. A single over-narrow row is a false positive at scale,
  in a read-only fetched library the user cannot edit.
- **Likelihood**: high without mitigation. The `integer` mapping alone covered 7,469 sites, against a
  Lua 5.1 corpus with no integer subtype and a type engine that relates `number` and `integer` not
  at all (`LuaPrimitiveType.kt:10-18`).
- **Mitigation**: (a) §3.4's "widest type that is still true" rule, with integral types mapped to
  `number`; (b) unknown types to `any`, never a guess; (c) unknown bases emitted as stubs so no
  inheritance chain is severed (3 cases); (d) **DR-08** measures the actual delta before publishing;
  (e) checklist Scenario 3.2 requires **zero** new type errors on a real ZeroBrane file, with the
  stated fix always being to widen the mapping, never to narrow user code.
- **Sequencing — BUG-419 is a hard ordering dependency for DR-08, and it does *not* shield this
  feature.** BUG-419 (`in_progress`) is the same failure mode from the engine side, and its
  2026-08-07 probe measures the interaction exactly:

  | member | assignability emissions | demand **declared** | demand **inferred** | survives BUG-419's rule |
  |---|---:|---:|---:|---:|
  | zerobrane | 4,452 | **3 (0.1 %)** | 4,449 (99.9 %) | **3** |

  Two consequences, in opposite directions:

  1. **BUG-419 removes the ambient floor.** 99.9 % of ZeroBrane's assignability emissions are the
     engine checking an inferred demand against an inferred value; BUG-419 demotes them to a
     hypothesis tier. ZeroBrane's surviving error count goes 4,452 → 3.
  2. **BUG-419 gives TARGET-10's own contracts no protection at all — by design.** Its emission
     rule 2 names "an annotation, **a stub signature**, a declared global" as exactly the things
     that *are* contracts. Every `@param` this feature emits is a stub signature. So ~1,900
     ZeroBrane `wx.*` call sites migrate out of the 99.9 % suppressed bucket and into the 0.1 %
     reported bucket. The reason ZeroBrane survives with 3 errors today is that almost nothing is
     declared; TARGET-10 declares ~10,000 contracts.

  **Therefore DR-08 must run *after* BUG-419 lands.** Measured before, wx-induced errors are buried
  in a 4,452-emission floor and the delta is unreadable; measured after, every one is visible and
  attributable to a §3.4 row — which is the whole point of the measurement. This is an ordering
  dependency, not a preference.

  Rule 1 (viral unknowns) does help this feature marginally: `any`-mapped types stay gradual. Rule 3
  does not apply to us. **Nothing in BUG-419 addresses the `number`/`integer` mapping** — that was
  this design's own defect and is fixed in §3.4, not by the engine.

- **Reciprocal impact on BUG-419 — worth telling its owner.** BUG-419's epistemic argument opens
  with ZeroBrane's "1,945 identifiers the engine admits it cannot resolve… a name-model ~84 %
  unknown". TARGET-10 removes **1,877 of those 1,945** (`zerobrane.baseline`), taking the unknown
  share to roughly 3 %. The *rule* stands on the probe's 99.9 % independently, but the ZeroBrane
  framing that motivates it stops being true once this lands.

### Risk 1.2: The emitted shape resolves in tests but not in a real IDE

- **Impact**: the whole feature is inert; `wx.` completes nothing despite a green suite.
- **Likelihood**: low — `LibraryRootTestCase` registers a real `SyntheticLibrary` root and the
  BUG-394/395/398/399 regressions exercise the same path — but the schema-engine lesson (a feature
  that passed unit tests because they hand-registered the extension point, and was dead live) says
  this class of failure is real.
- **Mitigation**: [human-verification-checklists.md](human-verification-checklists.md) exercises a
  real IDE with the real fetched tarball, not a fixture root, before the feature is called done.

### Risk 1.3: The tree is large enough to hurt indexing

- **Impact**: enabling `wxlua` makes project open slow. The tree is roughly an order of magnitude
  larger than any existing entry (love2d's tarball is 97 KB; the wx binding is ~1.6 MB of `.i`
  source producing ~5,500 constants and ~4,000 methods).
- **Likelihood**: medium — unmeasured until DR-04.
- **Mitigation**: **DR-04** measures it before any generator code is written. If the budget is
  exceeded, the specified fallback is a second catalog entry carrying the rarely-used namespaces
  (`wxrichtext` — 289 KB of `.i` on its own — plus `wxpropgrid` and `wxwebview`) which `wxlua` does
  **not** `require`. That is a data change, not a design change: the split is a partition of the
  `Group` list at emission time.

### Risk 1.4: Publishing an outward-facing repository

- **Impact**: the catalog entry points at a URL only the maintainer can create; a repo published
  under the wrong owner, or later renamed, breaks `wxlua` for every user (the fetch fails soft —
  no root, no crash — but the feature is dead).
- **Likelihood**: low, and entirely within the maintainer's control.
- **Mitigation**: Phase 4 requires explicit maintainer confirmation before pushing. `version` is the
  commit SHA and every URL must embed it (`LuaDefinitionCatalogLoader.kt:69-73`), which is the whole
  integrity argument — note the `sha256` is **ADVISORY** and would not catch a substituted artefact
  (premise table above), so the URL pin is doing the work, not the hash.

### Risk 1.5: Licence handling of a derived work

- **Impact**: shipping a wxWindows-Licence derivative from an Apache-2.0 project without attribution
  is a licence breach.
- **Likelihood**: low given the mitigation, but the failure is not self-announcing.
- **Mitigation**: the tree is **never bundled** — it is fetched at runtime and lives in its own
  repository under its own `LICENCE` (verbatim `wxLua/docs/licence.txt`), with `PROVENANCE.md`
  naming the upstream commit. This repo carries only a URL and a `THIRD-PARTY.md` row
  (TARGET-10-10). Every generated file's header states the licence (design §3.5).

## Design Gaps

### Gap 2.1: Whether a Lua path can be both a constructor function and a static-method table

- **Question**: wxLua exposes `wx.wxFileName(path)` and `wx.wxFileName.GetCwd()` at the same path.
  In Lua a value is a function or a table, not both. Does Lunar resolve `wx.wxFileName.GetCwd` when
  a library file declares `function wx.wxFileName(p) end` **and**
  `function wx.wxFileName.GetCwd() end`?
- **Options / leaning**: Branch A (`statics_mode="dotted"`, both declarations) if it resolves;
  Branch B (`statics_mode="on-class"`, statics move to the class table and `wx.wxFileName.GetCwd()`
  stops completing) if it does not. Leaning A. Both are fully specified in design §3.6, and the
  ranking is settled: constructors outrank statics (~750 vs 499, and the pinned corpus references
  `wx.wxFileName` 106 times, overwhelmingly as a constructor call).
- **Resolved by**: **DR-02**, before Phase 2. Fold the verdict into design §3.6.

### Gap 2.2: What the indexing budget actually is

- **Question**: requirements → Non-Functional asserts a budget must exist but cannot state a number
  before measurement.
- **Options / leaning**: measure first-index wall-clock and heap with and without the root; set the
  budget from the measurement rather than inventing a target.
- **Resolved by**: **DR-04**, before Phase 1. Fold the number into requirements → Non-Functional and
  decide Risk 1.3's fallback on it.

### Gap 2.3: Which owner/name the published repository takes

- **Question**: design §7 writes `<owner>/lunar-definitions-wxlua`. The owner is a maintainer
  decision and the name determines `rootPrefix` (`<repo>-<sha>/library`).
- **Options / leaning**: `lunar-definitions-wxlua` under the same owner as the plugin, so
  `rootPrefix` and the attribution URL are predictable.
- **Resolved by**: **DR-03**, at the start of Phase 4 — it is a naming decision, not an unknown, and
  it blocks nothing before then.

### Gap 2.4: Whether a namespace table declared in one file can be extended from sibling files

- **Question**: design §3.5.2 writes `---@class wx` + `wx = {}` in `library/wx.lua` and every member
  (`wx.wxID_ANY = nil`, `function wx.wxFrame(…) end`) in `library/wx/<cpp>.lua`, with no
  re-declaration. Does `wx.<caret>` complete members contributed by those sibling files?
- **Options / leaning**: `split` (keep the layout) if it resolves; `single` (one file per namespace,
  each self-contained, matching love2d and `runtime/standard/lua-5.4/string.lua`) if not. Leaning
  `split` on the strength of `LuaMemberFieldIndex` / `LuaGlobalAssignmentIndex`
  (`lang/indexing/LuaGlobalAssignmentIndex.kt:28,38`, BUG-391) — but that is the *purpose* of an
  index, not an observation, and this design does not assert behaviour from reading. Both layouts
  are fully specified in design §3.5.2, and no other section changes between them.
- **Why it needs its own DR**: TC 6 and TC 7 both put class, constructor and constant in one file,
  so they would pass whichever way this lands — the exact "the fixture takes the other branch"
  failure the planning bar warns about. TC 7a exists specifically to exercise the split layout.
- **Resolved by**: **DR-06**, before Phase 2. Fold the verdict into design §3.5.2.

## Technical Debt & Future Work

- **TBD: `@overload` does not carry a type.** `LuaTypeGraphBridge` never reads `overloadTagList`, so
  a `---@overload fun(…): T` gives parameter hints and no inference. Every LuaCATS library in the
  ecosystem uses `@overload` heavily — love2d's `love.math.newTransform` is a plain example — so
  this costs inference across `love2d` and `openresty` today, not only wxLua. Worth filing as a
  type-engine bug independently of this feature.
- **TBD: statics on a constructor-function path** (Branch B only). If DR-02 lands on Branch B, file a
  bug: a library-declared `function ns.Name.Static()` should resolve even when `ns.Name` is also a
  function. Until then `wx.wxFileName.GetCwd()` does not complete, and the user guide says so.
- **TBD: tracking upstream wxLua.** The tree is pinned to one commit and refreshed by hand. A
  scheduled re-pin is out of scope; the generator's determinism (TARGET-10-04) is what makes a
  future refresh a reviewable diff instead of a rewrite.
- **TBD: descriptions.** wxLua's `///` comments are sparse, so most emitted members carry no prose.
  Enriching from the wxWidgets doxygen XML (`wxLua/bindings/parse_doxygen_xml.lua` exists upstream)
  is a plausible later pass; it changes no shape, only doc text.
- **TBD: `wxwebview` in `detectionPatterns`.** The entry declares patterns for `wx`, `wxstc` and
  `wxaui` only. `wxwebview` is emitted and resolvable but will not itself trigger a TARGET-09
  suggestion; a file using only `wxwebview.*` is vanishingly rare.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|---|---|---|---|
| TARGET-10-00-DR-01 | Enumerate LuaCATS org + `LuaLS/LLS-Addons` and confirm no wxLua definition library exists; read ZeroBrane's `api/lua/wxwidgets.lua` | The premise of the whole feature | **done** — [research.md §1–2](research.md). 28 LuaCATS repos, 86 LLS-Addons submodules, no wxLua. ZeroBrane's file is an 812-byte runtime introspector. |
| TARGET-10-00-DR-02 | Spike the dual `function wx.wxFileName(p)` + `function wx.wxFileName.GetCwd()` declaration through `LibraryRootTestCase` | Gap 2.1 (design §3.6 branch) | todo — **blocks Phase 2** |
| TARGET-10-00-DR-03 | Settle the published repository's owner and name | Gap 2.3 | todo — **blocks Phase 4** |
| TARGET-10-00-DR-04 | Measure first-index wall-clock and heap for a tree of the real order of magnitude | Gap 2.2, Risk 1.3 | todo — **blocks Phase 1** |
| TARGET-10-00-DR-05 | Parse-coverage spike over the real `.i` files against the pinned ZeroBrane corpus | Risk 1.1 (is the format tractable at all?) | **done** — [research.md §5](research.md). 82% / 96% / 95% for `wx` / `wxstc` / `wxaui` from a 40-line parser implementing 4 of the 10 forms. A **lower bound**, not a target; the Phase 3 floors come from the first real run. |
| TARGET-10-00-DR-08 | Measure the type-error delta: baseline ZeroBrane's `LuaTypeAssignability` (358) and `LuaReturnTypeMismatch` (65), then re-measure with the generated tree registered. Separately spike `---@param x number` vs `---@param x integer` against a numeric literal at `LUA51` to confirm §3.4's mapping choice empirically | Risk 1.6 | todo — **blocks Phase 4** (publish) |
| TARGET-10-00-DR-06 | Spike the split namespace layout through `LibraryRootTestCase` (TC 7a): `wx.lua` with only the `---@class wx` header, members in `wx/wxcore.lua`, assert `wx.<caret>` completes them | Gap 2.4, Risk 1.2 | todo — **blocks Phase 2** |
| TARGET-10-00-DR-07 | Count every declaration form in the pinned `.i` files **and execute the §3.2/§3.3 rules over all 42 of them** | Risk 1.1 | **done** — reference implementation checked in at `tooling/spikes/target-10-wxi-grammar/probe.py`; firing counts in design §3.3, coverage in §3.8 (98/99/95%). Counting alone overturned the "six forms" premise; *running* it then overturned five more assumptions that counting could not see — see the note below. |

**Why DR-07 had to be executed, not counted.** The first grammar written from the counted form table
looked complete and was wrong in five ways, each silent:

| Assumption | What running it did |
|---|---|
| a name group of `(\w+)` suffices for a type header | `class wxDateTime::TimeZone` captured the *parent*; TimeZone's members merged into the real `wxDateTime`, and 18 enum aliases collided with class names |
| `%`-guards are single tokens | `%A && %B <decl>` (170 lines) left `&& <decl>`, matching no rule — 85 declarations lost including `wxRealPath` and `wxLocale`'s constructor |
| only `class` declares a bound type | `struct` (8) never opened class state; 7 types lost **and** `wx.IsCompatible` emitted as a global function that does not exist |
| block comments can be stripped before line comments | `// … include/aui/*.h` opened block state and swallowed 2,400 lines — **the entire `wxaui` namespace emitted empty, no error** |
| multi-line `/** */` prose can be ignored | unmatched `(` in doxygen prose fed the continuation join and lost `wxFileName`, `wxFile`, `wxDir`, `wxStandardPaths` |
| a continuation join needs no bound | one stray `(` consumed the rest of a file |
| `%`-attributes are line-leading | `%delete` sits *inside* `class %delete Name` (487 of 789); classes misparsed and **~4,800 methods emitted as global functions** — inventing API, not dropping it |
| all `%`-tokens are strippable | `%wxEventType` (507) and `%member_func` (14) are declaration keywords; their rules became unreachable |

Every one passed a `grep`-based review. This is the concrete case for the bar's "behaviour must be
EXECUTED" rule, and it is why Phase 1's exit criteria include reproducing the measured counts.

## Test Case Gaps

- **Overload rendering** has no test case in [requirements.md](requirements.md). It affects only
  parameter hints (never inference, design §3.5.6), so it is covered by the Phase 2 golden-file
  comparison rather than by a behavioural TC. Noted so the omission is deliberate, not missed.
- **The `wxwebview` namespace** is emitted but has no TC. It goes through exactly the same code path
  as `wxstc` (one `.i` file, one `hook_lua_namespace`), which TC 4 covers generically.
- **Failure of the real fetch** (bad hash, no network) has no new TC — it is TARGET-08 behaviour,
  already covered by `LuaDefinitionLibraryFetcherTest`, and this feature adds no code to that path.
- **DR-04's budget** cannot have a TC until the number exists; Phase 0 produces both.

## See Also

- Research: [research.md](research.md)
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
