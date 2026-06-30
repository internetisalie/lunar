---
id: "NFR"
title: "Non-Functional Requirements"
type: "spec"
priority: "medium"
folders:
  - "[[features]]"
---

# Technical Non-Functional Requirements

- **Kotlin Idiomaticity:** 100% of new logic must be in Kotlin, leveraging coroutines for background indexing. Finish conversion of legacy Java code.
- **Performance:** Code completion should return results in under 100ms for projects up to 50k lines.
- **Dumb Mode Awareness:** Ensure basic keyword completion works even during project indexing.
- **Reference Resolution Caching:** Use `CachedValuesManager` to cache binding information and reference resolution results, automatically invalidating on PSI changes.

