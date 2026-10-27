---
id: "BUG-536"
title: "Per-task runIde sandboxes carry only lib/: the lua/, platform/ and runtime/ assets and the license files are missing"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-536: per-task sandboxes lack the plugin's assets

Found by MAINT-44's HV-02 live check on GoLand 2026.2.3 (`runIde`). Dev tooling only: the released
zip is built from the default sandbox and was never affected.

## 1. Reproduction
1. `tooling/gce-builder/gce-builder.sh run buildPlugin prepareSandbox_runIde`.
2. `ls .intellijPlatform/sandbox/lunar/GO-2026.2.3/plugins_runIde/lunar/`.
3. In the `runIde` sandbox, Debug a Lua run configuration.

## 2. Expected vs Actual Behavior
- **Expected**: `plugins_runIde/lunar/` matches the default `plugins/lunar/` and the zip: `lib/`, `lua/`,
  `platform/`, `runtime/`, `LICENSE`, `NOTICE`, `THIRD-PARTY.md`.
- **Actual**: only `lib/`. Debug fails with *Error running 'Unnamed': Failed to locate plugin directory*
  (`LuaRunConfiguration.kt:324`, `LuaFileUtil.getPluginVirtualDirectoryChild("lua")` returns null), and
  anything else that reads `runtime/` or `platform/` breaks in sandbox sessions. `plugins_runIde261/`
  is the same.

Evidence: `~/.cache/claude-scratch/lunar/maint-44/hv/hv02-runIde-fail.png`.

## 3. Context / Environment
- `build.gradle.kts`, the `tasks { prepareSandbox { from(...) } }` block
- IntelliJ Platform Gradle Plugin 2.19.0

## 4. Root Cause
The asset copy configured only the task named `prepareSandbox`. IJPGP 2.x registers a separate
`PrepareSandboxTask` for every run/test registration, each writing its own `plugins_<task>` directory:
`tasks --all` on the builder lists `prepareSandbox`, `prepareSandbox_runIde`, `prepareSandbox_runIde261`,
`prepareSandbox_runIdeBackend`, `prepareSandbox_runIdeFrontend`, `prepareSandbox_integrationTest`,
`prepareTestSandbox` and `prepareTestIdePerformanceSandbox`. `runIde` launches from
`prepareSandbox_runIde`, so it got the plugin jars and nothing else. The default sandbox, and the zip
`buildPlugin` builds from it, were correct.

## 5. Fix Strategy
Apply the same `from(...)` specs to every `PrepareSandboxTask` except the unit-test ones:
`tasks.withType<PrepareSandboxTask>().matching { !it.name.startsWith("prepareTest") }.configureEach { … }`
in place of `prepareSandbox { … }`. Inside the block `pluginName` is still the configured task's own
property, so each copy lands in its own `plugins_<task>/lunar/`.

The unit-test sandboxes keep only `lib/`, as before: unit tests do not read the assets.

## 6. Test Strategy
`SandboxAssetsIntegrationTest` gains `every prepared sandbox carries the plugin assets`. The `test` task
depends on `prepareSandbox` and `prepareSandbox_runIde` and passes both `pluginDirectory` paths in
`lunar.sandbox.pluginDirs`; the test asserts each has non-empty `lua/`, `platform/` and `runtime/`
directories and a `LICENSE` file. `prepareSandbox_runIde` uses the test platform, so this adds no IDE
download. `prepareSandbox_runIde261` would pull GoLand 2026.1.3 into every test run, so it is checked
on the builder and live instead.

## 7. Verification
- Red before the fix: `Expected a non-empty asset directory: …/GO-2026.2.3/plugins_runIde/lunar/lua`.
  Green after it, 2 of 2.
- `buildPlugin` before and after the fix: `prepareSandbox` and `buildPlugin` are UP-TO-DATE after the
  change, and the zip's entry list is identical (122 lines).
- `prepareSandbox_runIde261` on the builder: `plugins_runIde261/lunar/` holds `LICENSE`, `NOTICE`,
  `THIRD-PARTY.md`, `lib`, `lua`, `platform`, `runtime`.
- Live: Debug against `/usr/local/bin/lua` stops at a breakpoint on `runIde` and `runIde261`.
