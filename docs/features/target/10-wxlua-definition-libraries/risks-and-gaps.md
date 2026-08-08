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
- **Likelihood — ACTIVE from day one, and that is the good outcome.** **BUG-425** (`high`, filed 2026-08-07)
  measures that a signature declared outside the file under analysis **never reaches the type
  graph**: not demoted to the hypothesis tier, emitting nothing at all. Its probe shows even
  `Too few arguments` stays silent across a file boundary, so the callee's `Function` type is
  absent, parameters and return alike. A definition library is out-of-file by construction, so on
  today's engine this feature's ~10,000 contracts put **zero** through the declared-demand path.
  **Sequencing decision, 2026-08-07: BUG-423, BUG-424 and BUG-425 all land BEFORE this feature.**
  That dissolves the problem rather than mitigating it. Had TARGET-10 shipped first, its contracts
  would have been inert — and inert is a fuse, not safety: the corpus sweep carries no definition
  libraries (MAINT-37, `todo`), so BUG-425's own verification would have been blind to the
  population it activated, and ~10,000 unvalidated contracts would have arrived in users' editors at
  once, attributable to neither feature. With 425 landing first the mechanism is live **before** the
  data exists, so every contract this feature adds is live on arrival, measured by this feature's own
  DR-08, before publication. The explosion, if any, is ours, on our gate, attributable to us.

  The two bugs ahead of it are the right ones to have first: **BUG-423** (arithmetic/concat
  string↔number coercion) was the largest class surviving BUG-419's rule, and **BUG-424** (operator
  metamethods, 655 LPeg claims) the largest overall. Both are *language-rule* demands — the ERROR
  tier they clean up is the tier this feature is about to load. Adding 10,000 contracts to a tier
  with two known large false-positive classes would have made DR-08 unreadable.
  The `integer` mapping alone covered 7,469 sites, against a Lua 5.1 corpus with no integer subtype
  and a type engine that relates `number` and `integer` not at all (`LuaPrimitiveType.kt:10-18`).
- **Mitigation**: (a) §3.4's "widest type that is still true" rule, with integral types mapped to
  `number`; (b) unknown types to `any`, never a guess; (c) unknown bases emitted as stubs so no
  inheritance chain is severed (3 cases); (d) **DR-08**, re-specified below because a corpus delta measures **zero** today and would read as
  "safe";
  (e) checklist Scenario 3.2 requires **zero** new type errors on a real ZeroBrane file, with the
  stated fix always being to widen the mapping, never to narrow user code; and — the one that
  actually addresses the fuse — **(f) the sequencing below, which arranges for the detonation to
  land on our corpus gate rather than on users.**

- **~~MAINT-37 must land before BUG-425~~ — moot, and superseded by the ordering above.** Retained
  because the reasoning still applies if the order ever changes. MAINT-37 remains valuable as
  *ongoing* regression protection once this ships — a pinned `wxlua` in the sweep is what catches a
  future re-pin or type-engine change degrading the map — but it is no longer load-bearing for the
  initial ship, and the no-`@param` fallback below is not needed.

  <details><summary>Superseded reasoning</summary>
  MAINT-37 ("corpus sweeps run with pinned definition libraries") is normally read as the
  *beneficiary* of this feature. It is also its **safety mechanism**, and the arrow points the other
  way: once the ZeroBrane sweep pins `wxlua`, BUG-425's fix trips **our** gate — in CI, against a
  recorded baseline, attributable to the commit that caused it — before any user sees it. Without
  that ordering there is no tripwire anywhere in the system.

  Three consequences, all cheap:
  1. **MAINT-37 must pin `wxlua` specifically** into the ZeroBrane sweep, not merely gain the
     capability to pin libraries. A capability nobody exercises is not a tripwire.
  2. **BUG-425 gains a verification item**: its fix must be measured with a definition library
     enabled. On the bare corpus its own gate is blind to the population it activates.
  3. If MAINT-37 cannot precede BUG-425, the conservative fallback is to emit the v1 tree
     **without `@param`/`@return` on methods** — `@class`, constructors' `@return` and constants
     only. That keeps completion, navigation, `require("wx")` and all 1,877 undeclared-variable
     hits (the entire delivered value) while reducing the contract surface to near zero, and defers
     parameter types to a follow-up that can be measured. It is a generator flag, not a redesign.

  </details>
