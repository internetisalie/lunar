---
id: "REDIS-06-PLAN"
title: "Implementation Plan"
type: "plan"
status: "planned"
parent_id: "REDIS-06"
folders:
  - "[[features/redis/06-sandbox-quickdoc-refinements/requirements|requirements]]"
---

# REDIS-06: Implementation Plan

Two independent fixes; either phase can ship alone and leave the build green. Do Phase 1
first (it is the harder, TestLogger-sensitive one).

## Phases

### Phase 1: Sandbox shadowed-local exemption [Must]
- **Goal**: `LuaRedisSandboxInspection` no longer flags a name bound to a local, still flags a
  genuine global, using a side-effect-free scope walk (no VFS).
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.analysis.redis.LocalBindingScopeProcessor` — a
        `PsiScopeProcessor` matching only local declaration kinds — realizes design §2.1, §3.1.
  - [ ] Edit `LuaRedisSandboxInspection`: add private `bindsToLocal(ref, rootName): Boolean`
        helper calling `LuaResolveUtil.scopeCrawlUp` with the new processor — realizes design §2.2, §3.1.
  - [ ] Insert `if (bindsToLocal(o, rootName)) return` into `visitNameRef` after `rootNameOf`,
        before the platform guard — realizes design §3.1 integration.
- **Exit criteria**: TC 1, 2, 4 (shadowed local: no warning) and TC 3 (genuine global: still
  warned) pass; existing `LuaRedisSandboxInspectionTest` cases unchanged; full gce-builder suite
  green with 0 new `TestLoggerAssertionError` (risks §1.1).

### Phase 2: Quick-doc caret-on-STRING gate [Must]
- **Goal**: `RedisCommandDocumentationTargetProvider` returns a target only when the caret is on
  the command-name STRING literal.
- **Tasks**:
  - [ ] Edit `RedisCommandDocumentationTargetProvider.documentationTargets`: add
        `element.elementType == LuaTokenTypes.STRING` gate and the
        `site.nameLiteral?.string !== element` identity check — realizes design §2.3, §3.2.
- **Exit criteria**: TC 5 (caret on `"GET"` → one target) and TC 6, 7, 8 (caret elsewhere →
  empty) pass; existing `LuaRedisCommandDocumentationTest` cases unchanged.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| REDIS-06-01 | M | Phase 1 |
| REDIS-06-02 | M | Phase 2 |

## Verification Tasks
- [ ] Extend `LuaRedisSandboxInspectionTest` with the shadowed-local negative cases (TC 1, 2, 4)
      and confirm the existing `print` positive case (TC 3) still warns — covers TC 1–4.
- [ ] Extend `LuaRedisCommandDocumentationTest` with caret-elsewhere negative cases (TC 6, 7, 8)
      alongside the existing caret-on-`"GET"` positive case (TC 5) — covers TC 5–8.
- [ ] Run the FULL gce-builder suite (`run test`, not an isolated `--tests` pattern) to confirm
      no `TestLoggerAssertionError` — covers the REDIS-06-01 acceptance criterion and risks §1.1.
- [ ] Run `human-verification-checklists.md` in the sandbox IDE.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Sandbox shadowed-local exemption | planned | Must |
| Phase 2: Quick-doc caret-on-STRING gate | planned | Must |
