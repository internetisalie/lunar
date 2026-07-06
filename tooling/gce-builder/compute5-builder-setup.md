# Compute5 Linux Builder — Provisioning Runbook (`debian13` / `lunar-builder`)

End-to-end record of standing up the self-hosted Lunar build/test VM on the **compute5** KVM
host — now the **primary** builder. The `gce-builder.sh` wrapper drives it via its **default**
`BACKEND=libvirt` path (see [config.sh](config.sh) / [gce-builder.sh](gce-builder.sh)); the GCE
spot VM is the opt-in fallback (`GCE_BUILDER_BACKEND=gce`).

> **Provenance of the steps below.** Part A (host image + VM + base OS) was performed by the
> **operator** on compute5; the commands shown there are the standard reconstruction of each step
> (exact tooling was the operator's choice) and are corroborated by what was later observed on the
> running VM. Parts B–D were **executed by the agent** over SSH and are exact.

---

## Result — final state

| Property | Value |
|---|---|
| KVM host | **compute5** (`qemu+ssh://ubuntu@compute5/system`; SSH `~/.ssh/config`: `compute5` → `10.0.2.236`, user `ubuntu`, key `~/.ssh/pi.key`) |
| libvirt domain | **`debian13`** |
| vCPU / RAM | **4 / 16 GiB** (15 GiB usable) |
| Disk | 100 GB qcow2 (`default` pool); root `/dev/vda1` ext4 spans the full disk, ~93 GB free |
| Guest OS | **Debian 13 (trixie)**, hostname **`lunar-builder`** |
| NIC | `enp1s0`, **static `192.168.3.10/24`** (bridged to compute5 `br0`; MAC `52:54:00:13:dd:b6`) |
| Console | VNC on **`compute5:5901`** (`0.0.0.0`, no password) |
| Access | `ssh -i ~/.ssh/pi.key builder@192.168.3.10` (build user) / `root@192.168.3.10` (admin) |
| JDK | Amazon **Corretto 21** (21.0.11) → `/opt/jdk` |
| Other tooling | mingw-w64 GCC 14, lua5.4 (5.4.7) + lua-socket, fontconfig + fonts-dejavu-core |
| `GRADLE_USER_HOME` | `/opt/cache/gradle` (owned by `builder`) |

**cloud-init is disabled on this image** (`status: disabled — disabled-by-generator`). That is why
several first-boot conveniences (hostname, `growpart`, SSH key seeding) did **not** run
automatically and were done explicitly — see the notes inline.

---

## Part A — Host image, VM creation, base OS (operator, on compute5)

### A1. Download the Debian Trixie cloud image
Debian 13 `genericcloud` amd64 (virtio/KVM-optimized, cloud-init-enabled qcow2):
```bash
cd /var/lib/libvirt/images   # or the 'default' pool dir
wget https://cloud.debian.org/images/cloud/trixie/latest/debian-13-genericcloud-amd64.qcow2 \
     -O debian13.qcow2
# size the disk up-front (cloud-init growpart won't run — see note)
qemu-img resize debian13.qcow2 100G
```
> **Note (cloud-init disabled):** normally cloud-init runs `growpart`+`resize2fs` on first boot to
> fill the disk. With cloud-init disabled that never happens — but on this VM the root partition
> already spans the full 100 GB (`/dev/vda1`, ~93 GB free), so no manual `growpart` was needed.

### A2. Set the root password
Offline, into the image (no boot required):
```bash
sudo virt-customize -a debian13.qcow2 --root-password password:'<chosen-password>'
```

### A3. Install root's SSH `authorized_keys`
Seed the `pi.key` public key for `root` (this is the key the agent later used):
```bash
sudo virt-customize -a debian13.qcow2 \
     --mkdir /root/.ssh --chmod 0700:/root/.ssh \
     --upload ~/.ssh/pi.key.pub:/root/.ssh/authorized_keys \
     --chmod 0600:/root/.ssh/authorized_keys
```

### A4. Set the hostname — ⚠️ FORGOTTEN initially
This step was **intended but skipped** during initial prep, and because cloud-init is disabled it
was not auto-set either — so the VM first came up as `localhost`. It was corrected later, to
`lunar-builder`, during the agent bootstrap (Part B, `hostnamectl set-hostname lunar-builder`).
The offline equivalent would have been:
```bash
sudo virt-customize -a debian13.qcow2 --hostname lunar-builder
```

### A5. Create the VM (4 vCPU, 16 GB RAM)
Defined on compute5's libvirt with the qcow2 as its disk and a bridged NIC on `br0`
(the `192.168.3.x` segment), e.g.:
```bash
virt-install --name debian13 \
  --vcpus 4 --memory 16384 \
  --disk path=/var/lib/libvirt/images/debian13.qcow2,bus=virtio \
  --network bridge=br0,model=virtio \
  --graphics vnc,listen=0.0.0.0 \
  --os-variant debian13 \
  --import --noautoconsole
```
Observed result: domain `debian13`, `enp1s0` NIC (MAC `52:54:00:13:dd:b6`), VNC on
`compute5:5901` (`0.0.0.0`, no password). The VM shows a **blank serial console** (this image's
kernel routes console to VGA, not `ttyS0`), so boot state is viewed via VNC, not `virsh console`.

### A6. Static IP via netplan (`enp1s0` → `192.168.3.10`)
`/etc/netplan/60-static.yaml` on the guest (chmod 600), modern `routes:` syntax:
```yaml
network:
  version: 2
  renderer: networkd
  ethernets:
    enp1s0:
      dhcp4: false
      dhcp6: false
      addresses:
        - 192.168.3.10/24
      routes:
        - to: default
          via: 192.168.3.1
      nameservers:
        addresses:
          - 192.168.3.1
```
```bash
sudo chmod 600 /etc/netplan/60-static.yaml
sudo netplan apply
```
> Since Debian doesn't ship netplan by default, this implies `netplan.io` was installed (or the
> equivalent was expressed as a `systemd-networkd` `.network` file). Reachability confirmed: pings
> from the workstation (itself on the `.3` VLAN, `192.168.3.7`) and from compute5 (its `.3`-net
> address `192.168.3.236`). NB compute5 is multi-homed — `192.168.3.236` on the builder's segment,
> while the `~/.ssh/config`/`virsh` management path uses its `10.0.2.236` address (Result table).