- **Sequencing — BUG-419's defect 3 has ALREADY LANDED, and this feature's baseline is post-fix.**
  `31d9c761` (2026-08-07) shipped "incompatibility is a diagnostic only when something DECLARED it".
  It re-baselined the corpus in the same commit:

  | member | `LuaTypeAssignability` before → after | `LuaReturnTypeMismatch` |
  |---|---:|---:|
  | zerobrane | 997 → **358** | 83 → **65** |
  | luarocks / luacheck / penlight | 478→213 / 376→201 / 317→135 | — |

  So the 358 and 65 that DR-08 measures against are **already clean**, and **DR-08 is unblocked
  today** — it does not wait on anything. (`LuaUndeclaredVariable` is untouched at 1,945; different
  inspection.)

  **Correction to an earlier draft of this section**, which quoted the report's probe table (4,452
  emissions, 99.9 % inferred, 3 survivors) as the expected outcome. The implementation refuted that
  prediction: the real reduction was **−58 %, not ~100 %**, for two reasons recorded in
  `31d9c761` — the 7,433 was a graph-level count *before* dedup, and the probe counted only
  annotations as "declared". The landed model has **three** kinds of demand, not two:

  | demand | example | verdict |
  |---|---|---|
  | user annotation | `---@param n number` | contract → **ERROR** |
  | **language rule** | `a .. b` needs a string | contract → **ERROR** |
  | inference | `f()` needs `fun()` | guess → HYPOTHESIS |

  **Two mechanics from the landed change bear on us — but only once BUG-425 is fixed (see
  Likelihood above). On today's engine neither reaches a definition library.**

  1. A **stub signature is a user-declared demand** — the commit names it explicitly. Every
     `@param` this feature emits is therefore a contract whose violation is an ERROR. BUG-419 gives
     this feature no protection; it is the mechanism by which our contracts become enforceable.
  2. **Declaredness propagates transitively.** `checkFunctionCompatibility` wires
     argument-variable → parameter-variable, and `VariableElement` now resolves a variable's demand
     as declared *if any of its reads is* — a fix the commit made after finding declared `@param`
     violations were being silently demoted. So wx contracts reach every variable that flows into a
     wx call, not just the immediate argument. The enforcement surface is wider than the ~1,900
     direct call sites.

  BUG-419's two reopened verification items were **closed in `1a5fd807`**: the BUG-417 parity
  criterion now passes as a test (and its own vacuous `0 vs 0` failure mode is designed out —
  `assertAnchored` requires the measured total within 25 of the ratchet baseline before any verdict
  is believed), and the declared-contract ERROR path is mutation-proved. What remains open is
  defects 1 and 2 (viral unknowns; `Undefined` union arms), held back deliberately because fixing 1
  alone would *create* false positives, plus BUG-424. None blocks this feature; defect 1 would help
  marginally, by keeping `any`-mapped types gradual.

- **Correction, third pass.** This section previously claimed DR-08 would be "the first real load on
  BUG-419's declared-contract path". BUG-425 refutes that: the load is zero until out-of-file
  signatures reach the graph. Recorded because the error is instructive — the analysis kept running
  ahead of what had actually been measured, which is the failure mode the planning bar's
  "behaviour must be EXECUTED" axis exists to catch.

  **Nothing in BUG-419 addresses the `number`/`integer` mapping.** That was this design's own
  defect, fixed in §3.4 — and now more load-bearing, since declared demands are genuinely enforced.

- **Correction, fourth pass — BUG-425 and BUG-427 both shipped, and the load is still not "10 000".**
  Out-of-file signatures now reach the graph, so this feature's contracts are live on arrival as the
  sequencing decision intended. But the enforcement is **deliberately narrow**, and the caveats are
  the plan's to absorb, not the bugs':

  1. **Exact arity only.** A declared parameter is checked only when the call supplies exactly one
     argument per parameter, with no vararg. A looser "the arity fits" rule was implemented and
     measured putting **244 false positives** on the corpus — 123 from `table.insert` alone, whose
     `@overload` and optional `pos` make positional matching guesswork. `@overload` never reaches
     the type engine at all, so any call that does not fill every slot is one it cannot align.
     Generated wx bindings are mostly all-required, so most should qualify — but any signature this
     feature emits with an optional parameter is **unenforced on short calls**, silently.
  2. **Scalar parameter types only.** A demand built from a `Table` or `Function` wires edges into
     the shared signature's own member nodes and corrupts it for every other call site (measured;
     it broke `table.sort`'s comparator). So `---@param parent wxWindow` and any callback parameter
     are **not checked** — only `number`/`string`/`boolean`/unions of those are.

  For DR-08 this sharpens the instrument rather than blunting it: the sample of ~50 signatures
  should be chosen to include **both** an exact-arity scalar call (which will be enforced) and an
  optional-parameter or class-typed one (which will not), so the checklist records the boundary
  instead of discovering it. A clean DR-08 on class-typed parameters alone would prove nothing.

- **Reciprocal impact on BUG-419 — worth telling its owner.** BUG-419's epistemic argument opens
  with ZeroBrane's "1,945 identifiers the engine admits it cannot resolve… a name-model ~84 %
  unknown". TARGET-10 removes **1,877 of those 1,945** (`zerobrane.baseline`), taking the unknown
  share to roughly 3 %. The landed rule stands on its own measurement, but the ZeroBrane framing
  that motivates the *remaining* defects 1–2 stops being true once this lands.

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
| TARGET-10-00-DR-08 | **Validate §3.4's type map where contracts actually reach.** A corpus delta measures zero today (BUG-425), so it cannot be the instrument. Instead: sample ~50 emitted wx signatures spanning every §3.4 row, inline them **same-file** with representative ZeroBrane call sites, and assert no diagnostic. Then run the corpus delta anyway and **record the expected zero explicitly**, so a future reader cannot mistake it for a pass. Include the `---@param x number` vs `integer` case at `LUA51` | Risk 1.6 | todo — **blocks Phase 4** (publish) |
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
