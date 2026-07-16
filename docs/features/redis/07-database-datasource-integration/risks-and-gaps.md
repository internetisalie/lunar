---
id: "REDIS-07-RISKS"
title: "Risks & Gaps"
type: "risk"
status: "planned"
parent_id: "REDIS-07"
folders:
  - "[[features/redis/07-database-datasource-integration/requirements|requirements]]"
---

# REDIS-07: Risks & Gaps

> **This is the most important artifact for REDIS-07 and should be read first.** The feature's
> core viability is genuinely uncertain: it depends on reading a Redis data source's endpoint
> **and stored password** out of the **closed-source** `com.intellij.database` plugin. The
> **de-risking spike (Phase 0) is the gate** — [design.md](design.md) and
> [implementation-plan.md](implementation-plan.md) Phases 1+ are **conditional on DR-1 and DR-2
> passing**. Do not begin optional-module scaffolding until the spike returns go.

## Go / No-Go criteria (evaluated at end of Phase 0)

- **GO (full feature as designed):** DR-1 shows the saved password is readable without a modal
  prompt (or with an acceptable one-time consent), **and** DR-2 shows host/port/db/user/TLS are
  extractable from `LocalDataSource` + `getUrl()`.
- **GO (degraded):** DR-2 passes but DR-1 fails (password not silently readable). Ship import with
  a **re-prompt for password** in the native connection; drop REDIS-07-04's "no modal" guarantee to
  a Should. The endpoint-reuse value (host/port/db/TLS/SSH awareness) still lands.
- **NO-GO (shelve):** DR-2 fails — the endpoint is not extractable in a stable, parseable form.
  Without the endpoint there is nothing to import. Record findings, set REDIS-07 `cancelled`, and
  leave native connections as the only path.

## Critical Risks

### Risk 1.1: Credential read requires a modal or a live connection (the primary unknown)
- **Impact**: if the password can only be obtained by initiating a JDBC connection (which prompts /
  downloads the Redis driver), the "borrow the connection silently" value proposition collapses to
  "re-type your password".
- **Likelihood**: medium. `DatabaseCredentials.getInstance().getPassword(dataSource): OneTimeString`
  exists (grounded via `javap` on the bundled jar) and reads PasswordSafe directly, but its
  behaviour when the user chose "Save: Forever" vs. session-only vs. "Ask" is unverified against the
  live plugin.
- **Mitigation**: **DR-1** below. Fallback is the degraded-GO path (import with null password +
  notice, §3.3 step 3 in design).

### Risk 1.2: Closed-source API drift across platform releases (DR-4)
- **Impact**: `com.intellij.database` is not in the local `intellij-community` checkout; there is no
  source and the API is lightly documented. A signature used here (`getUrl`, `getSslCfg`,
  `getDbms`, `DatabaseCredentials.getPassword`) could change in a future GoLand/IDEA build, breaking
  compilation or behaviour silently.
- **Likelihood**: medium over time.
- **Mitigation**: isolate all DB refs in the optional module (a compile break there does not affect
  the always-loaded plugin), pin the tested `platformVersion`, and cover the endpoint-parse logic
  (the part *not* dependent on the API) with pure unit tests. **DR-4** records the caveat and
  minimum since-build.

### Risk 1.3: Optional module leaks a hard reference into always-loaded code
- **Impact**: a `com.intellij.database` class referenced from `plugin.xml`-registered (always-loaded)
  code ⇒ `NoClassDefFoundError` on Community/CE IDEs and in CI (`build-plugin.yml`), which run
  **without** the Database plugin. This would break the whole plugin, not just this feature.
- **Likelihood**: medium (easy to slip — e.g. importing a DB type into `LuaRedisConnectionsConfigurable`).
- **Mitigation**: the always-loaded seam is an EP **interface** (`LuaRedisDataSourceImporter`) whose
  signature contains **no** `com.intellij.database` types (design §7). **DR-3** proves the build/run
  is clean with the plugin absent, and it is the exit gate for Phase 1.

### Risk 1.4: `DbDataSource.delegate` is not a `LocalDataSource`
- **Impact**: §3.1 step 2 (`ds.delegate as? LocalDataSource`) yields null ⇒ Redis data sources are
  invisible to the picker.
- **Likelihood**: low (Redis data sources are local), but unverified live.
- **Mitigation**: **DR-2** confirms the cast; fallback path (`LocalDataSourceManager` +
  `getUniqueId()` match) noted in design §3.1.

### Risk 1.5: TLS-trust / SSH not carried degrades to a broken connection
- **Impact**: importing `tls=true` from a data source that relied on a custom CA or SSH tunnel yields
  a native connection that cannot actually connect (Lunar's `RespClient` has system-default trust
  only, `RespClient.kt:152`, and no SSH).
- **Likelihood**: medium for enterprise data sources.
- **Mitigation**: REDIS-07-08 surfaces the SSH-not-carried notice; the TLS custom-CA limitation is
  documented at import. This is a *known* gap of Lunar's transport that REDIS-07 works *around*, not
  a defect introduced here — cross-referenced, not fixed.

