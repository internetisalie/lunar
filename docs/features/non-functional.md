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
- **Performance — completion has two latencies, and the exhaustive one is bounded by WORK, not time.**
  - **Time-to-first-result: under 100 ms**, independent of index size *and* of how many candidates
    match. This is the binding latency target and the one a user perceives. It must hold for the
    largest indexed content, not an average.
  - **Exhaustive work: proportional to entries matching the receiver, not to index size.** A
    traversal that visits unrelated entries to find related ones fails this even when it is fast on
    a small project. Verified by *instrumenting entries traversed*, not by a clock — which makes it
    machine-independent and the strongest of the three checks. Today's code violates it directly:
    `getAllKeys` visits every key in the project (~10 000 on a wxLua tree) to find one receiver's
    (BUG-429, COMP-09).
  - **Exhaustive latency: no fixed figure, deliberately.** It scales with matching entries, so no
    single number bounds it across machines and project sizes. A slow exhaustive phase is acceptable;
    a slow *first* phase, or a phase whose work grows with unrelated content, is not.
  - **Per-keystroke amplification.** Typing `wx.wxF` cancels and restarts enumeration five times.
    Enumeration must therefore be *cancellable* (`ProgressManager.checkCanceled()` inside the
    traversal, not merely between phases), *incremental* (results offered as found, never batched
    behind the complete set), and off the EDT. Cancellation makes repeated traversal survivable, not
    free — which is why the work bound above matters more than the latency one.
  - **Consumers that cannot be incremental set the real exhaustive cost.** The checker
    (`LuaTypeAssignability`, `LuaReturnTypeMismatch`), the corpus sweep and documentation rendering
    all need the *complete* member set before they can produce anything. For them exhaustive time
    **is** the user-facing latency, so "no latency budget" is not permission to be slow — it is a
    statement that the work bound, not a stopwatch, is what governs.
  - **Scope: index entries, not project lines.** The former wording — "for projects up to 50k lines"
    — bounded index size by a proxy that omits everything not in project files. A definition library
    or bundled stub contributes index entries while contributing zero project lines, while measuring
    tens of seconds to first completion and remaining technically in-spec. Library, stub and
    dependency content counts.
  - ⚠ The narrow-vs-broad prefix pair once cited here (18 429 ms vs 25 352 ms) is **WITHDRAWN**:
    single-shot, time-to-*exhaustive* rather than time-to-first, and a 38 % gap inside that harness
    family's demonstrated ±60 % run-to-run spread. The surviving evidence that **entries** dominate is
    COMP-09 design §1.9 (a 3-member receiver 41 ms vs a 3 600-member receiver 1 641 ms, both cold, in
    separate files) and §4.10b's entry counts (50 traversed for a 50-member receiver, unchanged by
    4 000 unrelated keys).
  - **Two tiers, because one flat budget is not achievable and pretending otherwise selects a
    flattering fixture.** The < 100 ms time-to-first target applies to a receiver whose members are
    **syntactically declared** — every generated definition library. A receiver bound through
    `require`, a call, or any expression an index cannot see through resolves through the type graph
    and is **not** covered by it; its cost is the declaring file's graph build. See COMP-09 design
    §4.12. A gate must assert tier 1 and *record* tier 2, never quietly test only the first.
  - **Targets stated here must be enforced by a test that fails without them.** A documented target
    with no gate behind it is how a 129× miss went unnoticed until a feature tripped over it; the
    performance suite's assertions were `assertTrue(elapsed > 0)` and it is excluded from the routine
    loop.
- **Dumb Mode Awareness:** Ensure basic keyword completion works even during project indexing.
- **Reference Resolution Caching:** Use `CachedValuesManager` to cache binding information and reference resolution results, automatically invalidating on PSI changes.

