---
id: "MAINT-31-RISKS"
title: "Design Gaps & De-risking"
type: "risk"
parent_id: "MAINT-31"
folders:
  - "[[features/maint/31-dead-code-sweep/requirements|requirements]]"
---

# MAINT-31: Dead-Code Sweep — Skipped Items & Deviations

Per the design's verification protocol, every review-§3 item was re-verified by repo-wide
reference search at implementation time (2026-07-17). Outcome record:

## Skipped / deferred (not deleted here)

| Item | Disposition |
|---|---|
| Settings-event API (`setProjectToolBindingAndNotify`, `setGlobalBinding`, `LuaSettingsChangeListener`) | **Deferred to TOOLING-08** (design scope decision — it wires-or-deletes the mechanism). `setTargetAndNotify` has live callers (`LuaTargetSynchronizer.kt:91`, `LuaProjectConfigurable.kt:178`). |
| `DebugCommandKind.EXIT` / `controller.terminate()` | **Deferred to MAINT-24** (wire a graceful exit from `stop()` vs delete — a debugger-hardening decision). |
| `lua.flex:160` unreachable `\\'` rule; `luacats.flex` `TAG_OVERLOAD` state + `COMMENT_END` | **Deferred to MAINT-27** — `.flex` edits require parser/lexer regeneration (local generator jars), out of place in a pure-deletion chore. |

## Moot (already gone before this sweep)

| Item | Evidence |
|---|---|
| Dead `catch (InterruptedException)` around `accept()` in `LuaDebuggerController` | Removed by MAINT-22's coroutine rewrite — zero hits today. |
| `command/LuaCommandLine.kt` adjacencies | Review #65's file was already deleted; Phase 1 removed the surviving `LuaRunProfile` remnants only. |

## Deviations from the raw §3 list

- **`WITH`/`CONTINUE` deletion also removed their two `LuaSyntax.KeywordTokens` entries** —
  the only references outside the declarations. Unreachable by construction (the lexer never
  emits either token, so the highlight-set membership can never match); verified no `.flex`/
  `.bnf`/`src/main/gen` references.
- **`LuaAutoImportInsertHandler` KDoc** updated where it referenced the removed
  `isImported` field.
- **Snapshot entry-point consolidation** kept `LuaTypesSnapshot.forFile` and removed
  `LuaTypesVisitor.getTypes`, redirecting all callers mechanically (hint providers, annotator,
  completion, tests).

## Verification

Per-phase compile gates green on gce-builder; final gate = full suite with
`--rerun-tasks --no-build-cache` (the remote build cache is known to mask test breakage on
deletion-heavy changes) + `ktlintCheck` no-new-violations. Results recorded in the phase
commits.
