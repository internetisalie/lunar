---
id: "TARGET-07-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "TARGET-07"
folders:
  - "[[features/target/07-lua-5.5-stdlib-stubs/requirements|requirements]]"
---

# TARGET-07: Risks & Gaps

## Critical Risks

### Risk 1.1: Resolver silently not picking up the new dir
- **Impact**: 5.5 target still gets no stdlib completion despite the files existing.
- **Likelihood**: low. `PlatformVersionRegistry.kt:21` already registers `("5.5","lua-5.5")`,
  `Target.kt:44/67` maps it to `LUA55` and `runtime/standard/lua-5.5`, and
  `RuntimeLibraryProvider` resolves by path with no per-version switch. The mechanism is
  identical to Valkey, which is proven by `ValkeyStubResourceTest`.
- **Mitigation**: Phase 3's `testTableCreateResolvesUnder55Target` is the direct guard; if it
  fails, the resolver path — not the stubs — is at fault and the gap escalates to Gap 2.1.

### Risk 1.2: Header/prose corruption from a broad find-replace
- **Impact**: MIT copyright range (`1994–2025`) or doc prose altered, breaking attribution
  fidelity.
- **Likelihood**: low if design §3.1 is followed (only two `builtin.lua` lines edited).
- **Mitigation**: `git diff` the 5.5 dir against a fresh copy of 5.4; only `builtin.lua` and
  `table.lua` may differ.

## Design Gaps

### Gap 2.1: Is any resolver wiring actually required for `LUA55`?
- **Question**: Could SYNTAX-09 have wired 5.5 to *no* stubs in a way that shadows the new dir?
- **Options / leaning**: Investigation (design §1) found the path is fully additive — no code
  path special-cases 5.5 to skip stubs; the pre-SYNTAX-09 `†` note in the epic table
  (`runtime/standard/lua-5.5/`, "maps to `LUA54` until `LUA55` added") is now stale but the
  registry/Target already point at `lua-5.5`/`LUA55`. **Leaning: no wiring needed.**
- **Resolved by**: DR-01 (confirm with the live resolution test before setting anything to
  `done`). Fold the confirmed answer into design §1 if it deviates.

## Technical Debt & Future Work
- **TBD: `luacheckStd = "lua55"`** — the 5.5 registry entry maps to `lua54` because upstream
  luacheck has no `lua55` std set. Revisit when luacheck ships one; changing it now would break
  linting on 5.5 projects. Out of scope for TARGET-07.
- **TBD: 5.5-specific type refinements** — `table.create`'s stub is a plain
  `fun(nseq, nrec?): table`; a richer generic return type is not modeled (parity with the
  existing untyped-table stubs). Deferred.
- **Roadmap placement**: `docs/roadmap.md` still lists TARGET-07 as `todo`. This planning pass
  does not edit the roadmap; the parent/supervisor flips it `todo`→`planned`. Noted so it is
  not mistaken for a defect.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| DR-01 | Land the 5.5 stub dir and run `testTableCreateResolvesUnder55Target` / `...DoesNotResolveUnder54Target` to confirm additive resolution + gating with zero Kotlin change. | Risk 1.1, Gap 2.1 | todo |
| DR-02 | **Verify delta completeness against the upstream source** (review caught this: the "`table.create` is the *only* new 5.5 stdlib symbol + `_VERSION` bump" claim rests on model knowledge — no Lua 5.5 manual exists in the local reference repos). Diff the 5.4→5.5 stdlib against the published Lua 5.5 `manual.html` "Changes" section (or the PUC-Rio release notes) and confirm no additional new/changed/removed stdlib symbol was missed. If a symbol is found, add it to the stub set before landing. | Delta-completeness gap (review Nit A) | todo |

## Test Case Gaps
- No test asserts that **every** 5.4 symbol (not just `table.insert`) is present in 5.5.
  TARGET-07-08 is covered structurally by the name-set parity test (TC 4) plus one representative
  member resolution (TC 3); a per-symbol sweep is deemed overkill for a verbatim copy. If the
  copy is ever hand-authored instead of `cp -r`, add a stronger parity assertion.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
