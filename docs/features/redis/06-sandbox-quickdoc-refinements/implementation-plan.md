---
id: "REDIS-06-PLAN"
title: "Implementation Plan"
type: "plan"
status: "done"
parent_id: "REDIS-06"
folders:
  - "[[features/redis/06-sandbox-quickdoc-refinements/requirements|requirements]]"
---

# REDIS-06: Implementation Plan

Two independent fixes; either phase can ship alone and leave the build green. Do Phase 1
first (it is the harder, TestLogger-sensitive one).

## Phases

### Phase 1: Sandbox shadowed-local exemption [Must] — **DONE**
- **Goal**: `LuaRedisSandboxInspection` no longer flags a name bound to a local, still flags a
  genuine global, using a side-effect-free scope walk (no VFS).
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.analysis.redis.LocalBindingScopeProcessor` — a
        `PsiScopeProcessor` matching only local declaration kinds — realizes design §2.1, §3.1.
  - [x] Edit `LuaRedisSandboxInspection`: add private `bindsToLocal(ref, rootName): Boolean`
        helper calling `LuaResolveUtil.scopeCrawlUp` with the new processor — realizes design §2.2, §3.1.
  - [x] Insert `if (bindsToLocal(o, rootName)) return` into `visitNameRef` after `rootNameOf`,
        before the platform guard — realizes design §3.1 integration.
- **Verified**: full GCE-builder suite green (2001 tests, 0 failures) with 0 `TestLoggerAssertionError`
  / 0 `resolveModule` (DR-01, risk §1.1); new TC 1/2/3/4/4a/4b cases pass in
  `LuaRedisSandboxInspectionTest`.
- **Exit criteria**: TC 1, 2, 4 (shadowed local: no warning) and TC 3 (genuine global: still
  warned) pass; existing `LuaRedisSandboxInspectionTest` cases unchanged; full gce-builder suite
  green with 0 new `TestLoggerAssertionError` (risks §1.1).

### Phase 2: Quick-doc caret-on-STRING gate [Must] — **DONE**
- **Goal**: `RedisCommandDocumentationTargetProvider` returns a target only when the caret is on
  the command-name STRING literal.
- **Tasks**:
  - [x] Edit `RedisCommandDocumentationTargetProvider.documentationTargets`: add
        `element.elementType == LuaElementTypes.STRING` gate and the
        `site.nameLiteral?.string !== element` identity check — realizes design §2.3, §3.2.
- **Exit criteria**: TC 5 (caret on `"GET"` → one target) and TC 6, 7, 8 (caret elsewhere →
  empty) pass; existing `LuaRedisCommandDocumentationTest` cases unchanged.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| REDIS-06-01 | M | Phase 1 |
| REDIS-06-02 | M | Phase 2 |

## Verification Tasks
- [x] Extend `LuaRedisSandboxInspectionTest` with the shadowed-local negative cases (TC 1, 2, 4)
      and confirm the existing `print` positive case (TC 3) still warns — covers TC 1–4.
      (Also added TC 4a self-shadow + TC 4b numeric/generic-for; commit e01ae17b.)
- [x] Extend `LuaRedisCommandDocumentationTest` with caret-elsewhere negative cases (TC 6, 7, 8)
      alongside the existing caret-on-`"GET"` positive case (TC 5) — covers TC 5–8.
- [x] Run the FULL gce-builder suite (`run test`, not an isolated `--tests` pattern) to confirm
      no `TestLoggerAssertionError` — covers the REDIS-06-01 acceptance criterion and risks §1.1.
      (GCE full suite = 2001 tests / 0 failures; 0 `TestLoggerAssertionError` / `resolveModule`.)
- [ ] Run `human-verification-checklists.md` in the sandbox IDE.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Sandbox shadowed-local exemption | done | Must |
| Phase 2: Quick-doc caret-on-STRING gate | done | Must |
