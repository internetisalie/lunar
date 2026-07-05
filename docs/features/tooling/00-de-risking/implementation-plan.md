---
id: "TOOLING-00-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "TOOLING-00"
folders:
  - "[[features/tooling/00-de-risking/requirements|requirements]]"
---

# TOOLING-00: Implementation Plan

Sequences the six spikes from [design.md](design.md). Order rationale: the cheap in-repo
checks land first (they gate the persistence and download designs of TOOLING-01/-04),
then the POSIX build chain (00-01 feeds 00-04), then the network-acquisition and
decision spikes. Every phase leaves the build green — shell spikes live outside the
Gradle source sets; the two test classes join the normal suite.

## Phases

### Phase 1: In-repo platform checks [Must]
- **Goal**: Prove the download-infra classpath and the clean-break persistence mechanism
  inside the unit suite; commit the feed format.
- **Tasks**:
  - [ ] Create `src/test/kotlin/net/internetisalie/lunar/toolchain/LuaProvisioningClasspathSpikeTest.kt`
        with fixture archives under `src/test/resources/toolchain/` — realizes design §2.5
  - [ ] Create the sample feed `src/main/resources/toolchain/toolchain-feed.json`
        (schema per design §2.5; placeholder pins until Phases 2–3 record them) — realizes design §2.5
  - [ ] Create `src/test/kotlin/net/internetisalie/lunar/toolchain/LuaToolchainSerializationSpikeTest.kt`
        with the spike state classes and the legacy-XML fixture — realizes design §2.6
  - [ ] Run `tooling/gce-builder/gce-builder.sh run test`; write
        `results/download-infra.md` and `results/clean-break-serialization.md`
- **Exit criteria**: TC 6 and TC 7 green in the suite; both results docs committed; any
  §2.6 fallback finding (field rename) recorded.

### Phase 2: POSIX build chain [Must]
- **Goal**: The source-build recipe and the C-rock install path proven end to end on the
  Linux builder.
- **Tasks**:
  - [ ] Create `tooling/spikes/tooling-00/build-lua-posix.sh` (download/verify per design
        §3.1; recipe per design §2.1 incl. the `luaconf.h` patch) — realizes design §2.1
  - [ ] Run it; verify TC 1 assertions (`lua -v`, baked `package.path`, no readline);
        write `results/posix-source-build.md` incl. the macosx flag-set and the recorded
        SHA-256 pin (also fill the pin into the feed sample) — realizes design §2.1
  - [ ] Create `tooling/spikes/tooling-00/install-crock.sh` (luarocks configure/make,
        Run A + Run B with `CC=/nonexistent/cc`) — realizes design §2.4
  - [ ] Run both passes; finalize the §3.2 heuristic against the captured output; write
        `results/c-rock-install.md` with the notification copy — realizes design §2.4, §3.2
- **Exit criteria**: TCs 1, 4, 5 pass on the builder image; heuristic verified to match
  Run B and not Run A.

### Phase 3: Windows prebuilt acquisition & live VM verification [Must]
- **Goal**: The compile-free Windows path validated end to end — Linux acquisition plus
  live execution on the Windows 11 KVM VM over VNC.
- **Tasks**:
  - [ ] Create `tooling/spikes/tooling-00/fetch-windows-prebuilt.sh` (SourceForge
        redirect + luarocks standalone; magic-bytes check per design §4) — realizes design §2.2 stage 1
  - [ ] Run it; assert layout (TC 2, Linux assertions); record pins into the feed
        sample — realizes design §2.2 stage 1
  - [ ] **Prepare the `win11` VM** (KVM/virt-manager — already installed, no ISO): revert
        its clean snapshot and boot —
        `virsh snapshot-revert win11 "Fresh Install" && virsh start win11` (guest
        `TESTING\tester`, empty password; bare box, no IDE) — design §2.2 stage 2 prerequisite
  - [ ] Drive the VM over VNC (verify-in-ide conventions): assemble the tree in-VM via
        PowerShell `Invoke-WebRequest`/`Expand-Archive`, run `lua54.exe -v` and
        `luarocks.exe --version` in CMD and PowerShell, capture banners via
        screenshot/OCR; observe SmartScreen/AV behavior live — realizes design §2.2 stage 2
  - [ ] Write `results/windows-prebuilt.md` (pins, VM record, VNC evidence screenshots,
        observed SmartScreen/AV section) — realizes design §2.2
- **Exit criteria**: TC 2 passes (Linux assertions + VNC-observed banners); results doc
  contains the VM record and the observed-caveats section.

### Phase 4: LuaJIT decision spike [Should]
- **Goal**: A binding ship/descope decision for LuaJIT provisioning in v1.
- **Tasks**:
  - [ ] Create `tooling/spikes/tooling-00/build-luajit-posix.sh` (git clone keeping
        `.git`, make, hand-install) — realizes design §2.3
  - [ ] Run it on Linux; write the darwin-arm64 paper assessment; apply the design §2.3
        decision matrix and record the outcome in `results/luajit-git-make.md` — realizes design §2.3
  - [ ] Update [../tooling-risks-and-gaps.md](../tooling-risks-and-gaps.md) (Risk 1.3 /
        Gap 2.4 status + DR table) with the decision
- **Exit criteria**: TC 3 executed (either verdict); decision recorded in both the
  results doc and the risks doc.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| TOOLING-00-01 | M | Phase 2 |
| TOOLING-00-02 | M | Phase 3 |
| TOOLING-00-03 | S | Phase 4 |
| TOOLING-00-04 | M | Phase 2 |
| TOOLING-00-05 | M | Phase 1 (pins completed in Phases 2–3) |
| TOOLING-00-06 | M | Phase 1 |

## Verification Tasks
- [ ] `tooling/gce-builder/gce-builder.sh run "test --tests *LuaProvisioningClasspathSpikeTest*"` — covers TC 6
- [ ] `tooling/gce-builder/gce-builder.sh run "test --tests *LuaToolchainSerializationSpikeTest*"` — covers TC 7
- [ ] Manual run of the three shell spikes on the gce-builder image (Linux), asserting
      the TC 1/3/4/5 conditions and TC 2's Linux assertions; verdicts recorded in the
      results docs
- [ ] VNC-driven execution session on the Windows 11 KVM VM asserting TC 2's banner
      checks, with screenshot evidence attached to `results/windows-prebuilt.md`
- [ ] Cross-check: every `<pin recorded by …>` placeholder in
      `src/main/resources/toolchain/toolchain-feed.json` replaced with a real digest for
      assets actually downloaded; feed re-parses green (part of TC 6)
- [ ] Update the DR-table statuses in [../tooling-risks-and-gaps.md](../tooling-risks-and-gaps.md)
      and regenerate `docs/status.md` (`python3 scripts/gen_status.py`)

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: In-repo platform checks | todo | Must |
| Phase 2: POSIX build chain | todo | Must |
| Phase 3: Windows prebuilt acquisition & live VM verification | todo | Must |
| Phase 4: LuaJIT decision spike | todo | Should |
