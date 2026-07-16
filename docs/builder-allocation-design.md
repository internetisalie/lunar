---
id: "BUILDER-ALLOC"
title: "Builder VM Allocation Design"
type: "guide"
priority: "medium"
folders:
  - "[[features]]"
---

# Builder VM Allocation — Design

**Status:** Design / plan (not implemented). **Intent:** elastic multi-VM builds on the **GCE
backend**; keep the self-hosted **libvirt** VM as the cheap default, made safe by serialization.
Scripts live under [`tooling/gce-builder/`](../tooling/gce-builder/).

## 1. Problem

`gce-builder.sh run <task>` rsyncs the working tree to **one** VM and runs `./gradlew` there.
All sessions share the same three pieces of state, so two concurrent `run`s corrupt each other:

- one rsync target — `REMOTE_DIR=lunar` with `rsync … --delete`
  ([config.sh:51](../tooling/gce-builder/config.sh), [gce-builder.sh:116](../tooling/gce-builder/gce-builder.sh));
  a second sync mid-build rewrites the first's source tree;
- one `GRADLE_USER_HOME=/opt/cache/gradle` ([config.sh:53](../tooling/gce-builder/config.sh)) —
  concurrent Gradle daemons + a shared build cache / lock files;
- on GCE, one persistent `lunar-builder-cache` disk attached **RW to a single instance**
  ([config.sh:40](../tooling/gce-builder/config.sh),
  [gce-builder.sh:86](../tooling/gce-builder/gce-builder.sh) `mode=rw,boot=no`).

Observed symptom (documented hazard): concurrent runs produce **bogus mass test failures** from a
corrupted shared index, and killing one `run` does not stop the detached daemon on the VM. There is
**no lock** today. This has already forced a real stall — a TYPE-10 build had to be held so it would
not corrupt an in-flight SYNTAX-18 build.

## 2. Goals / Non-goals

**Goals**
- No cross-session corruption — a build never sees another session's tree/cache/daemon.
- Parallel builds when wanted, elastically, without pre-provisioning a fixed fleet.
- Keep the libvirt single-VM path as the zero-cost default; make it *correct* via serialization.
- Bounded cost and leak-safe (no forgotten running VMs).
- One interface for callers; the backend decides serialize-vs-parallel.

**Non-goals**
- Changing the build/test tasks themselves.
- A general CI system (that is Gitea Actions); this is the local/agent build executor.
- Multi-region / HA. One project, one zone.

## 3. The lease abstraction — session-scoped, NOT run-scoped

A single `gce-builder.sh run <task>` is **sub-atomic**. A real unit of work — an interactive debug
session, a supervised feature implementation, an agent task — is **dozens of `run`s** (`run build`,
`run test`, `run "test --tests *X*"`, iterate…). So the allocation unit is a **session**, not a
`run`. A session holds **one warm VM** for its whole duration; every `run` in it **reuses** that VM —
hot Gradle daemon, hot cache, no per-run create — exactly today's persistent-VM ergonomics. A `run`
resolves its session's lease and targets that VM:

```
lease = ensure_lease(session_key)      # create/warm a VM the FIRST run; REUSE it on every run after
rsync → $lease.target ; ssh $lease.target ./gradlew … ; capture logs
# NO teardown here — an individual run's success or failure does NOT touch VM lifecycle.
```

- **`session_key`** identifies the session: the **worktree/checkout path** by default (parallel agents
  already use separate worktrees per repo convention → separate VMs, no cross-agent collision),
  overridable by an explicit `GCE_BUILDER_LEASE` for finer control.
- **Teardown is idle-driven, never per-run.** The VM self-STOPs after `IDLE_MINUTES` with no `run`
  (the existing idle-shutdown, [config.sh:27](../tooling/gce-builder/config.sh)) and the reaper deletes
  it later; or an explicit `gce-builder.sh lease release`. So a session's dozens of runs all hit a
  warm VM, and a finished/walked-away session is reaped automatically — no success/failure branching.

Two backends implement `ensure_lease` / release:

| Backend | `ensure_lease(session_key)` | Parallelism | teardown |
|---|---|---|---|
| **libvirt** (default) | `flock` the single VM per session-key; concurrent *sessions* queue | none (one VM) | lock released on session end / idle |
| **gce** (opt-in) | one warm VM per session-key, reused across its runs, COW-seeded cache | elastic (a VM per concurrent session) | idle-STOP + reap; or `lease release` |

