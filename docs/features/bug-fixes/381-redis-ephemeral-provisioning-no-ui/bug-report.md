---
id: "BUG-381"
title: "Ephemeral Redis/Valkey provisioning (Docker / local binary) is fully built but has no UI — unreachable without hand-editing XML"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-381: Ephemeral Redis/Valkey provisioning (Docker / local binary) is fully built but has no UI

## 1. Reproduction

1. Open *Settings → Languages & Frameworks → Lua → Lua Project → Redis Connections*.
2. Add a connection and look for a way to make it launch an **ephemeral Docker** Redis/Valkey
   container (`redis:8` / `valkey/valkey:8`) or a **local `redis-server` / `valkey-server` binary**
   for the run/debug session — i.e. the "dev-container-style deploy → run → tear-down" flow.
3. Also check the **Redis Script** run-configuration editor for a server-source / provisioning
   selector.

Observed: there is **no control anywhere** to choose Docker or local-binary provisioning. Every
connection created through the UI is a plain **Remote** connection; the ephemeral-server capability
can only be enabled by hand-editing `.idea/lunar-redis.xml`.

## 2. Expected vs Actual Behavior

- **Expected**: the Redis Connections form (or the run-config editor) offers a **Server** choice —
  *Remote* / *Local binary (`redis-server`/`valkey-server`)* / *Docker image* — so a user can define
  a connection that spins up a session-scoped server, runs the script/function against it, and tears
  it down on session end. This is the documented REDIS-01 provisioning story.
- **Actual**: the UI only ever produces `Remote` connections; the Docker / local-binary provisioning
  variants are unreachable from any UI.

## 3. Context — the capability is complete except for its entry point

Every line reference below was re-verified against `main` on 2026-08-21; the figures the 2026-07-16
investigation recorded had all drifted by 1–12 lines and are corrected here.

| piece | where | state |
| :-- | :-- | :-- |
| Model | `LuaRedisServerConnection.kt:44` — `sealed interface LuaRedisProvisioning` with `Remote` (`:46`), `LocalBinary(toolKindId)` (`:49`), `Docker(image)` (`:54`) | done |
| Launcher | `LuaRedisServerLauncher.kt:75` dispatches; `launchBinary` `:85`, `launchDocker` `:101` (`docker run --rm -d -p <port>:6379 <image>`) | done |
| Consumers | `LuaRedisRunProfileState.kt:149` and `LuaLdbController.kt:113` both read `connection.provisioning` and launch/stop per session | done |
| Persistence | `LuaRedisConnectionSettings.provisioningOf` `:86` round-trips all three kinds through `.idea/lunar-redis.xml`, keyed on `provisioningKind` `:36` | done |
| Tool kinds | `LuaToolKindRegistry.kt:146` `redis-server`, `:158` `valkey-server` — both registered, classified `Tier.PLATFORM_SERVER` (`LuaToolKindClassifier.kt:21`) | done |
| Integration tests | `src/redisIntegrationTest/…` exercises real `redis:8` / `valkey/valkey:8` Docker provisioning | done |
| **UI** | — | **missing** |

**The launcher owns host and port for every non-Remote kind.** `launchBinary` and `launchDocker`
both call `seams.allocatePort()` and return `host = "127.0.0.1"` (`:97`, `:114`), and
`LuaRedisRunProfileState.openClient` (`:153`) prefers the launched values over the connection's. So
a Docker or LocalBinary connection's **Host and Port fields are dead input** — a fact the UI has to
express, or a user will set them and be baffled when the server appears elsewhere.

## 4. Root Cause

**`LuaRedisConnectionDraft` has no `provisioning` field at all**
([`LuaRedisConnectionsConfigurable.kt:230`](../../../../src/main/kotlin/net/internetisalie/lunar/redis/connection/LuaRedisConnectionsConfigurable.kt)).
The draft is the settings page's whole in-memory model of a connection, so provisioning is absent
from both ends of its round trip:

- `toConnection()` `:240` cannot pass through what it does not hold, so it **hardcodes**
  `provisioning = LuaRedisProvisioning.Remote` at `:249`.
- `from(connection, password)` `:257` copies id/name/host/port/tls/username/database and **drops
  `connection.provisioning` on the floor**.

`ConnectionForm` `:165` therefore has no control to add — there is nowhere for it to put the value.
Its own class KDoc at `:32` already claims the detail form covers
"host/port/TLS/auth/db/**provisioning**", which is the one place in the code that asserts this
control exists. It does not.

### 4a. This is not only a missing feature — the settings page DESTROYS provisioning

