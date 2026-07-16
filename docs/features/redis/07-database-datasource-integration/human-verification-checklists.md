---
id: "REDIS-07-CHECKLIST"
title: "Verification Checklists"
type: "qa"
status: "planned"
parent_id: "REDIS-07"
folders:
  - "[[features/redis/07-database-datasource-integration/requirements|requirements]]"
---

# Verification Checklists: REDIS-07 — Reuse an IntelliJ Database Redis Data Source

Live verification runs in GoLand over VNC (`verify-in-ide` flow). The credential and endpoint reads
cannot be unit-tested, so this checklist is the real-flow DoD gate for Phases 0/3/4.

## Setup (once)
- [ ] In GoLand, open **Database** tool window ▸ **+** ▸ **Data Source ▸ Redis**; when prompted,
      **Download** the Redis driver.
- [ ] Create a standalone Redis data source: host `127.0.0.1`, port `6379`, db `0`, user + password,
      **Save: Forever**. Test the connection green.
- [ ] Create a second data source that is **not** Redis (e.g. PostgreSQL) for the filter check.

## Phase 0 — Spike gate (must pass before Phases 1–4)
- [ ] **DR-1**: from the spike action, `DatabaseCredentials.getInstance().getPassword(ds)` returns the
      saved password with **no modal**. Record the working receiver (`DbDataSource` vs `LocalDataSource`).
- [ ] **DR-1**: repeat with the data source's auth set to "Ask" — record the behaviour (prompt vs null).
- [ ] **DR-2**: `DbPsiFacade.getInstance(project).dataSources` includes the Redis data source;
      `delegate as? LocalDataSource` is non-null; `getDbms().name == "REDIS"`; `getUrl()` is a
      parseable `jdbc:redis://…`. Capture the exact `getUrl()` strings for standalone / TLS / cluster
      / auth data sources into the parser fixtures.
- [ ] **DR-3**: `runIde` loads `lunar-database.xml`; a plugin build/run with `com.intellij.database`
      disabled loads with **no** `NoClassDefFoundError` and no import button.
- [ ] **Go/No-Go verdict written** in [risks-and-gaps.md](risks-and-gaps.md).

## Phase 1 — Optional-module isolation (REDIS-07-01)
- [ ] In a **Community** IDE (or with the Database plugin disabled), the plugin loads cleanly and the
      Redis Connections settings page has **no** "Import from Database data source…" button (TC-10).
- [ ] `idea.log` shows no `NoClassDefFoundError`/`com.intellij.database` class-load error.

## Phase 3/4 — Import flow (REDIS-07-02…08)
- [ ] Open **Settings ▸ Languages & Frameworks ▸ Lua ▸ Redis Connections**. The
      **Import from Database data source…** button is present (paid IDE).
- [ ] Click it: the picker lists **only** the Redis data source, not the PostgreSQL one (TC-7).
- [ ] Import the Redis data source. A new native connection appears, selected, with the correct
      name/host/port/db/username; **Use TLS** reflects the data source's SSL state (TC-8, TC-4/TC-5).
- [ ] The password is present without re-typing (or, in degraded-GO, a "set the password" notice
      appears) — REDIS-07-04.
- [ ] Click **Test Connection** on the imported connection — it connects (TC-8).
- [ ] Run a **Redis Script** run config (REDIS-01) against the imported connection — it executes
      end to end (proves the snapshot is a fully-native connection).
- [ ] Import a **cluster** data source (`jdbc:redis:cluster://…`): an error dialog appears; **no**
      connection is created (TC-6).
- [ ] Import a data source with an **SSH tunnel** enabled: the SSH-not-carried notice is shown
      (REDIS-07-08).

## Regression
- [ ] Existing native connections and their run configs are unaffected; the settings page behaves as
      before when the import feature is not used.

## See Also
- Requirements: [requirements.md](requirements.md)
- Risks & gaps (spike gate): [risks-and-gaps.md](risks-and-gaps.md)
