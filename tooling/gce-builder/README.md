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
Spot is billed only while **running**. Three layers keep it from billing when forgotten:

1. **Hard TTL (native)** — `--max-run-duration` (default `4h`) with `--instance-termination-action=STOP`:
   GCE auto-stops the VM that long after it starts, regardless of activity. The ultimate backstop.
2. **Idle auto-shutdown (on-VM)** — a systemd timer (`lunar-idle.timer`, every 5 min) powers the
   VM off after `GCE_BUILDER_IDLE_MINUTES` (default `30`) of no real build activity. Liveness is
   measured by **CPU loadavg** (busy ⇔ 1-min loadavg ≥ `GCE_BUILDER_IDLE_LOAD_THRESHOLD`, default
   `1.0`), **not** by SSH-connection presence — so a leaked/background SSH session (agents are
   known to not reap these) can't pin the VM alive. `sshd` `ClientAliveInterval`/`CountMax` also
   reap dead/half-open tunnels server-side, and the client sets `ServerAliveInterval` so a killed
   local process tears its session down rather than leaving a half-open socket. Handles "ran a
   build, walked away" and "an automation client leaked a connection."
3. **Manual** — `stop` (halt compute billing) and `delete` / `delete --with-cache` (full teardown).

Both automatic actions **STOP** the VM (disks persist, `start` to resume), so nothing is lost —
only compute billing halts. The persistent cache disk still costs ~$6/mo until `delete --with-cache`.
Tune via `GCE_BUILDER_MAX_RUN_DURATION`, `GCE_BUILDER_IDLE_MINUTES`, `GCE_BUILDER_IDLE_LOAD_THRESHOLD`,
`GCE_BUILDER_TERMINATION_ACTION`.

> Note on leaked SSH sessions: never background a `run`/`shell` (e.g. `&` or an agent's
> run-in-background). It's no longer *necessary* for safety — idle shutdown ignores connections —
> but a backgrounded interactive `shell` doing nothing will still be reaped after the idle window,
> and a backgrounded `run` whose build finished simply wastes the open socket until then.

Optionally, set a project **billing budget alert** (account-level email warning):
```bash
gcloud billing budgets create --billing-account=<ID> --display-name="lunar-builder" \
  --budget-amount=10USD --threshold-rule=percent=0.5 --threshold-rule=percent=0.9
```

## Files
- `config.sh` — parameters (project, zone, machine, disks), env-overridable.
- `startup-script.sh` — idempotent VM bootstrap: mounts/formats the cache disk, installs Corretto 21.
- `gce-builder.sh` — the lifecycle CLI.
