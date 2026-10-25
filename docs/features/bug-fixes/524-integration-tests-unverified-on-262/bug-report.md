---
id: "BUG-524"
title: "The IDE-Starter integration tests are unverified on GoLand 2026.2, and pin a 253 ide-starter"
type: "bug"
parent_id: "BUG"
status: "todo"
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
- **Actual**: unknown. MAINT-21 measured only that the sources compile
  (`compileIntegrationTestKotlin`, MAINT-21 DR-16). Not run.

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
