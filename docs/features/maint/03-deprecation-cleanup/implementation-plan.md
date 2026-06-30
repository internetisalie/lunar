---
id: MAINT-03-PLAN
title: Deprecation Cleanup Plan
type: plan
parent_id: MAINT-03
---

# Implementation Plan

## Phase 1: Migrate [Must]
- **Tasks**: Replace deprecated API calls.
- **Verification**: Clean build with no warnings.

## Phase 2: Modernize Gradle Plugins [Should]
- **Tasks**: Update `org.jetbrains.intellij.platform` to `2.17.0` in `gradle/libs.versions.toml`.
- **Verification**: Verify that the plugin initialization warning goes away and build successfully runs via the remote builder `tooling/gce-builder/gce-builder.sh run build`.