### A7. Install `openssh-server`
```bash
sudo apt-get update && sudo apt-get install -y openssh-server
```

### A8. Generate SSH host keys
```bash
sudo ssh-keygen -A          # regenerate all host key types
```

### A9. Enable + start sshd
```bash
sudo systemctl enable --now ssh
```
> During initial verification the agent saw `:22` refused for ~1 minute, then it came up — sshd was
> just slow to start on this boot. Final state: `sshd` listening on `0.0.0.0:22`, `root` login with
> `pi.key` working.

---

## Part B — Guest bootstrap: build dependencies + JDK (agent)

Ran [builder-bootstrap.sh](builder-bootstrap.sh) as `root@192.168.3.10` — the de-GCP'd port of
[startup-script.sh](startup-script.sh) (no GCP idle/TTL/cache-disk machinery):

```bash
ssh -i ~/.ssh/pi.key root@192.168.3.10 'bash -s' < tooling/gce-builder/builder-bootstrap.sh
```

It installs/sets:
- **apt (`--no-install-recommends`)**: `git rsync wget curl ca-certificates python3 tar unzip
  fontconfig fonts-dejavu-core lua5.4 lua-socket gcc-mingw-w64-x86-64`; then `fc-cache -f`.
  - `fontconfig` + `fonts-dejavu-core` are **required** for headless editor/inlay tests (otherwise
    `RuntimeException: Fontconfig head is null`).
  - `lua5.4` + `lua-socket` back `TestLuaDebugHarness`.
  - `gcc-mingw-w64-x86-64` (GCC 14) supports the Windows Lua cross-build spike.
- **Corretto 21** (21.0.11) → `/opt/jdk`.
- **`/etc/profile.d/lunar-java.sh`**: exports `JAVA_HOME=/opt/jdk`, prepends it to `PATH`, sets
  `GRADLE_USER_HOME=/opt/cache/gradle`.
- **Hostname** → `lunar-builder` (corrects the skipped A4).
- **`~/bin/lua`** symlink → `lua5.4`, and creates `/opt/cache/gradle`.

Verified: `java -version` → Corretto 21.0.11; `x86_64-w64-mingw32-gcc` → 14; `lua5.4` → 5.4.7.

---

## Part C — Non-root build user (agent)

