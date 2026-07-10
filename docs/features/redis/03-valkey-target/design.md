---
id: "REDIS-03-DESIGN"
title: "Technical Design"
type: "design"
status: "todo"
parent_id: "REDIS-03"
folders:
  - "[[features/redis/03-valkey-target/requirements|requirements]]"
---

# Technical Design: REDIS-03 — Valkey Runtime Target

## 1. Architecture Overview

### Current State

The TARGET epic already models runtime targets as `(LuaPlatform, VersionEntry)` pairs and
resolves per-target stdlib stubs from a `runtime/<platform>/<version>/` resource tree:

- `net.internetisalie.lunar.platform.LuaPlatform` (`platform/LuaPlatform.kt:3`) — enum with
  `STANDARD, LUAJIT, NGX, PANDOC, REDIS, TARANTOOL`. **`VALKEY` does not exist** (grep
  `LuaPlatform.VALKEY` → 0 hits; see §Grounding ledger). It is the only enum member REDIS-03
  adds.
- `net.internetisalie.lunar.platform.target.PlatformVersionRegistry`
  (`platform/target/PlatformVersionRegistry.kt:14`) — `object` holding a
  `Map<LuaPlatform, List<VersionEntry>>`; `REDIS` maps to `redis-5/redis-6/redis-7` with
  luacheck stds `redis5/redis6/redis7` (`:27-31`).
- `net.internetisalie.lunar.platform.target.VersionEntry`
  (`platform/target/VersionEntry.kt:14`) — `data class VersionEntry(label, pathSegment,
  luacheckStd)`.
- `net.internetisalie.lunar.platform.target.Target` (`platform/target/Target.kt:16`) —
  `getImplicitLanguageLevel()` (`:37`, `REDIS -> LUA51` at `:45`), `getLibraryRootPath()`
  (`:65`, `"runtime/${platform.pathSegment}/${version.pathSegment}"`), `getLuacheckStd()`
  (`:73`).
- `net.internetisalie.lunar.platform.target.RuntimeLibraryProvider`
  (`platform/target/RuntimeLibraryProvider.kt:24`) — resolves the library root via
  `classLoader.getResource(target.getLibraryRootPath())` → `VfsUtil.findFileByURL`. Returns
  `null` when no resources are bundled.
- Redis stdlib stubs live at `src/main/resources/runtime/redis/redis-7/` (`redis.lua`,
  `global.lua`, `cjson.lua`, `cmsgpack.lua`, `bit.lua`, `struct.lua`, `os.lua`). `redis.lua`
  declares `---@class redis` + `redis = {}` (`redis.lua:1,12`) with EmmyLua-style
  `---@field`/`function redis.foo(...)` members. `global.lua` declares `KEYS`/`ARGV` as
  `---@type string[]` globals (`global.lua:1-8`).
