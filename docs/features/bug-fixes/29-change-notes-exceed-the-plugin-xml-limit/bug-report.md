---
id: "BUG-29"
title: "A release's change notes exceed plugin.xml's 65535-character limit, so the Plugin Verifier rejects the plugin"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-29: A release's change notes exceed plugin.xml's 65535-character limit

## Reproduction

On `c793e7d9`, `./gradlew verifyPlugin -PpluginVersion=0.21.99` fails:

> Invalid plugin descriptor 'plugin.xml'. The value of the '<change-notes>' parameter is too long.
> Its length is 67168 which is more than maximum 65535 characters long.

## Expected vs Actual

- **Expected:** a tag build verifies and publishes.
- **Actual:** `verifyPlugin` reports the plugin invalid, so the tag build fails and nothing is
  published. The routine lane runs `buildPlugin` but not `verifyPlugin`, so only a tag build sees it.

## Root Cause

`build.gradle.kts` sets `patchPluginXml`'s `changeNotes` to the HTML of the whole CHANGELOG
milestone block: a tag version has no `## [x.y.z]` section, so the lookup falls back to
`getLatest()`, the `## [0.21]` block. The changelog is per-milestone, and every patch adds its
entries to that block, so it grows without bound. Its markdown was 52432 characters at v0.21.16,
which passed, and 57632 at v0.21.18, which renders to 67168.

## Fix Strategy

`fitChangeNotes` in `build.gradle.kts` keeps the rendered notes whole when they fit under 65000
characters. Otherwise it keeps the newest top-level entries that fit, closes the list, and appends
a link to the full `CHANGELOG.md`.

## Test Strategy

`verifyPlugin` on the builder at this commit, against both pinned IDEs, with a version that falls
back to the `## [0.21]` block: it fails before the fix with the message above and passes after.
