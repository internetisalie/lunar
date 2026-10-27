---
id: "BUG-539"
title: "Unit tests read the plugin's JSON schemas from a jar outside the allowed VFS roots; the first test to load one fails on CI"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-539: the plugin jar is outside the tests' allowed VFS roots

Found by PR #95's CI. Test infrastructure only; users are not affected.

## 1. Reproduction
1. Run the unit suite from a checkout outside `$HOME` (CI checks out to `/workspace`).
2. Let a test that loads a JSON schema provider run before any other test has loaded the schemas.

## 2. Expected vs Actual Behavior
- **Expected**: the suite passes regardless of test order and checkout location.
- **Actual**: on PR #95's CI, `LuaCompletionBugTest` (`l`, `lo`, `loc after stmt`) and
  `LuaLanguageLevelInspectionTest.noErrorsForRegularOperatorsInAllVersions` failed with
  `VfsRootAccessNotAllowedError: File accessed outside allowed roots:
  jar:///workspace/.intellijPlatform/sandbox/lunar/GO-2026.2.3/plugins-test/lunar/lib/lunar-0.18.0.jar!/jsonschema/…`.
  The same tests pass on main's CI and on the builder.

## 3. Surface
The `test` task's JVM environment (`build.gradle.kts`).

## 4. Root Cause
`RockspecSchemaProvider`, `LuacheckrcSchemaProvider` and `BustedSchemaProvider` find their schemas with
`JsonSchemaProviderFactory.getResourceFile`, which goes through `VfsUtil.findFileByURL`. The plugin
classes load from the test sandbox's jar (`plugins-test/lunar/lib/`), so the URL points into that jar.
`VfsRootAccess.assertAccessInTests` does not allow that directory: its roots include `/workspace/build`
and `$HOME`, not `.intellijPlatform/sandbox`. The check only fires when a file first enters the VFS, so
only the first test to load a schema fails, and which test that is depends on test order. Adding test
classes in PR #95 changed the order on CI. On the builder the checkout is under `$HOME`, which is
allowed, so the builder suite never fails.

## 5. Fix Strategy
The `test` task passes the test sandbox's plugin directory in `vfs.additional-allowed-roots`, which
`VfsRootAccess.allowedRoots` reads.

## 6. Test Strategy
`PluginResourceVfsRootTest` resolves a schema resource to its jar or directory and asserts that it lies
under a `vfs.additional-allowed-roots` entry. The builder cannot reproduce the original failure, so this
checks the wiring that prevents it.

## 7. Verification
- Mutation: without the `vfs.additional-allowed-roots` property the test fails; with it, it passes.
- PR #95's CI build job is green with the fix.
