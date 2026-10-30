---
id: "MAINT-47"
title: "47: Other IDE Families"
type: "feature"
parent_id: "MAINT"
status: "todo"
priority: "medium"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-47: Other IDE Families

## The gap

Lunar is described as a plugin for the IntelliJ Platform: GoLand, IntelliJ IDEA, PyCharm, CLion
and others. Only GoLand is checked. `verifyPlugin` runs against GoLand 2026.1.3 and 2026.2.3
(`build.gradle.kts`, `pluginVerification.ides`), unit tests run on GoLand (`platformType = GO`),
and the IDE Starter integration tests launch GoLand 2026.2.3. Nothing shows that Lunar loads or
works in any other IDE family.

`plugin.xml` declares two non-optional module dependencies besides the platform:
`com.intellij.modules.coverage` and `com.intellij.modules.json`. An IDE that ships without either
module cannot load Lunar at all.

## Scope

- **Decide which families are supported.** At least IntelliJ IDEA (Community and Ultimate),
  PyCharm, CLion, RustRover and WebStorm are candidates. Record the decision, and make the
  project description and the marketplace description match it.
- **Verify every supported family.** Add each one to `pluginVerification.ides` at the
  `pluginSinceBuild` floor and the current line, so `verifyPlugin` reports missing dependencies
  and binary incompatibilities in each.
- **The hard dependencies.** Establish which candidate IDEs lack the coverage or JSON module. For
  each one that does, either make the dependency optional (moving the features that need it into
  an optional config file), or leave that family unsupported.
- **A live check per supported family**: Lunar loads, a `.lua` file gets Lunar highlighting and
  analysis, and run/debug configurations appear. This is a one-time check per family, not a
  standing CI job.

## Not established

- Which IDEs lack `com.intellij.modules.coverage` or `com.intellij.modules.json`.
- Whether GoLand-specific APIs are used anywhere outside the platform modules.
- The cost in CI time and disk of verifying several more IDEs. Each one downloads a full IDE
  distribution.
- Whether IDE Starter integration tests should run in more than one family.
