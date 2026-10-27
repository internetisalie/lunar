---
id: "TARGET-11"
title: "11: Neovim (`vim.*`) Definition Library"
type: "feature"
status: "planned"
priority: "high"
parent_id: "TARGET"
folders:
  - "[[features/target/requirements|requirements]]"
---

# TARGET-11: Neovim (`vim.*`) Definition Library

## Overview

Neovim plugins and configs reference the `vim` global hundreds of times per project, and Lunar
reports every reference as an undeclared variable. Neovim ships its own LuaCATS API definitions in
`runtime/lua` (`vim/_meta/*.lua`, `uv/_meta.lua` and the `vim.*` modules). This feature adds one
pinned [TARGET-08](../08-on-demand-definition-libraries/requirements.md) catalog entry, `neovim`,
that registers that subtree, so enabling it declares `vim` and its direct members. It is a
catalog-data change plus tests; the fetch, extraction and registration machinery is TARGET-08's,
unchanged.

Pinning `neovim` for a corpus member, and measuring its cost there, are assigned to [[MAINT-39]].
This feature neither performs nor waits for them. Parent epic: [TARGET](../requirements.md).

## Scope

### In Scope
- The `neovim` catalog entry (design §2.1).
- Generalising the bundled-catalog invariant that every `rootPrefix` ends `/library` (design §2.2).
- Automated tests on a synthetic archive laid out like the pinned one, and a real-archive pin check.
- A live check in GoLand against the real archive.

### Out of Scope
- **Members of nested namespaces that Lunar does not reach**: `vim.api.*`, `vim.fn.*`, `vim.uv.*`,
  `vim.fs.*`, `vim.log.levels.*` and the others listed in DR-04. [[BUG-505]] and [[BUG-507]] own
  the engine defects. Some namespaces do complete (`vim.o`, `vim.bo`, `vim.v`; DR-04).
- **`jit`** (LuaJIT). Neovim's `runtime/lua` does not declare it (DR-01); TARGET-12 (LuaJIT runtime
  stubs) owns it.
- **Corpus pin and corpus cost**: assigned to MAINT-39 (above). TARGET-11-09 bounds the cost with a
  temporary probe instead (design §4.2).
