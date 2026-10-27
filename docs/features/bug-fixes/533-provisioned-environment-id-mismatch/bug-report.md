---
id: "BUG-533"
title: "A provisioned environment is registered under one id and recorded in its manifest under another, so every reopen offers to re-register it"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-533: provisioned environment id mismatch

Found by MAINT-44's HV-03 live check on GoLand 2026.2.3 (`runIde`), in the ROCKS-17 flow
(*New Project → LuaRocks* with *Provision isolated environment* ticked).

## 1. Reproduction
1. *File → New → Project… → LuaRocks*, tick *Provision isolated environment*, create the project.
2. Wait for the *Provisioned Lua toolchain 'lua 5.4' (2 tools)* balloon.
3. Compare `.idea/lunar.xml` (`LuaToolchainProjectSettings.environments[].id`) with
   `.lua/.lunar-env.json` (`environmentId`).
4. Close and reopen the project.

## 2. Expected vs Actual Behavior
- **Expected**: one id in both files; no notification on reopen.
- **Actual**: `lunar.xml` holds `5dde471a-…`, the manifest holds `7437459d-…`. On every reopen
  `LuaEnvRedetectionStartup` posts *Lunar environment 'lua 5.4' found at …/.lua but not registered*
  with a *Re-register* action. Clicking it does not help: the notification returns on the next
  reopen. The registered tools also carry the manifest's id as their `environmentId`, so they do not
  belong to the environment record they are listed in.

Evidence: `~/.cache/claude-scratch/lunar/maint-44/hv/r10.png` (provisioned balloon) and
`hv03-runIde-reopen-not-registered.png` (the reopen balloon).

## 3. Context / Environment
- `rocks/init/LuaRocksInterpreterInitializer.kt` (`scheduleProvision`)
- `toolchain/provision/LuaProvisionEngine.kt` (`manifestFor`)
- `toolchain/provision/LuaProvisionResultSink.kt` (`RegistryProvisionResultSink.register`)
- `toolchain/registry/LuaToolchainProjectSettings.kt` (`upsertEnvironment`)
- `toolchain/provision/LuaEnvRedetection.kt`, `LuaEnvRedetectionStartup.kt`

## 4. Root Cause
Two components each mint an id for the same `rootDir`, and the merge keeps the first one while
the manifest keeps the second.

1. `LuaRocksProjectGenerator.generateProject` → `LuaRocksInterpreterInitializer.scheduleProvision`
   calls `upsertEnvironmentAndActivate(LuaEnvironmentState(name, rootDir))` with a blank id.
   `LuaToolchainProjectSettings.upsertEnvironment` finds no match and assigns
   `UUID.randomUUID()` (**id A**, the one in `lunar.xml`). It then queues
   `LuaToolProvisioner.provision` with `runAfterOpened`.
2. `LuaToolProvisioner.provision` → `LuaProvisionEngine.execute` → `provisionAll` →
   `manifestFor` (`LuaProvisionEngine.kt:198`): there is no manifest yet, so
   `existing?.environmentId ?: UUID.randomUUID()` mints **id B**. Every per-item
   `LuaEnvManifest.write` records B.
3. `register` hands `LuaProvisionResult(environmentId = B, rootDir)` to
   `RegistryProvisionResultSink.register`, which tags each tool with B and calls
   `upsertEnvironmentAndActivate(LuaEnvironmentState(B, …, rootDir, toolIds))`.
   `upsertEnvironment` matches the existing record **by normalized `rootDir`** and builds
   `spec.copy(id = existing.id.ifBlank { spec.id })`, so the record keeps **A**.
4. On reopen, `LuaEnvRedetection.findOrphan` reads the manifest (B), finds B absent from the
   registered ids ({A}), and reports an orphan. *Re-register* runs the same sink with B, the same
   `rootDir` match keeps A, and nothing rewrites the manifest, so the orphan persists.

The same mismatch reaches the other entry points that provision a `rootDir` already registered
without a manifest:
- **Recreate Environment** (`LuaRecreateToolchainAction`) deletes the directory, manifest
  included, and re-provisions; the engine mints a fresh id while the record keeps its old one.
