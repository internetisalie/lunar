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
# Hard TTL (THE guarantee): GCE auto-stops the VM this long after it starts, regardless of
# activity, so worst-case billing for any leak/hang/forgotten VM is bounded to one TTL.
MAX_RUN_DURATION="${GCE_BUILDER_MAX_RUN_DURATION:-2h}"
# Idle auto-shutdown (a friendly early-stop): the VM powers itself off after this many minutes
# only when genuinely abandoned — no live SSH session AND low CPU load (see startup-script.sh).
# It will NOT kill the VM merely because no build is running this instant; an interactive or
# intermittent session keeps it up. Dead leaked sessions are reaped by sshd keepalive; a live-idle
# leak is bounded by the hard TTL above. Both actions STOP (not delete): disks persist, restartable.
IDLE_MINUTES="${GCE_BUILDER_IDLE_MINUTES:-10}"
# 1-min loadavg at/above which a connection-less VM still counts as "busy building" (covers a
# build whose SSH link dropped; an idle Gradle daemon sits well below this on an 8-vCPU box).
IDLE_LOAD_THRESHOLD="${GCE_BUILDER_IDLE_LOAD_THRESHOLD:-0.0}"
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

# ── Backend selection ────────────────────────────────────────────────────────
# libvirt = DEFAULT. A self-hosted, always-on VM on a KVM host (compute5), reached by plain
#           SSH. Provisioned once by hand (builder-bootstrap.sh); this wrapper only
#           sync/run/shell/start/stop/status against it — no cloud lifecycle, no idle-TTL
#           (self-hosted = no per-hour cost), no separate cache disk (the VM's own persistent
#           disk holds GRADLE_USER_HOME). See compute5-builder-setup.md.
# gce     = FALLBACK/alternative — the original GCE spot VM (all the gcloud/idle/TTL machinery
#           above). Opt in with: GCE_BUILDER_BACKEND=gce ./gce-builder.sh …
BACKEND="${GCE_BUILDER_BACKEND:-libvirt}"

# libvirt-backend settings (compute5 builder). REMOTE_JAVA_HOME=/opt/jdk and
# GRADLE_USER_HOME=/opt/cache/gradle above already match the bootstrapped VM.
LV_SSH_HOST="${LUNAR_BUILDER_HOST:-192.168.3.10}"
LV_SSH_USER="${LUNAR_BUILDER_USER:-builder}"
LV_SSH_KEY="${LUNAR_BUILDER_KEY:-$HOME/.ssh/pi.key}"
LV_LIBVIRT_URI="${LUNAR_BUILDER_LIBVIRT_URI:-qemu+ssh://ubuntu@compute5/system}"
LV_DOMAIN="${LUNAR_BUILDER_DOMAIN:-debian13}"
# On the libvirt backend the builder identity comes from LUNAR_BUILDER_USER/_KEY (via LV_SSH_*);
# this override intentionally wins over any GCE_BUILDER_REMOTE_USER/_SSH_KEY (those are gce-only).
if [ "$BACKEND" = "libvirt" ]; then
  REMOTE_USER="$LV_SSH_USER"
  SSH_KEY="$LV_SSH_KEY"
fi

# Repo root = two levels up from this script.
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

gc() { gcloud --project "$PROJECT" "$@"; }