- Member resolution follows `---@class X : Y` parent types:
  `LuaTypeManagerImpl.kt:191-193` reads `classTag.parentTypes.argTypeList` into `superTypes`;
  `LuaLocalVarStubElementType.kt:26` indexes the extends type. So `---@class server : redis`
  inherits every `redis` field/method (grounds AC-3's "no duplication").
- The project target is applied by the environment→target sync path:
  `LuaTargetSynchronizer.targetFor(info)` (`toolchain/resolve/LuaTargetSynchronizer.kt:81-83`)
  calls `PlatformVersionRegistry.resolveTarget(info.platform, label)`; `info.platform` is a
  `LuaPlatform` carried on `LuaRuntimeInfo`. Reads use
  `LuaProjectSettings.getInstance(project).state.getTarget().platform`
  (`settings/LuaProjectSettings.kt:90`).
- REDIS-01 flavor seam (design §4.3): connections read `INFO server`, split on `\r\n`, key/value
  on `:`, and derive flavor from the presence of a `valkey_version` line. REDIS-01 §4.3
  explicitly states: "REDIS-03 replaces this heuristic with `SERVER_NAME`; REDIS-01 uses
  `INFO`." That inline heuristic is the seam REDIS-03 consumes and centralizes.

**Why insufficient**: Valkey is not a selectable/derivable platform (no enum member, no registry
entries, no stubs); `server.*`/`SERVER_*` are unresolved references under any target; nothing
warns when the connected server flavor disagrees with the project target; nothing flags
non-portable Valkey-only API in Redis-targeted scripts.

### Prior Art in This Repo

| Concern | Existing component (file:line) | REDIS-03 relationship |
|---------|--------------------------------|------------------------|
| Platform enum | `LuaPlatform` (`platform/LuaPlatform.kt:3`) | **EXTENDS** — adds one member `VALKEY` |
| Version registry | `PlatformVersionRegistry` (`platform/target/PlatformVersionRegistry.kt:15`) | **EXTENDS** — adds one map entry |
| Target derivation | `Target.getImplicitLanguageLevel` (`Target.kt:37`) | **EXTENDS** — adds one `when` branch `VALKEY -> LUA51` |
| Stub resolution | `RuntimeLibraryProvider` (`RuntimeLibraryProvider.kt:24`), stub `.lua` in `runtime/redis/redis-7/` | **REUSED unchanged** — new `runtime/valkey/valkey-{7.2,8}/` roots consumed by the same code |
| Class inheritance for stubs | `---@class X : Y` → `LuaTypeManagerImpl.kt:191`, `LuaLocalVarStubElementType.kt:26` | **REUSED** — `---@class server : redis` |
| Inspection pattern | `LuaDeprecatedApiInspection` (`analysis/inspections/LuaDeprecatedApiInspection.kt:18`), registered `plugin.xml:202-209` | **PATTERN** — new inspection is a sibling `LocalInspectionTool`, not a modification of any existing one (grep for an existing portability/flavor inspection → 0 hits) |
| Quick fix pattern | `LuaAddToGlobalsQuickFix` (`analysis/inspections/LuaAddToGlobalsQuickFix.kt:13`) implements `LocalQuickFix` | **PATTERN** — new `LocalQuickFix` |
| Identifier rewrite | `LuaElementFactory.createIdentifier` (`lang/psi/LuaElementFactory.kt:12`), used by rename | **REUSED** — quick fix replaces the `server` identifier leaf |
| Member-ref PSI walk | `LuaNameReference.getQualifiedName` (`lang/LuaNameReference.kt:139-172`) | **PATTERN** — the inspection reuses the exact `LuaNameRef → LuaIndexExpr(DOT) → LuaVarSuffix → LuaVar → nameRef` walk |
| Flavor heuristic | REDIS-01 design §4.3 inline `INFO server` flavor derivation | **CENTRALIZES + REPLACES** — the `valkey_version` heuristic moves into `LuaRedisServerFlavor` (a REDIS-01 amendment; see §7 and risks-and-gaps DR-01) |

No pre-existing Valkey support, portability inspection, or flavor-mismatch warning exists.
Searched: `grep -rn "VALKEY\|Valkey\|server\.\*\|SERVER_NAME\|portab\|Flavor" src/main`.

### Target State

```
LuaPlatform.VALKEY  [NEW enum member]
  └─ PlatformVersionRegistry[VALKEY] = [7.2, 8]         (§2.1)  →  Target derivation reused
        └─ runtime/valkey/valkey-7.2/, valkey-8/         (§2.4 stub resources)
              ├─ redis.lua        (compat namespace — copy of the redis-7 base)
              ├─ server.lua       (---@class server : redis  + server = {})
              ├─ server_global.lua (SERVER_NAME/VERSION/VERSION_NUM globals)
              ├─ global.lua       (KEYS/ARGV — copy of redis-7)
              └─ cjson/cmsgpack/bit/struct/os.lua (copies of redis-7 base)
LuaRedisServerFlavor  [NEW]  (§2.5)  ← consumed by REDIS-01 connect (§7 amendment)
  └─ LuaRedisFlavorWarning  [NEW]  (§2.6)  once-per-session mismatch notification
LuaValkeyPortabilityInspection  [NEW]  (§2.7)  + LuaValkeyToRedisQuickFix  [NEW]  (§2.8)
  registered in plugin.xml <localInspection> (§7)
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.platform.LuaPlatform.VALKEY` **[NEW enum member]**
- **Responsibility**: Identify the Valkey platform for target derivation and library-root paths.
- **Threading**: N/A (enum constant).
- **Collaborators**: `PlatformVersionRegistry`, `Target`.
- **Change**: add one constant to the existing enum (`platform/LuaPlatform.kt:3`):
  ```kotlin
  VALKEY("Valkey", "valkey"),
  ```
  `label = "Valkey"` (UI text via `toString()`), `pathSegment = "valkey"` (resource dir).
  Inserted after `REDIS` to keep Redis/Valkey adjacent in `entries` order.

### 2.2 `PlatformVersionRegistry` **[EDIT]** — VALKEY registry entry
- **Responsibility**: Register the two Valkey versions.
- **Threading**: Object initialized once at classload; read-only thereafter.
- **Change**: add one map entry (`PlatformVersionRegistry.kt:15` map literal), placed after the
  `REDIS` entry:
  ```kotlin
  LuaPlatform.VALKEY to listOf(
      VersionEntry("7.2", "valkey-7.2", luacheckStd = "redis7"),
      VersionEntry("8",   "valkey-8",   luacheckStd = "redis7"),
  ),
  ```
  `luacheckStd = "redis7"` for both: no dedicated `valkey` std exists upstream (documented in
  requirements + epic Open Gaps). `defaultVersion(VALKEY)` is the first entry `"7.2"` (matches
  `defaultVersion` semantics at `:58`).

### 2.3 `Target` **[EDIT]** — implicit level branch
- **Responsibility**: Map a Valkey target to its language level.
- **Change**: add one branch to `getImplicitLanguageLevel()` (`Target.kt:38-50`), after the
  `REDIS` branch:
  ```kotlin
  platform == LuaPlatform.VALKEY -> LuaLanguageLevel.LUA51
  ```
  `getLibraryRootPath()` and `getLuacheckStd()` need **no change** — they are already generic
  over `platform.pathSegment` / `version.luacheckStd` (`Target.kt:65,73`). Update the KDoc
  mapping list at `Target.kt:29` to note `Valkey (all versions) -> LUA51`.

### 2.4 Valkey stub resources **[NEW]**
- **Responsibility**: Provide resolvable `server.*`, `SERVER_*`, and compatibility `redis.*` /
  `KEYS` / `ARGV` symbols for Valkey targets.
- **Threading**: Static resources; loaded by `RuntimeLibraryProvider` (read action / indexing).
- **Collaborators**: `RuntimeLibraryProvider.getLibraryRoot` (unchanged),
  `PlatformLibraryProvider.getPackageFiles` (`project/PlatformLibraryProvider.kt:120`; treats
  `global.lua`/`builtin.lua` as package-root `""`, other files as their basename package).
- **Layout** — two dirs `src/main/resources/runtime/valkey/valkey-7.2/` and `…/valkey-8/`, each
  containing:
  | File | Content | Source |
  |------|---------|--------|
  | `redis.lua` | `---@class redis` + `redis = {}` + all members | byte copy of `runtime/redis/redis-7/redis.lua` (AC-4 compat namespace) |
  | `global.lua` | `KEYS`/`ARGV` `---@type string[]` | byte copy of `runtime/redis/redis-7/global.lua` |
  | `cjson.lua`, `cmsgpack.lua`, `bit.lua`, `struct.lua`, `os.lua` | shared sandbox libs | byte copies of the `redis-7` files |
  | `server.lua` | `---@class server : redis` + `server = {}` (see §3.2) | **[NEW]** — inherits the full `redis` surface; declares no per-member `---@field` (inheritance provides them) |
  | `server_global.lua` | `SERVER_NAME`/`SERVER_VERSION`/`SERVER_VERSION_NUM` globals (see §3.2) | **[NEW]** |
- **Rationale for copies not symlinks**: the resource tree is flat per-target (each other target
  dir is self-contained today, e.g. `runtime/standard/lua-5.*`); `RuntimeLibraryProvider` lists a
  single dir's `children` (`RuntimeLibraryProvider.kt:38-42`) with no cross-dir include. Copying
  the base keeps that invariant; the *only* hand-authored, non-duplicated surface is `server.lua`
  (inherits via `---@class server : redis`) and `server_global.lua` (RISK-R06 single-overlay
  intent is honoured at the `server` layer, which is the part that can drift).

### 2.5 `net.internetisalie.lunar.redis.connection.LuaRedisServerFlavor` **[NEW]**
- **Responsibility**: Parse an `INFO server` reply body into a `(flavor, version)` pair; centralize
  the flavor heuristic REDIS-01 §4.3 declared inline.
- **Threading**: Pure function on a `String`; callable on the REDIS-01 pooled coroutine.
- **Collaborators**: called by REDIS-01's Test-Connection / handshake path (§7 amendment) with the
  `INFO server` bulk-string body; result compared to `LuaProjectSettings…getTarget().platform`.
- **Key API**:
  ```kotlin
  enum class ServerFlavor { REDIS, VALKEY }
  data class ServerFlavorInfo(val flavor: ServerFlavor, val version: String)  // version = "8.0.1"
  object LuaRedisServerFlavor {
      fun detect(infoServerBody: String): ServerFlavorInfo   // §3.3
      fun mismatches(detected: ServerFlavor, target: LuaPlatform): Boolean  // §3.3
  }
  ```

### 2.6 `net.internetisalie.lunar.redis.connection.LuaRedisFlavorWarning` **[NEW]**
- **Responsibility**: Show a single non-modal warning per session when a connection's detected
  flavor mismatches the project target platform.
- **Threading**: Notification dispatch on EDT (`Notifications.Bus`/`NotificationGroupManager`);
  the session-guard set is a project-service field guarded for thread-safety.
- **Collaborators**: `NotificationGroupManager.getInstance().getNotificationGroup(
  "notification.group.lunar.tools")` (existing group, `plugin.xml:540`); `LuaRedisServerFlavor`;
  called from the REDIS-01 connect path (§7 amendment).
- **Key API**:
  ```kotlin
  @Service(Service.Level.PROJECT)
  class LuaRedisFlavorWarning(private val project: Project) {
      fun warnOnceIfMismatch(connectionId: String, detected: ServerFlavor, target: LuaPlatform)
      companion object { fun getInstance(project: Project): LuaRedisFlavorWarning = project.service() }
  }
  ```
  `warnOnceIfMismatch` no-ops unless `LuaRedisServerFlavor.mismatches(detected, target)`; then it
  atomically adds `connectionId` to a `MutableSet<String>` and shows the notification only if the
  id was newly added (§3.4). No hard `Project`/`Editor` field beyond the constructor-injected
  `Project` (contract §4: light service).

### 2.7 `net.internetisalie.lunar.analysis.inspections.LuaValkeyPortabilityInspection` **[NEW]**
- **Responsibility**: Under a Redis project target, flag `server.<member>` member access and
  `SERVER_NAME`/`SERVER_VERSION`/`SERVER_VERSION_NUM` name references as Valkey-only
  (non-portable). Silent under Valkey and non-Redis targets.
- **Threading**: `buildVisitor` runs in a read action on the inspection thread (platform-managed);
  no I/O.
- **Collaborators**: `LuaProjectSettings.getInstance(holder.project).state.getTarget().platform`
  (`settings/LuaProjectSettings.kt:90`); `LuaVisitor` (`gen/…/LuaVisitor.java`); PSI types
  `LuaNameRef`, `LuaIndexExpr`, `LuaVarSuffix`, `LuaVar`, `LuaElementTypes.DOT` (all grounded via
  `LuaNameReference.kt:142-171`); `LuaValkeyToRedisQuickFix` (§2.8).
- **Key API**:
  ```kotlin
  class LuaValkeyPortabilityInspection : LocalInspectionTool() {
      override fun getShortName(): String = "LuaValkeyPortability"
      override fun getGroupDisplayName(): String = "Lua"
      override fun getDisplayName(): String = "Valkey-only API under Redis target"
      override fun isEnabledByDefault(): Boolean = true
      override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WARNING
      override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor // §3.5
  }
  ```
- **Registration guard**: the visitor early-returns an empty `PsiElementVisitor.EMPTY_VISITOR`
  unless the target platform is `LuaPlatform.REDIS` (target read once in `buildVisitor`, not per
  element).

### 2.8 `net.internetisalie.lunar.analysis.inspections.LuaValkeyToRedisQuickFix` **[NEW]**
- **Responsibility**: Rewrite a flagged `server.<member>` access to `redis.<member>` by replacing
  the base `server` identifier leaf.
- **Threading**: `applyFix` wraps the PSI edit in `WriteAction.run` (contract §1), matching
  `LuaAddToGlobalsQuickFix.kt:17`.
- **Collaborators**: `LuaElementFactory.createIdentifier(project, "redis")`
  (`lang/psi/LuaElementFactory.kt:12`, returns a bare identifier leaf).
- **Key API**:
  ```kotlin
  class LuaValkeyToRedisQuickFix : LocalQuickFix {
      override fun getFamilyName(): String = "Replace 'server' with 'redis'"
      override fun applyFix(project: Project, descriptor: ProblemDescriptor) // §3.6
  }
  ```
  Offered **only** for the `server.<member>` case (a 1:1 compat rename exists). **Not** offered for
  `SERVER_*` globals — no `redis` equivalent (§3.5, TC-INSP-2).

## 3. Algorithms

### 3.1 Registry / target derivation (no new logic — data only)
- **Input → Output**: `(LuaPlatform.VALKEY, versionLabel)` → `Target` via the unchanged
  `resolveTarget`/`findVersion` path (`PlatformVersionRegistry.kt:68,81`).
- **Rules**: `getLibraryRootPath()` yields `runtime/valkey/valkey-7.2` / `runtime/valkey/valkey-8`
  (§2.4 dirs exist → `RuntimeLibraryProvider.getLibraryRoot` non-null). `getImplicitLanguageLevel`
  → `LUA51` (§2.3). `getLuacheckStd` → `"redis7"` (§2.2). No fallback/empty path (both dirs are
  bundled).
- **Migration edge (AC-2)**: an old `lunar.xml` never contained `VALKEY`, so no tag becomes invalid.
  A `lunar.xml` newly written with `platform="VALKEY"` deserializes via `TargetState.toTarget()`
  (`LuaProjectSettings.kt:36-40`): `findVersion(VALKEY, label)` → the entry, else
  `defaultVersion(VALKEY)` → `"7.2"`. Unknown label → `"7.2"` (graceful), never null-throws.

### 3.2 Stub authoring — `server.lua` and `server_global.lua`
- **`server.lua`** (both version dirs, identical):
  ```lua
  ---A Valkey compatibility alias for the `redis` API. All members mirror `redis.*`.
  ---@class server : redis
  server = {}
  ```
  Inheriting `: redis` makes every `redis` field/method resolve as a `server` member
  (`LuaTypeManagerImpl.kt:191-193` superTypes) — no per-member re-declaration (AC-3 no-dup). If a
  reviewer requires explicit members for hover docs, the fallback is to copy the `function
  server.<x>(...)` block set from `redis.lua`; the inheritance form is preferred and is the
  authored default.
- **`server_global.lua`** (both dirs, identical):
  ```lua
  ---The server software name ("valkey").
  ---@type string
  SERVER_NAME = ""

  ---The server software version (e.g. "8.0.0").
  ---@type string
  SERVER_VERSION = ""

  ---The numeric server version (e.g. 80000).
  ---@type number
  SERVER_VERSION_NUM = 0
  ```
  Mirrors the `global.lua` `---@type` idiom (`runtime/redis/redis-7/global.lua:1-8`). Named
  `server_global.lua` (not `global.lua`) so it does **not** collide with the copied `global.lua`
  (`KEYS`/`ARGV`); both are treated as package-root only if named exactly `global.lua`/`builtin.lua`
  (`PlatformLibraryProvider.kt:125`), so `server_global.lua` is registered as package `"server_global"`
  — its declarations are still global assignments (`SERVER_NAME = ""`) and resolve as globals via
  the global index regardless of package name (matches how `global.lua`'s `KEYS` resolves).

### 3.3 Flavor detection (`LuaRedisServerFlavor.detect` / `.mismatches`)
- **Input → Output**: `detect(infoServerBody: String)` → `ServerFlavorInfo`.
- **`detect` steps**:
  1. Split `infoServerBody` on `"\n"`; for each line, `trimEnd('\r')`.
  2. Build a lookup: for lines containing `':'`, `key = line.substringBefore(':')`,
     `value = line.substringAfter(':').trim()`.
  3. If a `valkey_version` key is present → `flavor = VALKEY`, `version = valkey_version`'s value.
  4. Else if `redis_version` present → `flavor = REDIS`, `version = redis_version`'s value.
  5. Else → `flavor = REDIS`, `version = ""` (conservative default; matches REDIS-01 §4.3 which
     also reads `redis_version` as the primary key).
- **`mismatches(detected, target)` steps** (exact table; `target` = project platform):
  | detected \ target | REDIS | VALKEY | other |
  |-------------------|-------|--------|-------|
  | REDIS  | false | true  | false |
  | VALKEY | true  | false | false |

  Rule: mismatch is `true` only when both sides are a known flavor and they disagree
  (`(target == REDIS && detected == VALKEY) || (target == VALKEY && detected == REDIS)`). Any other
  target (STANDARD, LUAJIT, …) → `false` (no warning; the user has not opted into a Redis/Valkey
  target).
- **Edge**: `valkey_version` takes precedence when *both* keys appear (Valkey emits both for compat
  — TC-FLV-1); empty body → `(REDIS, "")`, `mismatches` false vs a Redis target.

### 3.4 Once-per-session warning (`LuaRedisFlavorWarning.warnOnceIfMismatch`)
- **Input**: `(connectionId: String, detected: ServerFlavor, target: LuaPlatform)`.
- **Steps**:
  1. If `!LuaRedisServerFlavor.mismatches(detected, target)` → return.
  2. `if (!shownConnectionIds.add(connectionId)) return` — `add` returns `false` if already
     present (session-idempotent per connection). `shownConnectionIds` is a
     `java.util.concurrent.ConcurrentHashMap.newKeySet<String>()` field on the project service
     (thread-safe; cleared only on service dispose = "per session").
  3. Build a notification: title "Redis/Valkey flavor mismatch", content e.g. "Connected server is
     Valkey but the project target is Redis. `server.*` APIs will not be flagged as expected —
     consider a Valkey target." `NotificationType.WARNING`.
  4. `NotificationGroupManager.getInstance().getNotificationGroup("notification.group.lunar.tools")
     .createNotification(content, WARNING).notify(project)` on EDT.
- **Rules**: keyed on `connectionId` (not flavor) so reconnecting the same connection stays quiet;
  a *different* connection with the same mismatch warns once too. Wrapped in try/catch → `Logger`
  (contract §2 error bounding); a notification failure never breaks the connect flow.

### 3.5 Portability inspection visitor (`LuaValkeyPortabilityInspection.buildVisitor`)
- **Input → Output**: PSI file under inspection → `ProblemsHolder` registrations.
- **Guard**: `val target = LuaProjectSettings.getInstance(holder.project).state.getTarget().platform;
  if (target != LuaPlatform.REDIS) return PsiElementVisitor.EMPTY_VISITOR` (AC-7: silent under
  Valkey/STANDARD/etc.). Read once per visitor build.
- **Visitor** (`object : LuaVisitor()`):
  - `visitNameRef(o: LuaNameRef)`:
    1. `val name = o.identifier.text`.
    2. **Global case**: if `name in SERVER_GLOBALS` (= `{"SERVER_NAME","SERVER_VERSION",
       "SERVER_VERSION_NUM"}`) **and** `o` is not itself a member segment of an index expr (i.e.
       `o.parent !is LuaIndexExpr`, mirroring `LuaDeprecatedApiInspection.isDeclaration` intent) →
       `holder.registerProblem(o, "`$name` is a Valkey-only global and is not portable to Redis",
       ProblemHighlightType.GENERIC_ERROR_OR_WARNING)` (no quick fix).
    3. **Base-of-server-access case**: if `name == "server"` and `o` is the **base** name-ref of a
       dotted access `server.<member>` — detected exactly as `LuaNameReference.getQualifiedName`
       does: `o.parent is LuaVar` whose `varSuffixList.first()` is a `LuaVarSuffix` wrapping a
       `LuaIndexExpr` that `findChildByType(LuaElementTypes.DOT) != null` — then
       `holder.registerProblem(o, "`server.*` is a Valkey-only namespace and is not portable to
       Redis", ProblemHighlightType.GENERIC_ERROR_OR_WARNING, LuaValkeyToRedisQuickFix())`.
  - No visit fires for `redis.*` (TC-INSP-6) or under a non-Redis target (TC-INSP-4/5).
- **Rules / edge handling**:
  - Only the **base** `server` identifier is flagged (one problem per access), not the member leaf
    — so `server.call` reports once, on `server`.
  - A local variable literally named `server` that is *not* used as a dotted base does not match
    the base-of-access shape (its parent is not a `LuaVar` with a DOT suffix) → no false positive.
  - `SERVER_*` used as a table member (`t.SERVER_NAME`) is excluded by the `parent !is LuaIndexExpr`
    check.
- **Constants**: `private val SERVER_GLOBALS = setOf("SERVER_NAME","SERVER_VERSION",
  "SERVER_VERSION_NUM")`.

### 3.6 Quick fix (`LuaValkeyToRedisQuickFix.applyFix`)
- **Input**: `ProblemDescriptor` whose `psiElement` is the flagged base `server` `LuaNameRef`.
- **Steps**:
  1. `val serverRef = descriptor.psiElement as? LuaNameRef ?: return`.
  2. `val identifierLeaf = serverRef.identifier` (the `IDENTIFIER` leaf).
  3. `WriteAction.run<RuntimeException> { val redisId =
     LuaElementFactory.createIdentifier(project, "redis") ?: return@run;
     identifierLeaf.replace(redisId) }`.
- **Rules**: replaces only the base identifier leaf, leaving `.` + member + args intact →
  `server.call("PING")` becomes `redis.call("PING")` (TC-INSP-3). Null factory result → no-op (no
  `!!`). Idempotent (re-running on `redis.*` won't match the inspection, so no fix is offered).

## 4. External Data & Parsing

### 4.1 `INFO server` reply body
- **Format**: a RESP bulk string of `key:value` lines separated by `\r\n`, e.g.
  ```
  # Server
  redis_version:7.4.0
  redis_mode:standalone
  ```
  Valkey additionally emits `valkey_version:8.0.1` (and keeps `redis_version:7.2.x` for compat).
  Comment lines start with `#`; they contain no `:` value pair the parser cares about (the `#
  Server` line has no `:` → skipped by step 2).
- **Parse strategy**: line-split + `substringBefore/After(':')` (§3.3); identical framing to
  REDIS-01 §4.3 (this component *is* that parse, extracted).
- **Maps to**: `ServerFlavorInfo`.
- **Failure handling**: missing both version keys → `(REDIS, "")` (no throw); the caller's mismatch
  check then no-ops for a Redis target.

REDIS-03 consumes no other external/unstructured input (stubs are our own resources; the wire
protocol itself is owned by REDIS-01).

## 5. Data Flow

### Example 1: Author `server.call` under a Redis target
1. User sets/derives a Redis target; opens `x.lua` containing `server.call("PING")`.
2. `LuaValkeyPortabilityInspection.buildVisitor` reads target = `REDIS` → active.
3. `visitNameRef(server)` matches the base-of-access shape (§3.5 case 3) → WARNING + quick fix.
4. User invokes "Replace 'server' with 'redis'" → §3.6 replaces the leaf → `redis.call("PING")`;
   re-inspection finds nothing (TC-INSP-3, TC-INSP-6).

### Example 2: Switch to a Valkey target
1. Env/target resolves to `Target(VALKEY, "8")` (`LuaTargetSynchronizer` → `resolveTarget`).
2. `PlatformLibraryProvider` reloads; `RuntimeLibraryProvider.getLibraryRoot` →
   `runtime/valkey/valkey-8` (§2.4). `server.lua`/`server_global.lua`/`redis.lua`/`global.lua`
   index.
3. `server.call`, `SERVER_NAME`, `redis.call`, `KEYS` all resolve (TC-STUB-1..4).
4. The inspection's guard reads target = `VALKEY` → `EMPTY_VISITOR`; no warnings (TC-INSP-4).

### Example 3: Connect to a Valkey server under a Redis target (REDIS-01 seam)
1. REDIS-01 connect path issues `INFO server`; passes the body to
   `LuaRedisServerFlavor.detect` (§7 amendment) → `(VALKEY, "8.0.1")`.
2. REDIS-01 calls `LuaRedisFlavorWarning.getInstance(project).warnOnceIfMismatch(connId, VALKEY,
   REDIS)` → mismatch true, id newly added → one WARNING notification (TC-FLV-1, TC-FLV-3).
3. Reconnecting the same connection → `add` returns false → silent (TC-FLV-3).

## 6. Edge Cases

- **Both version keys present** (Valkey emits `redis_version` + `valkey_version`): `valkey_version`
  wins (§3.3 step 3) → correctly `VALKEY` (TC-FLV-1).
- **`server` as a plain local** (`local server = {}; server.foo()`): still matches the dotted-base
  shape and *would* warn. This is acceptable and consistent with the feature intent (any `server.*`
  dotted access under a Redis target is non-portable); documented in risks-and-gaps Gap 2.1. The
  narrow guard (`name == "server"` + DOT suffix) keeps unrelated locals unaffected when not dotted.
- **`SERVER_NAME` shadowed by a local**: the inspection flags name references by text; a local
  assignment `local SERVER_NAME = 1` — the assignment target is a `LuaNameRef` whose parent is a
  declaration context, not an `LuaIndexExpr`. It would be flagged. Rare; documented as accepted
  (Gap 2.1) — matches the deliberately simple text-based `SERVER_GLOBALS` rule (no resolve call,
  keeping the inspection cheap and I/O-free).
- **Valkey `redis.lua` copy vs Redis base drift** (RISK-R06): both are byte copies at authoring
  time; a checklist item (risks-and-gaps) re-syncs them when the Redis base changes.
- **No live connection at edit time**: the inspection is purely target-driven (settings read), never
  touches the network (contract §1: no I/O in inspection).

## 7. Integration Points

### 7.1 plugin.xml — inspection registration (mirrors `plugin.xml:202-209`)
```xml
<!-- plugin.xml, in the existing <extensions defaultExtensionNs="com.intellij"> block -->
<localInspection
        language="Lua"
        shortName="LuaValkeyPortability"
        displayName="Valkey-only API under Redis target"
        groupName="Lua"
        enabledByDefault="true"
        level="WARNING"
        implementationClass="net.internetisalie.lunar.analysis.inspections.LuaValkeyPortabilityInspection"/>
```

### 7.2 plugin.xml — flavor-warning project service
```xml
<projectService
        serviceImplementation="net.internetisalie.lunar.redis.connection.LuaRedisFlavorWarning"/>
```
(Reuses the existing `notification.group.lunar.tools` group, `plugin.xml:540` — no new
`notificationGroup` needed. If REDIS-03 lands before/without REDIS-01 wiring, the service and
`LuaRedisServerFlavor` are still independently unit-testable.)

### 7.3 REDIS-01 seam amendment (requested — see risks-and-gaps DR-01)
REDIS-01 design §4.3 derives flavor inline in Test-Connection. REDIS-03 requests that REDIS-01:
- call `LuaRedisServerFlavor.detect(infoServerBody)` instead of its inline `valkey_version`
  presence check (single source of truth), and
- after a successful connect, call
  `LuaRedisFlavorWarning.getInstance(project).warnOnceIfMismatch(connection.id, detected.flavor,
  LuaProjectSettings.getInstance(project).state.getTarget().platform)`.

This is the only cross-feature change. It is additive to REDIS-01 (the Test-Connection version/flavor
string is computed from the same `ServerFlavorInfo`). If REDIS-01 ships first, this is a small edit;
if REDIS-03 ships first, `LuaRedisServerFlavor`/`LuaRedisFlavorWarning` exist and REDIS-01 wires them
at its connect site. **No REDIS-01 requirement changes** — only its design §4.3 note ("REDIS-03
replaces this heuristic with `SERVER_NAME`") is realized; REDIS-03 uses `INFO server` (not
`SERVER_NAME`) for detection, which is strictly more available than reading a script global — the
§4.3 note is amended to say "REDIS-03 centralizes the `INFO server` heuristic in
`LuaRedisServerFlavor`."

### 7.4 Registry / enum / target — no plugin.xml
`LuaPlatform.VALKEY`, the registry entry, and the `Target` branch are pure code; no registration.
The stub resources are picked up by the existing `AdditionalLibraryRootsProvider`
(`PlatformLibraryProvider`, already registered) — no new EP.

## 8. Requirement Coverage

| Requirement (AC) | Priority | Implemented by (section) |
|------------------|----------|--------------------------|
| AC-1 VALKEY registry entries | S | §2.1, §2.2, §2.3, §3.1 |
| AC-2 platform enumeration + migration | S | §2.1, §2.2, §3.1 |
| AC-3 `server.*` + `SERVER_*` stubs (inherit, no dup) | S | §2.4, §3.2 |
| AC-4 `redis.*` / `KEYS` / `ARGV` under Valkey | S | §2.4, §3.2 |
| AC-5 flavor detect + once-per-session warning | S | §2.5, §2.6, §3.3, §3.4, §7.3 |
| AC-6 portability inspection + quick fix | S | §2.7, §2.8, §3.5, §3.6, §7.1 |
| AC-7 target-aware (silent under Valkey/non-Redis) | S | §2.7, §3.5 |
| AC-8 unit tests | S | all above; test list in implementation-plan Phase 5 |

## 9. Alternatives Considered

- **Symlink/shared stub base instead of copies** — rejected: `RuntimeLibraryProvider` lists a single
  dir's `children` with no include mechanism; a shared base would need a new resolver seam. Copies
  keep the existing invariant; only `server.lua` is genuinely new (RISK-R06 overlay honoured where
  it matters).
- **Explicit `---@field` members on `server`** instead of `---@class server : redis` — rejected as
  the default: duplicates the whole `redis` surface (violates AC-3 no-dup) and doubles RISK-R06
  drift surface. Inheritance is grounded (`LuaTypeManagerImpl.kt:191`); explicit members remain a
  documented fallback if hover-doc fidelity requires it.
- **Detect flavor via a script global (`SERVER_NAME`)** — rejected: requires running a probe script;
  `INFO server` is a plain command available on every server and already read by REDIS-01. (This
  supersedes the literal wording of REDIS-01 §4.3; see §7.3.)
- **Resolve the flagged member before warning (semantic inspection)** — rejected: a text-based
  `SERVER_GLOBALS`/`server`-base rule is I/O-free and cheap; the rare shadowing false positive
  (Gap 2.1) is an acceptable trade for keeping the inspection off the resolve path.

## 10. Open Questions

_None — feature has cleared the planning bar. The two cross-feature/authoring items (REDIS-01 seam
wiring, base-stub drift) are tracked as de-risking tasks DR-01/DR-02 in risks-and-gaps.md._
