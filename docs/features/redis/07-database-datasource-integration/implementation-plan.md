---
id: "REDIS-07-PLAN"
title: "Implementation Plan"
type: "plan"
status: "planned"
parent_id: "REDIS-07"
folders:
  - "[[features/redis/07-database-datasource-integration/requirements|requirements]]"
---

# REDIS-07: Implementation Plan

> **SPIKE-FIRST.** **Phase 0 is a gate.** Phases 1–4 are **conditional** on Phase 0's DR-1/DR-2
> returning **GO** (see [risks-and-gaps.md](risks-and-gaps.md) Go/No-Go). Do not scaffold the
> optional module until the spike confirms the credential/endpoint reads work. New code lives under
> `net.internetisalie.lunar.redis.database` (**[NEW]** package) and
> `net.internetisalie.lunar.redis.connection` (existing); tests under
> `src/test/kotlin/net/internetisalie/lunar/redis/database/` with the repo's `Test…` prefix
> convention (grounded: `src/test/kotlin/net/internetisalie/lunar/redis/`).

## Phases

### Phase 0: De-risking spike — GATE [Must]
- **Goal**: prove REDIS-07 is buildable at all: the Database Redis endpoint + password are readable,
  and the optional-module mechanics are clean when the plugin is absent.
- **Tasks**:
  - [ ] Run **REDIS-00-DR-01** (credential read, no modal) — risks-and-gaps.
  - [ ] Run **REDIS-00-DR-02** (endpoint extraction + capture real `getUrl()` fixtures) — risks-and-gaps.
  - [ ] Run **REDIS-00-DR-03** (optional-module builds/runs with the plugin present *and* absent).
  - [ ] Run **REDIS-00-DR-04** (record API-stability caveat, pin since-build).
- **Exit criteria**: a written Go/No-Go verdict (risks-and-gaps). **GO** ⇒ proceed. **Degraded GO**
  ⇒ proceed with REDIS-07-04 relaxed to Should (password re-prompt). **NO-GO** ⇒ set REDIS-07
  `cancelled`; stop.
- **Conditional gate**: **Phases 1–4 below start only on a GO / degraded-GO verdict.**

### Phase 1: Optional-module scaffolding + always-loaded seam [Must] — *conditional on Phase 0 GO*
- **Goal**: the module loads with the plugin, is invisible without it; no import logic yet.
- **Tasks**:
  - [ ] Add `com.intellij.database` to `platformBundledPlugins` in `gradle.properties` — realizes design §7.
  - [ ] Add `<depends optional="true" config-file="lunar-database.xml">com.intellij.database</depends>`
        to `plugin.xml` — realizes design §7 (pattern from `plugin.xml:29`).
  - [ ] Create `src/main/resources/META-INF/lunar-database.xml` (initially empty `<idea-plugin/>`,
        header comment like `lunar-terminal.xml`) — realizes design §7.
  - [ ] Create the always-loaded EP interface
        `net.internetisalie.lunar.redis.database.LuaRedisDataSourceImporter` (with `EP_NAME`) —
        realizes design §2.4, §7. **No `com.intellij.database` types in its signature.**
  - [ ] Register `<extensionPoint name="redisDataSourceImporter" ...>` in `plugin.xml` — design §7.
- **Exit criteria**: TC-10 — plugin builds and loads with the Database plugin **absent** (CI/Community
  profile); no `NoClassDefFoundError`; no import button. (DR-3 already proved this manually; this
  phase makes it permanent.)

### Phase 2: Pure URL parser [Must] — *conditional on Phase 0 GO*
- **Goal**: the unit-testable core, independent of the Database plugin.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.redis.database.LuaRedisJdbcUrlParser` + `LuaRedisJdbcParse`
        — realizes design §2.1, §4.1.
  - [ ] Unit test `TestLuaRedisJdbcUrlParser` with the DR-2-captured fixtures — covers TC-1…TC-6, TC-9.
- **Exit criteria**: TC-1 through TC-6 and TC-9 pass as JUnit tests (no Database plugin needed).

### Phase 3: Data-source reader + import action [Must] — *conditional on Phase 0 GO*
- **Goal**: the `com.intellij.database`-touching layer, all inside the optional module.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.redis.database.LuaRedisDataSourceReader`
        (`enumerateRedisDataSources`, `readEndpoint`) — realizes design §2.2, §3.1, §3.2, §3.3.
  - [ ] Create `LuaRedisDatabaseImporter` (implements the Phase-1 EP interface) and
        `LuaRedisDatabaseImportAction` (picker + off-EDT read + cluster reject + snapshot write) —
        realizes design §2.3, §3.4. Register the importer in `lunar-database.xml` — design §7.
  - [ ] Wire the off-EDT read via `LunarCoroutineScopeService` + `withBackgroundProgress`, marshalling
        back with `withContext(Dispatchers.EDT)` — realizes REDIS-07-07 (design §2.3).
- **Exit criteria**: DR-verified live in GoLand — TC-7 (Redis-only picker), TC-8 (snapshot present in
  `LuaRedisConnectionSettings` + password in `LuaRedisCredentialStore`), TC-6 (cluster rejected).

### Phase 4: Settings-page button + notices [Must/Should] — *conditional on Phase 0 GO*
- **Goal**: surface the action; SSH/TLS limitation notices.
- **Tasks**:
  - [ ] Extend `net.internetisalie.lunar.redis.connection.LuaRedisConnectionsConfigurable`: add the
        "Import from Database data source…" button, gated on
        `LuaRedisDataSourceImporter.EP_NAME.extensionList.firstOrNull()` being present — realizes
        design §2.4. Select the imported row on success.
  - [ ] SSH-tunnel-not-carried notice (REDIS-07-08) and TLS custom-CA limitation note in the
        confirmation — realizes design §3.4 step 3.
- **Exit criteria**: VNC — the button appears in a paid IDE, is absent in a Community IDE; SSH notice
  shown for a tunnelled data source; imported connection runs a Redis Script (REDIS-01) end to end.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| REDIS-07-01 | M | Phase 1 |
| REDIS-07-02 | M | Phase 3 (§3.1) |
| REDIS-07-03 | M | Phase 2 (parser) + Phase 3 (§3.2) |
| REDIS-07-04 | M | Phase 0 (DR-1 gate) + Phase 3 (§3.3) |
| REDIS-07-05 | M | Phase 3 (§3.4) |
| REDIS-07-06 | M | Phase 2 (classify) + Phase 3 (reject) |
| REDIS-07-07 | M | Phase 3 |
| REDIS-07-08 | S | Phase 4 |

## Verification Tasks
- [ ] `TestLuaRedisJdbcUrlParser` — covers TC-1…TC-6, TC-9 (Phase 2).
- [ ] Build/run with `com.intellij.database` absent — covers TC-10 (Phase 1).
- [ ] Live VNC import against a real Redis data source — covers TC-7, TC-8 and the credential path
      (Phase 3/4); driven via the `verify-in-ide` flow.
- [ ] Run [human-verification-checklists.md](human-verification-checklists.md).

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 0: De-risking spike (GATE) | todo | Must |
| Phase 1: Optional-module scaffolding + seam | todo | Must |
| Phase 2: Pure URL parser | todo | Must |
| Phase 3: Data-source reader + import action | todo | Must |
| Phase 4: Settings button + notices | todo | Should |
