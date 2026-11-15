---
id: "MAINT-61-FILES"
title: "Files over Target"
type: "results"
parent_id: "MAINT-61"
folders:
  - "[[features/maint/61-own-build-cost/requirements|requirements]]"
---

# MAINT-61: Files over Target
Every pass that this item's two change sweeps (`change-sweep1`, `change-sweep2`, engine code of
`d3841670`) filed under MAINT-61, and the item that now holds it (MAINT-60-02). *Sweep 1* and
*Sweep 2* are the pass's value in each sweep, `pass` when it met its bound, and the other item in
parentheses when that sweep filed it elsewhere. *Hub* is `own` when the file's own build reached
250 ms, else the dominant dependency the sweep named. *Item* is decided from the member and the hub alone:

1. a factoryplanner pass is MAINT-67;
2. a pass whose member and hub are listed below is MAINT-68;
3. any other pass is MAINT-61.

The MAINT-68 hubs are this fixed list, which is the input to regenerating the table. It was taken from
DR-01's stack samples ([risks-and-gaps.md](risks-and-gaps.md)), whose committed row gives the buckets
of two of these hubs (`druid/component.lua`, opensmce `src/RootScene.lua`): blinkcmp `lua/blink/cmp/lib/window/init.lua`;
differ `lua/differ/git/init.lua`; druid `druid/base/drag.lua`, `druid/component.lua`, `druid/druid.lua`;
kristal `src/engine/game/battle/arena.lua`, `src/engine/game/battle/battlecutscene.lua`,
`src/engine/game/battle/bullet.lua`; opensmce `src/Game/Sphere.lua`, `src/RootScene.lua`; prism
`engine/init.lua`; rampant `libs/BaseUtils.lua`. `druid/component.lua` is borderline (visitor 34 %,
pair loop 30 %) and stays in MAINT-68 because the visitor is its largest bucket. The sweep's verdict still
prints MAINT-61 for all of them (Gap 2.2).

