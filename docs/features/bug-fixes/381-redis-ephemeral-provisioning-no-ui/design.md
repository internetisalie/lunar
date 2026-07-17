---
id: "BUG-381-DESIGN"
title: "Technical Design: Redis/Valkey ephemeral-provisioning connection UI"
type: "design"
parent_id: "BUG-381"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# Technical Design: BUG-381 — Redis/Valkey ephemeral-provisioning connection UI

## 1. Architecture Overview

### Current State

The ephemeral Redis/Valkey provisioning backend is **fully built and tested**; only the UI entry
point is missing. Grounded surface (all `file:line` verified 2026-07-17):

- **Model** — `LuaRedisProvisioning` is a `sealed interface` with three variants and stable kind
  constants: `Remote` (object), `LocalBinary(val toolKindId: String)`, `Docker(val image: String)`;
  constants `KIND_REMOTE="REMOTE"`, `KIND_LOCAL_BINARY="LOCAL_BINARY"`, `KIND_DOCKER="DOCKER"`
  (`redis/connection/LuaRedisServerConnection.kt:45-61`). The connection carries it:
  `LuaRedisServerConnection(… val provisioning: LuaRedisProvisioning)` (`:13-22`).
- **Launcher** — `LuaRedisServerLauncher.launch(provisioning): LaunchedServer`
  (`redis/connection/LuaRedisServerLauncher.kt:75`) dispatches `LocalBinary -> launchBinary` (`:84`,
  binary resolved via `LuaToolResolver.getInstance().resolve(project, toolKindId)?.path`) and
  `Docker -> launchDocker` (`:99`, `docker run --rm -d -p <freePort>:6379 <image>`). It is unit-seamed:
  `LaunchSeams(resolveToolPath, resolveDockerPath, allocatePort)` (`:32`) plus package-internal
  `buildBinaryCommandLine` (`:152`) / `buildDockerCommandLine` (`:163`).
- **Consumers** — `LuaRedisRunProfileState` (`redis/run/LuaRedisRunProfileState.kt:135-137`) and the
  LDB debugger `LuaLdbController` (`redis/debug/LuaLdbController.kt:113-115`) both read
  `connection.provisioning`, short-circuit on `Remote`, and otherwise call `LuaRedisServerLauncher(...).launch(provisioning)`.
- **Persistence** — `LuaRedisConnectionSettings` already round-trips all three variants to
  `.idea/lunar-redis.xml` via `ConnectionState.{provisioningKind, toolKindId, dockerImage}`
  (`redis/connection/LuaRedisConnectionSettings.kt:29-40`), `applyProvisioning` (`:109`) and
  `provisioningOf` (`:88`), with defaults `toolKindId ?: "redis-server"` and `dockerImage ?: "redis:8"`.
- **Tool kinds** — `redis-server` and `valkey-server` are registered kinds in
  `LuaToolKindRegistry` (`toolchain/registry/LuaToolKindRegistry.kt:133,144`).

**The one defect:** `LuaRedisConnectionDraft.toConnection()` hardcodes
`provisioning = LuaRedisProvisioning.Remote` (`redis/connection/LuaRedisConnectionsConfigurable.kt:237`),
and `ConnectionForm` (`:153-205`) has no provisioning control. So the settings UI can only ever
produce `Remote` connections; the launcher path is dead unless the user hand-edits
`.idea/lunar-redis.xml`.

### Prior Art in This Repo

- **`ConnectionForm`** (`LuaRedisConnectionsConfigurable.kt:153`) — the exact form this design
  **EXTENDS**. It is a `panel { row(...) { cell(...) } }` Kotlin-UI-DSL form with an `onEdited`
  callback, `bind(draft)`, and `snapshot(id)`. This design adds a server-source combo + two
  conditional detail rows to it; it does **not** create a second form.
- **`LuaRedisConnectionDraft`** (`:217`) — the mutable in-panel snapshot. This design **EXTENDS**
  it with three new fields (`provisioningKind`, `toolKindId`, `dockerImage`) and rewrites
  `toConnection()` (`:228`) to build the chosen variant instead of the hardcoded `Remote`.