The report previously called this "not a crash; there is a workaround (hand-edit
`.idea/lunar-redis.xml`), hence medium priority". **That workaround does not survive contact with
the settings page,** and nothing in the original write-up noticed:

1. `reset()` `:88` loads every persisted connection into a draft via `LuaRedisConnectionDraft.from`
   — provisioning is dropped here.
2. `apply()` `:74` iterates **every** draft, not only edited ones, and calls
   `settings.upsert(draft.toConnection())`.
3. `upsert` (`LuaRedisConnectionSettings.kt`) **replaces the persisted state wholesale** —
   `myState.connections[existingIndex] = persisted` — it does not merge.

So editing *any* field of *any* connection and pressing OK silently rewrites **every** connection's
provisioning to `Remote`. A user who hand-edited the XML to get a Docker server loses it the next
time they touch an unrelated port number, with no error and no diff to look at.

`isModified()` `:72` compares `model.items != savedDrafts()` — both sides are provisioning-blind
drafts, so the loss is invisible to the modified check as well. It cannot *trigger* an apply on its
own, but it cannot prevent one either.

**Why no test caught it:** `LuaRedisConnectionDraft` and `toConnection()` have **zero** test
coverage — `grep -rn 'LuaRedisConnectionDraft\|toConnection' src/test` returns nothing. The draft is
the only part of this round trip that is untested, and it is the part that is broken.

**Priority raised `medium` → `high` on 2026-08-21** on the strength of §4a: silent loss of persisted
user configuration is a different class of defect from an unreachable feature, and it is the
*documented workaround* that is being destroyed.

## 5. Fix Strategy

**Two changes, in this order, because the first is a data-loss fix that stands alone and the second
is the feature.** Land them as separate commits so the regression fix is not entangled with UI
review.

### Step 1 — make the draft carry provisioning (fixes §4a with no UI at all)

- Add `val provisioning: LuaRedisProvisioning` to `LuaRedisConnectionDraft` `:230`.
- `from(...)` `:257` copies `connection.provisioning`.
- `toConnection()` `:240` passes it through; delete the hardcoded `LuaRedisProvisioning.Remote` at
  `:249`.
- `ConnectionForm.snapshot(id)` `:203` carries the bound draft's provisioning through unchanged,
  so a connection the user never edits keeps what it had.

After this step the settings page is *lossless* — every existing Docker/LocalBinary connection
survives an Apply — while still offering no way to create one. That is a strictly better state than
today and is independently shippable.

### Step 2 — add the control

- `ConnectionForm` `:165` gains `provisioningCombo: ComboBox<Kind>` (*Remote* / *Local binary* /
  *Docker*) plus two conditional rows:
  - **Local binary** — a combo filled from `LuaRedisProvisioning.SERVER_TOOL_KIND_IDS`, resolved
    against `LuaToolKindRegistry` for display names.

    > **Corrected during implementation.** This step originally said to filter
    > `LuaToolKindRegistry` by `LuaToolKindClassifier.Tier.PLATFORM_SERVER`, "today exactly
    > `redis-server`, `valkey-server`", so a third server kind would appear for free. **That tier
    > holds three kinds, not two.** It is assigned by *absence of capabilities*, and
    > `lua-language-server` has none either — so the first implementation offered a language server
    > as a Redis server. The test written for this row is what caught it, before any of it ran in an
    > IDE. "Which binaries speak RESP" is Redis domain knowledge and now lives in
    > `LuaRedisProvisioning`'s companion, next to the launcher that runs them.
  - **Docker** — a text field defaulting to `redis:8`.
- `bind(draft)` `:190` sets all three; `snapshot(id)` `:203` reads them back into the draft.
- The kind combo fires `onEdited()` and toggles row visibility.
- **Disable Host and Port when the kind is not Remote**, per §3 — the launcher allocates both, so
  leaving them editable presents input that is silently ignored.

### Explicitly out of scope

- **The run-config editor** (`LuaRedisSettingsEditor`, `LuaRedisRunConfiguration.kt:312`). Its
  `connectionCombo` `:317` *references* a connection; provisioning is a property of the connection,
  not of the run. Adding a second place to set it would create two sources of truth for one value.
- **REDIS-07** (reuse an IntelliJ Database data source). Different question — where a connection's
  *definition* comes from, not where its *server* comes from. No dependency in either direction.

## 6. Test Strategy