| Member | File | Bound | Sweep 1 | Sweep 2 | Hub | Item |
|---|---|---|---|---|---|---|
| awesome | `lib/naughty/core.lua` | cold-250 | 990 ms | 951 ms | `lib/awful/placement.lua` | MAINT-61 |
| awesome | `lib/naughty/core.lua` | edit-250 | 853 ms | 887 ms | `lib/awful/placement.lua` | MAINT-61 |
| awesome | `lib/naughty/layout/box.lua` | edit-250 | timeout | 921 ms | `lib/awful/placement.lua` | MAINT-61 |
| awesome | `lib/naughty/layout/init.lua` | edit-250 | 449 ms | pass | `lib/awful/placement.lua` | MAINT-61 |
| blinkcmp | `lua/blink/cmp/lib/window/init.lua` | cold-250 | 446 ms | 322 ms (MAINT-62) | own | MAINT-68 |
| blinkcmp | `lua/blink/cmp/lib/window/init.lua` | edit-250 | 441 ms | 364 ms (MAINT-63) | own | MAINT-68 |
| differ | `lua/differ/command.lua` | cold-250 | 1869 ms | 1856 ms | `lua/differ/git/init.lua` | MAINT-68 |
| differ | `lua/differ/git/init.lua` | edit-250 | 658 ms | 471 ms (MAINT-63) | own | MAINT-68 |
| differ | `lua/differ/view.lua` | cold-250 | 468 ms | 415 ms | own | MAINT-61 |
| differ | `lua/differ/view.lua` | edit-250 | 665 ms | 547 ms | own | MAINT-61 |
| druid | `druid/base/back_handler.lua` | cold-250 | 776 ms | 864 ms | `druid/component.lua` | MAINT-68 |
| druid | `druid/base/back_handler.lua` | edit-250 | 643 ms | 510 ms | `druid/component.lua` | MAINT-68 |
| druid | `druid/base/blocker.lua` | edit-250 | 589 ms | 556 ms | `druid/component.lua` | MAINT-68 |
| druid | `druid/base/button.lua` | cold-250 | pass | 504 ms | own | MAINT-61 |
| druid | `druid/base/button.lua` | edit-250 | timeout | timeout | own | MAINT-61 |
| druid | `druid/base/drag.lua` | cold-250 | 469 ms | 279 ms | own | MAINT-68 |
| druid | `druid/base/drag.lua` | edit-250 | 576 ms | 660 ms | `druid/component.lua` | MAINT-68 |
| druid | `druid/base/hover.lua` | edit-250 | 544 ms | 542 ms | `druid/component.lua` | MAINT-68 |
| druid | `druid/base/scroll.lua` | cold-250 | 377 ms | 396 ms | own | MAINT-61 |
| druid | `druid/base/scroll.lua` | edit-250 | 962 ms | 897 ms | own | MAINT-61 |
| druid | `druid/base/static_grid.lua` | edit-250 | 650 ms | 627 ms | own | MAINT-61 |
| druid | `druid/base/text.lua` | cold-250 | 368 ms | 366 ms (MAINT-62) | own | MAINT-61 |
| druid | `druid/base/text.lua` | edit-250 | 633 ms | 637 ms | `druid/component.lua` | MAINT-68 |
| druid | `druid/component.lua` | edit-250 | 554 ms | 421 ms | own | MAINT-68 |
| druid | `druid/custom/rich_input/rich_input.lua` | cold-250 | 471 ms | 766 ms | own | MAINT-61 |
| druid | `druid/custom/rich_input/rich_input.lua` | edit-250 | 986 ms | 977 ms | own | MAINT-61 |
| druid | `druid/custom/rich_text/rich_text.lua` | edit-250 | 570 ms | 554 ms | `druid/component.lua` | MAINT-68 |
| druid | `druid/druid.lua` | cold-250 | 7338 ms | 7727 ms | own | MAINT-68 |
| druid | `druid/druid.lua` | edit-250 | timeout | timeout | `druid/system/druid_instance.lua` | MAINT-61 |
| druid | `druid/extended/container.lua` | cold-250 | 955 ms | 654 ms | own | MAINT-61 |
| druid | `druid/extended/container.lua` | edit-250 | 724 ms | 928 ms | own | MAINT-61 |
| druid | `druid/extended/data_list.lua` | edit-250 | 655 ms | 683 ms | own | MAINT-61 |
| druid | `druid/extended/hotkey.lua` | cold-250 | 260 ms | pass | own | MAINT-61 |
| druid | `druid/extended/hotkey.lua` | edit-250 | 521 ms | 428 ms | `druid/component.lua` | MAINT-68 |
| druid | `druid/extended/input.lua` | cold-250 | 437 ms | 439 ms | own | MAINT-61 |
| druid | `druid/extended/input.lua` | edit-250 | 776 ms | 919 ms | own | MAINT-61 |
| druid | `druid/extended/lang_text.lua` | cold-250 | 352 ms | 288 ms | own | MAINT-61 |
| druid | `druid/extended/lang_text.lua` | edit-250 | 542 ms | 614 ms | `druid/component.lua` | MAINT-68 |
| druid | `druid/extended/layout.lua` | cold-250 | pass | 300 ms | own | MAINT-61 |
| druid | `druid/extended/layout.lua` | edit-250 | 618 ms | 729 ms | `druid/component.lua` | MAINT-68 |
| druid | `druid/extended/progress.lua` | cold-250 | 365 ms | pass | own | MAINT-61 |
| druid | `druid/extended/progress.lua` | edit-250 | 787 ms | 592 ms | own | MAINT-61 |
| druid | `druid/extended/slider.lua` | cold-250 | 317 ms | 314 ms | own | MAINT-61 |
| druid | `druid/extended/slider.lua` | edit-250 | 584 ms | 556 ms | own | MAINT-61 |
| druid | `druid/extended/swipe.lua` | edit-250 | 580 ms | 681 ms | `druid/component.lua` | MAINT-68 |
| druid | `druid/extended/timer.lua` | edit-250 | 639 ms | 515 ms | own | MAINT-61 |
| druid | `druid/system/druid_instance.lua` | edit-250 | timeout | timeout | `druid/base/button.lua` | MAINT-61 |
| factoryplanner | `modfiles/backend/calculation/LUDecomposition.lua` | cold-250 | 3132 ms | 3223 ms | `modfiles/backend/data/Machine.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/calculation/LUDecomposition.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/calculation/SimplexTableau.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/calculation/gaussian_engine.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/calculation/sequential_engine.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/calculation/simplex_engine.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/calculation/solver.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/calculation/solver_util.lua` | edit-250 | timeout | timeout | `modfiles/backend/data/Machine.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/calculation/structures.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/data/Beacon.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/data/District.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/data/DistrictItemSet.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/data/Factory.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/data/Floor.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/data/Fuel.lua` | edit-250 | timeout | timeout (MAINT-63) | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/data/Line.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/data/Machine.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/data/Module.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/data/ModuleSet.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/data/Object.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/data/Recipe.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/data/SimpleItem.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/handlers/defaults.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/handlers/generator_util.lua` | edit-250 | timeout | timeout (MAINT-63) | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/handlers/integrator.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/handlers/interface.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/handlers/loader.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/init.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/migrations/migration_2_0_2.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/migrations/migration_2_0_41.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/migrations/migration_2_0_45.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/migrations/migration_2_1_17.lua` | edit-250 | timeout | timeout (MAINT-63) | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/migrations/migrator.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/control.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/base/calculator_dialog.lua` | edit-250 | timeout | timeout | `modfiles/backend/data/Machine.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/base/compact_dialog.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/base/main_dialog.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/base/modal_dialog.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/components/item_highlights.lua` | edit-250 | timeout | timeout | `modfiles/backend/data/Machine.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/components/item_views.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/components/module_configurator.lua` | edit-250 | timeout | timeout (MAINT-63) | `modfiles/backend/data/Machine.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/dialogs/beacon_dialog.lua` | edit-250 | timeout | timeout | `modfiles/backend/data/Machine.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/event_handler.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/main/district_list.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/main/districts_box.lua` | edit-250 | timeout | timeout | `modfiles/backend/data/Machine.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/main/factory_list.lua` | edit-250 | 636 ms | timeout | `modfiles/backend/data/Line.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/main/item_boxes.lua` | edit-250 | pass | 792 ms | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/main/production_box.lua` | edit-250 | pass | timeout | `modfiles/backend/data/Machine.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/availability.lua` | edit-250 | pass | timeout | `modfiles/backend/data/Machine.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/clipboard.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/context.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/cursor.lua` | edit-250 | timeout (MAINT-63) | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/dictionary.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/effects.lua` | edit-250 | timeout | timeout (MAINT-63) | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/format.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/globals.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/gui.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/lib.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/nth_tick.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/porter.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/preferences.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/temperature.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| gitsigns | `lua/gitsigns/diff_int.lua` | cold-250 | 567 ms | 481 ms | `definitions:uv/_meta.lua` | MAINT-61 |
| gitsigns | `lua/gitsigns/diff_int.lua` | edit-250 | pass | 575 ms | `definitions:uv/_meta.lua` | MAINT-61 |
| kong | `kong/clustering/compat/checkers.lua` | cold-250 | 312 ms | pass | `definitions:openresty-17f581b2e6f2d3a30ab0e6564eb2eb40426db6b5/ngx.lua` | MAINT-61 |
| kristal | `src/engine/game/battle/battlecutscene.lua` | cold-250 | 272 ms | 285 ms | own | MAINT-68 |
| kristal | `src/engine/game/battle/enemybattler.lua` | edit-250 | 365 ms | 343 ms | own | MAINT-61 |
| kristal | `src/engine/game/common/data/item.lua` | cold-250 | 291 ms | 334 ms | own | MAINT-61 |
| kristal | `src/engine/game/common/data/item.lua` | edit-250 | pass | 290 ms | own | MAINT-61 |
| kristal | `src/engine/game/common/inventory.lua` | edit-250 | pass | 346 ms | own | MAINT-61 |
| ldoc | `ldoc/markdown.lua` | edit-250 | 308 ms | 339 ms | own | MAINT-61 |
| ljtk | `lang/bcsave.lua` | cold-250 | 1913 ms | 1667 ms | `lang/parser.lua` | MAINT-61 |
| ljtk | `lang/parser.lua` | edit-250 | timeout | timeout | own | MAINT-61 |
| luals | `script/vm/compiler.lua` | cold-250 | 50193 ms | 50958 ms | own | MAINT-61 |
| opensmce | `src/BootScreen/BootScreen.lua` | cold-250 | 974 ms | 673 ms | `src/RootScene.lua` | MAINT-68 |
| opensmce | `src/Configs/utils.lua` | cold-250 | 407 ms | 358 ms | own | MAINT-61 |
| opensmce | `src/Configs/utils.lua` | edit-250 | 501 ms | 565 ms | own | MAINT-61 |
| opensmce | `src/Game/DummyLevel.lua` | cold-250 | 2854 ms | 2709 ms | `src/Game/SphereGroup.lua` | MAINT-61 |
| opensmce | `src/Game/DummyLevel.lua` | edit-250 | timeout | timeout | `src/Game/SphereGroup.lua` | MAINT-61 |
| opensmce | `src/Game/Game.lua` | cold-250 | 1284 ms | 1114 ms | `src/Game/Shooter.lua` | MAINT-61 |
| opensmce | `src/Game/Game.lua` | edit-250 | timeout | timeout | `src/Game/SphereGroup.lua` | MAINT-61 |
| opensmce | `src/Game/Level.lua` | edit-250 | timeout | timeout | `src/Game/SphereGroup.lua` | MAINT-61 |
| opensmce | `src/Game/Map.lua` | edit-250 | timeout | timeout | `src/Game/SphereGroup.lua` | MAINT-61 |
| opensmce | `src/Game/Path.lua` | edit-250 | timeout | timeout | `src/Game/SphereGroup.lua` | MAINT-61 |
| opensmce | `src/Game/Shooter.lua` | edit-250 | 511 ms | 615 ms | own | MAINT-61 |
| opensmce | `src/Game/SphereChain.lua` | edit-250 | timeout | timeout | `src/Game/SphereGroup.lua` | MAINT-61 |
| opensmce | `src/Game/SphereGroup.lua` | edit-250 | timeout | timeout | own | MAINT-61 |
| opensmce | `src/GameBase.lua` | edit-250 | timeout | timeout | `src/Game/SphereGroup.lua` | MAINT-61 |
| opensmce | `src/UI/Manager.lua` | edit-250 | timeout | timeout | `src/Game/SphereGroup.lua` | MAINT-61 |
| opensmce | `src/UI/Widget.lua` | edit-250 | timeout | timeout | `src/Game/SphereGroup.lua` | MAINT-61 |
| opensmce | `src/UI/WidgetLevel.lua` | edit-250 | timeout | timeout | `src/Game/SphereGroup.lua` | MAINT-61 |
| prism | `engine/core/level.lua` | cold-250 | 426 ms | pass | own | MAINT-61 |
| prism | `engine/core/level.lua` | edit-250 | 304 ms | 413 ms | own | MAINT-61 |
| prism | `engine/math/color.lua` | cold-250 | 1441 ms | 1185 ms | own | MAINT-61 |
| prism | `engine/math/color.lua` | edit-250 | timeout | timeout | own | MAINT-61 |
| prism | `engine/math/vector.lua` | cold-250 | 385 ms | 557 ms | own | MAINT-61 |
| prism | `engine/math/vector.lua` | edit-250 | 487 ms | 569 ms | own | MAINT-61 |
| prism | `spectrum/display.lua` | cold-250 | 1037 ms | 1052 ms | own | MAINT-61 |
| prism | `spectrum/display.lua` | edit-250 | timeout | timeout | own | MAINT-61 |
| rampant | `libs/BaseUtils.lua` | cold-250 | 432 ms | 439 ms | own | MAINT-68 |
| restyhttp | `lib/resty/http.lua` | cold-250 | 450 ms | 339 ms (MAINT-62) | `definitions:openresty-17f581b2e6f2d3a30ab0e6564eb2eb40426db6b5/ngx.lua` | MAINT-61 |
| snabb | `src/lib/yang/alarms.lua` | cold-250 | 1854 ms | 1865 ms | `src/lib/yang/schema.lua` | MAINT-61 |
| snabb | `src/lib/yang/alarms.lua` | edit-250 | timeout | pass | `src/lib/yang/schema.lua` | MAINT-61 |
| snabb | `src/lib/yang/path_data.lua` | edit-250 | 751 ms (MAINT-63) | timeout | `src/lib/yang/schema.lua` | MAINT-61 |
| snabb | `src/lib/yang/yang.lua` | edit-250 | timeout | timeout | `src/lib/yang/schema.lua` | MAINT-61 |
| telescope | `lua/telescope/actions/history.lua` | cold-250 | 639 ms | 610 ms | `definitions:uv/_meta.lua` | MAINT-61 |
| telescope | `lua/telescope/builtin/__diagnostics.lua` | edit-250 | timeout | pass | `lua/telescope/pickers.lua` | MAINT-61 |
| telescope | `lua/telescope/builtin/__lsp.lua` | cold-250 | 550 ms | 428 ms | `definitions:vim/lsp.lua` | MAINT-61 |
| telescope | `lua/telescope/builtin/init.lua` | edit-250 | pass | 290 ms | own | MAINT-61 |
| telescope | `lua/telescope/command.lua` | edit-250 | pass | 356 ms | `lua/telescope/builtin/init.lua` | MAINT-61 |
| tntqueue | `queue/abstract.lua` | cold-250 | 290 ms | pass | own | MAINT-61 |
| weakauras | `WeakAuras/BaseRegions/CircularProgressTexture.lua` | cold-250 | pass | 276 ms | own | MAINT-61 |