- **Run-config editor `LuaRedisSettingsEditor`** (`redis/run/LuaRedisRunConfiguration.kt:272`) —
  searched; it selects a connection purely **by id** (`config.connectionId`, resolved through
  `LuaRedisConnectionSettings.getInstance(project).findById(id)` at `:154-155`; combo persists only
  the id at `:330`). Provisioning travels with the connection model, not the run config. **No
  run-config change is needed** (this design does not touch `LuaRedisSettingsEditor`) — see §6.
- **`LuaToolResolver`** (`toolchain/resolve/LuaToolResolver.kt:22` `resolve(project, kindId)`) — the
  existing resolver the LocalBinary path already uses; **reused**, not duplicated.
- No existing "server source" combo exists anywhere — searched `redis/` and `toolchain/ui/`. New
  control is genuinely new.

### Target State

`ConnectionForm` gains a **Server source** combo (`Remote` / `Local binary` / `Docker image`) plus
two conditional detail rows; the draft carries the choice; `toConnection()` builds the matching
`LuaRedisProvisioning`. Nothing downstream changes — launcher, persistence, consumers, and the
run-config editor already handle all three variants.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.redis.connection.LuaRedisConnectionDraft` (edit)

- **Responsibility**: carry the chosen provisioning through the form → connection path.
- **Threading**: pure data; touched only on the EDT (form) — no threading concerns.
- **Collaborators**: `LuaRedisProvisioning` (model), `LuaRedisConnectionSettings` (persist via
  `toConnection()`).
- **Change**: add three fields and rewrite `toConnection()` + `from(...)` + `newDefault()`.
  ```kotlin
  data class LuaRedisConnectionDraft(
      val id: String,
      val name: String,
      val host: String,
      val port: Int,
      val tls: Boolean,
      val username: String?,
      val password: String?,
      val database: Int,
      val provisioningKind: String,   // KIND_REMOTE | KIND_LOCAL_BINARY | KIND_DOCKER
      val toolKindId: String,         // used only when kind == KIND_LOCAL_BINARY
      val dockerImage: String,        // used only when kind == KIND_DOCKER
  ) {
      fun toConnection(): LuaRedisServerConnection =
          LuaRedisServerConnection(
              id, name, host, port, tls, database, username,
              provisioning = provisioningFromDraft(),   // §3.1
          )
      // toEndpoint() unchanged (Remote host/port only — used only by Test Connection)
      companion object {
          fun from(connection: LuaRedisServerConnection, password: String?): LuaRedisConnectionDraft // §3.2
          fun newDefault(): LuaRedisConnectionDraft // provisioningKind = KIND_REMOTE, toolKindId = "redis-server", dockerImage = "redis:8"
      }
  }
  ```
- **Constant reuse**: the three kind strings are `LuaRedisProvisioning.KIND_REMOTE` /
  `KIND_LOCAL_BINARY` / `KIND_DOCKER` (already defined, `LuaRedisServerConnection.kt:57-59`) — do
  not introduce new literals.

### 2.2 `LuaRedisConnectionsConfigurable.ConnectionForm` (edit — the inner class at `:153`)

- **Responsibility**: add the server-source combo + conditional detail rows; include their values in
  `bind` / `snapshot`; show/hide detail rows on combo change.
- **Threading**: EDT only (Swing layout; engineering-contract §1) — matches the existing form.
- **Collaborators**: `LuaToolKindRegistry` (populate the Local-binary kind choices).
- **New controls** (Kotlin-UI-DSL cells, mirroring the existing `JBTextField`/`JBCheckBox` style):
  ```kotlin
  val serverSourceCombo = ComboBox(arrayOf(SERVER_REMOTE, SERVER_LOCAL, SERVER_DOCKER)) // display-string items
  val toolKindCombo = ComboBox(arrayOf("redis-server", "valkey-server"))
  val dockerImageField = JBTextField(20) // default "redis:8"
  ```
  The three display strings are UI constants in the companion:
  ```kotlin
  private const val SERVER_REMOTE = "Remote"
  private const val SERVER_LOCAL = "Local binary"
  private const val SERVER_DOCKER = "Docker image"
  ```
- **Layout** — add three rows to the existing `panel { … }` (`:164`), placed after `row("Port:")`
  and before the Test Connection button, using `.visibleIf(...)` predicates driven by the combo (§3.3):
  ```kotlin
  row("Server:") { cell(serverSourceCombo) }
  row("Server binary kind:") { cell(toolKindCombo) }.visibleIf(localSelected)
  row("Docker image:")       { cell(dockerImageField) }.visibleIf(dockerSelected)
  ```
  `visibleIf` takes a `ComponentPredicate`; build it from the combo selection (see §3.3). The combo
  also fires `onEdited()` (register via `serverSourceCombo.addActionListener { onEdited() }`,
  matching the existing `tlsCheckBox.addActionListener { onEdited() }` at `:202`).
- **`bind(draft)`** — additionally set `serverSourceCombo.selectedItem` from `draft.provisioningKind`
  (map kind→display via §3.3), `toolKindCombo.selectedItem = draft.toolKindId`,
  `dockerImageField.text = draft.dockerImage`.
- **`snapshot(id)`** — additionally read the combo/detail controls into the three new draft fields
  (map display→kind via §3.3).

### 2.3 `LuaRedisConnectionsConfigurable.apply()` (no change)

`apply()` (`:70`) already calls `draft.toConnection()` (`:79`) and persists via `settings.upsert(...)`.
Because `toConnection()` now yields the chosen variant and `LuaRedisConnectionSettings.applyProvisioning`
already serializes all three, **no change is needed** to `apply`, `reset`, `isModified`, or the
persistence layer.

## 3. Algorithms

### 3.1 `provisioningFromDraft()` — draft → `LuaRedisProvisioning`

- **Input → Output**: `LuaRedisConnectionDraft` → `LuaRedisProvisioning`.
- **Steps** (private helper on the draft):
  1. `when (provisioningKind)`:
     - `KIND_LOCAL_BINARY` → `LuaRedisProvisioning.LocalBinary(toolKindId)`.
     - `KIND_DOCKER` → `LuaRedisProvisioning.Docker(dockerImage)`.
     - else (`KIND_REMOTE` or unknown) → `LuaRedisProvisioning.Remote`.
- **Edge handling**: an unrecognized kind string falls through to `Remote` (mirrors
  `LuaRedisConnectionSettings.provisioningOf`'s `else -> Remote` at `:93`, keeping form and
  persistence symmetric). `toolKindId` / `dockerImage` are always non-null in the draft (defaulted),
  so no null handling is needed.

### 3.2 `from(connection, password)` — connection → draft (reverse map)

- **Input → Output**: `(LuaRedisServerConnection, String?)` → `LuaRedisConnectionDraft`.
- **Steps**: copy scalars as today (`:244-254`), then derive the three provisioning fields from
  `connection.provisioning`:
  1. `Remote` → `provisioningKind = KIND_REMOTE`, `toolKindId = "redis-server"`, `dockerImage = "redis:8"` (defaults; detail rows hidden anyway).
  2. `LocalBinary(k)` → `provisioningKind = KIND_LOCAL_BINARY`, `toolKindId = k`, `dockerImage = "redis:8"`.
  3. `Docker(img)` → `provisioningKind = KIND_DOCKER`, `toolKindId = "redis-server"`, `dockerImage = img`.
- **Rationale**: the unused detail field keeps its default so re-selecting a kind never shows a blank.

### 3.3 Combo display ↔ kind mapping + visibility predicates

- **display → kind**: `"Remote"→KIND_REMOTE`, `"Local binary"→KIND_LOCAL_BINARY`, `"Docker image"→KIND_DOCKER`.
- **kind → display**: inverse of the above; unknown kind → `"Remote"`.
- **Visibility**: `localSelected` is true iff `serverSourceCombo.selectedItem == SERVER_LOCAL`;
  `dockerSelected` iff `== SERVER_DOCKER`. Build each as a `ComponentPredicate` whose
  `invoke()` reads the combo and which re-evaluates on the combo's `addActionListener`. Both detail
  rows are hidden for `Remote`.
- **No other non-trivial algorithm exists** — the launcher/persistence logic is pre-existing and
  unchanged.

## 4. External Data & Parsing

None. This feature consumes no CLI/text/file/network input — it only threads an enum-like choice from
Swing controls into an in-memory data class. (The launcher's `docker`/binary process I/O and its
command-line parsing already exist and are untouched.)

## 5. Data Flow

### Example 1: Create a Docker connection

1. User opens *Settings → … → Lua → Redis Connections*, clicks **+** → `addConnection()` (`:94`)
   adds a `newDefault()` draft (kind `REMOTE`).
2. User sets Name/Host/Port, then picks **Docker image** in the Server combo → `visibleIf` shows the
   Docker image row (default `redis:8`); `onEdited()` fires → `snapshot(id)` writes
   `provisioningKind=KIND_DOCKER, dockerImage="redis:8"` back into the model (`:118`).
3. User clicks **OK** → `apply()` (`:70`) → `draft.toConnection()` builds
   `LuaRedisProvisioning.Docker("redis:8")` → `settings.upsert(...)` → `applyProvisioning` writes
   `provisioningKind="DOCKER"`, `dockerImage="redis:8"` to `.idea/lunar-redis.xml`.
4. Later, running a Redis Script that references this connection id →
   `LuaRedisRunProfileState` reads `connection.provisioning` (`Docker`) →
   `LuaRedisServerLauncher.launch(...)` runs `docker run --rm -d -p <port>:6379 redis:8`, script runs
   against it, `LaunchedServer.stop` removes the container on session end. **All pre-existing.**

### Example 2: Reopen a Local-binary connection (round-trip)

1. `.idea/lunar-redis.xml` holds `provisioningKind="LOCAL_BINARY" toolKindId="valkey-server"`.
2. `reset()` → `savedDrafts()` → `LuaRedisConnectionDraft.from(...)` maps it to
   `provisioningKind=KIND_LOCAL_BINARY, toolKindId="valkey-server"` (§3.2).
3. `bind(draft)` selects **Local binary** in the combo and `valkey-server` in the kind combo; the
   binary-kind row is shown, the Docker row hidden.

## 6. Edge Cases

- **Run-config editor** — deliberately untouched: it references connections by id (§1 Prior Art);
  changing a connection's server source needs no run-config edit. Documented here so an implementer
  does not "helpfully" add a redundant selector.
- **Switching a connection's kind mid-edit** — the unused detail field keeps its default (§3.2), so
  toggling `Docker → Local binary → Docker` never blanks the image.
- **Local binary not on the machine** — not a form concern; the launcher already throws a
  user-facing `ExecutionException` ("binary not found… or use Docker",
  `LuaRedisServerLauncher.kt:86`) at session launch. The form does not validate binary presence.
- **`toEndpoint()` / Test Connection** — unchanged: it uses only host/port and is meaningful for
  `Remote`. For a not-yet-launched Docker/Local connection, Test Connection against the configured
  host/port may fail; that is acceptable (out of scope — noted in risks).

## 7. Integration Points

**No `plugin.xml` change.** `LuaRedisConnectionsConfigurable` is already registered
(`projectConfigurable … displayName="Redis Connections"`, `META-INF/plugin.xml:583`). This change is
entirely inside that already-registered configurable and the `LuaRedisConnectionDraft` data class in
the same file. No new extension point, service, index, or action.

- Reuses existing services: `LuaRedisConnectionSettings.getInstance(project)`,
  `LuaToolKindRegistry` (for the two `*-server` kind ids).

## 8. Requirement Coverage

| Requirement (bug-report §Fix direction) | Priority | Implemented by (section) |
|-----------------------------------------|----------|--------------------------|
| Server-source combo (Remote / Local binary / Docker), default Remote | M | §2.2, §3.3 |
| Per-choice detail fields (binary kind for Local; image for Docker) | M | §2.2, §3.3 |
| Thread the choice draft → connection → launcher (replace hardcoded Remote) | M | §2.1, §3.1 |
| Round-trip persistence per source kind (form → XML → form) | M | §2.1, §3.2, §5 Ex.2 |
| No run-config editor change (connection referenced by id) | M | §1 Prior Art, §6 |

## 9. Alternatives Considered

- **Add the selector to the run-config editor instead** — rejected: the run config references a
  connection by id (`connectionId`); the server source is a property of the *connection*, so it
  belongs on the connection form. Duplicating it on the run config would create two sources of truth.
- **A new "provisioning" sub-dialog** — rejected as heavier than needed; two conditional rows in the
  existing form (the platform's standard `visibleIf` pattern) suffice.
- **Free-text tool-kind field for Local binary** — rejected in favor of a two-item combo
  (`redis-server` / `valkey-server`), the only registered server kinds
  (`LuaToolKindRegistry.kt:133,144`), preventing invalid ids.

## 10. Open Questions

_None — feature has cleared the planning bar._
