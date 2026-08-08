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
- **Performance — completion latency is measured to FIRST result, not to the exhaustive set.**
  - **Time-to-first-result: under 100 ms**, independent of index size. This is the binding target and
    the one a user perceives. It must hold for the largest indexed content, not an average.
  - **Time-to-exhaustive-result: no fixed budget.** It scales with the number of **index entries
    traversed**, so a single figure cannot bound it. Required instead: enumeration is *incremental*
    (results are offered as they are found, never batched behind the full set), *cancellable*
    (`ProgressManager.checkCanceled()` in the traversal), and off the EDT. A slow exhaustive phase is
    acceptable; a slow *first* phase is not.
  - **Scope: index entries, not project lines.** The former wording — "for projects up to 50k lines"
    — bounded index size by a proxy that omits everything not in project files. A definition library
    or bundled stub contributes index entries while contributing zero project lines, which is how a
    530 KiB library root measured 25 352 ms while remaining technically in-spec (BUG-429, COMP-09).
    Library, stub and dependency content counts.
  - Measured evidence that entries rather than results dominate: against one 530 KiB root, a **narrow**
    prefix matching a handful of candidates cost 18 429 ms where a **broad** prefix cost 25 352 ms.
- **Dumb Mode Awareness:** Ensure basic keyword completion works even during project indexing.
- **Reference Resolution Caching:** Use `CachedValuesManager` to cache binding information and reference resolution results, automatically invalidating on PSI changes.

