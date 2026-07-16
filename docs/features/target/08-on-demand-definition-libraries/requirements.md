---
id: TARGET-08
parent_id: TARGET
type: feature
folders:
  - "[[features/target/requirements|requirements]]"
title: "TARGET-08: On-demand LuaLS / LuaCATS Definition Libraries"
status: "planned"
priority: "low"
vf_icon: 🔵
---

# TARGET-08: On-demand LuaLS / LuaCATS Definition Libraries

**Requirement**: Let a project consume community LuaLS / LuaCATS definition libraries (love2d, busted, luassert, openresty, …) for the third-party Lua libraries it uses, **fetched and enabled on demand** rather than bundled — reusing the existing library-root injection (`PlatformLibraryProvider` / `AdditionalLibraryRootsProvider`) and the existing LuaCATS `@meta` annotation parsing.
**Priority**: Could (roadmap C)
**Status**: Planned
**Design reference**: [design.md](design.md)

---

## Overview

Lunar bundles first-party platform stdlib stubs under `runtime/` (TARGET-04). It does **not** ship type definitions for the wider community library ecosystem (love2d, busted, luassert, openresty, …), which the [LuaCATS](https://github.com/LuaCATS) org and the LuaLS addon ecosystem publish as trees of `.lua` files carrying `@meta` LuaCATS annotations.

TARGET-08 lets a user **enable** one or more such libraries per project. On enable, Lunar fetches the definition tree from a bundled curated catalog (direct tarball download, off-EDT, with SHA-256 verification), extracts it into a per-user on-disk cache, and registers the extracted tree as a live `SyntheticLibrary` root through the same `AdditionalLibraryRootsProvider` seam that `LuaRocksLibraryProvider` already uses. Once registered, the existing indexer and LuaCATS parser index the `@meta` definitions, giving completion, resolution, and type inference for the library's API with **no other code change**.

Parent epic: [[features/target/requirements|TARGET]].

---

## Scope

### In Scope

- A **bundled curated catalog** (a JSON resource, mirroring `toolchain/lunar-toolchain-feed.json`) of a small v1 set of well-known LuaCATS definition libraries, each pinned to a versioned tarball URL + SHA-256 + byte size.
- A **per-project enable list** (which catalog entries are enabled), persisted in `.idea/lunar.xml` so a team shares it via VCS.
- **On-demand fetch**: direct tarball download of an enabled library, off-EDT, with the same size + SHA-256 verification and on-disk cache used by TOOLING provisioning, extracted into a per-user cache directory.
- **Registration into resolution** via a new `AdditionalLibraryRootsProvider` that exposes each enabled + fetched tree as a `SyntheticLibrary` root, and refreshes the library index when the enable list changes.
- A **settings UI** (a project `Configurable` nested under the existing "Lua Project" settings) listing the catalog with enable checkboxes and a per-row fetched/not-fetched status.
- **Graceful offline / fetch-failure handling**: enabling a not-yet-cached library with no network surfaces a balloon error and leaves the enable list unchanged for that entry; an already-cached library resolves with no network.
- **Attribution**: a per-library `license` + `attributionUrl` field in the catalog, surfaced read-only in the settings UI for any enabled library.

### Out of Scope

- A **browsable / searchable catalog** beyond the small curated v1 set, arbitrary git/URL entry, or auto-suggesting libraries from a project's `require` calls — deferred (see `risks-and-gaps.md`, Future Work).
- **Fetching from the live GitHub API / cloning git repos** — v1 uses pinned tarball URLs in the bundled catalog only.
- **Auto-update / version pinning UI** — a catalog entry is a single pinned version; bumping it is a plugin update.
- Changes to the **first-party `runtime/` stdlib stubs** (TARGET-04 / TARGET-07) — this feature is strictly additive and independent.
- **Bundling** any community definition files inside the plugin jar — nothing is bundled; the catalog holds only URLs + hashes.

---

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| TARGET-08-01 | **Bundled catalog model** | M | A typed, validated model of the bundled catalog JSON listing each library's id, display name, pinned tarball URL(s), sha256, size, rootPrefix, license and attribution URL. |
| TARGET-08-02 | **Per-project enable list** | M | The set of enabled catalog library ids is persisted in `lunar.xml` and read back per project. |
| TARGET-08-03 | **On-demand fetch + cache** | M | Enabling a library that is not yet cached downloads its tarball off-EDT, verifies size + SHA-256, and extracts it into a per-user cache keyed by library id + version. An already-cached library is reused with no network. |
| TARGET-08-04 | **Library-root registration** | M | Each enabled + fetched library tree is exposed as a `SyntheticLibrary` source root via an `AdditionalLibraryRootsProvider`, so the indexer and LuaCATS parser see its `@meta` definitions. |
| TARGET-08-05 | **Index refresh on change** | M | Changing the enable list refreshes the additional-library roots and drops resolve caches so newly-enabled definitions become resolvable without an IDE restart. |
| TARGET-08-06 | **Settings UI** | S | A project `Configurable` lists the catalog with an enable checkbox per row, a fetched/not-fetched status column, and the license + attribution URL for enabled rows. |
| TARGET-08-07 | **Offline / failure handling** | M | A fetch with no network (or a hash/size mismatch, or an unsupported archive) surfaces an error balloon via the tools notification group and does not add the failing library to the effective (fetched) set. |
| TARGET-08-08 | **Attribution surfaced** | S | The license string and attribution URL of every enabled library are shown read-only in the settings UI. |

---

## Detailed Specifications

### TARGET-08-01: Bundled catalog model

The catalog is a bundled JSON resource `/definitions/lunar-definitions-catalog.json` (parallel to `/toolchain/lunar-toolchain-feed.json`). Each entry is one community definition library pinned to exactly one version. Fields: `id` (stable key, e.g. `love2d`), `displayName`, `version`, `urls` (ordered mirror list), `sha256`, `size` (bytes), `rootPrefix` (top-level archive dir to strip, nullable), `license` (SPDX id, e.g. `MIT`), `attributionUrl`. Parsing is explicit (no reflection defaults); a missing required field is a corrupt-catalog error, exactly like `LuaToolchainFeedLoader`.

### TARGET-08-02: Per-project enable list

The enabled ids are a `List<String>` on `LuaProjectSettings.State`, serialized in `.idea/lunar.xml` (the existing project storage). An id in the list that is not present in the catalog is ignored (treated as not enabled) — no error.

### TARGET-08-03: On-demand fetch + cache

On enable, the fetcher resolves the entry, and if not already cached, downloads and extracts it. The cache root is `<system>/lunar/definitions/<id>-<version>/`. "Cached" means that directory exists and is non-empty. Download reuses the identical download+verify contract as TOOLING (`HttpRequests.saveToFile`, size then SHA-256, `.part` temp then atomic move, mirror fallthrough); extraction reuses the tar.gz/zip decompressor with `rootPrefix` stripping.

### TARGET-08-04 / TARGET-08-05: Registration & refresh

A new `AdditionalLibraryRootsProvider` returns one `SyntheticLibrary` per enabled id whose on-disk cache directory exists, exposing that directory (recursively via `SyntheticLibrary` source roots) so the platform indexes every `.lua` file underneath. Changing the enable list calls the existing `PlatformLibraryIndex.reload()` (roots change + stub rebuild) and `PsiManager.dropResolveCaches()`.

### TARGET-08-06 / TARGET-08-08: Settings UI

A project `Configurable` nested under the existing `LuaProjectConfigurable` shows a table: `[✓] | Library | Version | Status | License`. Toggling a checkbox on `apply()` mutates the enable list, triggers fetch (off-EDT) for newly-enabled uncached rows, and refreshes roots. The license + attribution URL are shown for each enabled row (attribution URL as a clickable `HyperlinkLabel`).

### TARGET-08-07: Offline / failure handling

If fetch fails (no network, hash/size mismatch, unsupported archive), an ERROR balloon is posted to `notification.group.lunar.tools`, and the id is **not** added to the effective fetched set for this session (its cache stays absent), though it may remain in the persisted enable list so a later online `apply()` retries. The provider only ever registers ids whose cache dir exists, so a failed fetch simply contributes no roots.

---

## Behavior Rules

- **Ordering / precedence**: definition-library roots are additive to and independent of the `runtime/` stdlib and installed-rocks roots; there is no precedence — all contribute source roots and resolution merges them.
- **Idempotence**: enabling an already-cached, already-enabled library is a no-op (no re-download).
- **No bundling**: the plugin jar ships only the catalog JSON (URLs + hashes), never any community `.lua` file.
- **Cache is per-user, not per-project**: two projects enabling `love2d@11.4` share one cache dir; the enable list is per-project.
- **Threading**: all fetch/extract runs on a `Task.Backgroundable`; root registration reads only settings + VFS; the `reload()` roots-change runs on the EDT in a write action (as it already does for TARGET-04).

---

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | TARGET-08-01 | The bundled `/definitions/lunar-definitions-catalog.json` | `LuaDefinitionCatalogLoader.load()` | Returns a catalog whose `libraries` contains an entry with `id == "love2d"`, non-blank `sha256`, `size > 0`, and `license == "MIT"`. |
| 2 | TARGET-08-01 | A catalog JSON string with an entry missing `sha256` | `LuaDefinitionCatalogLoader.parse(json)` | Throws `LuaProvisionException` mentioning the corrupt/missing field (no silent default). |
| 3 | TARGET-08-02 | A project whose `lunar.xml` `enabledDefinitionLibraries` = `["busted"]` | `LuaProjectSettings.getInstance(p).state.enabledDefinitionLibraries` | Returns `["busted"]`. |
| 4 | TARGET-08-03 | A pre-seeded cache dir `<system>/lunar/definitions/busted-2.2/` containing `library/busted.lua` | `LuaDefinitionLibraryFetcher.ensureCached(entry)` (busted@2.2) | Returns the existing dir path with **no** network call (verified by a fetcher constructed with a downloader spy asserting zero invocations). |
| 5 | TARGET-08-04 | A project with `busted` enabled and its cache dir pre-seeded with a `@meta` file `---@meta`\n`function assert.is_true(v) end` | `LuaDefinitionLibraryProvider.getAdditionalProjectLibraries(project)` | Returns a collection containing one `SyntheticLibrary` whose source roots include the busted cache dir. |
| 6 | TARGET-08-04 | Same project + pre-seeded busted `@meta` defs, index built | Completion at `assert.` in a `.lua` fixture | The completion list contains `is_true` (the definition resolves through the registered root). |
| 7 | TARGET-08-05 | A project with the busted root registered and indexed | Enable list changes from `[]` to `["busted"]` via `setEnabledDefinitionLibrariesAndRefresh(...)` | `PlatformLibraryIndex.reload()` is invoked and `PsiManager.dropResolveCaches()` is called (verified by a spy / observable resolution of a busted symbol that failed before the change). |
| 7b | TARGET-08-04 | A project with **no** definition libraries enabled | `LuaDefinitionLibraryProvider.getAdditionalProjectLibraries(project)` | Returns an empty collection (no roots, no error). |
| 8 | TARGET-08-07 | An enabled library `openresty` whose cache dir does not exist and a downloader spy that throws | `LuaDefinitionLibraryFetcher.ensureCached(entry)` | Returns `null` (no cache dir created); the provider contributes no `openresty` root; an ERROR balloon is requested on `notification.group.lunar.tools`. |
| 9 | TARGET-08-08 | Catalog entry `love2d` with `license == "MIT"`, `attributionUrl == "https://github.com/LuaCATS/love2d"` | Render the settings table row for `love2d` | The row shows `MIT` and a hyperlink to the attribution URL. |

---

## Acceptance Criteria

- [ ] The bundled catalog JSON parses into a typed model; a missing required field is a corrupt-catalog error (TC 1, 2).
- [ ] The enable list round-trips through `lunar.xml` (TC 3).
- [ ] An already-cached library is reused with zero network calls; an uncached one is fetched off-EDT with size + SHA-256 verification (TC 4).
- [ ] Each enabled + cached library is exposed as a `SyntheticLibrary` root and its `@meta` API becomes completable/resolvable (TC 5, 6).
- [ ] Changing the enable list refreshes roots and drops resolve caches without an IDE restart (TC 7); no libraries enabled → no roots (TC 7b).
- [ ] A fetch failure (offline / hash mismatch) surfaces a balloon and contributes no root (TC 8).
- [ ] The settings UI lists the catalog with enable checkboxes, status, license, and attribution link (TC 9).
- [ ] Real-flow DoD: with `love2d` enabled and fetched, a `.lua` file using the love2d API gets completion + resolution + hover in GoLand (VNC-verified).

---

## Non-Functional Requirements

- **Threading**: fetch/extract on a `Task.Backgroundable` pooled thread; never block the EDT. Root registration touches only settings + VFS reads. `reload()` runs its roots-change on the EDT in a write action.
- **Memory**: no retained hard refs to `Project`/`VirtualFile` in the provider or fetcher (follow `LuaRocksLibraryProvider`, which holds only a `List<VirtualFile>` inside the `SyntheticLibrary` value object per call).
- **Caching**: on-disk cache per (id, version); no re-download when present and non-empty.
- **Cancellation**: the fetch task honours `ProgressIndicator.checkCanceled()` during download and extraction.
- **Security**: every fetched artifact is size + SHA-256 verified against the pinned catalog value before use.

---

## Dependencies

- **TARGET-04** (Library Root Resolution — DONE): the `AdditionalLibraryRootsProvider` + `PlatformLibraryIndex.reload()` seam this reuses.
- **LuaCATS `@meta` parsing** (DONE): the lexer/parser that indexes `@meta` definition files once they are a registered library root (`luacats/lang/lexer/luacats.flex:105`).
- **TOOLING download/verify stack** (DONE): `LuaArtifactDownloader`, `LuaArchiveExtractor`, `LuaProvisionException` — reused for the fetch mechanic.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
- Verification: [human-verification-checklists.md](human-verification-checklists.md)