This matches the intent *and* today's ergonomics: the self-hosted single VM is best *serialized* per
session; the cloud gives one warm VM per concurrent session. The lease keeps callers identical across
both, and the warm VM is reused across a session's many runs rather than churned per run.

## 4. libvirt backend — serialize (immediate correctness, no cloud)

The single `debian13` VM ([config.sh:71](../tooling/gce-builder/config.sh)) can only safely run one
build at a time. Wrap the whole `run` in an advisory lock:

- `acquire`: `flock` on `~/.cache/gce-builder/<domain>.lock` (or a lease file **on the VM**, so it
  also guards a *different local checkout* hitting the same VM). Blocks until free; `--no-wait` to
  fail-fast instead.
- `release`: lock auto-releases when the holding process dies (flock semantics) → no stale locks even
  if a `run` is `kill -9`'d. Release should also best-effort `pkill -f GradleDaemon` on the VM before
  unlocking (the daemon is detached — killing the local `run` does not stop it).
- Result: concurrent `run`s **queue** instead of corrupting. No parallelism, but acceptable for a
  single self-hosted box, and it is the ~1-hour fix that ends the corruption today.

## 5. gce backend — elastic ephemeral pool (the parallel path)

**Model: one warm VM per session** (not per `run` — see §3). The **first** `run` of a session creates
a VM named for its session-key (`lunar-builder-<key-hash>`); **every subsequent `run` reuses it**
warm. Each session owns private state (its own VM, `REMOTE_DIR`, `GRADLE_USER_HOME`, COW disk), so
concurrent sessions never collide. The VM is created/started once and reused across the session's
dozens of runs, then idle-STOPped + reaped when the session ends — never churned per run.

### 5.1 Cache seeding — reuse `lunar-builder-cache` as a copy-on-write base (recommended)

A fresh VM needs `GRADLE_USER_HOME` = the multi-GB IntelliJ-platform + dependency cache; a cold
download is ~10 min. The single `lunar-builder-cache` disk attaches **RW to one VM only**, so it
cannot be shared across a pool directly. But GCE `disk create --source-snapshot` is **copy-on-write**:
a disk cloned from a snapshot shares the base's blocks and only diverges on write. So the existing
cache disk becomes the pool's COW base:

- Keep `lunar-builder-cache` as the **golden base**, warmed by a periodic full build.
- **Snapshot** it — you snapshot to clone (you cannot `--source-disk` a disk that is live RW-attached
  to a running VM).