## Design Gaps

### Gap 2.1: `LocalDataSource` as a `DasDataSource` receiver for `getPassword`
- **Question**: `DatabaseCredentials.getPassword(DasDataSource)` — is a `LocalDataSource` a valid
  argument, or must the `DbDataSource` (which `extends DasDataSource`) be passed instead?
- **Options / leaning**: pass the `DbDataSource` (guaranteed `DasDataSource`) rather than the
  `LocalDataSource`; keep both handles in `LuaRedisDataSourceRef` if needed.
- **Resolved by**: **DR-1** (fold the confirmed receiver back into design §3.3).

### Gap 2.2: Redis driver-not-downloaded state
- **Question**: the Redis **driver** is download-on-demand; does a Redis data source *exist* in
  `DbPsiFacade.dataSources` before the driver is downloaded, and does `getDbms().name == "REDIS"`
  hold then?
- **Options / leaning**: likely yes (the data source's dbms is fixed at creation, independent of
  driver bits), but unverified.
- **Resolved by**: **DR-2** (if the data source only materializes post-download, document that the
  user must download the Redis driver once before import).

## Technical Debt & Future Work
- **TBD: live data-source reference (design §9 alt B).** A `LuaRedisProvisioning.DatabaseDataSource`
  variant that re-reads the data source per run (tracks edits, no snapshot drift). Deferred: it
  re-triggers the DR-1 credential read on run/debug background threads and couples REDIS-01/02 to the
  paid-IDE boundary. Revisit only if users ask for auto-sync.
- **TBD: import custom TLS trust / SSH tunnel into Lunar's transport.** Would require extending
  `RespClient` (custom `SSLContext`, an SSH tunnel). Large; out of scope. REDIS-07 is the *workaround*
  for the missing feature, not its delivery.
- **TBD: cluster support.** Blocked on `RespClient` single-node design (epic non-goal).

## Roadmap placement (note — do not edit roadmap here)
REDIS-07 is **not** in `docs/roadmap.md` yet. It is post-MVP and spike-gated; a likely slot is
Wave 19-adjacent, **after** the deferred REDIS-06 ("Redis sandbox + quick-doc gating refinements").
Placement and wave assignment are the roadmap owner's call — this note is a pointer, not an edit.
The parent epic `docs/features/redis/requirements.md` is currently `status: done`; adding REDIS-07
as a `Could`/`planned` row re-opens a tail of optional work under REDIS without changing the shipped
01–05 scope.

## Pre-Implementation De-risking Tasks

**Phase 0 is these DR tasks. They gate everything.** Run DR-1/DR-2 live in GoLand (VNC) against a
real Redis data source (create one, download the Redis driver, save a password) — the
`verify-in-ide` flow. Record outcomes inline before advancing.

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| REDIS-00-DR-01 | In a scratch/spike branch, from a light action, call `DatabaseCredentials.getInstance().getPassword(ds)` on a saved-password Redis data source in GoLand; confirm it returns the secret with **no modal**. Test "Save: Forever" and "Ask" modes. Record which receiver (`DbDataSource` vs `LocalDataSource`) works. | Risk 1.1, Gap 2.1 | todo |
| REDIS-00-DR-02 | For the same data source, confirm `DbPsiFacade.getInstance(project).dataSources` contains it, `delegate as? LocalDataSource` is non-null, `getDbms().name == "REDIS"`, and `getUrl()` returns a parseable `jdbc:redis://…`. Verify pre- and post-driver-download (Gap 2.2). Capture 3–4 real `getUrl()` strings (standalone / TLS / cluster / auth) to seed `LuaRedisJdbcUrlParser` fixtures. | Risk 1.4, Gap 2.2, DR-2 | todo |
| REDIS-00-DR-03 | Add `com.intellij.database` to `platformBundledPlugins`, wire `<depends optional=... config-file="lunar-database.xml">`, and confirm: (a) `runIde` in GoLand loads the module; (b) a build/run with the plugin **absent** (simulate the CI/Community profile) loads with **no** `NoClassDefFoundError` and no import button. | Risk 1.3, REDIS-07-01 | todo |
| REDIS-00-DR-04 | Record the closed-source API stability caveat: pin the tested `platformVersion`/since-build; note that `getUrl`/`getSslCfg`/`getDbms`/`DatabaseCredentials.getPassword` are grounded only to the bundled `database-plugin` jars (no platform source), and flag them for re-verification on each platform bump. | Risk 1.2 | todo |

## Test Case Gaps
- Live credential read cannot be unit-tested (no PasswordSafe-backed Database data source in a light
  fixture) — covered by DR-1 and the VNC checklist, not JUnit.
- Cluster **connection** behaviour is not tested (rejected before any socket opens); only the
  cluster **classification** (TC-6) is unit-tested.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design (spike-validated approach): [design.md](design.md)
- Implementation plan (Phase 0 gate): [implementation-plan.md](implementation-plan.md)
- Epic risk register: [../redis-risks-and-gaps.md](../redis-risks-and-gaps.md)
