# GCE spot build/test executor

A dedicated, **uncontended** Google Compute Engine spot VM for running `./gradlew` (build, test,
benchmarks). The local dev box is shared (observed load ~18 on 20 cores), which makes the
IntelliJ-platform test suite slow and its timings too noisy to measure reliably. This executor
gives stable, faster runs and lets the perf/benchmark suites mean something.

## Why spot + a persistent cache disk
- **Spot** (preemptible) is cheap and fine here — builds are idempotent and restartable. On
  preemption the VM is *stopped* (not deleted), so it can be restarted.
- A **persistent cache disk** holds `GRADLE_USER_HOME` (the multi-GB IntelliJ/GoLand platform and
  dependency cache), so a re-created VM doesn't re-download it. The disk survives `delete`.

## Prerequisites
- `gcloud` authenticated with access to the project (default `cosmic-region-400615`).
- Compute Engine API enabled and spot quota for the machine type in `northamerica-northeast1`.

## Usage
```bash
cd tooling/gce-builder
./gce-builder.sh create          # cache disk (if absent) + spot VM, wait for bootstrap
./gce-builder.sh run test        # sync working tree, then ./gradlew test on the VM
./gce-builder.sh run "test -PwithPerf"   # full suite incl. perf/benchmark
./gce-builder.sh run build       # compile + verifyPlugin + check
./gce-builder.sh status          # state, external IP, cache-disk presence
./gce-builder.sh stop            # pause to stop compute billing (cache disk persists)
./gce-builder.sh start           # resume
./gce-builder.sh delete          # remove the VM; cache disk kept
./gce-builder.sh delete --with-cache   # remove the VM AND the cache disk
```

`run` syncs the working tree first via **rsync over SSH, honoring `.gitignore`** (so generated
trees like `out/`, `build/`, `.gradle/` are skipped — only ~5 MB of source moves), then runs
Gradle. Local edits are picked up without needing a git remote. Sync uses the VM's external IP
and the gcloud-generated key (`~/.ssh/google_compute_engine`); set `GCE_BUILDER_TUNNEL_IAP=1` to
route command SSH over IAP (note: IAP throttles large uploads, so rsync still uses direct SSH).

## Configuration
All values are env-overridable (see `config.sh`), e.g.:
```bash
GCE_BUILDER_MACHINE=c2-standard-16 GCE_BUILDER_ZONE=northamerica-northeast1-b ./gce-builder.sh create
```

## Cost control & auto-termination safeguards
Spot is billed only while **running**. The design is *one hard guarantee plus a friendly
early-stop*, so worst-case billing is bounded and the VM isn't killed out from under active use:

1. **Hard TTL — the guarantee** — `--max-run-duration` (default `2h`) with
   `--instance-termination-action=STOP`: GCE auto-stops the VM that long after it starts,
   regardless of activity. This bounds worst-case billing for *any* failure (leak, hang, forgotten
   VM) to a single TTL — cents at spot pricing — so nothing below needs to be bulletproof.
2. **Idle early-stop (on-VM)** — a systemd timer (`lunar-idle.timer`, every 5 min) powers the VM
   off after `GCE_BUILDER_IDLE_MINUTES` (default `30`) **only when genuinely abandoned**: no live
   SSH session **and** CPU 1-min loadavg below `GCE_BUILDER_IDLE_LOAD_THRESHOLD` (default `1.0`).
   So an interactive or intermittent session keeps it up — it is *not* killed merely because no
   build is running this instant. Leak handling lives in the layers around it, not in refusing to
   count connections: `sshd` `ClientAliveInterval`/`CountMax` reap **dead/half-open** tunnels in
   ~6 min (a killed agent's leaked connection stops counting shortly after), the client sets
   `ServerAliveInterval` so a dying local process tears its own session down, and a **live-but-idle**
   leaked session is bounded by the hard TTL above.

   > Caveat on measurement: load is *sampled* every 5 min (the kernel's 1-min rolling average), not
   > continuously accounted — adequate because a `run`/`shell` holds its SSH session for the whole
   > duration (always observed) and a connection-less build is caught by the load test.
3. **Manual** — `stop` (halt compute billing) and `delete` / `delete --with-cache` (full teardown).

Both automatic actions **STOP** the VM (disks persist, `start` to resume), so nothing is lost —
only compute billing halts. The persistent cache disk still costs ~$6/mo until `delete --with-cache`.
Tune via `GCE_BUILDER_MAX_RUN_DURATION`, `GCE_BUILDER_IDLE_MINUTES`, `GCE_BUILDER_IDLE_LOAD_THRESHOLD`,
`GCE_BUILDER_TERMINATION_ACTION`.

> Note on leaked SSH sessions: prefer not to background a `run`/`shell` (e.g. `&` or an agent's
> run-in-background). A live idle session counts as "busy" and keeps the VM up until the hard TTL
> — bounded (cents), but wasteful. A *dead* backgrounded session is reaped by keepalive in ~6 min.

Optionally, set a project **billing budget alert** (account-level email warning):
```bash
gcloud billing budgets create --billing-account=<ID> --display-name="lunar-builder" \
  --budget-amount=10USD --threshold-rule=percent=0.5 --threshold-rule=percent=0.9
```

## Files
- `config.sh` — parameters (project, zone, machine, disks), env-overridable.
- `startup-script.sh` — idempotent VM bootstrap: mounts/formats the cache disk, installs Corretto 21.
- `gce-builder.sh` — the lifecycle CLI.