Builds must **not** run as root — JetBrains IDEs/IDE-Starter (`integrationTest`) degrade under root,
and Gradle recommends against it. Created a dedicated, **sudo-less** `builder` user reusing the same
`pi.key`:

```bash
ssh -i ~/.ssh/pi.key root@192.168.3.10 '
  useradd -m -s /bin/bash builder
  install -d -m700 -o builder -g builder /home/builder/.ssh
  cp /root/.ssh/authorized_keys /home/builder/.ssh/authorized_keys
  chown builder:builder /home/builder/.ssh/authorized_keys; chmod 600 /home/builder/.ssh/authorized_keys
  chown -R builder:builder /opt/cache/gradle
  install -d -o builder -g builder /home/builder/bin
  ln -sf "$(command -v lua5.4)" /home/builder/bin/lua && chown -h builder:builder /home/builder/bin/lua
'
```

Verified as `builder@192.168.3.10`: `uid=1000`, `JAVA_HOME=/opt/jdk` (Java 21.0.11),
`GRADLE_USER_HOME=/opt/cache/gradle` writable, `~/bin/lua` → 5.4.7.

---

## Part D — Wrapper backend integration (agent, in the repo)

Added a `BACKEND=libvirt` path to the existing wrapper (not a fork — keeps the one `sync`/`run`/
`shell` interface that CLAUDE.md, the skills, and `generate-parser` all call):

- **[config.sh](config.sh)** — `BACKEND` selector + `LUNAR_BUILDER_{HOST,USER,KEY,LIBVIRT_URI,DOMAIN}`
  defaults; when `libvirt`, overrides `REMOTE_USER=builder` and `SSH_KEY=~/.ssh/pi.key`.
  (`REMOTE_JAVA_HOME=/opt/jdk` and `GRADLE_USER_HOME=/opt/cache/gradle` already matched.)
- **[gce-builder.sh](gce-builder.sh)** — backend branches:
  - `ssh_exec` → plain `ssh -i pi.key builder@192.168.3.10` (parses the gcloud-style `--command`).
  - `external_ip` → static host; `instance_exists` → `virsh domstate … | grep running`.
  - `create` → reachability + JDK health-check; `status` → `virsh domstate` + node summary.
  - `start`/`stop` → `virsh start`/`shutdown` on compute5; `delete` → refused (persistent VM).
  - `sync`/`run`/`shell` unchanged — they flow through the abstracted primitives; all GCP
    idle-marker/TTL logic is inert on this backend.

### Usage
`libvirt` is the **default** backend — no env var needed:
```bash
tooling/gce-builder/gce-builder.sh status   # domstate + node summary
tooling/gce-builder/gce-builder.sh sync     # rsync working tree → builder@192.168.3.10:lunar
tooling/gce-builder/gce-builder.sh run test # sync + ./gradlew test
tooling/gce-builder/gce-builder.sh start|stop   # virsh start/shutdown debian13 on compute5

GCE_BUILDER_BACKEND=gce tooling/gce-builder/gce-builder.sh run test   # fallback → GCE spot VM
```

---

## Verification

- `status` → `debian13 running … lunar-builder — 4 cores, 15Gi ram, 93G free`.
- `create` → JDK present, bootstrap OK.
- `sync` → tree mirrored to `builder@192.168.3.10:lunar`.
- `run test` → Gradle 8.14.4 self-downloaded, daemon started, platform + suite run (validation).

---

## Notes & gotchas

- **cloud-init disabled** (`disabled-by-generator`): no first-boot hostname/growpart/key-seeding —
  hence the explicit hostname (A4/B) and the operator's manual root-key + sshd steps (A3/A7–A9).
- **Serial console is blank** (kernel console → VGA); use VNC (`compute5:5901`) for boot-time views.
  It binds `0.0.0.0` with **no VNC password** — LAN-open; lock down with a `<graphics>` `passwd` or
  rebind to `127.0.0.1` if desired.
- **Root fs already full-size** (~93 GB free) despite cloud-init being off — no resize needed.
- **mingw is GCC 14** here (vs GCC 12 on the old GCE box); the Windows cross-build spike passes an
  explicit `AR="x86_64-w64-mingw32-ar rcu"` to satisfy newer binutils.
- **VNC to drive `debian13` via the vnc MCP server** requires repointing local `:5900` (held by the
  win11 tunnel) at `compute5:5901` — but the builder is normally driven over **SSH**, not VNC.
