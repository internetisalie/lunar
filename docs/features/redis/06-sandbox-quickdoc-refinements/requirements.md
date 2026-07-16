---
id: "REDIS-06"
title: "REDIS-06: Sandbox & Quick-Doc Gating Refinements"
type: "feature"
status: "planned"
priority: "low"
parent_id: "REDIS"
folders:
  - "[[features/redis/requirements|requirements]]"
---

# REDIS-06: Sandbox & Quick-Doc Gating Refinements

## Overview

Two deferred REDIS-04 correctness refinements (parent epic: [REDIS](../requirements.md)).
Both are gating fixes to existing, shipped REDIS-04 components — neither adds new user
surface. (1) `LuaRedisSandboxInspection` currently skips only *declaration positions*, not
full binding resolution, so a shadowing local (`local print = ...; print(x)`) is wrongly
flagged as a blocked sandbox global. (2) `RedisCommandDocumentationTargetProvider` returns a
command documentation target for any caret position inside a `redis.call(...)` expression,
not only when the caret is on the command-name STRING literal, so quick-doc over-triggers.

## Scope

### In Scope
- Add a side-effect-free (no VFS) local-binding resolution gate to `LuaRedisSandboxInspection`
  so a name that binds to a local variable / parameter / for-variable / local function in
  scope is never flagged, while a genuine blocked global still is.
- Add a caret-on-command-STRING gate to `RedisCommandDocumentationTargetProvider` so the
  documentation target is returned only when the caret element is the command-name string
  literal of the matched call site.
- Close the two "not TC-covered" gaps with real-flow tests for each fix (shadowed-local
  negative case + genuine-global positive case; caret-on-STRING positive + caret-elsewhere
  negative), plus a full-suite run to confirm no `TestLoggerAssertionError` regressions.

### Out of Scope
- Cross-file / stub-index / VFS resolution of the flagged name (that path is the source of
  the earlier `TestLoggerAssertionError` and is deliberately excluded — see design §3.1).
- Any change to the sandbox allowlist, the WARNING→ERROR severity escalation (RISK-R07), or
  the command-spec schema.
- Any change to quick-doc HTML content, presentation, or the spec lookup itself.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| REDIS-06-01 | **Shadowed-local sandbox exemption** | M | `LuaRedisSandboxInspection` must not flag a name that resolves to a local binding in scope (local var, parameter, numeric/generic for-variable, or local function), and must still flag a name that has no local binding (a genuine global). Resolution must be side-effect-free (no VFS / stub-index / module resolution). |
| REDIS-06-02 | **Quick-doc caret-on-STRING gate** | M | `RedisCommandDocumentationTargetProvider` must return a documentation target only when the caret element is the command-name STRING literal of the matched `redis.call`/`redis.pcall` site; a caret elsewhere in the call (receiver, member, other argument, punctuation) must return an empty list. |

## Detailed Specifications

### REDIS-06-01: Shadowed-local sandbox exemption
The inspection visits every root `LuaNameRef` (design REDIS-04 §3.7). Today it only skips
name-refs in *declaration* positions via `isDeclaration` (`LuaRedisSandboxInspection.kt:124`).
A *use* of a shadowing local — `local print = redis.log; print("x")` — is not a declaration
position, so `print` is still flagged, a false positive. The fix adds a resolution gate: if
the root name binds to a local declaration visible in scope, the ref is exempt (not a global,
so the sandbox rule does not apply). A name with no visible local binding is treated as a
global and evaluated against the allowlist as today.

The resolution must reuse the plugin's existing **Phase-1 local scope walk**
(`LuaScopeProcessor` + `PsiElement.processDeclarations`, as done in
`LuaNameReference.multiResolve` lines 40–78) and MUST NOT invoke
`LuaNameReference.resolve()`/`multiResolve()` in full, because that triggers Phase-2 external
resolution (VFS / `LuaTypeManagerImpl.resolveModule`) which raises `TestLoggerAssertionError`
in test fixtures (documented at `LuaRedisSandboxInspectionTest.kt:134-151`).