- **Re-register** (above) cannot repair an existing mismatch.

Unaffected: *Provision Toolchain…* and *Batch Provision* into a new directory (no record exists, so
the sink adds the engine's id), and *Change Versions* (the manifest exists and carries the
registered id). `LuaEnvironmentAdopter` registers discovered directories without provisioning or a
manifest and is out of scope.

## 5. Fix Strategy
Make the **registered environment record** the single source of truth for a `rootDir`'s id. It is
the id `upsertEnvironment` keeps anyway, and the one bindings and `activeEnvironmentId` refer to.

1. `LuaToolchainProjectSettings.environmentAt(rootDir)`: the record whose normalized `rootDir`
   matches, using the same `normalizeDir` comparison as `upsertEnvironment`.
2. `LuaProvisionResultSink.registeredEnvironmentId(project, rootDir): String?`, default `null`
   (spies keep today's behaviour); `RegistryProvisionResultSink` answers from `environmentAt`.
3. `LuaProvisionEngine.manifestFor` takes the id as
   `registered ?: existing manifest ?: new UUID`. When an existing manifest carries a different id,
   it is rewritten with the registered one immediately, so a re-provision whose items all skip still
   repairs a mismatched tree.
4. `RegistryProvisionResultSink.register` tags tools and upserts with
   `registeredEnvironmentId(project, rootDir) ?: result.environmentId`, so tools always carry the
   record's id whatever the caller passed.
5. *Re-register* moves into `LuaEnvReRegistration.reRegister(project, rootDir, manifest)`: it
   registers through the sink, then rewrites the manifest's `environmentId` to the registered id
   when they differ, so a project left mismatched by this bug is repaired by the notification's own
   action.

**A `rootDir` has one owner; the last project to provision it wins.** Environment records are
per project (`.idea/lunar.xml`), while the manifest lives in the directory. If two projects register
the same non-default `rootDir` (for example both point *Provision Toolchain…* at one shared
directory), each provision rewrites the manifest's id to its own record's id and tags the newly
registered tools with it, so the id flips between them and the other project's *Re-register* check
may fire. The TOOLING-02/04 designs do not provide for one directory serving several projects (the
"VCS-shared" case in TOOLING-02 is the same project on another machine), so this is a documented
limit rather than a follow-up.

The wizard keeps creating its record up front (the widget and the Re-register check both read it
before provisioning finishes); the engine now adopts that id instead of minting a second one.

## 6. Test Strategy
`LuaProvisionEnvironmentIdTest` (light fixture, real `RegistryProvisionResultSink` and project
settings, fake strategy, no network):
- **Wizard flow**: pre-register an environment at the root with a blank id (as
  `scheduleProvision` does), run the engine; the manifest id, the record id and every tool's
  `environmentId` are equal, and `LuaEnvRedetection.findOrphan` returns `null`. Red before the fix.
- **Recreate flow**: a registered record and no manifest; same assertions. Red before the fix.
- **Repair on re-provision**: a manifest carrying a stale id and a record with another; after a
  skip-all re-run the manifest carries the record's id. Red before the fix.
- **Re-register repair**: `LuaEnvReRegistration.reRegister` on a mismatched tree leaves no orphan.
- **New directory**: no record; the record is added with the manifest's id (guards against the
  fix breaking the plain *Provision Toolchain…* path).

## 7. Verification
- `LuaProvisionEnvironmentIdTest`: the wizard, recreate and repair cases failed before the fix with
  `the manifest records the registered id expected:<record id> but was:<engine id>`; all five pass
  after it. Disabling the manifest rewrite in `LuaEnvReRegistration` turns the re-register case red.
- Live, *New Project → LuaRocks* with provisioning: `.idea/lunar.xml` and `.lua/.lunar-env.json`
  carry the same id on GoLand 2026.2.3 (`runIde`) and 2026.1.3 (`runIde261`), and closing and
  reopening the project posts no *not registered* notification on either.