| test | asserts | red before |
| :-- | :-- | :-- |
| `toConnection()` preserves a `Docker("redis:8")` draft's provisioning | Step 1's pass-through | **yes** — returns `Remote` today |
| `from(connection, …)` preserves each of the three kinds | Step 1's other half | **yes** — drops it today |
| draft → connection → draft round trip is identity over all three kinds | the two halves agree | **yes** |
| **`apply()` on a page where a DIFFERENT connection was edited leaves a Docker connection's provisioning intact** | §4a directly — the actual user-visible defect | **yes** |
| the server-kind list is Redis-owned, fully registered, and excludes `lua-language-server` | Step 2's combo source | **yes, once written** — see the correction in §5 |
| ~~`snapshot(id)` returns the form's selected kind~~ | — | **not unit-tested; see below** |
| ~~Host/Port are disabled for a non-Remote kind~~ | — | **not unit-tested; see below** |

The fourth row is the one that matters and the one to write first: the other three can all pass
while the settings page still flattens a connection the user never touched.

**The last two rows are struck because reaching them needs test-only API, and it would not buy
what it looks like it buys.** `ConnectionForm` is a private inner class; exposing it to assert that
`snapshot` reads a combo would test the wiring while saying nothing about whether the control
renders, whether the conditional rows toggle, or whether Host/Port visibly grey out — which is the
whole question. Those are answered by the live pass below, and claiming unit coverage for them
would be the vacuous-gate shape this repo keeps finding. What *is* unit-reachable is the combo's
source of truth, and that row earned its place by going red.

**Mutation proof.** Not needed as a separate step here: the three round-trip tests were run against
the unfixed code and all three failed with `expected:<Docker(image=redis:8)> but
was:<…Remote>`. The pre-fix state *is* the mutation, and it is the exact mutation worth proving —
this defect's whole shape is a value silently replaced by a constant. Every assertion is written
through the existing public API rather than reading the new `provisioning` field, which is what let
them fail rather than fail to compile.

### Live verification — DONE 2026-08-21, with one step not reached

Driven over Xvfb on the builder VM (`verify-in-ide`), against a fresh `runIde` sandbox that logged
`Loaded custom plugins: lunar` at launch. Verified by screenshot and by reading
`.idea/lunar-redis.xml` back:

| # | check | result |
| :-- | :-- | :-- |
| 1 | the **Server** combo renders, offering exactly Remote server / Local binary / Docker image | ✅ |
| 2 | choosing Docker reveals the image row, **pre-filled `redis:8`** | ✅ *(see below)* |
| 3 | choosing Local binary reveals the binary row, showing the registry display name "Redis Server" | ✅ |
| 4 | **Host and Port grey out** for both non-Remote kinds, and are editable for Remote | ✅ |
| 5 | a Docker connection created **through the UI alone** persists as `provisioningKind=DOCKER` + `dockerImage=redis:8` | ✅ |
| 6 | reopening Settings shows it back as Docker — `from()` reads it | ✅ |
| 7 | **§4a**: adding and editing a *second* connection, then OK, leaves the Docker one untouched | ✅ |
| 8 | run a script against the ephemeral server end-to-end | **not reached** |

**Row 2 was a defect this pass found and fixed.** The image field was *empty* on switching to
Docker: `bindProvisioning` only wrote it for an already-Docker draft, and toggling the combo does not
re-bind. The persisted value was still correct (`selectedProvisioning` treats blank as the default),
so no unit test could have seen it — it was purely what the user meets. The field is now seeded for
every kind, which also stops it showing the *previous* connection's image.

**Row 8, stated rather than glossed.** A Redis Script run configuration is not offered from a
`.lua` file's editor context menu or gutter, so reaching it needs one built by hand through *Edit
Configurations*; that was not done. What it would exercise is the **launcher**, which this bug did
not touch and which `src/redisIntegrationTest` already covers against real `redis:8` /
`valkey/valkey:8` containers. Everything BUG-381 *changed* — form, draft, persistence — is covered
by rows 1–7, and row 5's XML is byte-identical in shape to what those integration tests consume.
The join of the two halves in one click remains unwitnessed, and is the honest gap.

## 7. Other Notes

- Same "capability exists, no UI" shape as the toolchain **global-bindings** gap and BUG-362's
  platform-target control — a recurring pattern worth watching in the settings surfaces. §4a
  suggests what to look for when it recurs: where a UI model omits a field, check whether the
  persistence layer *overwrites* rather than merges, because that turns an omission into data loss.
- Related to the REDIS connection-definition parity analysis (2026-07-16) and
  **[[../../redis/07-database-datasource-integration/requirements|REDIS-07]]**. This bug is the
  smaller, self-contained half and does not depend on it.
