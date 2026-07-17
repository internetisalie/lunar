---
id: "BUG-381-PLAN"
title: "Implementation Plan: Redis/Valkey ephemeral-provisioning connection UI"
type: "plan"
parent_id: "BUG-381"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-381: Implementation Plan

Precondition: `design.md` has cleared the planning bar (algorithms §3.1–§3.3 specified, classes
named, no `plugin.xml` change, Open Questions empty). All work is inside
`redis/connection/LuaRedisConnectionsConfigurable.kt` (the `ConnectionForm` inner class +
`LuaRedisConnectionDraft`); no other production file changes.

## Phases

### Phase 1: Draft model + provisioning mapping [Must]
- **Goal**: the draft carries the chosen provisioning and produces the correct
  `LuaRedisProvisioning` — unit-testable with no UI.
- **Tasks**:
  - [ ] Edit `LuaRedisConnectionDraft` (`LuaRedisConnectionsConfigurable.kt:217`): add
    `provisioningKind: String`, `toolKindId: String`, `dockerImage: String` fields — realizes design §2.1.
  - [ ] Add private `provisioningFromDraft()` and rewrite `toConnection()` to use it instead of the
    hardcoded `LuaRedisProvisioning.Remote` (`:237`) — realizes design §3.1.
  - [ ] Rewrite `LuaRedisConnectionDraft.from(connection, password)` to reverse-map
    `connection.provisioning` into the three fields — realizes design §3.2.
  - [ ] Update `newDefault()` to seed `KIND_REMOTE` / `"redis-server"` / `"redis:8"` — realizes design §2.1.
  - [ ] Reference the existing `LuaRedisProvisioning.KIND_*` constants — no new literals.
- **Exit criteria**: new unit tests TC-DRAFT-1..3 (below) pass; existing
  `TestLuaRedisConnectionSettings` still green (its `Remote` round-trip is unchanged).

### Phase 2: Form control (server-source combo + conditional rows) [Must]
- **Goal**: the settings form lets the user pick the server source and edits round-trip through
  `bind`/`snapshot`.
- **Tasks**:
  - [ ] Add `serverSourceCombo`, `toolKindCombo`, `dockerImageField` + the `SERVER_*` display
    constants to `ConnectionForm` (`:153`) — realizes design §2.2.
  - [ ] Add the three rows to the `panel { }` layout with `visibleIf(localSelected/dockerSelected)`
    predicates; wire `serverSourceCombo.addActionListener { onEdited() }` — realizes design §2.2, §3.3.
  - [ ] Extend `bind(draft)` to set the combo + detail controls (kind→display map) — realizes design §2.2, §3.3.
  - [ ] Extend `snapshot(id)` to read the combo + detail controls into the three draft fields
    (display→kind map) — realizes design §2.2, §3.3.
- **Exit criteria**: build green; manual smoke via the VNC checklist row VNC-1 (form shows/hides
  detail rows on combo change). No `plugin.xml` edit (design §7).

### Phase 3: Live verification (Docker provision → run → teardown) [Should]
- **Goal**: confirm a UI-created Docker connection actually launches and tears down a container.
- **Tasks**:
  - [ ] Run the `human-verification-checklists.md` VNC row VNC-2 on the builder (Docker available
    per DR-04) — realizes design §5 Example 1.
- **Exit criteria**: container starts on script run, reply renders, container removed on session end.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| Server-source combo, default Remote | M | Phase 2 |
| Per-choice detail fields | M | Phase 2 |
| Thread choice draft → connection (replace hardcoded Remote) | M | Phase 1 |
| Round-trip persistence per source kind | M | Phase 1 (+ Phase 2 form) |
| No run-config editor change | M | (design §6 — nothing to build) |

## Verification Tasks

Unit tests (add to `src/test/kotlin/net/internetisalie/lunar/redis/connection/`, JUnit style matching
`TestLuaRedisConnectionSettings`):

- [ ] **TC-DRAFT-1** — `toConnection()` maps `provisioningKind=KIND_DOCKER, dockerImage="redis:8"`
  to `LuaRedisProvisioning.Docker("redis:8")`. Covers design §3.1.
- [ ] **TC-DRAFT-2** — `toConnection()` maps `KIND_LOCAL_BINARY, toolKindId="valkey-server"` to
  `LuaRedisProvisioning.LocalBinary("valkey-server")`; default `KIND_REMOTE` maps to `Remote`. Covers §3.1.
- [ ] **TC-DRAFT-3** — full round-trip: build a Docker draft → `toConnection()` →
  `LuaRedisConnectionSettings.upsert` → `XmlSerializer.serialize`/`deserialize` → `findById` →
  `LuaRedisConnectionDraft.from(...)` yields `provisioningKind=KIND_DOCKER, dockerImage="redis:8"`.
  Repeat for LocalBinary and Remote. Covers design §3.1, §3.2, §5 Example 2. (Mirror the headless
  serializer pattern already in `TestLuaRedisConnectionSettings.stateRoundTripsThroughXmlSerializerWithoutSecret`.)
- [ ] **Launcher seam** — no new test needed: `TestLuaRedisServerLauncher` already asserts
  `buildBinaryCommandLine`/`buildDockerCommandLine` receive the right image/port via `LaunchSeams`
  (`LuaRedisServerLauncher.kt:32,152,163`). The launcher is unchanged, so this bug adds no launcher test.

Manual / VNC (add rows to `human-verification-checklists.md`):

- [ ] **VNC-1** — open Redis Connections, add a connection, cycle the Server combo Remote → Local
  binary → Docker; confirm the binary-kind row shows only for Local and the image row only for Docker.
- [ ] **VNC-2** (Docker, builder DR-04) — create a Docker connection (`redis:8`), run a Redis Script;
  confirm `docker` container starts, the RESP reply renders, and the container is removed on session
  end (`docker ps` empty afterward). Covers design §5 Example 1.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Draft model + provisioning mapping | todo | Must |
| Phase 2: Form control | todo | Must |
| Phase 3: Live verification | todo | Should |
