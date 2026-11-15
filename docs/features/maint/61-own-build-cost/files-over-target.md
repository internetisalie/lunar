---
id: "MAINT-61-FILES"
title: "Files over Target"
type: "results"
parent_id: "MAINT-61"
folders:
  - "[[features/maint/61-own-build-cost/requirements|requirements]]"
---

# MAINT-61: Files over Target
Every pass that MAINT-63's two change sweeps (`change-sweep1`, `change-sweep2`, engine code of
`8dad422c`) filed under MAINT-61, and the item that now holds it (MAINT-60-02). *Sweep 1* and
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
| awesome | `lib/naughty/core.lua` | cold-250 | 1110 ms | 1011 ms | `lib/awful/placement.lua` | MAINT-61 |
| awesome | `lib/naughty/core.lua` | edit-250 | timeout | timeout | `lib/awful/placement.lua` | MAINT-61 |
| awesome | `lib/naughty/layout/box.lua` | edit-250 | timeout | pass | `lib/awful/placement.lua` | MAINT-61 |
| awesome | `lib/naughty/layout/init.lua` | edit-250 | 583 ms | pass | `lib/awful/placement.lua` | MAINT-61 |
| awesome | `lib/naughty/notification.lua` | edit-250 | timeout | pass | `lib/awful/placement.lua` | MAINT-61 |
| blinkcmp | `lua/blink/cmp/lib/window/init.lua` | cold-250 | 318 ms (MAINT-62) | 379 ms | own | MAINT-68 |
| blinkcmp | `lua/blink/cmp/lib/window/init.lua` | edit-250 | 440 ms | 314 ms (MAINT-63) | own | MAINT-68 |
| differ | `lua/differ/command.lua` | cold-250 | 1910 ms | 1915 ms | `lua/differ/git/init.lua` | MAINT-68 |
| differ | `lua/differ/view.lua` | cold-250 | 544 ms | 462 ms | own | MAINT-61 |
| differ | `lua/differ/view.lua` | edit-250 | 746 ms | 592 ms | own | MAINT-61 |
| druid | `druid/base/back_handler.lua` | cold-250 | 781 ms | 693 ms | `druid/component.lua` | MAINT-68 |
| druid | `druid/base/back_handler.lua` | edit-250 | 586 ms | 610 ms | `druid/component.lua` | MAINT-68 |
| druid | `druid/base/blocker.lua` | edit-250 | 522 ms | 554 ms | `druid/component.lua` | MAINT-68 |
| druid | `druid/base/button.lua` | edit-250 | 858 ms | 883 ms | own | MAINT-61 |
| druid | `druid/base/drag.lua` | cold-250 | 354 ms | pass | own | MAINT-68 |
| druid | `druid/base/drag.lua` | edit-250 | 764 ms | timeout | own | MAINT-68 |
| druid | `druid/base/hover.lua` | cold-250 | pass | 410 ms | own | MAINT-61 |
| druid | `druid/base/hover.lua` | edit-250 | 614 ms | 485 ms | `druid/component.lua` | MAINT-68 |
| druid | `druid/base/scroll.lua` | cold-250 | 412 ms | 491 ms | own | MAINT-61 |
| druid | `druid/base/scroll.lua` | edit-250 | timeout | 783 ms | own | MAINT-61 |
| druid | `druid/base/static_grid.lua` | cold-250 | pass | 250 ms | own | MAINT-61 |
| druid | `druid/base/static_grid.lua` | edit-250 | 614 ms | 827 ms | own | MAINT-61 |
| druid | `druid/base/text.lua` | cold-250 | 424 ms | 406 ms | own | MAINT-61 |
| druid | `druid/base/text.lua` | edit-250 | 724 ms | 752 ms | own | MAINT-61 |
| druid | `druid/component.lua` | edit-250 | 304 ms | 343 ms | own | MAINT-68 |
| druid | `druid/custom/rich_input/rich_input.lua` | cold-250 | 429 ms | 433 ms | own | MAINT-61 |
| druid | `druid/custom/rich_input/rich_input.lua` | edit-250 | timeout | 946 ms | own | MAINT-61 |
| druid | `druid/custom/rich_text/rich_text.lua` | edit-250 | 731 ms | 826 ms | own | MAINT-61 |
| druid | `druid/druid.lua` | cold-250 | 9171 ms | 8053 ms | own | MAINT-68 |
| druid | `druid/druid.lua` | edit-250 | timeout | timeout | `druid/base/blocker.lua` | MAINT-61 |
| druid | `druid/extended/container.lua` | cold-250 | 591 ms | 737 ms | own | MAINT-61 |
| druid | `druid/extended/container.lua` | edit-250 | 823 ms | 938 ms | own | MAINT-61 |
| druid | `druid/extended/data_list.lua` | cold-250 | 338 ms | pass | own | MAINT-61 |
| druid | `druid/extended/data_list.lua` | edit-250 | 706 ms | 680 ms | own | MAINT-61 |
| druid | `druid/extended/hotkey.lua` | edit-250 | 709 ms | 806 ms | `druid/component.lua` | MAINT-68 |
| druid | `druid/extended/input.lua` | cold-250 | 543 ms | 559 ms | own | MAINT-61 |
| druid | `druid/extended/input.lua` | edit-250 | 799 ms | 866 ms | own | MAINT-61 |
| druid | `druid/extended/lang_text.lua` | edit-250 | 735 ms | 437 ms | `druid/component.lua` | MAINT-68 |
| druid | `druid/extended/layout.lua` | cold-250 | 465 ms | 390 ms | own | MAINT-61 |
| druid | `druid/extended/layout.lua` | edit-250 | 715 ms | 646 ms | own | MAINT-61 |
| druid | `druid/extended/progress.lua` | cold-250 | pass | 260 ms | own | MAINT-61 |
| druid | `druid/extended/progress.lua` | edit-250 | 792 ms | 735 ms | own | MAINT-61 |
| druid | `druid/extended/slider.lua` | cold-250 | 351 ms | pass | own | MAINT-61 |
| druid | `druid/extended/slider.lua` | edit-250 | 610 ms | 764 ms | own | MAINT-61 |
| druid | `druid/extended/swipe.lua` | edit-250 | 576 ms | 636 ms | `druid/component.lua` | MAINT-68 |
| druid | `druid/extended/timer.lua` | cold-250 | 277 ms | pass | own | MAINT-61 |
| druid | `druid/extended/timer.lua` | edit-250 | 630 ms | 588 ms | `druid/component.lua` | MAINT-68 |
| druid | `druid/system/druid_instance.lua` | cold-250 | 6824 ms | 6619 ms | own | MAINT-61 |
| druid | `druid/system/druid_instance.lua` | edit-250 | timeout | timeout | `druid/component.lua` | MAINT-68 |
| factoryplanner | `modfiles/backend/calculation/LUDecomposition.lua` | cold-250 | 3102 ms | 3450 ms | `modfiles/backend/data/Machine.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/calculation/LUDecomposition.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/calculation/SimplexTableau.lua` | edit-250 | timeout (MAINT-63) | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/calculation/gaussian_engine.lua` | edit-250 | timeout | timeout | `modfiles/backend/calculation/LUDecomposition.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/calculation/sequential_engine.lua` | edit-250 | timeout (MAINT-63) | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/calculation/simplex_engine.lua` | edit-250 | timeout | timeout | `modfiles/backend/data/Machine.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/calculation/solver.lua` | edit-250 | timeout | timeout | `modfiles/backend/data/Machine.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/calculation/solver_util.lua` | edit-250 | timeout | timeout | `modfiles/backend/data/Machine.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/calculation/structures.lua` | edit-250 | timeout | timeout | `modfiles/backend/data/Machine.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/data/Beacon.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/data/District.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/data/DistrictItemSet.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/data/Factory.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/data/Floor.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/data/Fuel.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/data/Line.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/data/Machine.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/data/Module.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/data/ModuleSet.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/data/Object.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/data/Realm.lua` | edit-250 | timeout | timeout | `modfiles/backend/data/Machine.lua` | MAINT-67 |
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
| factoryplanner | `modfiles/backend/migrations/migration_2_1_16.lua` | edit-250 | timeout (MAINT-63) | timeout | `modfiles/backend/data/Machine.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/migrations/migration_2_1_17.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/backend/migrations/migrator.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/control.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/base/calculator_dialog.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/base/compact_dialog.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/base/main_dialog.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/base/modal_dialog.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/components/item_highlights.lua` | edit-250 | timeout | timeout | `modfiles/backend/data/Machine.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/components/item_views.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/components/module_configurator.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/dialogs/beacon_dialog.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/dialogs/factory_dialog.lua` | edit-250 | timeout | 359 ms (MAINT-63) | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/dialogs/item_dialog.lua` | edit-250 | timeout | pass | `modfiles/backend/data/Machine.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/dialogs/machine_dialog.lua` | edit-250 | timeout | pass | `modfiles/backend/data/Machine.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/event_handler.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/main/district_list.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/main/districts_box.lua` | edit-250 | timeout | timeout | `modfiles/backend/data/Machine.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/main/factory_list.lua` | edit-250 | timeout | timeout | `modfiles/backend/data/Machine.lua` | MAINT-67 |
| factoryplanner | `modfiles/ui/main/production_box.lua` | edit-250 | 641 ms | timeout | `modfiles/backend/data/Line.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/actions.lua` | edit-250 | timeout | timeout | `modfiles/backend/data/Machine.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/clipboard.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/context.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/cursor.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/dictionary.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/effects.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/format.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/globals.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/gui.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/lib.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/nth_tick.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/porter.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/preferences.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| factoryplanner | `modfiles/util/temperature.lua` | edit-250 | timeout | timeout | `modfiles/backend/handlers/generator.lua` | MAINT-67 |
| gitsigns | `lua/gitsigns.lua` | cold-250 | 2604 ms (MAINT-62) | 3067 ms | `lua/gitsigns/hunks.lua` | MAINT-61 |
| gitsigns | `lua/gitsigns/actions.lua` | edit-250 | 804 ms (MAINT-63) | 745 ms | own | MAINT-61 |
| gitsigns | `lua/gitsigns/diff_int.lua` | cold-250 | 584 ms | 601 ms | `definitions:uv/_meta.lua` | MAINT-61 |
| gitsigns | `lua/gitsigns/diff_int.lua` | edit-250 | 713 ms | pass | `definitions:uv/_meta.lua` | MAINT-61 |
| gitsigns | `lua/gitsigns/word_diff.lua` | edit-250 | 715 ms (MAINT-63) | 855 ms | `lua/gitsigns/hunks.lua` | MAINT-61 |
| kristal | `src/engine/game/battle/arena.lua` | cold-250 | 542 ms | pass | own | MAINT-68 |
| kristal | `src/engine/game/battle/battlecutscene.lua` | cold-250 | 267 ms | 254 ms | own | MAINT-68 |
| kristal | `src/engine/game/battle/bullet.lua` | cold-250 | 278 ms | pass | own | MAINT-68 |
| kristal | `src/engine/game/battle/enemybattler.lua` | cold-250 | 295 ms | 306 ms | own | MAINT-61 |
| kristal | `src/engine/game/battle/enemybattler.lua` | edit-250 | 348 ms | 381 ms | own | MAINT-61 |
| kristal | `src/engine/game/battle/ui/tensionbar.lua` | cold-250 | 251 ms | pass | own | MAINT-61 |
| kristal | `src/engine/game/common/data/item.lua` | cold-250 | pass | 268 ms | own | MAINT-61 |
| kristal | `src/engine/game/common/data/item.lua` | edit-250 | 323 ms | pass | own | MAINT-61 |
| ldoc | `ldoc/markdown.lua` | cold-250 | 295 ms | 271 ms | own | MAINT-61 |
| ldoc | `ldoc/markdown.lua` | edit-250 | 384 ms | 270 ms | own | MAINT-61 |
| ljtk | `lang/bcsave.lua` | cold-250 | 1774 ms | 1600 ms | `lang/parser.lua` | MAINT-61 |
| ljtk | `lang/parser.lua` | edit-250 | timeout | timeout | own | MAINT-61 |
| luals | `script/vm/compiler.lua` | cold-250 | 48965 ms | 47089 ms | own | MAINT-61 |
| opensmce | `src/BootScreen/BootScreen.lua` | cold-250 | 757 ms | 1062 ms | `src/RootScene.lua` | MAINT-68 |
| opensmce | `src/Configs/utils.lua` | cold-250 | 415 ms | 362 ms | own | MAINT-61 |
| opensmce | `src/Configs/utils.lua` | edit-250 | 634 ms | 741 ms | own | MAINT-61 |
| opensmce | `src/Game/DummyLevel.lua` | cold-250 | 3625 ms | 3283 ms | `src/Game/SphereGroup.lua` | MAINT-61 |
| opensmce | `src/Game/DummyLevel.lua` | edit-250 | timeout | timeout | `src/Game/SphereGroup.lua` | MAINT-61 |
| opensmce | `src/Game/Game.lua` | cold-250 | 1160 ms | 1259 ms | `src/Game/Shooter.lua` | MAINT-61 |
| opensmce | `src/Game/Game.lua` | edit-250 | timeout | timeout | `src/Game/SphereGroup.lua` | MAINT-61 |
| opensmce | `src/Game/Level.lua` | edit-250 | timeout | timeout | `src/Game/SphereGroup.lua` | MAINT-61 |
| opensmce | `src/Game/Map.lua` | edit-250 | timeout | timeout | `src/Game/SphereGroup.lua` | MAINT-61 |
| opensmce | `src/Game/Path.lua` | edit-250 | timeout | timeout | `src/Game/SphereGroup.lua` | MAINT-61 |
| opensmce | `src/Game/Shooter.lua` | edit-250 | 715 ms | 537 ms | own | MAINT-61 |
| opensmce | `src/Game/Sphere.lua` | edit-250 | pass | 271 ms | own | MAINT-68 |
| opensmce | `src/Game/SphereChain.lua` | edit-250 | timeout | timeout | `src/Game/SphereGroup.lua` | MAINT-61 |
| opensmce | `src/Game/SphereGroup.lua` | edit-250 | timeout | timeout | own | MAINT-61 |
| opensmce | `src/GameBase.lua` | edit-250 | timeout | timeout | `src/Game/SphereGroup.lua` | MAINT-61 |
| opensmce | `src/UI/Manager.lua` | edit-250 | timeout | timeout | `src/Game/SphereGroup.lua` | MAINT-61 |
| opensmce | `src/UI/Widget.lua` | edit-250 | timeout | timeout | `src/Game/SphereGroup.lua` | MAINT-61 |
| opensmce | `src/UI/WidgetLevel.lua` | edit-250 | timeout | timeout | `src/Game/SphereGroup.lua` | MAINT-61 |
| prism | `engine/algorithms/astar/astar.lua` | cold-250 | 629 ms | 565 ms (MAINT-62) | `engine/init.lua` | MAINT-68 |
| prism | `engine/core/level.lua` | cold-250 | 422 ms | 322 ms | own | MAINT-61 |
| prism | `engine/core/level.lua` | edit-250 | 395 ms | 324 ms | own | MAINT-61 |
| prism | `engine/math/color.lua` | cold-250 | 1405 ms | 1030 ms | own | MAINT-61 |
| prism | `engine/math/color.lua` | edit-250 | timeout | timeout | own | MAINT-61 |
| prism | `engine/math/vector.lua` | cold-250 | 473 ms | 347 ms | own | MAINT-61 |
| prism | `engine/math/vector.lua` | edit-250 | 622 ms | 601 ms | own | MAINT-61 |
| prism | `spectrum/display.lua` | cold-250 | 948 ms | 974 ms | own | MAINT-61 |
| prism | `spectrum/display.lua` | edit-250 | 986 ms | 983 ms | own | MAINT-61 |
| rampant | `libs/BaseUtils.lua` | cold-250 | 482 ms | 484 ms | own | MAINT-68 |
| snabb | `src/lib/yang/alarms.lua` | cold-250 | 1870 ms | 1923 ms | `src/lib/yang/schema.lua` | MAINT-61 |
| snabb | `src/lib/yang/path_data.lua` | edit-250 | timeout | timeout | `src/lib/yang/schema.lua` | MAINT-61 |
| snabb | `src/lib/yang/schema.lua` | edit-250 | 371 ms | 277 ms | own | MAINT-61 |
| snabb | `src/lib/yang/yang.lua` | edit-250 | timeout (MAINT-63) | timeout | `src/lib/yang/schema.lua` | MAINT-61 |
| telescope | `lua/telescope/actions/history.lua` | cold-250 | 798 ms | 589 ms | `definitions:uv/_meta.lua` | MAINT-61 |
| telescope | `lua/telescope/builtin/__lsp.lua` | cold-250 | 456 ms | 466 ms | `definitions:vim/lsp.lua` | MAINT-61 |
