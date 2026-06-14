#!/usr/bin/env bash
# Lifecycle helper for the GCE spot build/test executor. Mirrors the docker/ harness style.
#
#   ./gce-builder.sh create        # cache disk (if absent) + spot VM, wait for bootstrap
#   ./gce-builder.sh sync          # push the working tree to the VM (excludes build/.gradle/.git)
#   ./gce-builder.sh run [args...] # ./gradlew <args> on the VM (default: test); sync first
#   ./gce-builder.sh shell         # interactive SSH
#   ./gce-builder.sh status        # instance + cache-disk status, external IP
#   ./gce-builder.sh stop|start    # pause/resume to save cost (cache disk persists either way)
#   ./gce-builder.sh delete        # delete the VM (cache disk kept; add --with-cache to remove)
#
# All settings come from config.sh and are env-overridable (GCE_BUILDER_*).
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=config.sh
source "$DIR/config.sh"

log() { printf '\033[1;36m[gce-builder]\033[0m %s\n' "$*"; }
die() { printf '\033[1;31m[gce-builder] %s\033[0m\n' "$*" >&2; exit 1; }

instance_exists() { gc compute instances describe "$INSTANCE" --zone "$ZONE" >/dev/null 2>&1; }
disk_exists()     { gc compute disks describe "$CACHE_DISK" --zone "$ZONE" >/dev/null 2>&1; }
external_ip()     { gc compute instances describe "$INSTANCE" --zone "$ZONE" \
                      --format='value(networkInterfaces[0].accessConfigs[0].natIP)'; }
# --quiet makes SSH-key generation non-interactive (empty passphrase) so scripted runs never hang.
# Direct SSH by default: it's fast and, unlike IAP, doesn't throttle large piped uploads (the tar
# `sync` stream). Set GCE_BUILDER_TUNNEL_IAP=1 to route over IAP (no external IP) — note IAP
# uploads are slow without NumPy. A single deterministic method (no `||` fallback, which cannot
# replay piped stdin to a second attempt).
SSH_FLAGS=(--quiet)
[ -n "${GCE_BUILDER_TUNNEL_IAP:-}" ] && SSH_FLAGS+=(--tunnel-through-iap)
# Client keepalive: if THIS process is killed or the network drops, the server sees no responses
# and tears the session down (paired with sshd ClientAlive* on the VM) instead of leaving a
# half-open tunnel that the VM might mistake for activity. Extra args after `--` go to ssh(1).
SSH_KEEPALIVE=(-o ServerAliveInterval=60 -o ServerAliveCountMax=3 -o ConnectTimeout=30)
ssh_exec() { gc compute ssh "$INSTANCE" --zone "$ZONE" "${SSH_FLAGS[@]}" "$@" -- "${SSH_KEEPALIVE[@]}"; }

cmd_create() {
  if ! disk_exists; then
    log "Creating persistent cache disk $CACHE_DISK ($CACHE_DISK_SIZE)…"
    gc compute disks create "$CACHE_DISK" --zone "$ZONE" --size "$CACHE_DISK_SIZE" --type pd-balanced
  else
    log "Cache disk $CACHE_DISK already exists (reusing — keeps the warm Gradle/IDE cache)."
  fi

  if instance_exists; then
    log "Instance $INSTANCE already exists; starting it if stopped."
    gc compute instances start "$INSTANCE" --zone "$ZONE" >/dev/null 2>&1 || true
  else
    log "Creating $PROVISIONING_MODEL VM $INSTANCE ($MACHINE) in $ZONE…"
    gc compute instances create "$INSTANCE" \
      --zone "$ZONE" \
      --machine-type "$MACHINE" \
      --provisioning-model "$PROVISIONING_MODEL" \
      --instance-termination-action "$TERMINATION_ACTION" \
      --max-run-duration "$MAX_RUN_DURATION" \
      --image-family "$BOOT_IMAGE_FAMILY" --image-project "$BOOT_IMAGE_PROJECT" \
      --boot-disk-size "$BOOT_DISK_SIZE" --boot-disk-type pd-balanced \
      --disk "name=$CACHE_DISK,device-name=$CACHE_DEVICE_NAME,mode=rw,boot=no,auto-delete=no" \
      --metadata-from-file startup-script="$DIR/startup-script.sh" \
      --metadata "idle-minutes=$IDLE_MINUTES,idle-load-threshold=$IDLE_LOAD_THRESHOLD" \
      --scopes cloud-platform
    log "Safeguards: hard TTL ${MAX_RUN_DURATION} (action ${TERMINATION_ACTION}) + idle auto-shutdown ${IDLE_MINUTES}m."
  fi

  log "Waiting for bootstrap (JDK 21 + cache mount)…"
  for i in $(seq 1 40); do
    if ssh_exec --command "test -f /var/run/lunar-bootstrap-done" >/dev/null 2>&1; then
      log "Bootstrap complete. $(ssh_exec --command "$REMOTE_JAVA_HOME/bin/java -version" 2>&1 | head -1)"
      return 0
    fi
    sleep 15
  done
  die "Bootstrap did not finish in time; check: ./gce-builder.sh shell  then  sudo cat /var/log/lunar-bootstrap.log"
}

