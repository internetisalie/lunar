---
id: "MAINT-61"
title: "61: Own-Build Cost of Slow Files"
type: "feature"
status: "todo"
priority: "high"
parent_id: "MAINT"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-61: Own-Build Cost of Slow Files

## Overview
A file's own snapshot build (the visitor's inference plus the checker's fixed-point passes over its (value, use) pairs) takes 250 ms or more, or a pass that reaches such a file is filed with it. Filed by MAINT-60 Phase 3 from the sweeps' `ranked.tsv`; MAINT-60-02 requires every pass that
missed a 250 ms bound to be named in the table of its cause item. v0.21.21 is held on this item
(`release HOLD` in MAINT-60's `verdict.txt` until no pass misses). Parent:
[[features/maint/requirements|MAINT]]. Measurement feature: [[MAINT-60]].

## Measured Cause
DR-06 (under `base`, 43 of the 77 files at or over 250 ms have an own build at or over 250 ms), DR-08 (a single checker pass outlasts the cutoff; pairs per millisecond vary by more than 100x between builds; 65 of 106 builds with 250 ms of self time spend it outside the checker, in the visitor's own inference) and DR-09 (50 after-edit passes with an own build at or over 250 ms).

## Scope
Holds: Every pass of the sweep attributed by design 3.3 rules 3 and 4 to a file whose own build is 250 ms or more (other than fallo `init.lua`, BUG-96, and luals `script/parser/compile.lua`, BUG-97), every pass of a file in a member stopped by an earlier file's timeout, and every pass that reads such a file as its dominant dependency.

Direction (not designed): Make the files in the table build in under 250 ms. The two known shapes are the checker's pair work (DR-08: `checkedPairs` up to 970 215 in one pass) and the visitor's inference (DR-08: 65 of 106 builds). Changes that alter findings need MAINT-64's decision first.

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| MAINT-61-01 | **Every listed pass under 250 ms** | M | Not Implemented | Every pass in the table below clears its bound in the MAINT-60 sweep (no `miss:` line for it in `verdict.txt`). |
| MAINT-61-02 | **Findings unchanged or recorded** | M | Not Implemented | The MAINT-54 audit's `luacheck` member, the MAINT-55 conformance results and the sweep digests are identical before and after the change, or each difference is recorded as a correction (MAINT-60-03). |

## Files over target

241 files, 300 passes; slowest measured druid/druid/druid.lua cold-250 9571 ms; 179 passes not measured to completion (timeout, unmeasured or not-run). Generated from the union of the `ranked.tsv` of three MAINT-60 Phase 3 sweeps on the libvirt
builder, run at commit `9fb91435` (the engine and sweep code the sweeps measured). A pass is in the
table when it missed in at least one run. *Value* is the largest measured time across the runs that
missed, or `timeout`, `not-run` or `unmeasured` when a run did not complete the pass. *Item* is the
item most of those runs filed the pass under (design 3.3 rule 4 reads a dependency's time in the
run being judged, so a pass near 250 ms moves between items); on a tie, the second run's. *Runs
missed* is how many of the 3 runs the pass missed in.

| Member | File | Bound | Value | Runs missed | Cause | Dependency | Dependency ms |
|---|---|---|---|---|---|---|---|
| apisix | `apisix/core/config_etcd.lua` | edit-250 | 929 ms | 3/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 261 |
| apisix | `apisix/core/config_xds.lua` | edit-250 | 360 ms | 3/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 271 |
| apisix | `apisix/core/env.lua` | edit-250 | 381 ms | 3/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 300 |
| awesome | `lib/gears/color.lua` | cold-250 | 2982 ms | 3/3 | reach | `awesome/lib/awful/placement.lua` | 529 |
| awesome | `lib/gears/color.lua` | edit-250 | timeout | 3/3 | edit cascade | `awesome/lib/awful/placement.lua` | 566 |
| awesome | `lib/gears/init.lua` | edit-250 | timeout | 3/3 | edit cascade | `awesome/lib/awful/placement.lua` | 599 |
| awesome | `lib/gears/surface.lua` | edit-250 | timeout | 3/3 | edit cascade | `awesome/lib/awful/placement.lua` | 845 |
| awesome | `lib/gears/wallpaper.lua` | edit-250 | timeout | 3/3 | edit cascade | `awesome/lib/awful/placement.lua` | 569 |
| awesome | `lib/naughty/constants.lua` | edit-250 | timeout | 3/3 | edit cascade | `awesome/lib/awful/placement.lua` | 545 |
| awesome | `lib/naughty/container/background.lua` | edit-250 | timeout | 3/3 | edit cascade | `awesome/lib/awful/placement.lua` | 576 |
| awesome | `lib/naughty/container/init.lua` | edit-250 | timeout | 3/3 | edit cascade | `awesome/lib/awful/placement.lua` | 580 |
| awesome | `lib/naughty/dbus.lua` | edit-250 | timeout | 3/3 | edit cascade | `awesome/lib/awful/placement.lua` | 594 |
| awesome | `lib/naughty/init.lua` | edit-250 | timeout | 3/3 | edit cascade | `awesome/lib/awful/placement.lua` | 410 |
| awesome | `lib/naughty/layout/box.lua` | edit-250 | timeout | 3/3 | edit cascade | `awesome/lib/awful/placement.lua` | 513 |
| awesome | `lib/naughty/layout/legacy.lua` | edit-250 | timeout | 3/3 | edit cascade | `awesome/lib/awful/placement.lua` | 306 |
| awesome | `lib/naughty/list/actions.lua` | edit-250 | timeout | 3/3 | edit cascade | `awesome/lib/awful/placement.lua` | 562 |
| awesome | `lib/naughty/list/init.lua` | edit-250 | timeout | 3/3 | edit cascade | `awesome/lib/awful/placement.lua` | 559 |
| awesome | `lib/naughty/list/notifications.lua` | edit-250 | timeout | 3/3 | edit cascade | `awesome/lib/awful/placement.lua` | 540 |
| awesome | `lib/naughty/notification.lua` | edit-250 | timeout | 3/3 | edit cascade | `awesome/lib/awful/placement.lua` | 481 |
| awesome | `lib/naughty/widget/_default.lua` | edit-250 | timeout | 3/3 | edit cascade | `awesome/lib/awful/placement.lua` | 665 |
| awesome | `lib/naughty/widget/_markup.lua` | edit-250 | timeout | 3/3 | edit cascade | `awesome/lib/awful/placement.lua` | 497 |
| awesome | `lib/naughty/widget/icon.lua` | edit-250 | timeout | 3/3 | edit cascade | `awesome/lib/awful/placement.lua` | 555 |
| awesome | `lib/naughty/widget/init.lua` | edit-250 | timeout | 3/3 | edit cascade | `awesome/lib/awful/placement.lua` | 532 |
| awesome | `lib/naughty/widget/message.lua` | edit-250 | timeout | 3/3 | edit cascade | `awesome/lib/awful/placement.lua` | 481 |
| awesome | `lib/naughty/widget/title.lua` | edit-250 | timeout | 3/3 | edit cascade | `awesome/lib/awful/placement.lua` | 721 |
| blinkcmp | `lua/blink-cmp.lua` | cold-250 | 6618 ms | 3/3 | reach | `definitions:neovim/uv/_meta.lua` | 460 |
| differ | `lua/differ/command.lua` | cold-250 | 6384 ms | 3/3 | reach | `/src/lua/differ/git/init.lua` | 703 |
| differ | `lua/differ/git/cat_file.lua` | edit-250 | timeout | 3/3 | edit cascade | `definitions:neovim/vim/_core/system.lua` | 364 |
| differ | `lua/differ/git/staging/init.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/lua/differ/panel/init.lua` | 300 |
| differ | `lua/differ/git/staging/union.lua` | edit-250 | timeout | 3/3 | edit cascade | `definitions:neovim/vim/_core/system.lua` | 336 |
| differ | `lua/differ/merge/continue.lua` | edit-250 | timeout | 3/3 | edit cascade | `definitions:neovim/vim/_core/editor.lua` | 269 |
| differ | `lua/differ/merge/init.lua` | edit-250 | timeout | 3/3 | edit cascade | `definitions:neovim/vim/_core/system.lua` | 258 |
| differ | `lua/differ/panel/init.lua` | edit-250 | timeout | 3/3 | edit cascade | `definitions:neovim/vim/_core/system.lua` | 327 |
| differ | `lua/differ/ui/winbar.lua` | cold-250 | 421 ms | 1/3 | own build |  |  |
| differ | `lua/differ/util/keymap.lua` | edit-250 | timeout | 3/3 | edit cascade | `definitions:neovim/uv/_meta.lua` | 381 |
| differ | `lua/differ/view.lua` | cold-250 | 689 ms | 3/3 | own build |  |  |
| differ | `lua/differ/view.lua` | edit-250 | timeout | 3/3 | edit cascade | `definitions:neovim/vim/_core/system.lua` | 361 |
| druid | `druid/base/back_handler.lua` | cold-250 | 1022 ms | 3/3 | own build |  |  |
| druid | `druid/base/back_handler.lua` | edit-250 | 916 ms | 3/3 | own build |  |  |
| druid | `druid/base/blocker.lua` | cold-250 | 276 ms | 1/3 | own build |  |  |
| druid | `druid/base/blocker.lua` | edit-250 | 667 ms | 3/3 | edit cascade | `/src/druid/component.lua` | 387 |
| druid | `druid/base/button.lua` | cold-250 | 315 ms | 3/3 | own build |  |  |
| druid | `druid/base/button.lua` | edit-250 | 1015 ms | 3/3 | own build |  |  |
| druid | `druid/base/drag.lua` | edit-250 | 913 ms | 3/3 | own build |  |  |
| druid | `druid/base/hover.lua` | cold-250 | 306 ms | 1/3 | own build |  |  |
| druid | `druid/base/hover.lua` | edit-250 | 1030 ms | 3/3 | own build |  |  |
| druid | `druid/base/scroll.lua` | cold-250 | 531 ms | 3/3 | own build |  |  |
| druid | `druid/base/scroll.lua` | edit-250 | 1216 ms | 3/3 | own build |  |  |
| druid | `druid/base/static_grid.lua` | cold-250 | 337 ms | 1/3 | own build |  |  |
| druid | `druid/base/static_grid.lua` | edit-250 | 1014 ms | 3/3 | own build |  |  |
| druid | `druid/base/text.lua` | cold-250 | 551 ms | 3/3 | own build |  |  |
| druid | `druid/base/text.lua` | edit-250 | 1139 ms | 3/3 | own build |  |  |
| druid | `druid/color.lua` | cold-250 | 485 ms | 3/3 | reach | `/usr/local/share/lua/5.1/dkjson.lua` | 408 |
| druid | `druid/color.lua` | edit-250 | 548 ms | 3/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 386 |
| druid | `druid/component.lua` | edit-250 | 682 ms | 3/3 | own build |  |  |
| druid | `druid/custom/rich_input/rich_input.lua` | cold-250 | 816 ms | 3/3 | own build |  |  |
| druid | `druid/custom/rich_input/rich_input.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/druid/component.lua` | 623 |
| druid | `druid/custom/rich_text/module/rt.lua` | edit-250 | 968 ms | 3/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 535 |
| druid | `druid/custom/rich_text/module/rt_parse.lua` | edit-250 | 841 ms | 3/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 537 |
| druid | `druid/custom/rich_text/module/rt_tags.lua` | edit-250 | 651 ms | 3/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 493 |
| druid | `druid/custom/rich_text/rich_text.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/druid/component.lua` | 531 |
| druid | `druid/druid.lua` | cold-250 | 9571 ms | 3/3 | own build |  |  |
| druid | `druid/druid.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/druid/component.lua` | 583 |
| druid | `druid/editor_scripts/defold_parser/defold_parser.lua` | edit-250 | 550 ms | 1/3 | own build |  |  |
| druid | `druid/extended/container.lua` | cold-250 | 735 ms | 3/3 | own build |  |  |
| druid | `druid/extended/container.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/druid/component.lua` | 818 |
| druid | `druid/extended/data_list.lua` | cold-250 | 353 ms | 2/3 | own build |  |  |
| druid | `druid/extended/data_list.lua` | edit-250 | 936 ms | 3/3 | own build |  |  |
| druid | `druid/extended/hotkey.lua` | edit-250 | 849 ms | 3/3 | own build |  |  |
| druid | `druid/extended/input.lua` | cold-250 | 583 ms | 3/3 | own build |  |  |
| druid | `druid/extended/input.lua` | edit-250 | 1107 ms | 3/3 | own build |  |  |
| druid | `druid/extended/lang_text.lua` | edit-250 | 830 ms | 3/3 | own build |  |  |
| druid | `druid/extended/layout.lua` | cold-250 | 401 ms | 2/3 | own build |  |  |
| druid | `druid/extended/layout.lua` | edit-250 | timeout | 3/3 | own build |  |  |
| druid | `druid/extended/progress.lua` | edit-250 | 802 ms | 3/3 | own build |  |  |
| druid | `druid/extended/slider.lua` | cold-250 | 392 ms | 3/3 | own build |  |  |
| druid | `druid/extended/slider.lua` | edit-250 | 1034 ms | 3/3 | own build |  |  |
| druid | `druid/extended/swipe.lua` | edit-250 | 806 ms | 3/3 | edit cascade | `/src/druid/component.lua` | 483 |
| druid | `druid/extended/timer.lua` | edit-250 | timeout | 3/3 | own build |  |  |
| druid | `druid/system/druid_instance.lua` | cold-250 | 8215 ms | 3/3 | own build |  |  |
| druid | `druid/system/druid_instance.lua` | edit-250 | timeout | 3/3 | own build |  |  |
| druid | `druid/system/utf8.lua` | edit-250 | 291 ms | 1/3 | own build |  |  |
| factoryplanner | `modfiles/backend/calculation/LUDecomposition.lua` | cold-250 | 4315 ms | 3/3 | reach | `/src/modfiles/backend/data/Machine.lua` | 657 |
| factoryplanner | `modfiles/backend/calculation/LUDecomposition.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 530 |
| factoryplanner | `modfiles/backend/calculation/SimplexTableau.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 347 |
| factoryplanner | `modfiles/backend/calculation/gaussian_engine.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 326 |
| factoryplanner | `modfiles/backend/calculation/sequential_engine.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 526 |
| factoryplanner | `modfiles/backend/calculation/simplex_engine.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 440 |
| factoryplanner | `modfiles/backend/calculation/solver.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 376 |
| factoryplanner | `modfiles/backend/calculation/solver_util.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 490 |
| factoryplanner | `modfiles/backend/calculation/structures.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 361 |
| factoryplanner | `modfiles/backend/data/Beacon.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 454 |
| factoryplanner | `modfiles/backend/data/District.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 368 |
| factoryplanner | `modfiles/backend/data/DistrictItem.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 380 |
| factoryplanner | `modfiles/backend/data/DistrictItemSet.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 319 |
| factoryplanner | `modfiles/backend/data/Factory.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 449 |
| factoryplanner | `modfiles/backend/data/FactoryItem.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 417 |
| factoryplanner | `modfiles/backend/data/Floor.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 450 |
| factoryplanner | `modfiles/backend/data/Fuel.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 417 |
| factoryplanner | `modfiles/backend/data/Line.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 517 |
| factoryplanner | `modfiles/backend/data/Machine.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 389 |
| factoryplanner | `modfiles/backend/data/Module.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 382 |
| factoryplanner | `modfiles/backend/data/ModuleSet.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 396 |
| factoryplanner | `modfiles/backend/data/Object.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 371 |
| factoryplanner | `modfiles/backend/data/Realm.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 384 |
| factoryplanner | `modfiles/backend/data/Recipe.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 387 |
| factoryplanner | `modfiles/backend/data/SimpleItem.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 354 |
| factoryplanner | `modfiles/backend/handlers/defaults.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 381 |
| factoryplanner | `modfiles/backend/handlers/generator_util.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 312 |
| factoryplanner | `modfiles/backend/handlers/integrator.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 351 |
| factoryplanner | `modfiles/backend/handlers/interface.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 338 |
| factoryplanner | `modfiles/backend/handlers/loader.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 352 |
| factoryplanner | `modfiles/backend/init.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 343 |
| factoryplanner | `modfiles/backend/migrations/migration_2_0_2.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 342 |
| factoryplanner | `modfiles/backend/migrations/migration_2_0_41.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 468 |
| factoryplanner | `modfiles/backend/migrations/migration_2_0_45.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 331 |
| factoryplanner | `modfiles/backend/migrations/migration_2_0_8.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 373 |
| factoryplanner | `modfiles/backend/migrations/migration_2_1_16.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 332 |
| factoryplanner | `modfiles/backend/migrations/migration_2_1_17.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 345 |
| factoryplanner | `modfiles/backend/migrations/migration_2_1_4.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 389 |
| factoryplanner | `modfiles/backend/migrations/migrator.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 384 |
| factoryplanner | `modfiles/control.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 360 |
| factoryplanner | `modfiles/ui/base/calculator_dialog.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 446 |
| factoryplanner | `modfiles/ui/base/compact_dialog.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 404 |
| factoryplanner | `modfiles/ui/base/main_dialog.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 403 |
| factoryplanner | `modfiles/ui/base/modal_dialog.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 410 |
| factoryplanner | `modfiles/ui/components/item_highlights.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 332 |
| factoryplanner | `modfiles/ui/components/item_views.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 353 |
| factoryplanner | `modfiles/ui/components/module_configurator.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 486 |
| factoryplanner | `modfiles/ui/dialogs/beacon_dialog.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 358 |
| factoryplanner | `modfiles/ui/dialogs/factory_dialog.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 425 |
| factoryplanner | `modfiles/ui/dialogs/item_dialog.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 332 |
| factoryplanner | `modfiles/ui/dialogs/machine_dialog.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 411 |
| factoryplanner | `modfiles/ui/dialogs/picker_dialog.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 357 |
| factoryplanner | `modfiles/ui/dialogs/porter_dialog.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 326 |
| factoryplanner | `modfiles/ui/dialogs/preferences_dialog.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 442 |
| factoryplanner | `modfiles/ui/dialogs/recipe_dialog.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 300 |
| factoryplanner | `modfiles/ui/dialogs/utility_dialog.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 357 |
| factoryplanner | `modfiles/ui/event_handler.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 351 |
| factoryplanner | `modfiles/ui/main/district_list.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 381 |
| factoryplanner | `modfiles/ui/main/districts_box.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 337 |
| factoryplanner | `modfiles/ui/main/factory_list.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 464 |
| factoryplanner | `modfiles/ui/main/item_boxes.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 341 |
| factoryplanner | `modfiles/ui/main/production_bar.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 383 |
| factoryplanner | `modfiles/ui/main/production_box.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 417 |
| factoryplanner | `modfiles/ui/main/production_handler.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 373 |
| factoryplanner | `modfiles/ui/main/production_table.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 385 |
| factoryplanner | `modfiles/ui/main/solver_bar.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 364 |
| factoryplanner | `modfiles/ui/main/title_bar.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 400 |
| factoryplanner | `modfiles/util/actions.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 489 |
| factoryplanner | `modfiles/util/availability.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 354 |
| factoryplanner | `modfiles/util/clipboard.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 385 |
| factoryplanner | `modfiles/util/context.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 388 |
| factoryplanner | `modfiles/util/cursor.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 440 |
| factoryplanner | `modfiles/util/dictionary.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 409 |
| factoryplanner | `modfiles/util/effects.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 329 |
| factoryplanner | `modfiles/util/format.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 350 |
| factoryplanner | `modfiles/util/globals.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 448 |
| factoryplanner | `modfiles/util/gui.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 323 |
| factoryplanner | `modfiles/util/lib.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 334 |
| factoryplanner | `modfiles/util/nth_tick.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 330 |
| factoryplanner | `modfiles/util/porter.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 360 |
| factoryplanner | `modfiles/util/preferences.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 395 |
| factoryplanner | `modfiles/util/temperature.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/modfiles/backend/handlers/generator.lua` | 375 |
| gitsigns | `lua/gitsigns.lua` | cold-250 | 6483 ms | 3/3 | reach | `definitions:neovim/uv/_meta.lua` | 551 |
| gitsigns | `lua/gitsigns/message.lua` | edit-250 | timeout | 3/3 | edit cascade | `gitsigns/lua/gitsigns/hunks.lua` | 250 |
| kong | `kong/clustering/compat/checkers.lua` | cold-250 | 338 ms | 3/3 | reach | `definitions:openresty/ngx.lua` | 254 |
| kong | `kong/plugins/jwt/handler.lua` | edit-250 | 629 ms | 2/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 292 |
| kristal | `src/engine/game/battle/battlecutscene.lua` | cold-250 | 343 ms | 3/3 | own build |  |  |
| kristal | `src/engine/game/battle/enemybattler.lua` | cold-250 | 414 ms | 2/3 | own build |  |  |
| kristal | `src/engine/game/battle/enemybattler.lua` | edit-250 | 481 ms | 3/3 | own build |  |  |
| kristal | `src/engine/game/battle/partybattler.lua` | cold-250 | 255 ms | 1/3 | own build |  |  |
| kristal | `src/engine/game/battle/partybattler.lua` | edit-250 | 314 ms | 1/3 | own build |  |  |
| kristal | `src/engine/game/battle/ui/tensionbar.lua` | cold-250 | 320 ms | 2/3 | own build |  |  |
| kristal | `src/engine/game/battle/ui/tensionbar.lua` | edit-250 | 388 ms | 3/3 | own build |  |  |
| kristal | `src/engine/game/common/actorsprite.lua` | edit-250 | 293 ms | 1/3 | own build |  |  |
| kristal | `src/engine/game/common/data/item.lua` | cold-250 | 422 ms | 3/3 | own build |  |  |
| kristal | `src/engine/game/common/data/item.lua` | edit-250 | 431 ms | 1/3 | own build |  |  |
| kristal | `src/engine/game/common/data/partymember.lua` | cold-250 | 304 ms | 1/3 | own build |  |  |
| kristal | `src/engine/game/common/data/partymember.lua` | edit-250 | 369 ms | 2/3 | own build |  |  |
| kristal | `src/engine/game/common/inventory.lua` | cold-250 | 282 ms | 1/3 | own build |  |  |
| kristal | `src/engine/game/common/inventory.lua` | edit-250 | 368 ms | 1/3 | own build |  |  |
| lde | `packages/lde-core/src/lockfile.lua` | cold-250 | 419 ms | 3/3 | reach | `/usr/local/share/lua/5.1/dkjson.lua` | 269 |
| lde | `packages/lde-core/src/lockfile.lua` | edit-250 | 419 ms | 3/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 272 |
| lde | `packages/lde-core/tests/build.test.lua` | cold-250 | 689 ms | 3/3 | own build |  |  |
| lde | `packages/lde-core/tests/build.test.lua` | edit-250 | timeout | 3/3 | own build |  |  |
| lde | `packages/lde-core/tests/compile.test.lua` | edit-250 | 572 ms | 3/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 343 |
| lde | `packages/lde-core/tests/features.test.lua` | edit-250 | 556 ms | 3/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 257 |
| lde | `packages/lde-core/tests/git.test.lua` | edit-250 | 520 ms | 3/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 260 |
| lde | `packages/lde-core/tests/global.test.lua` | edit-250 | 603 ms | 3/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 279 |
| lde | `packages/lde-core/tests/lockfile.test.lua` | edit-250 | 480 ms | 3/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 329 |
| lde | `packages/lde-core/tests/node.test.lua` | edit-250 | 471 ms | 3/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 257 |
| lde | `packages/lde-core/tests/optionaldeps.test.lua` | edit-250 | 467 ms | 3/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 261 |
| lde | `packages/lde-core/tests/package.test.lua` | edit-250 | 499 ms | 3/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 256 |
| lde | `packages/lde-core/tests/profile.test.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/packages/lde-core/tests/build.test.lua` | 473 |
| lde | `packages/lde-core/tests/timings.test.lua` | edit-250 | 532 ms | 3/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 352 |
| lde | `packages/lde-core/tests/update.test.lua` | edit-250 | 549 ms | 3/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 290 |
| lde | `packages/lde-core/tests/util.test.lua` | edit-250 | 632 ms | 3/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 307 |
| ldoc | `ldoc/markdown.lua` | cold-250 | 384 ms | 3/3 | own build |  |  |
| ldoc | `ldoc/markdown.lua` | edit-250 | 655 ms | 3/3 | own build |  |  |
| ljtk | `lang/bcsave.lua` | cold-250 | 2178 ms | 3/3 | reach | `/src/lang/parser.lua` | 1586 |
| ljtk | `lang/bcsave.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/lang/parser.lua` | 1566 |
| ljtk | `lang/compile.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/lang/parser.lua` | 1468 |
| ljtk | `lang/parser.lua` | edit-250 | 1886 ms | 3/3 | own build |  |  |
| luals | `script/vm/compiler.lua` | cold-250 | timeout | 3/3 | own build |  |  |
| luals | `script/vm/compiler.lua` | edit-250 | timeout | 3/3 | unmeasured |  |  |
| luals | `script/vm/def.lua` | cold-250 | unmeasured | 2/3 | unmeasured |  |  |
| luals | `script/vm/def.lua` | edit-250 | timeout | 3/3 | unmeasured |  |  |
| luals | `script/vm/doc.lua` | cold-250 | unmeasured | 2/3 | unmeasured |  |  |
| luals | `script/vm/doc.lua` | edit-250 | timeout | 3/3 | unmeasured |  |  |
| luals | `script/vm/field.lua` | cold-250 | unmeasured | 2/3 | unmeasured |  |  |
| luals | `script/vm/field.lua` | edit-250 | timeout | 3/3 | unmeasured |  |  |
| luals | `script/vm/function.lua` | cold-250 | unmeasured | 2/3 | unmeasured |  |  |
| luals | `script/vm/function.lua` | edit-250 | timeout | 3/3 | unmeasured |  |  |
| luals | `script/vm/generic.lua` | cold-250 | unmeasured | 2/3 | unmeasured |  |  |
| luals | `script/vm/generic.lua` | edit-250 | timeout | 3/3 | unmeasured |  |  |
| luals | `script/vm/global.lua` | cold-250 | unmeasured | 2/3 | unmeasured |  |  |
| luals | `script/vm/global.lua` | edit-250 | timeout | 3/3 | unmeasured |  |  |
| luals | `script/vm/infer.lua` | cold-250 | unmeasured | 2/3 | unmeasured |  |  |
| luals | `script/vm/infer.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/script/vm/type.lua` | 297 |
| luals | `script/vm/init.lua` | cold-250 | unmeasured | 2/3 | unmeasured |  |  |
| luals | `script/vm/init.lua` | edit-250 | timeout | 3/3 | unmeasured |  |  |
| luals | `script/vm/library.lua` | cold-250 | unmeasured | 2/3 | unmeasured |  |  |
| luals | `script/vm/library.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/script/vm/type.lua` | 391 |
| luals | `script/vm/node.lua` | cold-250 | unmeasured | 2/3 | unmeasured |  |  |
| luals | `script/vm/node.lua` | edit-250 | timeout | 3/3 | unmeasured |  |  |
| luals | `script/vm/operator.lua` | cold-250 | unmeasured | 2/3 | unmeasured |  |  |
| luals | `script/vm/operator.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/script/vm/type.lua` | 311 |
| luals | `script/vm/precompile.lua` | cold-250 | unmeasured | 2/3 | unmeasured |  |  |
| luals | `script/vm/precompile.lua` | edit-250 | timeout | 3/3 | unmeasured |  |  |
| luals | `script/vm/ref.lua` | cold-250 | unmeasured | 2/3 | unmeasured |  |  |
| luals | `script/vm/ref.lua` | edit-250 | timeout | 3/3 | unmeasured |  |  |
| luals | `script/vm/sign.lua` | cold-250 | unmeasured | 2/3 | unmeasured |  |  |
| luals | `script/vm/sign.lua` | edit-250 | timeout | 3/3 | unmeasured |  |  |
| luals | `script/vm/tracer.lua` | cold-250 | unmeasured | 2/3 | unmeasured |  |  |
| luals | `script/vm/tracer.lua` | edit-250 | timeout | 3/3 | unmeasured |  |  |
| luals | `script/vm/type.lua` | cold-250 | unmeasured | 2/3 | unmeasured |  |  |
| luals | `script/vm/type.lua` | edit-250 | timeout | 3/3 | unmeasured |  |  |
| luals | `script/vm/value.lua` | cold-250 | unmeasured | 2/3 | unmeasured |  |  |
| luals | `script/vm/value.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/script/vm/type.lua` | 402 |
| luals | `script/vm/variable.lua` | cold-250 | unmeasured | 2/3 | unmeasured |  |  |
| luals | `script/vm/variable.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/script/vm/type.lua` | 255 |
| luals | `script/vm/visible.lua` | cold-250 | unmeasured | 2/3 | unmeasured |  |  |
| luals | `script/vm/visible.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/script/vm/type.lua` | 301 |
| luals | `script/vm/vm.lua` | cold-250 | unmeasured | 2/3 | unmeasured |  |  |
| luals | `script/vm/vm.lua` | edit-250 | timeout | 3/3 | unmeasured |  |  |
| monarch | `monarch/monarch.lua` | cold-250 | 338 ms | 3/3 | own build |  |  |
| opensmce | `src/BootScreen/BootScreen.lua` | cold-250 | 1456 ms | 3/3 | reach | `/src/src/RootScene.lua` | 477 |
| opensmce | `src/Configs/utils.lua` | cold-250 | 474 ms | 3/3 | own build |  |  |
| opensmce | `src/Configs/utils.lua` | edit-250 | 817 ms | 3/3 | own build |  |  |
| opensmce | `src/Debug.lua` | cold-250 | 882 ms | 3/3 | reach | `/usr/local/share/lua/5.1/dkjson.lua` | 256 |
| opensmce | `src/Debug.lua` | edit-250 | 1019 ms | 3/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 346 |
| opensmce | `src/Game/DummyLevel.lua` | cold-250 | 4091 ms | 3/3 | reach | `/src/src/Game/SphereGroup.lua` | 3097 |
| opensmce | `src/Game/DummyLevel.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/src/Game/SphereGroup.lua` | 687 |
| opensmce | `src/Game/Game.lua` | cold-250 | 1549 ms | 3/3 | reach | `/src/src/Game/Level.lua` | 585 |
| opensmce | `src/Game/Game.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/src/Game/SphereGroup.lua` | 541 |
| opensmce | `src/Game/Level.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/src/Game/SphereGroup.lua` | 622 |
| opensmce | `src/Game/Map.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/src/Game/SphereGroup.lua` | 660 |
| opensmce | `src/Game/Path.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/src/Game/SphereGroup.lua` | 758 |
| opensmce | `src/Game/PathEntity.lua` | edit-250 | 274 ms | 3/3 | own build |  |  |
| opensmce | `src/Game/Shooter.lua` | edit-250 | 830 ms | 3/3 | own build |  |  |
| opensmce | `src/Game/Sphere.lua` | edit-250 | 378 ms | 3/3 | own build |  |  |
| opensmce | `src/Game/SphereChain.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/src/Game/SphereGroup.lua` | 620 |
| opensmce | `src/Game/SphereGroup.lua` | edit-250 | timeout | 3/3 | own build |  |  |
| opensmce | `src/GameBase.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/src/Game/SphereGroup.lua` | 626 |
| opensmce | `src/Network.lua` | cold-250 | 348 ms | 2/3 | reach | `/usr/local/share/lua/5.1/dkjson.lua` | 271 |
| opensmce | `src/Network.lua` | edit-250 | 391 ms | 3/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 348 |
| opensmce | `src/NetworkingTest/NetworkingTest.lua` | edit-250 | 422 ms | 3/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 336 |
| opensmce | `src/Threads/networkGet.lua` | edit-250 | 343 ms | 3/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 285 |
| opensmce | `src/Threads/networkPostSerialized.lua` | edit-250 | 320 ms | 2/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 297 |
| opensmce | `src/UI/Manager.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/src/Game/SphereGroup.lua` | 675 |
| opensmce | `src/UI/Widget.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/src/Game/SphereGroup.lua` | 664 |
| opensmce | `src/UI/WidgetLevel.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/src/Game/SphereGroup.lua` | 672 |
| prism | `engine/core/level.lua` | cold-250 | 383 ms | 3/3 | own build |  |  |
| prism | `engine/core/level.lua` | edit-250 | 443 ms | 3/3 | own build |  |  |
| prism | `engine/math/color.lua` | cold-250 | 1334 ms | 3/3 | own build |  |  |
| prism | `engine/math/color.lua` | edit-250 | 1560 ms | 3/3 | own build |  |  |
| prism | `engine/math/vector.lua` | cold-250 | 527 ms | 3/3 | own build |  |  |
| prism | `engine/math/vector.lua` | edit-250 | 624 ms | 3/3 | own build |  |  |
| prism | `spectrum/display.lua` | cold-250 | 1206 ms | 3/3 | own build |  |  |
| prism | `spectrum/display.lua` | edit-250 | 1035 ms | 3/3 | own build |  |  |
| rampant | `libs/BaseUtils.lua` | cold-250 | 608 ms | 3/3 | own build |  |  |
| snabb | `src/lib/yang/alarms.lua` | cold-250 | 4134 ms | 3/3 | reach | `snabb/src/lib/yang/schema.lua` | 447 |
| snabb | `src/lib/yang/path_data.lua` | edit-250 | timeout | 3/3 | edit cascade | `/src/src/lib/yang/schema.lua` | 354 |
| telescope | `lua/telescope/actions/generate.lua` | cold-250 | 5495 ms | 3/3 | reach | `/src/lua/telescope/actions/init.lua` | 914 |
| telescope | `lua/telescope/actions/init.lua` | cold-250 | 361 ms | 2/3 | own build |  |  |
| telescope | `lua/telescope/actions/layout.lua` | cold-250 | 618 ms | 2/3 | reach | `telescope/lua/telescope/pickers/layout_strategies.lua` | 412 |
| telescope | `lua/telescope/builtin/__diagnostics.lua` | cold-250 | 727 ms | 3/3 | reach | `telescope/lua/telescope/pickers.lua` | 414 |
| telescope | `lua/telescope/builtin/__files.lua` | cold-250 | 824 ms | 3/3 | reach | `telescope/lua/telescope/previewers/buffer_previewer.lua` | 296 |
| telescope | `lua/telescope/builtin/__internal.lua` | cold-250 | 747 ms | 3/3 | own build |  |  |
| telescope | `lua/telescope/builtin/__lsp.lua` | cold-250 | 803 ms | 3/3 | own build |  |  |
| telescope | `lua/telescope/debounce.lua` | cold-250 | 404 ms | 1/3 | own build |  |  |
| telescope | `lua/telescope/pickers.lua` | cold-250 | 325 ms | 1/3 | own build |  |  |
| telescope | `lua/telescope/previewers/buffer_previewer.lua` | cold-250 | 268 ms | 1/3 | own build |  |  |
| telescope | `lua/tests/automated/pickers/find_files_spec.lua` | edit-250 | timeout | 2/3 | edit cascade | `definitions:neovim/uv/_meta.lua` | 370 |
| tntcrud | `crud/count.lua` | edit-250 | timeout | 3/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 365 |
| tntcrud | `crud/readview.lua` | edit-250 | timeout | 3/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 341 |
| tntcrud | `crud/select/compat/select_old.lua` | edit-250 | 884 ms | 3/3 | edit cascade | `/usr/local/share/lua/5.1/dkjson.lua` | 323 |
| tntqueue | `queue/abstract.lua` | cold-250 | 675 ms | 3/3 | own build |  |  |
| weakauras | `WeakAuras/BaseRegions/CircularProgressTexture.lua` | cold-250 | 500 ms | 3/3 | own build |  |  |
