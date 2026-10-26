---
id: "BUG-525"
title: "The auto-import tests leave `sourcePath` pointing at `/src`, so in a container that mounts the repository at `/src` the whole checkout becomes a library and later tests fail"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-525: the auto-import tests leak `sourcePath` into later tests

## 1. Reproduction

Run the unit suite in the `lunar-ci` image with the checkout mounted at `/src`, as the MAINT-21
bisect harness does. It copies `/src` to `/workspace` and runs
`./gradlew test --rerun --no-daemon -PexcludeExternalFixtureTests`.

This is the smallest set that fails. Executed 2026-10-26 on `5ab0768a`:
```
--tests '*LuaAutoImportTest' --tests '*Lua51StdlibStubsTest' --tests '*StubGlobalSeedTypeTest'
```
| Mount point of the same tree | Tests | Failures |
|---|---|---|
| `/src` | 22 | 6: four in `Lua51StdlibStubsTest`, two in `StubGlobalSeedTypeTest` |
| `/source` (control) | 22 | 0 |

The trees and the commands are identical. The only difference is whether the path `/src` exists on the
container's local filesystem.

The failure only shows up when the tests run in this order. Each failing class passes when run alone.
It does not appear on Gitea CI or on the libvirt builder, because neither has a `/src` directory.

## 2. Expected vs Actual
- **Expected**: under target `STANDARD 5.1`, each Lua 5.1 name resolves to one declaration, in the
  active `lua-5.1` platform library. `KEYS` is `undefined` off a Redis target.
- **Actual**: each name resolves to 2–3 declarations, for example
  `debug.getlocal -> 2` and `rawlen -> 3` where 0 is expected. `KEYS` infers as `{ ... }`. In the full
  suite, 15–16 tests fail in the same way. `cjson.decode` has 6 declarations, the parameter-hint and
  scope tests fail, and `LuaTypeResolutionDumbModeTest` hits `IndexNotReadyException`.

## 3. Root cause

`LuaAutoImportTest` and `LuaAutoImportIntegrationTest` (both in `src/test/.../lang/completion/`)
point the source path at the light fixture's temp directory, in `configureSourcePathFor`:
```kotlin
LuaProjectSettings.getInstance(project).state.sourcePath = "$dir/?.lua;$dir/?/init.lua"
```
In the light fixture, `dir` is `/src`, which is the `temp:///src` path. Neither class restores the
value afterwards. `LuaProjectSettings` is a project service, and light tests reuse one project, so
every later test sees `sourcePath = "/src/?.lua;/src/?/init.lua"`.

`PlatformLibraryProvider.getExternalLibraries` (`project/PlatformLibraryProvider.kt`) turns each
absolute leading path that lies outside `project.basePath` into a "Search Trees" `SyntheticLibrary`.
It finds the directory with `VfsUtil.findFile(Paths.get(...))`, which looks on the **local**
filesystem. The light project's `basePath` is `/tmp/unitTest_…`. Where `/src` exists locally, the leak
therefore registers it as a library root. In the Docker harness, `/src` is the repository checkout,
so `src/main/resources/runtime/**` gets indexed beside the active platform library. That directory
holds every stdlib version's stubs and the Redis stubs. Test fixtures with `cjson` and similar
modules are indexed too, and indexing that large tree is what leaves later tests in dumb mode.

A temporary probe in `Lua51StdlibStubsTest.assertResolveCounts` printed this after `LuaAutoImportTest`
had run. The probe was run and then reverted.
```
sourcePath=/src/?.lua;/src/?/init.lua
basePath=/tmp/unitTest_resolveInitLuaNormalization_… libs=[ExternalLibraries:[/src], PlatformLibrary:[…lunar-0.18.0.jar!/runtime/standard/lua-5.1]]
debug.getlocal targets=[/src/src/main/resources/runtime/standard/lua-5.1/debug.lua, …lunar-0.18.0.jar!/runtime/standard/lua-5.1/debug.lua]
```

`PlatformLibraryProvider` behaves correctly here. A user who configures `/src/?.lua` does want `/src`
searched. The defect is in the test scaffolding: a test changed shared project settings and did not
restore them, and the value it left behind happens to name a real local directory.

`IndexedBasePlatformTestCase`'s `forceRebuild` was the first suspect. It is not the cause. It only
makes the leaked root's contents visible to stub lookups sooner.

## 4. Fix strategy

In both auto-import classes, save `LuaProjectSettings.state.sourcePath` in `setUp`, after
`super.setUp()`. Restore it in `tearDown`, before `super.tearDown()`, inside `try`/`finally`. This
follows the pattern `Lua51StdlibStubsTest.tearDown` already uses for the target. No production code
changes.

The fix is per-class rather than in `IndexedBasePlatformTestCase`. The two classes that change the
setting own its cleanup, and most of the other 35 subclasses never touch it.

## 5. Test strategy

The leak can only be shown through test order and the environment. A single test cannot show it
without asserting on its own cleanup, which would test nothing. The red/green evidence is therefore
the Docker run of the minimal set in section 1 (`/src` mount), red before the fix and green after,
followed by the full suite in Docker and on the builder.

## 6. Out of scope

Two other tests also leave `sourcePath` changed. `LuaExecutionEnvironmentBuilderTest` leaves
`/p/src/?.lua` or `""`, and `LuaSourcePathModuleResolutionTest` leaves a JUnit `@TempDir` path that
is deleted after the test. Neither path exists when later tests run, so neither causes failures.
They are not fixed here.

## Resolution

Both auto-import classes now save `sourcePath` in `setUp` and restore it in `tearDown`, as described
in section 4. Verified in the Docker harness with the checkout mounted at `/src`:

| Run | Tests | Failures |
|---|---|---|
| Minimal set, before the fix | 22 | 6 |
| Minimal set, after the fix | 22 | 0 |
| `GlobalSymbolRankingServiceTest`, `LuaAutoImport*`, `LuaEnterHandlerTest`, plus the two failing classes, before the fix | 68 | 6 |
| The same set, after the fix | 68 | 0 |
| Full suite (`test --rerun -PexcludeExternalFixtureTests`), after the fix | 3200 | 0 |

On the libvirt builder, the full `test --rerun` ran 3226 tests with 0 failures and 0 errors.
