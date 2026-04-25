# Technical Non-Functional Requirements

- **Kotlin Idiomaticity:** 100% of new logic must be in Kotlin, leveraging coroutines for background indexing.
- **Performance:** Code completion should return results in under 100ms for projects up to 50k lines.
- **Dumb Mode Awareness:** Ensure basic keyword completion works even during project indexing.