- Each session: `gc compute disks create lunar-cache-<session> --source-snapshot <latest>` → a private,
  COW, pre-seeded `/opt/cache/gradle`; attach it RW to the session VM at `CACHE_MOUNT`
  ([config.sh:42–43](../tooling/gce-builder/config.sh)); reaped with the VM when the session is torn down (only the session's divergent blocks are discarded — the base is untouched).
- Re-snapshot after a dependency bump — a `refresh-cache-snapshot` command, or opportunistically. The
  first build off a slightly-stale snapshot does a small **incremental** download (acceptable).

Why this over a golden boot image: it **reuses the existing cache-disk model** (no custom machine
image to build/maintain), keeps the boot disk stock debian-12 ([config.sh:34](../tooling/gce-builder/config.sh)),
and confines each session's mutable state to a cheap COW data disk that shares blocks with the base
until written. It is also the most direct evolution of today's `--disk name=lunar-builder-cache,mode=rw`
attach ([gce-builder.sh:86](../tooling/gce-builder/gce-builder.sh)) — swap the fixed disk for a
per-session COW clone of its snapshot.

**Alternative (fallback): golden machine image.** Bake the warmed cache into a custom boot-disk image;
each VM boots pre-seeded, no separate cache disk. Same COW-at-clone property (an image→boot-disk is
lazy-copy) but adds image lifecycle. Prefer the COW cache-disk unless a stock cache disk proves
awkward to keep warm.

### 5.2 `ensure_lease(session_key)` — create-or-reuse (gce)

The common case is **reuse** (a warm VM already exists for this session); creation happens only on the
first run of a new session:

```
name = "lunar-builder-$(hash session_key)"
if instance_running(name):  return target(name)                  # ← the common path: warm reuse
if instance_stopped(name):  gc compute instances start "$name" ; return target(name)   # resume idle-STOPped
# else — first run of a new session; create:
while running_leases(label=lunar-builder-pool) >= MAX_POOL: wait/backoff   # cost/quota guard
gc compute disks create "lunar-cache-$name" --source-snapshot "$LATEST_CACHE_SNAPSHOT"   # COW seed (§5.1)
gc compute instances create "$name" \
   --machine-type "$MACHINE" --provisioning-model SPOT \
   --image-family "$BOOT_IMAGE_FAMILY" --image-project "$BOOT_IMAGE_PROJECT" \
   --disk "name=lunar-cache-$name,device-name=$CACHE_DEVICE_NAME,mode=rw,boot=no,auto-delete=yes" \
   --labels "lunar-builder-pool=1,session=$(hash session_key),owner=$(id -un),created=$(date +%s)" \
   --max-run-duration "$MAX_RUN_DURATION" --instance-termination-action STOP   # hard-TTL leak bound
wait-for-ssh ; return { ip, REMOTE_USER, REMOTE_DIR=lunar, GRADLE_USER_HOME=/opt/cache/gradle }
```

`auto-delete=yes` on the COW disk means deleting the VM reaps its cache clone too. There is **no
teardown in the `run` path** — the VM lives on for the session's next run. Teardown is idle-STOP + the
reaper (§5.3), or an explicit `lease release`.

### 5.3 Lifecycle / leak safety

Teardown is **per session, idle-driven** — the same shape as today's single VM, one per session. No
per-run success/failure branching:

1. **No per-run teardown.** The VM persists across the session's runs (warm reuse); an individual
   run's success or failure never creates or deletes a VM.
2. **Idle-STOP.** After `IDLE_MINUTES` with no `run` ([config.sh:27](../tooling/gce-builder/config.sh),
   existing idle-shutdown) the session VM self-STOPs — the session is over or paused. A STOPped spot VM
   costs only its disks; a later run under the same key `start`s it back up warm (§5.2).
3. **Reap.** `gce-builder.sh reap` deletes STOPped `lunar-builder-*` VMs (+ their `auto-delete` COW
   disks, + orphan `lunar-cache-*`) whose age exceeds `REAP_MAX_AGE` — a **generous ceiling**
   (e.g. 24–48h) as a leak backstop, **not** a short grace timer. Run opportunistically from `run`
   and/or a local cron.
4. **Explicit release.** `gce-builder.sh lease release [session]` STOP+deletes now (end-of-task
   cleanup); `--all` sweeps every session.
5. **Hard TTL** — the existing `MAX_RUN_DURATION` auto-STOP ([config.sh:21](../tooling/gce-builder/config.sh))
   bounds a *running* VM whose session was abandoned mid-build.
6. **Spot preemption** → the run fails → retry; the session VM (or a fresh one under the same key)
   comes back ([config.sh:15](../tooling/gce-builder/config.sh), builds are idempotent).

**Inspecting a failed run:** because the VM is session-scoped it stays **up and warm** after a
failure — `gce-builder.sh shell [session]` reads `idea.log` / reproduces, then you re-run on the same
VM. It only STOPs on idle and is reaped after the generous ceiling, so nothing races your inspection.
(This is why the earlier per-run "grace period / KEEP_FAILED" complexity is gone — session idle-reap
subsumes it, and warm reuse across the session's dozens of runs is preserved, matching today.)

### 5.4 Cost

Pay only for build minutes × N concurrent **sessions** on **spot** `e2-standard-8`
([config.sh:13,16](../tooling/gce-builder/config.sh)); idle reaping + hard TTL bound leaks. The COW
snapshot removes the cold-download cost (paid at most once per session, then warm reuse) that would otherwise make session VMs uneconomical, and a COW clone consumes only its divergent blocks. `MAX_POOL` + GCE quota cap the
blast radius on spend.

## 6. CLI / config changes (planned)

- `run` — transparently `ensure_lease(session_key)` then build on the session's warm VM (no caller
  change; no per-run teardown).
- New subcommands: `pool-status` (list session VMs + age/owner/state), `lease release [session|--all]`
  (end a session now), `reap` (delete STOPped/aged VMs + orphan COW disks),
  `refresh-cache-snapshot` (re-warm + re-snapshot the base).
- New config ([config.sh](../tooling/gce-builder/config.sh)): `GCE_BUILDER_MAX_POOL` (max concurrent
  session VMs, default e.g. 3), `GCE_BUILDER_CACHE_SNAPSHOT` (the base snapshot name/family),
  `GCE_BUILDER_LEASE` (session key — defaults to the worktree path; set explicitly to share or split a
  session), `GCE_BUILDER_REAP_MAX_AGE` (generous reap age-ceiling, e.g. 24–48h — a backstop, not a
  short grace). Backward-compatible: libvirt stays the default and simply gains per-session
  serialization; the GCE pool is opt-in via `GCE_BUILDER_BACKEND=gce`.

### 6.1 Implementation language (Bash glue, Python allocator)

Follow the repo's existing seam — `tooling/gce-builder/*` is Bash, `scripts/*.py` is Python:

- **Bash** for process/lifecycle orchestration: the `run` wrapper, the libvirt `flock` lock, lease
  acquire/resolve, and the VNC/scrot verification driving (`scrot` / `xdotool` / `ssh` / `rsync` /
  `gcloud` / `virsh` shell-outs — the verify-in-ide flow rides the session's VM over its ssh host, no
  MCP/tunnel). Keeps `gce-builder.sh` one language. **Phase 1 (the lock) is pure Bash.**
- **Python** for the pool allocator + reaper (Phase 3): parsing `gcloud … --format=json`, VM-age math,
  `MAX_POOL` enforcement, session-key→VM resolution, reap-by-age. A small `scripts/builder_pool.py`
  the Bash front-end shells into — matches the repo's "logic → Python" convention and stays testable.

Guidance: **don't split prematurely** — if the pool logic stays small, `gcloud --format='value(...)'`
+ a little Bash may suffice; introduce Python only when the JSON/age/cap logic earns it. And **don't
rewrite the working Bash lifecycle in Python** for uniformity's sake — that's churn for no gain.

## 7. Failure modes

| Failure | Mitigation |
|---|---|
| Concurrent corruption (today's bug) | isolation — one VM + COW cache per **session** (gce) or per-session lock (libvirt) |
| Per-run VM churn kills warm reuse | session-scoped VM: created once, **reused warm** across the session's dozens of runs (§3, §5.2) |
| Failed run needs inspection | session VM stays up + warm; `gce-builder.sh shell [session]`; only idle-STOPs, reaped after the generous ceiling (§5.3) |
| Leaked / abandoned session VM | idle-STOP + `reap` (age ceiling) + `auto-delete` COW disk + hard TTL + labels + `lease release` |
| Spot preemption mid-build | run fails → retry; session VM comes back under the same key (idempotent) |
| Cache snapshot stale after a dep bump | incremental download on first build; periodic `refresh-cache-snapshot` |
| GCE quota / budget blown | `MAX_POOL` cap on concurrent session VMs + queue/backoff; clear error when capped |
| Stale libvirt lock after `kill -9` | `flock` auto-releases on process death; release also `pkill GradleDaemon` |
| Cache disk RW single-attach (pool) | eliminated — each session gets its own COW clone of the base snapshot, not the shared disk |

## 8. Rollout phases

1. **libvirt `flock` serialization** — ends the corruption today, zero cloud, ~1 hr. *(Do this first;
   it alone unblocks the shared-VM stalls.)*
2. **gce session lease** + one warm VM per session with a **cold** cache — parallel but slow first
   builds; proves the allocation model end-to-end.
3. **COW cache-snapshot seeding** + `reap` + `MAX_POOL` — fast ephemeral builds, leak-safe,
   cost-bounded.
4. *(Optional)* **Warm pool** — keep K VMs running behind a lease registry if cold-start latency ever
   matters more than idle cost. Not needed initially.

## 9. Open questions

- **COW cache-snapshot (recommended) vs golden machine image** for seeding (§5.1) — confirm the
  snapshot-per-session approach; it reuses the existing cache disk.
- **`MAX_POOL` default** and the GCE **budget ceiling** — what spend/quota is acceptable?
- **Default backend for parallel work** — keep libvirt default (serial) and only opt into gce for
  bursts, or flip heavy/parallel workloads to gce automatically?
- **Who runs the reaper** — opportunistic on each `run`, a local cron, or both?
- **Cache-snapshot refresh cadence** — after every dependency bump, on a timer, or when an incremental
  first-build download exceeds a threshold?

## 10. Relationship to the current setup

This extends, not replaces, the existing two-backend design
([config.sh:55–77](../tooling/gce-builder/config.sh),
[compute5-builder-setup.md](../tooling/gce-builder/compute5-builder-setup.md)): libvirt remains the
always-on default (now serialized), and the GCE machinery (spot, hard TTL, idle-shutdown, cache disk)
is repurposed from a single fixed `INSTANCE` + RW cache disk into an elastic, uniquely-named,
COW-snapshot-seeded pool.
