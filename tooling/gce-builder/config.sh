#!/usr/bin/env bash
# Shared configuration for the GCE spot build/test executor.
# Every value is overridable from the environment so nothing host-specific is hard-baked.
#
# The executor exists to run ./gradlew on a dedicated, UNCONTENDED spot VM — the local
# sandbox is a shared box (load ~18 on 20 cores) which makes test timings slow and noisy.

set -euo pipefail

PROJECT="${GCE_BUILDER_PROJECT:-cosmic-region-400615}"
ZONE="${GCE_BUILDER_ZONE:-northamerica-northeast1-a}"
INSTANCE="${GCE_BUILDER_INSTANCE:-lunar-builder}"
MACHINE="${GCE_BUILDER_MACHINE:-e2-standard-8}"        # 8 vCPU / 32 GB

# Spot (preemptible) — cheap, may be reclaimed. Builds are idempotent/restartable, so OK.
PROVISIONING_MODEL="${GCE_BUILDER_PROVISIONING_MODEL:-SPOT}"

# --- cost safeguards (auto-termination) ----------------------------------------------------
# Hard TTL: GCE auto-stops the VM this long after it starts, regardless of activity (belt).
MAX_RUN_DURATION="${GCE_BUILDER_MAX_RUN_DURATION:-4h}"
# Idle auto-shutdown: the VM powers itself off after this many minutes with no SSH session
# (suspenders — see startup-script.sh). Both actions STOP (not delete): disks persist, restartable.
IDLE_MINUTES="${GCE_BUILDER_IDLE_MINUTES:-30}"
TERMINATION_ACTION="${GCE_BUILDER_TERMINATION_ACTION:-STOP}"

# Boot disk (ephemeral — re-created with the VM).
BOOT_IMAGE_FAMILY="${GCE_BUILDER_IMAGE_FAMILY:-debian-12}"
BOOT_IMAGE_PROJECT="${GCE_BUILDER_IMAGE_PROJECT:-debian-cloud}"
BOOT_DISK_SIZE="${GCE_BUILDER_BOOT_DISK_SIZE:-40GB}"

# Persistent cache disk — SURVIVES VM delete/preemption. Holds GRADLE_USER_HOME, so the
# multi-GB IntelliJ/GoLand platform + dependency cache are downloaded once, not every run.
CACHE_DISK="${GCE_BUILDER_CACHE_DISK:-lunar-builder-cache}"
CACHE_DISK_SIZE="${GCE_BUILDER_CACHE_DISK_SIZE:-60GB}"
CACHE_DEVICE_NAME="lunar-cache"                         # stable /dev/disk/by-id/google-<name>
CACHE_MOUNT="/opt/cache"

# rsync transport: gcloud's generated key + the VM's external IP. Remote user matches the local
# one (gcloud OS-Login/metadata uses it; the bootstrap home was /home/$(id -un)).
REMOTE_USER="${GCE_BUILDER_REMOTE_USER:-$(id -un)}"
SSH_KEY="${GCE_BUILDER_SSH_KEY:-$HOME/.ssh/google_compute_engine}"

# Where the source tree is synced on the VM, and where the JDK is installed by bootstrap.
REMOTE_DIR="${GCE_BUILDER_REMOTE_DIR:-lunar}"
REMOTE_JAVA_HOME="${GCE_BUILDER_REMOTE_JAVA_HOME:-/opt/jdk}"
GRADLE_USER_HOME="${CACHE_MOUNT}/gradle"

# Repo root = two levels up from this script.
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

gc() { gcloud --project "$PROJECT" "$@"; }