cmd_sync() {
  instance_exists || die "No instance; run: ./gce-builder.sh create"
  local ip; ip="$(external_ip)"
  [ -n "$ip" ] || die "Instance has no external IP (rsync transport needs one)."
  [ -f "$SSH_KEY" ] || die "SSH key $SSH_KEY missing — run './gce-builder.sh shell' once to generate it."
  log "Syncing working tree → $REMOTE_USER@$ip:$REMOTE_DIR via rsync (honoring .gitignore)…"
  # Honor the project's .gitignore (per-directory merge) so generated trees are skipped — out/
  # (IDE output, ~10GB), build/, .gradle/, idea-sandbox, etc. — without hand-maintaining a list.
  # .git itself isn't gitignored, so exclude it explicitly. --delete keeps the remote a mirror.
  rsync -az --delete \
    --filter=':- .gitignore' --exclude='/.git/' \
    -e "ssh -i '$SSH_KEY' -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o IdentitiesOnly=yes" \
    "$REPO_ROOT/" "$REMOTE_USER@$ip:$REMOTE_DIR/"
  log "Sync done."
}

cmd_run() {
  local args="${*:-test}"
  cmd_sync
  log "Running on $INSTANCE: ./gradlew $args"
  # Stamp the activity marker at start so the load-based idle check never trips during a build's
  # brief low-CPU startup window; the build's own CPU load keeps it alive thereafter.
  ssh_exec --command "touch /var/run/lunar-last-activity 2>/dev/null; cd '$REMOTE_DIR' && JAVA_HOME='$REMOTE_JAVA_HOME' GRADLE_USER_HOME='$GRADLE_USER_HOME' ./gradlew $args"
}

cmd_status() {
  instance_exists || { log "Instance $INSTANCE: NOT CREATED"; disk_exists && log "Cache disk $CACHE_DISK: exists"; return 0; }
  gc compute instances describe "$INSTANCE" --zone "$ZONE" \
     --format='table(name,status,machineType.basename(),scheduling.provisioningModel,scheduling.instanceTerminationAction,scheduling.maxRunDuration.seconds)'
  log "External IP: $(external_ip 2>/dev/null || echo n/a)"
  log "Safeguards: hard TTL ${MAX_RUN_DURATION} + idle auto-shutdown ${IDLE_MINUTES}m @ loadavg<${IDLE_LOAD_THRESHOLD} (maxRunDuration above is in seconds)."
  disk_exists && log "Cache disk $CACHE_DISK: present (persists across VM delete)."
}

cmd_shell()  { instance_exists || die "No instance."; ssh_exec; }
cmd_stop()   { instance_exists || die "No instance."; log "Stopping $INSTANCE…"; gc compute instances stop "$INSTANCE" --zone "$ZONE"; }
cmd_start()  { instance_exists || die "No instance."; log "Starting $INSTANCE…"; gc compute instances start "$INSTANCE" --zone "$ZONE"; }

cmd_delete() {
  if instance_exists; then
    log "Deleting VM $INSTANCE (cache disk kept unless --with-cache)…"
    gc compute instances delete "$INSTANCE" --zone "$ZONE" --quiet
  fi
  if [ "${1:-}" = "--with-cache" ] && disk_exists; then
    log "Deleting cache disk $CACHE_DISK…"
    gc compute disks delete "$CACHE_DISK" --zone "$ZONE" --quiet
  else
    disk_exists && log "Cache disk $CACHE_DISK retained (delete with: ./gce-builder.sh delete --with-cache)."
  fi
}

case "${1:-help}" in
  create) shift; cmd_create "$@" ;;
  sync)   shift; cmd_sync "$@" ;;
  run)    shift; cmd_run "$@" ;;
  status) shift; cmd_status "$@" ;;
  shell|ssh) shift; cmd_shell "$@" ;;
  stop)   shift; cmd_stop "$@" ;;
  start)  shift; cmd_start "$@" ;;
  delete) shift; cmd_delete "$@" ;;
  help|--help|-h) sed -n '2,20p' "${BASH_SOURCE[0]}" ;;
  *) die "Unknown command '${1}'. Try: ./gce-builder.sh help" ;;
esac
