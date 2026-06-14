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

## Cost control
Spot is billed only while **running**. `stop` halts compute billing; the small persistent-disk
charge continues until `delete --with-cache`. Always `stop` or `delete` when done.

## Files
- `config.sh` — parameters (project, zone, machine, disks), env-overridable.
- `startup-script.sh` — idempotent VM bootstrap: mounts/formats the cache disk, installs Corretto 21.
- `gce-builder.sh` — the lifecycle CLI.
