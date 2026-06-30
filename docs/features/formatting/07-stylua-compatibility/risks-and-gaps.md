---
id: "FORMAT-07-RISKS"
title: "Risks & Gaps — Stylua Compatibility"
type: "risk"
parent_id: "FORMAT-07"
folders:
  - "[[features/formatting/07-stylua-compatibility/requirements|requirements]]"
---

# FORMAT-07: Risks & Gaps

## Critical Risks

### Risk 1.1: Stylua binary unavailable in test CI
- **Impact**: Tests that depend on a real Stylua binary cannot run in CI environments
  that lack it.
- **Likelihood**: high (CI machines don't have Stylua pre-installed)
- **Mitigation**: Tests use a mock shell script (e.g., a temp-dir `echo`-based script)
  registered via `LuaToolManager` that simulates Stylua's I/O behavior (exit 0 with
  normalized text, exit 1 with a stderr message). No real Stylua binary is required
  in CI. A separate integration test (run manually or in the docker sandbox) validates
  with a real Stylua installation.

### Risk 1.2: Stylua version incompatibility
- **Impact**: Very old Stylua versions (<0.10.0) do not support `--stdin-filepath`.
- **Likelihood**: low (Stylua 0.10.0 was released in 2021)
- **Mitigation**: `LuaToolValidator` already extracts the Stylua version at registration
  time. If a version check is needed in the future, it can be added to `canFormat()`.
  For now, document the minimum version requirement (≥0.10.0) and let the error
  handling path catch CLI failures if an older version is used.

### Risk 1.3: Stylua deadlocks on large files
- **Impact**: The 30s timeout catches this, but the IDE appears frozen to the user during
  those 30s if the formatting was triggered from the EDT-side UI.
- **Likelihood**: low (Stylua is a fast Rust binary; sub-second for typical files)
- **Mitigation**: The platform's `AsyncDocumentFormattingSupport` handles the timeout
  and shows a notification. The 30s default is appropriate. If users report timeouts
  in practice, we can expose a configurable timeout in `LuaApplicationSettings`.

## Design Gaps

### Gap 2.1: Stylua config file discovery edge cases
- **Question**: Does Stylua's config discovery work correctly when the file being
  formatted is inside a symlinked directory or a non-standard VFS layout?
- **Options / leaning**: Rely on Stylua's own discovery algorithm (it walks up from the
  file's directory). We pass the correct working directory and filename; if Stylua
  fails to find the config, it uses defaults, which is acceptable behavior.
- **Resolved by**: Manual verification with symlinked project directories during the
  human-verification-checklist phase. No code changes anticipated.

### Gap 2.2: Stylua configuration preview / conflict detection
- **Question**: Should Lunar detect when Stylua config conflicts with
  `LuaCodeStyleSettings` and warn the user?
- **Options / leaning**: Deferred — this is a future UX improvement. The current design
  treats Stylua as an alternative formatter; users who choose it accept its config.
  A future feature could parse `.stylua.toml` and map settings to code-style preview.
- **Resolved by**: Explicitly out of scope (see requirements §Out of Scope).

## Technical Debt & Future Work
- **TBD: Parse `.stylua.toml` and populate a settings preview** — UI polish deferred
  to a future feature.
- **TBD: Expose Stylua timeout as a user-configurable setting** — only needed if
  real-world usage shows timeouts.
- **TBD: Support range formatting** — Stylua does not support partial-file formatting,
  so this is blocked upstream.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| FORMAT-07-DR-01 | Verify mock-stylua test approach works with `AsyncDocumentFormattingService` in a light fixture test | Risk 1.1 | todo |
| FORMAT-07-DR-02 | Test `--stdin-filepath` behavior with a real Stylua binary (≥0.10.0) on a sample project with `.stylua.toml` | Risk 1.2, Gap 2.1 | todo |

## Test Case Gaps
- **Stylua with non-ASCII characters in path/filename**: Not tested explicitly; UTF-8
  encoding is assumed throughout. If platform bug reports surface encoding issues,
  add a test case.
- **Stylua with non-UTF-8 encoding of the Lua file**: Lua source files are UTF-8 by
  standard. Not tested.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
