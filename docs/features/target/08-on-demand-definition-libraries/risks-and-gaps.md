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
- **TBD: Browsable / searchable catalog** — a Plugins-page-style browse+install UI over a larger catalog, and/or auto-suggesting libraries from a project's unresolved `require` calls. Deferred; the graduation trigger below.
- **TBD: Arbitrary user-supplied definition sources** — direct git/URL/local-path entries beyond the bundled catalog.
- **TBD: Cache management** — eviction, "clear cache", size reporting.
- **TBD: Auto-update** — catalog versions are pinned; bumping is a plugin update in v1.

## Epic Placement Recommendation

**Keep TARGET-08 under the TARGET epic for v1.** Rationale: the load-bearing reuse is TARGET-04's library-root injection (`AdditionalLibraryRootsProvider` + `PlatformLibraryIndex.reload()`) and the LuaCATS `@meta` parser — both TARGET/language-resolution concerns. The fetch mechanic reuses two leaf TOOLING utilities (`LuaArtifactDownloader`, `LuaArchiveExtractor`) but not the TOOLING provisioning pipeline, so TARGET-08 is not naturally a TOOLING feature.

**Graduation trigger** (revisit placement — likely a new `DEFS` epic, or a TOOLING id): if TARGET-08 grows a full **browsable catalog / install UI** (Future Work item 1) or **arbitrary user-supplied sources** (item 2). At that point the fetch/catalog/UI surface outweighs the library-resolution reuse and warrants its own epic. This is a product-owner call, flagged in the roadmap brief. No code change is required to move it later.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| TARGET-00-DR-01 | Choose the v1 curated set (recommend: `love2d`, `busted`, `luassert`, `openresty`) and confirm each LuaCATS repo has a tagged release whose extracted tree yields completion for a sample symbol. | Risk 1.2, Gap 2.1 | todo |
| TARGET-00-DR-02 | For each chosen entry, resolve the exact tarball URL(s), sha256, byte size, and `rootPrefix`; populate `lunar-definitions-catalog.json`. | Risk 1.1 | todo |
| TARGET-00-DR-03 | **Prove completion/resolution through an injected `SyntheticLibrary` in a light fixture** (review N1): pre-seed a temp dir with a `---@meta` `.lua` file, register it via `LuaDefinitionLibraryProvider`, and assert a symbol from it completes/resolves (`completeBasic`/`multiResolve`). Capture the exact `VfsRootAccess.allowRootAccess` + refresh plumbing required and fold it into TC 6. If intractable headless, downgrade TC 6 to a VNC real-flow DoD. | Risk 1.4 | todo |
| TARGET-00-DR-03 | Spike the online fetch path once (busted) end-to-end: download → verify → extract → register → resolve a symbol; confirm no EDT block and the balloon fires on a forced offline run. | Risk 1.1, TARGET-08-07 | todo |

## Test Case Gaps
- The **live network** fetch path is covered only by the DR-03 spike + the VNC DoD, not by an automated unit test (deliberate — unit tests pre-seed the cache and use a spy downloader, per the engineering contract's light-fixture rule).
- **Concurrent enable of many libraries** (parallel fetch ordering) is not test-cased; v1 fetches sequentially in one background task (§3.4), so no concurrency to test.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