Interaction with existing checks: the resolution gate runs in addition to `isDeclaration`; a
declaration is still skipped first (cheap check before the scope walk).

### REDIS-06-02: Quick-doc caret-on-STRING gate
`documentationTargets(file, offset)` calls `file.findElementAt(offset)` then
`RedisCallSiteMatcher.match(element)`, which walks *up* to the enclosing `LuaFuncCall` from
**any** descendant element (`RedisCallSiteMatcher.kt:60-62`). So the caret on `redis`, on
`call`, on `KEYS[1]`, or on a comma still yields a match and a documentation target. The fix
requires the caret element to be the STRING token AND to be the command-name literal of the
matched site: `element.elementType == LuaTokenTypes.STRING` and the matched
`RedisCallSite.nameLiteral?.string === element`. When either condition fails, return an empty
list.

## Behavior Rules
- A local binding always wins over a global for the sandbox exemption (Lua scoping): if a
  local named `os` is in scope, `os.getenv(...)` is NOT flagged even though `os.getenv` would
  otherwise be blocked.
- The quick-doc gate compares the caret element by identity (`===`) to the matched site's
  command-name string element; it does not re-derive the offset.
- Both fixes are no-ops outside Redis/Valkey targets (existing platform guard, unchanged).

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | REDIS-06-01 | Redis 7+ target; `local print = redis.log\nprint("x")` | `enableInspections(LuaRedisSandboxInspection()) + doHighlighting(WARNING)` | No "Redis script sandbox" warning on the `print` use. |
| 2 | REDIS-06-01 | Redis 7+ target; `local io = {}\nio.read()` | same | No sandbox warning on `io` (shadowed by local table). |
| 3 | REDIS-06-01 | Redis 7+ target; `print("x")` (no shadowing local) | same | Sandbox warning `'print' is not available in the Redis script sandbox` is present (genuine global still flagged). |
| 4 | REDIS-06-01 | Redis 7+ target; `local function f(print) print("x") end` | same | No sandbox warning on the parameter use of `print`. |
| 5 | REDIS-06-02 | Redis 7+ target; `redis.call("GET<caret>", KEYS[1])` | `provider.documentationTargets(file, caretOffset)` | List of size 1 (target for GET). |
| 6 | REDIS-06-02 | Redis 7+ target; `redis.<caret>call("GET", KEYS[1])` (caret on member) | same | Empty list. |
| 7 | REDIS-06-02 | Redis 7+ target; `redis.call("GET", KEYS<caret>[1])` (caret in 2nd arg) | same | Empty list. |
| 8 | REDIS-06-02 | Redis 7+ target; `re<caret>dis.call("GET")` (caret on receiver) | same | Empty list. |

## Acceptance Criteria
- [ ] REDIS-06-01: TC 1, 2, 4 produce no sandbox warning; TC 3 still produces the warning.
- [ ] REDIS-06-01: the full gce-builder suite runs with 0 `TestLoggerAssertionError`s
      attributable to this change (the deferral reason).
- [ ] REDIS-06-02: TC 5 returns exactly one target; TC 6, 7, 8 return empty.
- [ ] No regression in the existing `LuaRedisSandboxInspectionTest` and
      `LuaRedisCommandDocumentationTest` cases.

## Non-Functional Requirements
- **Threading**: both fixes run inside the inspection / documentation-provider read context
  already established by the platform; all PSI access is transient (no retained
  `PsiElement`/`Project`/`Editor`). No EDT blocking, no I/O.
- **Side-effect freedom**: the sandbox resolution gate must not touch the VFS, stub index, or
  type engine (design §3.1) — a pure PSI scope walk only.
- **Method size**: new helpers stay ≤30 logic lines and ≤3 args (engineering contract §3).

## Dependencies
- [REDIS-04](../04-language-integration/requirements.md) — Done. Provides the two target
  classes and `RedisCallSiteMatcher`.
- `LuaScopeProcessor` / `PsiElement.processDeclarations` — the side-effect-free scope walk.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
- Checklists: [human-verification-checklists.md](human-verification-checklists.md)
