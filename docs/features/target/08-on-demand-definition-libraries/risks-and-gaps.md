---
id: TARGET-08-RISKS
parent_id: TARGET-08
type: risk
folders:
  - "[[features/target/08-on-demand-definition-libraries/requirements|requirements]]"
title: "Risks & Gaps"
---

# TARGET-08: Risks & Gaps

## Critical Risks

### Risk 1.1: Upstream tarball URLs drift or 404
- **Impact**: a pinned catalog URL becomes unreachable → fetch fails → the library never resolves (but no crash — the provider just contributes no root, §3.5).
- **Likelihood**: medium (GitHub release/tag tarballs are stable, but repos can be renamed/deleted).
- **Mitigation**: pin to immutable GitHub `archive/refs/tags/vX.Y.tar.gz` URLs with sha256; allow a mirror list per entry (`urls` is ordered, reusing `LuaArtifactDownloader`'s fallthrough). A broken pin is a plugin-update fix; degradation is graceful (balloon + no root).

### Risk 1.2: Community defs vary in `@meta` layout / quality
- **Impact**: some LuaCATS repos put defs under `library/`, some at root; a tree without `.lua` files under it yields no completion.
- **Likelihood**: medium.
- **Mitigation**: the provider registers the whole extracted (post-`rootPrefix`) dir as a source root; the indexer walks recursively, so nested `library/` layouts still index. DR-01 validates each v1 entry actually surfaces completion.

### Risk 1.3: `reload()` is a global stub rebuild
- **Impact**: enabling a library rebuilds the stub index across all open projects (existing `PlatformLibraryIndex.reload()` behaviour), a brief indexing pause.
- **Likelihood**: high (by design), low severity.
- **Mitigation**: accepted — it is the established TARGET-04 mechanism and enable is a rare user action. A per-project incremental refresh is future work.

### Risk 1.4: No proven light-fixture precedent for completion-from-synthetic-root (review N1)
- **Impact**: The design's load-bearing assumption — "once a directory is a `SyntheticLibrary` source root, the indexer + LuaCATS parser index every `.lua`, so no per-feature indexer work is needed" — is architecturally sound but **not test-precedented in this repo**. Every existing library-root test (`LuaRocksLibraryProviderTest`, `LibraryProviderTest`, `LibraryLoadingAfterTargetChangeTest`) asserts only that the provider *returns roots*; none asserts a symbol actually *resolves/completes* through an injected `SyntheticLibrary`, and `LuaRocksLibraryProviderTest`'s `isInLibrary` assertion is commented out. TC 6 (completion through the registered root) may need `VfsRootAccess.allowRootAccess` + a dirty/VFS refresh that the plan does not yet spell out — an implementer could otherwise burn time discovering it.
- **Likelihood**: medium — the mechanism is real (it's how TARGET-04/rocks roots work in the running IDE) but the *headless-fixture* wiring is unproven here.
- **Mitigation**: DR-03 below proves the end-to-end resolution in a light fixture and captures the exact `VfsRootAccess`/refresh setup TC 6 needs; the design/plan then cite that as the working precedent. If the light fixture proves intractable, fall back to a VNC real-flow DoD for TC 6 (the platform behaviour is not in doubt in a live IDE).

## Design Gaps

### Gap 2.1: Fetch source model beyond the curated tarball set
- **Question**: should v1 support arbitrary user-supplied git/URL entries or a live browsable catalog?
- **Options / leaning**: **No for v1.** Bundled curated tarball catalog only (design §4.1, §9). Arbitrary URL / git / browser is the graduation trigger (see Future Work).
- **Resolved by**: scoped out in requirements (Out of Scope) — not an open design decision.

### Gap 2.2: Cache eviction / disk growth
- **Question**: when is a disabled library's cache dir removed?
- **Options / leaning**: **Never in v1** (design §6). Disabling drops the root but leaves the cache so re-enable is instant. A "clear cache" action is future work.
- **Resolved by**: scoped out; documented in Future Work.

## Technical Debt & Future Work
- **TBD: Browsable / searchable catalog** — a Plugins-page-style browse+install UI over a larger catalog. Deferred; the graduation trigger below.
- **TBD: Addon auto-detection — the follow-up that turns TARGET-08 from a capability into a fix
  (flagged 2026-08-03, needs investigation).** TARGET-08 ships enablement, not improvement: nothing
  changes for a project until a user ticks a library. The MAINT-33 corpus quantifies what that
  leaves on the table — **1507 of 1569 (96%)** `LuaUndeclaredVariable` hits across luarocks and
  luacheck are busted globals (`describe`, `it`, `before_each`, `after_each`, `finally`,
  `lazy_setup`, `lazy_teardown`, `pending`); per project, luarocks 937/954 and luacheck 570/615.
  These are `level="ERROR"` and `enabledByDefault`, so any project with a busted suite opens as a
  wall of red until someone finds the setting.
  - **The detection signal does not need inventing — the addons declare it (found 2026-08-04).**
    Every entry in the catalog is a **LuaLS addon**, and each ships a `config.json` manifest at its
    repo root carrying the trigger LuaLS itself matches on:

    | Entry | `config.json` |
    |---|---|
    | luassert | `"words": ["require[%s%(\"']+luassert[%)\"']"]` |
    | busted | `"settings": {"Lua.workspace.library": ["${3rd}/luassert/library"]}` |
    | love2d | `"name": "LÖVE"`, `"words": ["love%.%w+"]`, `"Lua.runtime.version": "LuaJIT"`, `"Lua.runtime.special": {"love.filesystem.load": "loadfile"}` |

    So the original open question — *unresolved `require`? a `spec/` tree? a `.busted` config? the
    rockspec's `test_dependencies`?* — is answered upstream, per addon, by its author. Detection
    becomes "match each catalog entry's `words` against project sources", not a hand-rolled
    heuristic per framework. What auto-detection *does* once it fires (silently enable vs suggest vs
    quick-fix) remains genuinely open and still needs deciding.
  - **`rootPrefix` currently discards `config.json`.** Extraction keeps `<repo>/library` only, so
    the manifest never reaches disk — the cache holds `busted-<sha>/busted.lua` and nothing else.
    **Retaining it is the cheap prerequisite for everything below and should land first**; it is
    close to free now and is the input each item needs.
  - **Three things in the manifest are already hand-derived in our catalog, and can drift:**
    - `requires` — DR-01 discovered busted→luassert *by hand* and the catalog schema grew a bespoke
      field for it. Busted declares it itself via `Lua.workspace.library`'s `${3rd}/<id>/library`.
      A pin bump can silently change upstream's answer while ours stays put.
    - `displayName` — we hand-wrote "LÖVE (love2d)"; the manifest says `"name": "LÖVE"`.
    - **Runtime version** — love2d declares `Lua.runtime.version: "LuaJIT"`, and we ignore it.
      Lunar has a first-class target/language-level concept, so enabling love2d today leaves the
      project on whatever target it had. That is a semantic mismatch, not cosmetics.
  - **Price the pattern translation before planning.** `words` are **Lua patterns, not regexes** —
    `love%.%w+` is not valid Java regex. Translating the subset in use (`%a %d %l %s %u %w %x`, `-`
    as a lazy quantifier, `%1`–`%9` back-references, anchors) is bounded and well-understood but is
    real work needing its own tests. Running them through a Lua interpreter instead is not viable on
    an IDE code path. Budget this explicitly rather than meeting it mid-implementation.
  - **A cheaper alternative exists and should be priced first**: teaching `LuaUndeclaredVariable`
    about test-framework globals directly, the way stdlib globals already work. That removes the
    same 96% with no catalog, no fetch, and no UI — but it hard-codes a framework list into an
    inspection rather than deriving it from definitions, so it trades correctness-by-construction
    for immediacy. The two are not mutually exclusive.
  - **Measurement caveat**: the corpus probably *cannot* verify either fix as-is — the sweep runs
    headless with no network and no enable list, so the `LuaUndeclaredVariable` floor will not move
    unless the fixture pre-seeds a cached definition tree. Establish that before claiming a number.
- **TBD: Arbitrary user-supplied definition sources** — direct git/URL/local-path entries beyond the bundled catalog.
- **TBD: Cache management** — eviction, "clear cache", size reporting.
- **TBD: Auto-update** — catalog versions are pinned; bumping is a plugin update in v1.

## Epic Placement Recommendation

**Keep TARGET-08 under the TARGET epic for v1.** Rationale: the load-bearing reuse is TARGET-04's library-root injection (`AdditionalLibraryRootsProvider` + `PlatformLibraryIndex.reload()`) and the LuaCATS `@meta` parser — both TARGET/language-resolution concerns. The fetch mechanic reuses two leaf TOOLING utilities (`LuaArtifactDownloader`, `LuaArchiveExtractor`) but not the TOOLING provisioning pipeline, so TARGET-08 is not naturally a TOOLING feature.

**Graduation trigger** (revisit placement — likely a new `DEFS` epic, or a TOOLING id): if TARGET-08 grows a full **browsable catalog / install UI** (Future Work item 1) or **arbitrary user-supplied sources** (item 2). At that point the fetch/catalog/UI surface outweighs the library-resolution reuse and warrants its own epic. This is a product-owner call, flagged in the roadmap brief. No code change is required to move it later.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| TARGET-00-DR-01 | Choose the v1 curated set (recommend: `love2d`, `busted`, `luassert`, `openresty`) and confirm each LuaCATS repo has a tagged release whose extracted tree yields completion for a sample symbol. | Risk 1.2, Gap 2.1 | **done 2026-08-03 — premise FALSIFIED, see DR-01 findings below** |
| TARGET-00-DR-02 | For each chosen entry, resolve the exact tarball URL(s), sha256, byte size, and `rootPrefix`; populate `lunar-definitions-catalog.json`. | Risk 1.1 | **done 2026-08-03** — data below |
| TARGET-00-DR-03 | **done 2026-08-03 — see DR-03 findings below.** Prove completion/resolution through an injected `SyntheticLibrary` in a light fixture (review N1): pre-seed a temp dir with a `---@meta` `.lua` file, register it via `LuaDefinitionLibraryProvider`, and assert a symbol from it completes/resolves (`completeBasic`/`multiResolve`). Capture the exact `VfsRootAccess.allowRootAccess` + refresh plumbing required and fold it into TC 6. If intractable headless, downgrade TC 6 to a VNC real-flow DoD. | Risk 1.4 | todo |
| TARGET-00-DR-03 | Spike the online fetch path once (busted) end-to-end: download → verify → extract → register → resolve a symbol; confirm no EDT block and the balloon fires on a forced offline run. | Risk 1.1, TARGET-08-07 | todo |

## Test Case Gaps
- The **live network** fetch path is covered only by the DR-03 spike + the VNC DoD, not by an automated unit test (deliberate — unit tests pre-seed the cache and use a spy downloader, per the engineering contract's light-fixture rule).
- **Concurrent enable of many libraries** (parallel fetch ordering) is not test-cased; v1 fetches sequentially in one background task (§3.4), so no concurrency to test.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)

## DR-01 / DR-02 findings (2026-08-03) — four deltas from the design

Measured against the live upstream, not assumed. Each of these changes what Phase 1 must build.

### 1. Nothing upstream is tagged — pin by commit SHA
`git ls-remote --tags` returns **zero tags** for all four candidate repos (`LuaCATS/love2d`,
`busted`, `luassert`, `openresty`) **and** for `LuaLS/LLS-Addons`. The design's "pinned to a
versioned tarball URL … resolved from a release tag" is not achievable: these are rolling repos.

**Resolution**: pin the **commit SHA**, exactly as `tooling/corpus/corpus.tsv` already does for the
corpus ("pin to release tags resolved to their commit SHA, never a moving branch" — same intent,
one fewer indirection). `https://github.com/LuaCATS/<id>/archive/<sha>.tar.gz` returns 200. The
`version` field therefore carries the short SHA, not a semver; the requirements' "single pinned
version" contract is preserved, its *spelling* is not.

### 2. The tarball sha256 pins the wrong layer — verify, but never hard-fail
`codeload` tarballs are **generated on demand**, and GitHub has changed their compression before
(Jan 2023), silently invalidating every published archive sha256 worldwide.

An earlier revision of this file called that a blocker for Phase 3. It is not, for two reasons:

1. **The commit SHA already pins the content.** A git commit hash *is* a content hash of the tree,
   so `archive/<sha>.tar.gz` always contains the same files — only the gzip framing can differ. The
   tarball sha256 is a second, weaker pin on a *regenerated artifact*, i.e. it hashes the packaging
   rather than the payload. That is exactly why it is fragile.
2. **Nothing here is executed.** These are `---@meta` type stubs; Lunar indexes and parses them and
   the cache dir never joins `package.path`. The worst case of an unexpected tree is wrong
   completions, not code execution — unlike the `lua-language-server` binary TOOLING-04 fetches,
   where a checksum is a genuine safety control. Transport integrity is already covered by HTTPS.

**Resolution for Phase 3**: keep `sha256`/`size` as an *advisory* integrity check — log a warning
on mismatch and proceed — rather than refusing to register the library. A hard failure would break
fetching for every user simultaneously the next time GitHub changes its archive format, in exchange
for preventing an outcome that is not a security event. If a hard pin is ever wanted, hash the
**extracted tree**, which is stable, instead of the archive.

### 3. Entries have inter-dependencies — the model needs to carry them
`busted/config.json` declares `"Lua.workspace.library": ["${3rd}/luassert/library"]`. Enabling
busted alone leaves `assert` / `spy` / `stub` / `mock` unresolved, because its own definitions
open with `assert = require("luassert")`. The catalog model in design §2.1 has no dependency field.
**Resolution**: add `requires: [id, …]` to `LuaDefinitionEntry`, resolved transitively at enable
time. `${3rd}` is a LuaLS placeholder for its third-party addon dir and must NOT be interpreted as
a path by us.

### 4. Definitions live under `library/`, not the archive root
Every addon is `<repo>-<sha>/library/*.lua` plus a `config.json` sibling. The design's `rootPrefix`
("top-level archive dir to strip") conflates two things: the SHA-named wrapper dir, and the
`library/` subdir that is the actual root to register. Registering the archive root would index
`config.json` as project content and miss nothing useful.
**Resolution**: `rootPrefix` names the path **to register**, i.e. `<repo>-<sha>/library`.

### Resolved catalog data (DR-02)

| id | commit | size | `.lua` | sha256 |
|----|--------|------|--------|--------|
| love2d | `c630dd883cda128a19d850bd5e3911110b271609` | 97126 | 20 | `851a6a080cdeaad1e1601553bafc43879aaf466fe0f1b458932d181a98e7250e` |
| busted | `5ed85d0e016a5eb5eca097aa52905eedf1b180f1` | 2040 | 1 | `c33499e7be61511ac48f4815777c3df55322d0ee61fd92c223921999e5543ce8` |
| luassert | `d3528bb679302cbfdedefabb37064515ab95f7b9` | 7965 | 7 | `236ee34400c553924803a0ce155d3b7d6f0fe4e5577f5eb34b91f96e9f42cea5` |
| openresty | `17f581b2e6f2d3a30ab0e6564eb2eb40426db6b5` | 86612 | 73 | `974abe8078c0a2e0d2fc3cdc635b53aa7eea4c3a96c6793e66fe13ad5e9eb47a` |

**The busted entry is validated against the corpus**: its single `library/busted.lua` defines
`after_each async before_each describe done expose file finally insulate it lazy_setup
lazy_teardown pending randomize setup teardown` — covering **all eight** symbols behind the
1507 `LuaUndeclaredVariable` false positives measured in luarocks and luacheck (see TARGET-09).


## DR-03 findings (2026-08-03) — Risk 1.4 was real, and half the assumption is false

Run as `Dr03SyntheticLibraryResolutionSpikeTest`, a throwaway provider rather than Phase 4's, so a
failure could not be confounded by unwritten code. It is kept as the working precedent TC 6 needs.

### The fixture plumbing TC 6 requires (this was the unknown)

Three steps, in order. Omitting the third silently empties the index and fails every assertion:

1. `VfsRootAccess.allowRootAccess(testRootDisposable, <dir>)` — light fixtures refuse reads outside
   the project tree. Note the import is `com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess`, not
   the `testFramework` one.
2. Register through the EP (`AdditionalLibraryRootsProvider.EP_NAME.point.registerExtension(...)`)
   and then announce it: `ProjectRootManagerEx.makeRootsChange(EmptyRunnable, false, true)` inside a
   write action. Registration alone does nothing — the platform caches its root set.
3. **`IndexingTestUtil.waitUntilIndexesAreReady(project)`.** This is the step nothing in the repo
   did. `isInLibrary` flips to `true` *immediately* after step 2, which makes it look like the tree
   is available; the stub index is still empty until the rescan scheduled by the roots change has
   actually run.

### What is proven

- ✅ `ProjectFileIndex.isInLibrary` returns true for a file under the registered root.
- ✅ The definition **is** stub-indexed: `LuaGlobalDeclarationIndex` finds `dr03_probe` under
  `GlobalSearchScope.allScope`. So resolution through a `SyntheticLibrary` genuinely works, and
  TARGET-08-04 needs no per-feature indexer work — the design is right about that.

### What is NOT true — a Phase 4 work item the design does not account for

- ❌ **Completion does not surface a library symbol**, even with the index populated. Empty in both
  statement and expression position.
- **Cause**: `GlobalSymbolRankingService` searches `GlobalSearchScope.projectScope(project)`
  (`GlobalSymbolRankingService.kt:110` and `:180`), and project scope excludes library files by
  definition. Nothing about the fixture is at fault.
- **Consequence**: the design's "with **no other code change**" claim (requirements Overview) holds
  for *resolution* but not for *completion*, and TC 6 as written ("completion resolves a busted
  symbol") cannot pass until the scope is widened — `allScope`, or project ∪ libraries. Verify
  whether that service is the only consumer before changing it; widening a ranking scope affects
  every global completion, not just definition libraries.
- The spike deliberately does **not** assert the current completion behaviour: encoding today's
  wrong answer would lock the defect in and turn the eventual fix into a test failure.
