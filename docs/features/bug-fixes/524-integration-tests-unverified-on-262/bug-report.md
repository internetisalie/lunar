---
id: "BUG-524"
title: "The IDE-Starter integration tests are unverified on GoLand 2026.2, and pin a 253 ide-starter"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-524: integration tests unverified on 2026.2

Filed from [MAINT-21](../../maint/21-gradle9-ijpgp-262/requirements.md) (its Out of Scope list, and
risks-and-gaps "Technical Debt & Future Work"). It applies once MAINT-21's Phase 2 has landed.
Until then `:integrationTest` targets 2026.1.3, as before.

**Why medium.** After that merge, `testVersion = 2026.2.3`, and every `gce-builder.sh run build` runs
`:integrationTest` against 2026.2.3 through this unverified path. That is the build gate reviewers
are told to run. A failure there will look like a regression in whatever change is being gated.

## 1. Reproduction
On the MAINT-21 branch (`testVersion = 2026.2.3`, IJPGP 2.19.0), on the builder, with a GoLand
license available as `IdeProductResolver` expects:

```bash
~/.cache/claude-scratch/lunar/bin/builder-run libvirt integrationTest --rerun
```

## 2. Expected vs Actual Behavior
- **Expected**: the `src/integrationTest` suite launches GoLand 2026.2.3 through IDE-Starter and
  passes, as it does on 2026.1.3.
- **Actual** (measured on the libvirt builder, `integrationTest --rerun`, GoLand key supplied):
  14 tests, 3 failed. All three IDE-launching tests died before starting an IDE with
  `NoClassDefFoundError: com/intellij/ide/starter/ide/IdeProductProvider`, at
  `IdeProductResolver.getIdeProductByType`. The 11 tests that never launch an IDE passed.

## 3. Context / Environment
- **Lua Version**: n/a.
- **Relevant Files**: `build.gradle.kts`. `integrationTestImplementation` pins
  `com.jetbrains.intellij.tools:ide-starter:253.29346.240` and `libs.ide.starter`
  (`gradle/libs.versions.toml` `ide-starter = "253.29346.240"`). It also declares
  `testFramework(TestFrameworkType.Starter, configurationName = "integrationTestImplementation")`;
  the IJPGP 2.18.0 changelog says that type adds "product-specific IDE Starter dependencies …
  for IntelliJ Platform `262` and newer". The 253 pins and the 262 Starter dependencies are both on
  that classpath.
- **Other Notes**: `:integrationTest` is not run by CI (`.agents/ci-topology.md` §7). It runs on
  the builder only, through `run build`, and so on every `run build` after MAINT-21 merges.

## 4. Root Cause

The integration tests compiled against one ide-starter and ran against another.

- **Compile classpath: 253.** The explicit `ide-starter:253.29346.240` pin supplied
  `com.intellij.ide.starter.ide.IdeProductProvider`, so `compileIntegrationTestKotlin` succeeded
  (MAINT-21 DR-16).
- **Runtime classpath: 262.** `testFramework(TestFrameworkType.Starter)` adds Starter modules with
  `strictly [262, 262.10968.67]`. `dependencies --configuration integrationTestRuntimeClasspath`
  showed `ide-starter-junit5:253.29346.240 -> 262.10968.63` and `ide-starter:262.10968.63`. Every
  253 pin was upgraded silently.
- **The API moved in 262.** `IdeProductProvider` is not in `ide-starter-262.10968.63.jar`. Each
  product's `IdeInfo` now lives in an `ide-starter-product-<name>` module. That module registers it
  in Starter's DI through `ServiceLoader` (`IdeProductInit`), tagged with an `IdeInfoType`.
  `IdeInfoType.fromProductCode` is also non-null in this build.

**The pins could not simply be dropped.** With both 253 pins removed, the run got past product
lookup and then failed on `NoClassDefFoundError: jetbrains/buildServer/messages/serviceMessages/
ServiceMessage` and `NoSuchMethodError: CoroutineDispatcher.limitedParallelism$default(…)`. The
Starter framework's own dependency declarations exclude the platform jars, the IntelliJ coroutines
fork (`1.10.2-intellij-2`) and `org.jetbrains.teamcity:serviceMessages`. The stale
`kotlinx-coroutines-core-jvm:1.7.1` pin was then the only coroutines on the classpath.
The `ide-starter-junit5` pin was what brought the full runtime tree in, through a declaration of our
own that carries no excludes.

**Baseline.** The previous builder results (2026-08-23, GoLand 2026.1.3) show the cross-file
completion test timing out on the License dialog, with "No license key found". The suite was not
green on the builder before MAINT-21 either. The cause was environmental: no key was installed
where `IdeProductResolver` looks. No 2026.1.3 rerun was needed to separate the two causes: the 262
failure is a class-loading error raised before any IDE starts.

## 5. Fix Strategy (delivered)

- `build.gradle.kts`: removed the `ide-starter:253.29346.240` pin and the `kotlinx-coroutines-core-jvm:1.7.1`
  pin. `ide-starter-junit5` is now declared **without a version**. Its version comes from the
  Starter framework's strict 262 constraint, so it follows `platformVersion`, and the platform runtime
  tree still comes in through our own declaration. The coroutines pin was removed because the
  platform's fork now arrives through that tree. Whether the stock 1.7.1 jar would shadow the fork
  if both were present was not tested; this fix does not depend on the answer.
- `gradle/libs.versions.toml`: removed the `ide-starter` version and library entries, which are now unused.
- `IdeProductResolver.getIdeProductByType`: replaced the `IdeProductProvider` `when` with an
  `IdeInfoType` lookup by product code, followed by `di.direct.instance<IdeInfo>(tag = …)`. The IC
  fallback for unknown codes is kept.

## 6. Verification

- `integrationTest --rerun` on the libvirt builder: **14 tests, 0 failures, 0 errors**, from fresh
  JUnit XML. The IDE tests launched `GO-262.10968.67` (2026.2.3), and each applied the license.
  `assertCompletionCommand CONTAINS helper_from_a helper_from_b` passed.
- The two intermediate states also failed as described above. The first was the pins as shipped by
  MAINT-21, and the second was all pins dropped. The suite therefore discriminates this defect.
- **License, for whoever runs this next:** `IdeProductResolver` reads `LUNAR_LICENSE_KEY`, then the newest
  `~/.config/JetBrains/GoLand*/goland.key` of the user running Gradle. `gce-builder.sh run` does
  not forward environment variables. On the builder, the activated key therefore has to sit at
  `/home/builder/.config/JetBrains/GoLand<ver>/goland.key`. It is a copy of the activated key
  (the ~12 KB file activation writes), not the offline activation code.
