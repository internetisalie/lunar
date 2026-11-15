---
id: "MAINT-63-FILES"
title: "Files over Target"
type: "results"
parent_id: "MAINT-63"
folders:
  - "[[features/maint/63-edit-cascade-cost/requirements|requirements]]"
---

# MAINT-63: Files over Target
The file table MAINT-60-02 requires for this item's cause. Generated from this item's two change
sweeps (`change-sweep1`, `change-sweep2`, engine code of `8dad422c`), all `edit-250`. A file is
listed when at least one sweep filed its edit pass as edit cascade under `SnapshotCostVerdict`'s attribution. *Sweep 1* and *Sweep 2* are the
edit read's time, or `timeout`. *Item* is the item each sweep filed the pass under: `MAINT-63`,
`MAINT-61 (dep)` (a dependency's own build reached 250 ms), `MAINT-61 (own)`, `BUG-96` or
`BUG-97` (the own build of fallo `lua/fallo/init.lua` or luals `script/parser/compile.lua`,
`SnapshotCostVerdict.OWN_BUILD_ITEMS`), or `pass`. *Runs* is
how many sweeps filed it as edit cascade. *Builds*, *Dependency* and *Dependency ms* come from a
sweep that did, the first when both did.

| Member | File | Sweep 1 | Sweep 2 | Item 1 | Item 2 | Runs | Builds | Dependency | Dependency ms |
|---|---|---|---|---|---|---|---|---|---|
| awesome | `lib/gears/wallpaper.lua` | 110 ms | 470 ms | pass | MAINT-63 | 1/2 | 29 | `awesome/lib/wibox/hierarchy.lua` | 53 |
| awesome | `lib/naughty/dbus.lua` | 50 ms | 444 ms | pass | MAINT-63 | 1/2 | 13 | `awesome/lib/awful/client.lua` | 227 |
| awesome | `lib/naughty/init.lua` | timeout | 802 ms | MAINT-63 | MAINT-63 | 2/2 | 58 | `awesome/lib/awful/placement.lua` | 138 |
| awesome | `lib/naughty/widget/_default.lua` | 787 ms | 11 ms | MAINT-63 | pass | 1/2 | 50 | `awesome/lib/wibox/widget/init.lua` | 77 |
| awesome | `lib/naughty/widget/message.lua` | 11 ms | 324 ms | pass | MAINT-63 | 1/2 | 26 | `awesome/lib/wibox/widget/base.lua` | 34 |
| blinkcmp | `lua/blink/cmp/completion/init.lua` | 424 ms | 421 ms | MAINT-63 | MAINT-63 | 2/2 | 18 | `blinkcmp/lua/blink/cmp/completion/list.lua` | 97 |
| blinkcmp | `lua/blink/cmp/completion/trigger/init.lua` | 420 ms | 419 ms | MAINT-63 | MAINT-63 | 2/2 | 23 | `blinkcmp/lua/blink/cmp/init.lua` | 108 |
| blinkcmp | `lua/blink/cmp/completion/windows/ghost_text/init.lua` | 296 ms | 210 ms | MAINT-63 | pass | 1/2 | 9 | `blinkcmp/lua/blink/cmp/init.lua` | 109 |
| blinkcmp | `lua/blink/cmp/completion/windows/render/init.lua` | 311 ms | 282 ms | MAINT-63 | MAINT-63 | 2/2 | 15 | `blinkcmp/lua/blink/cmp/init.lua` | 88 |
| blinkcmp | `lua/blink/cmp/config/init.lua` | 326 ms | 272 ms | MAINT-63 | MAINT-63 | 2/2 | 18 | `blinkcmp/lua/blink/cmp/init.lua` | 76 |
| blinkcmp | `lua/blink/cmp/fuzzy/init.lua` | 204 ms | 385 ms | pass | MAINT-63 | 1/2 | 14 | `blinkcmp/lua/blink/cmp/init.lua` | 74 |
| blinkcmp | `lua/blink/cmp/fuzzy/rust/init.lua` | 332 ms | 155 ms | MAINT-63 | pass | 1/2 | 20 | `blinkcmp/lua/blink/cmp/init.lua` | 85 |
| blinkcmp | `lua/blink/cmp/lib/window/init.lua` | 440 ms | 314 ms | MAINT-61 (own) | MAINT-63 | 1/2 | 3 | `neovim:vim/_meta/vimfn.gen.lua` | 99 |
| blinkcmp | `lua/blink/cmp/signature/init.lua` | 929 ms | 391 ms | MAINT-63 | MAINT-63 | 2/2 | 35 | `blinkcmp/lua/blink/cmp/completion/trigger/context.lua` | 111 |
| blinkcmp | `lua/blink/cmp/signature/trigger.lua` | 252 ms | 186 ms | MAINT-63 | pass | 1/2 | 8 | `blinkcmp/lua/blink/cmp/init.lua` | 96 |
| blinkcmp | `lua/blink/cmp/sources/snippets/luasnip.lua` | 457 ms | 406 ms | MAINT-63 | MAINT-63 | 2/2 | 19 | `blinkcmp/lua/blink/cmp/init.lua` | 92 |
| differ | `lua/differ/git/init.lua` | 290 ms | 267 ms | MAINT-63 | MAINT-63 | 2/2 | 10 | `differ/lua/differ/panel/init.lua` | 78 |
| differ | `lua/differ/git/staging/frozen.lua` | 339 ms | 109 ms | MAINT-63 | pass | 1/2 | 9 | `differ/lua/differ/model/stage.lua` | 124 |
| differ | `lua/differ/git/staging/init.lua` | 255 ms | 57 ms | MAINT-63 | pass | 1/2 | 10 | `differ/lua/differ/git/staging/union.lua` | 76 |
| differ | `lua/differ/merge/continue.lua` | 597 ms | 336 ms | MAINT-63 | MAINT-63 | 2/2 | 17 | `differ/lua/differ/git/init.lua` | 124 |
| differ | `lua/differ/panel/init.lua` | 208 ms | 326 ms | pass | MAINT-63 | 1/2 | 9 | `neovim:vim/_meta/api.gen.lua` | 79 |
| differ | `lua/differ/pr/init.lua` | 575 ms | 653 ms | MAINT-63 | MAINT-63 | 2/2 | 24 | `differ/lua/differ/panel/init.lua` | 116 |
| factoryplanner | `modfiles/backend/calculation/SimplexTableau.lua` | timeout | timeout | MAINT-63 | MAINT-61 (dep) | 1/2 | 33 | `/src/modfiles/backend/init.lua` | 128 |
| factoryplanner | `modfiles/backend/calculation/sequential_engine.lua` | timeout | timeout | MAINT-63 | MAINT-61 (dep) | 1/2 | 27 | `/src/modfiles/backend/init.lua` | 184 |
| factoryplanner | `modfiles/backend/data/FactoryItem.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 44 | `/src/modfiles/backend/data/Realm.lua` | 100 |
| factoryplanner | `modfiles/backend/handlers/generator.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 48 | `/src/modfiles/backend/data/Machine.lua` | 102 |
| factoryplanner | `modfiles/backend/handlers/generator_util.lua` | timeout | timeout | MAINT-61 (dep) | MAINT-63 | 1/2 | 36 | `/src/modfiles/backend/handlers/generator.lua` | 198 |
| factoryplanner | `modfiles/backend/handlers/prototyper.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 47 | `/src/modfiles/backend/data/ModuleSet.lua` | 104 |
| factoryplanner | `modfiles/backend/migrations/migration_2_1_16.lua` | timeout | timeout | MAINT-63 | MAINT-61 (dep) | 1/2 | 43 | `/src/modfiles/backend/calculation/solver.lua` | 135 |
| factoryplanner | `modfiles/ui/dialogs/factory_dialog.lua` | timeout | 359 ms | MAINT-61 (dep) | MAINT-63 | 1/2 | 12 | `/src/modfiles/backend/data/Factory.lua` | 155 |
| factoryplanner | `modfiles/ui/dialogs/picker_dialog.lua` | 739 ms | 129 ms | MAINT-63 | pass | 1/2 | 18 | `/src/modfiles/backend/data/Factory.lua` | 143 |
| gitsigns | `lua/gitsigns.lua` | 275 ms | 181 ms | MAINT-63 | pass | 1/2 | 7 | `gitsigns/lua/gitsigns/git.lua` | 102 |
| gitsigns | `lua/gitsigns/actions.lua` | 804 ms | 745 ms | MAINT-63 | MAINT-61 (own) | 1/2 | 23 | `gitsigns/lua/gitsigns/actions/diff.lua` | 148 |
| gitsigns | `lua/gitsigns/actions/diffthis.lua` | 283 ms | 68 ms | MAINT-63 | pass | 1/2 | 13 | `gitsigns/lua/gitsigns/deleted_preview.lua` | 41 |
| gitsigns | `lua/gitsigns/attach.lua` | timeout | 962 ms | MAINT-63 | MAINT-63 | 2/2 | 26 | `gitsigns/lua/gitsigns/hunks.lua` | 211 |
| gitsigns | `lua/gitsigns/cli.lua` | timeout | 860 ms | MAINT-63 | MAINT-63 | 2/2 | 30 | `gitsigns/lua/gitsigns/actions.lua` | 148 |
| gitsigns | `lua/gitsigns/deleted_preview.lua` | 540 ms | 594 ms | MAINT-63 | MAINT-63 | 2/2 | 16 | `gitsigns/lua/gitsigns/hunks.lua` | 180 |
| gitsigns | `lua/gitsigns/git.lua` | 279 ms | 295 ms | MAINT-63 | MAINT-63 | 2/2 | 6 | `gitsigns/lua/gitsigns/git/repo.lua` | 114 |
| gitsigns | `lua/gitsigns/manager.lua` | 672 ms | 562 ms | MAINT-63 | MAINT-63 | 2/2 | 21 | `gitsigns/lua/gitsigns/hunks.lua` | 233 |
| gitsigns | `lua/gitsigns/sign_renderer.lua` | 482 ms | 701 ms | MAINT-63 | MAINT-63 | 2/2 | 17 | `gitsigns/lua/gitsigns/hunks.lua` | 191 |
| gitsigns | `lua/gitsigns/status.lua` | 573 ms | 318 ms | MAINT-63 | MAINT-63 | 2/2 | 16 | `gitsigns/lua/gitsigns/hunks.lua` | 190 |
| gitsigns | `lua/gitsigns/word_diff.lua` | 715 ms | 855 ms | MAINT-63 | MAINT-61 (dep) | 1/2 | 20 | `gitsigns/lua/gitsigns/hunks.lua` | 212 |
| luals | `script/vm/compiler.lua` | timeout | timeout | BUG-97 | MAINT-63 | 1/2 | 28 | `luals/script/utility.lua` | 178 |
| prism | `engine/core/system_manager.lua` | 136 ms | 255 ms | pass | MAINT-63 | 1/2 | 2 | `/src/engine/init.lua` | 62 |
| snabb | `src/lib/yang/binary.lua` | timeout | timeout | MAINT-63 | MAINT-63 | 2/2 | 28 | `snabb/src/lib/yang/data.lua` | 180 |
| snabb | `src/lib/yang/data.lua` | 445 ms | 427 ms | MAINT-63 | MAINT-63 | 2/2 | 4 | `snabb/src/lib/yang/schema.lua` | 192 |
| snabb | `src/lib/yang/path.lua` | 888 ms | 841 ms | MAINT-63 | MAINT-63 | 2/2 | 10 | `/src/src/lib/yang/schema.lua` | 233 |
| snabb | `src/lib/yang/rpc.lua` | 495 ms | 479 ms | MAINT-63 | MAINT-63 | 2/2 | 5 | `/src/src/lib/yang/schema.lua` | 227 |
| snabb | `src/lib/yang/state.lua` | timeout | 570 ms | MAINT-63 | MAINT-63 | 2/2 | 27 | `snabb/src/lib/yang/data.lua` | 189 |
| snabb | `src/lib/yang/yang.lua` | timeout | timeout | MAINT-63 | MAINT-61 (dep) | 1/2 | 14 | `/src/src/lib/yang/schema.lua` | 235 |
| telescope | `lua/telescope/actions/generate.lua` | 217 ms | 333 ms | pass | MAINT-63 | 1/2 | 16 | `telescope/lua/telescope/actions/init.lua` | 112 |
| telescope | `lua/telescope/actions/init.lua` | 85 ms | 281 ms | pass | MAINT-63 | 1/2 | 13 | `telescope/lua/telescope/utils.lua` | 34 |
| telescope | `lua/telescope/builtin/__diagnostics.lua` | 466 ms | 445 ms | MAINT-63 | MAINT-63 | 2/2 | 23 | `telescope/lua/telescope/pickers.lua` | 159 |
| telescope | `lua/telescope/builtin/__files.lua` | 279 ms | 48 ms | MAINT-63 | pass | 1/2 | 10 | `telescope/lua/telescope/previewers/buffer_previewer.lua` | 84 |
| telescope | `lua/telescope/builtin/__git.lua` | 398 ms | 822 ms | MAINT-63 | MAINT-63 | 2/2 | 16 | `telescope/lua/telescope/pickers.lua` | 134 |
| telescope | `lua/telescope/builtin/__internal.lua` | 313 ms | 215 ms | MAINT-63 | pass | 1/2 | 4 | `telescope/lua/telescope/builtin/init.lua` | 145 |
| telescope | `lua/telescope/command.lua` | 253 ms | 145 ms | MAINT-63 | pass | 1/2 | 8 | `telescope/lua/telescope/builtin/init.lua` | 130 |
| telescope | `lua/telescope/mappings.lua` | 229 ms | 259 ms | pass | MAINT-63 | 1/2 | 12 | `telescope/lua/telescope/actions/init.lua` | 94 |
| telescope | `lua/tests/automated/action_spec.lua` | 214 ms | 276 ms | pass | MAINT-63 | 1/2 | 8 | `telescope/lua/telescope/actions/init.lua` | 139 |
| telescope | `lua/tests/automated/command_spec.lua` | 112 ms | 439 ms | pass | MAINT-63 | 1/2 | 12 | `telescope/lua/telescope/builtin/init.lua` | 137 |
| telescope | `lua/tests/automated/diagnostics_spec.lua` | 396 ms | 589 ms | MAINT-63 | MAINT-63 | 2/2 | 20 | `telescope/lua/telescope/pickers.lua` | 121 |
| telescope | `lua/tests/automated/telescope_spec.lua` | 445 ms | 541 ms | MAINT-63 | MAINT-63 | 2/2 | 20 | `telescope/lua/telescope/pickers.lua` | 156 |
| telescope | `lua/tests/helpers.lua` | 276 ms | 230 ms | MAINT-63 | pass | 1/2 | 8 | `telescope/lua/telescope/previewers/buffer_previewer.lua` | 64 |
| telescope | `lua/tests/pickers/find_files__scrolling_descending_cycle.lua` | 310 ms | 471 ms | MAINT-63 | MAINT-63 | 2/2 | 18 | `telescope/lua/telescope/builtin/init.lua` | 146 |

## See Also
- Requirements: [requirements.md](requirements.md)