- **Auto-detection**: [TARGET-09](../09-addon-auto-detection/requirements.md).
- **Any change to `net.internetisalie.lunar.definitions` production logic.** The only main-source
  edit is a KDoc sentence (design §2.3).

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| TARGET-11-01 | **Pinned catalog entry** | M | Not Implemented | The bundled catalog carries the `neovim` entry of design §2.1, field for field. |
| TARGET-11-02 | **Root below the archive wrapper** | M | Not Implemented | The bundled-catalog invariant requires every `rootPrefix` to name a directory strictly below its `<repo>-<version>` wrapper, instead of requiring a `/library` suffix. |
| TARGET-11-03 | **`vim` is declared** | M | Not Implemented | With the library provisioned, references to `vim` raise no `Undeclared variable 'vim'`, and `vim.` completes the direct members the registered tree declares. |
| TARGET-11-04 | **Only `runtime/lua` is registered** | M | Not Implemented | Lua files outside `runtime/lua` in the archive (Neovim's `runtime/plugin/`, `runtime/ftplugin/`) declare nothing. |
| TARGET-11-05 | **The pin is real** | M | Not Implemented | The pinned URL yields the pinned bytes, and the archive holds the definition files under `rootPrefix`. |
| TARGET-11-06 | **Works live** | M | Not Implemented | Enabled in GoLand from the settings page, the real archive fetches, indexes without error and declares `vim`, with no deprecated-API warning on `vim`, and an undeclared `M` or `uv` in user code is still reported. |
| TARGET-11-09 | **Highlighting cost** | M | Not Implemented | On lazy.nvim, highlighting with the library on takes at most 1.5× as long as with it off, measured as design §4.2 states. |
| TARGET-11-07 | **Release note** | S | Not Implemented | `CHANGELOG.md` records the new library under the release that ships it. |
| TARGET-11-08 | **Nested namespace members** | W | Future Work | `vim.api.`, `vim.fn.`, `vim.uv.` and the other namespaces complete. Delivered by [[BUG-505]] and [[BUG-507]], not here. |

## Detailed Specifications

### TARGET-11-01
Pinned to Neovim `v0.12.6` (commit `779c3696…`), the latest stable tag on 2026-10-15 (DR-01). The
`license` value and its reasoning are in design §2.1.

### TARGET-11-02
The `/library` suffix is a property of LuaCATS addon repositories, not of definition libraries:
Neovim's definitions live in `runtime/lua`. The invariant's purpose, never registering the archive
wrapper itself, is kept (design §2.2).

### TARGET-11-06
On main the real tree indexes without error (DR-02), `vim` carries no deprecated-API warning
(DR-10), and an undeclared `M` or `uv` in user code is still reported (DR-11). Each holds because of
a merged fix (Dependencies); TC 8's mutations revert them.

### TARGET-11-09
The library is opt-in, but every file of a user who enables it pays the cost. On main the ratio is
1.71×, and a parser defect, [[BUG-517]], accounts for about two-thirds of that sweep gap (DR-08).
TARGET-11 therefore waits for BUG-517 and is measured against this bound after it lands.

The bound is 1.5× because it lies between today's 1.71× and the 1.24× measured with that parse
removed (DR-08 `c8`). It fails on the measured defect, and it leaves room for run-to-run spread:
DR-08's library runs range from 71.6 s to 81.8 s, about 13 % of their mean.

## Test Cases

`NeovimDefinitionLibraryTest` is design §2.4. No mutation edits an assertion or the fixture. Every
mutation in TC 1–7 has been run and turned its case red at the assertion (DR-07, DR-01); those of
TC 3, 5 and 6 also on main.

| # | Req | Given | When | Then | Mutation that turns it red |
|---|---|---|---|---|---|
| 1 | 01 | The bundled catalog | `LuaDefinitionCatalogLoaderTest.neovimEntryIsPinnedToNeovim0126` | Every field of `entry("neovim")` equals design §2.1 | Change any one field of the shipped entry (run: `rootPrefix` → the wrapper) |
| 2 | 02 | The bundled catalog | `LuaDefinitionCatalogLoaderTest.everyBundledEntryIsPinnedAndAttributed` | Passes for every entry | Set `neovim`'s `rootPrefix` to `neovim-779c36969e31080503be2dfccbcc3f999e289606` (the wrapper): fails naming `neovim` |
| 3 | 03 | Synthetic archive (design §2.4), `neovim` provisioned | Highlight `vim.schedule(function() end)` / `vim.list_extend({}, {})` | No `Undeclared variable 'vim'` | Shipped `rootPrefix` → `neovim-779c…/runtime/plugin`: provisioning succeeds with only `outside.lua`, and `vim` is undeclared |
| 4 | 03 | Same archive, **not** provisioned | Highlight `vim.schedule(function() end)` | `Undeclared variable 'vim'` present | Control for TC 3. Remove `enableInspections(LuaUndeclaredVariableInspection())` from `setUp`: the warning is absent and this case fails, where TC 3 would pass vacuously |
| 5 | 03 | Archive provisioned | Complete `vim.<caret>` | Lookup strings contain `list_extend` and `schedule` | Shipped `rootPrefix` → `neovim-779c…/runtime/lua/vim/_core`: `vim/_meta/builtin.lua` is outside it, so `schedule` is missing |
| 6 | 04 | Archive provisioned; it also holds `<wrapper>/runtime/plugin/outside.lua` = `function outside_root() end` | Highlight `outside_root()` | `Undeclared variable 'outside_root'` present | Shipped `rootPrefix` → `neovim-779c…/runtime`: the plugin file is registered and the list is empty |
| 7 | 05 | Network; the shipped catalog | The command in design §4.1 | Design §4.1's expected line | Change the entry's `sha256`: `fetch_library` raises `PinError` |
| 8 | 06 | GoLand with the plugin built from the implementation tree | [human-verification-checklists.md](human-verification-checklists.md) Scenario 1.1 | As stated there | Revert BUG-504's fix (`33326f3f`): `vim` shows the deprecated strikethrough. Revert BUG-509's (`a2399c0e`): no warning on `M` in step 6; revert BUG-510's (`a1d7226d`): none on `uv` |
| 9 | 09 | The implementation tree with BUG-517 merged; design §4.2's probe | Design §4.2: two runs of each arm | `library mean elapsedMs ÷ control mean elapsedMs` ≤ 1.5 | Already red: main without BUG-517 gives 1.71 (DR-08) |

## Acceptance Criteria
- [ ] TC 1–6 pass in `tooling/gce-builder/gce-builder.sh run test`; each mutation of TC 1–6 turns its case red once and is reverted (implementation-plan Phase 1).
- [ ] TC 7 prints design §4.1's expected line.
- [ ] TC 8 passes, recorded in the checklist with date and evidence.
- [ ] TC 9's ratio is ≤ 1.5, with all four `elapsedMs` values in the PR description.
- [ ] TARGET-11-07: the `CHANGELOG.md` entry exists.

## Non-Functional Requirements
- **Cost.** DR-01 measures the download and the registered tree. DR-08 measures the indexing and
  highlighting time and locates the excess. TARGET-11-09 bounds the highlighting time
  (risks-and-gaps Risk 1.2).
- **Threading.** No new runtime code; fetch and extraction run on TARGET-08's background task.

## Dependencies
- [[TARGET-08]] (done): catalog, fetcher, provider, settings page.
- [[BUG-503]] (done, `84fa67b0`), [[BUG-504]] (done, `33326f3f`), [[BUG-509]] (done, `a2399c0e`),
  [[BUG-510]] (done, `a1d7226d`): TARGET-11-06 rests on them (DR-02, DR-10, DR-11).
- [[BUG-517]] (done): gates the merge through TARGET-11-09 (DR-08).
- [[BUG-505]], [[BUG-507]]: deliver TARGET-11-08; not blocking.
- [[BUG-508]], [[BUG-511]], [[BUG-512]]: not blocking (risks-and-gaps, "The split").
- Consumer: [[MAINT-39]] pins this entry for its lazy.nvim member.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
- Checklist: [human-verification-checklists.md](human-verification-checklists.md)
