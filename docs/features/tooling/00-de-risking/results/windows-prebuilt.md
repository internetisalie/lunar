---
id: "TOOLING-00-02-RESULTS"
title: "Results: Windows Prebuilt Provisioning (TOOLING-00-02)"
type: "results"
parent_id: "TOOLING-00"
folders:
  - "[[features/tooling/00-de-risking/requirements|requirements]]"
---

# Results: TOOLING-00-02 — Windows Prebuilt Provisioning

## Stage 1 — Linux Acquisition (automated)

**Date**: 2026-07-06
**Verdict**: PASS

### Question answered
Can a working Windows toolchain be assembled purely from prebuilt downloads (no
compiler), and what are the exact asset names, SHA-256 pins, and SourceForge group
identifiers to put in the feed?

### Method

Script: `tooling/spikes/tooling-00/fetch-windows-prebuilt.sh <destdir>`

Executed on the local Linux workstation (no gce-builder needed — downloads are
OS-independent artifacts; the script requires only `curl`, `python3`, and `unzip`).

#### SourceForge URL finding (design §4 validation)

The design's SourceForge redirect pattern
`https://sourceforge.net/projects/luabinaries/files/<ver>/Tools%20Executables/…/download`
was probed first. With `curl -L`, SourceForge serves a JavaScript-rendered HTML
anti-bot page rather than the ZIP binary — the magic-bytes guard
(`PK\x03\x04` = hex `504b0304`) correctly detects this and aborts with a clear error.

**Resolution**: The script uses the direct CDN URL pattern
`https://downloads.sourceforge.net/project/luabinaries/<ver>/Tools%20Executables/…`
which serves the archive without JavaScript intermediation and passes the magic-bytes
check.

**SourceForge group name confirmed**: `Tools Executables`
(URL path component `Tools%20Executables`; this answers the "unrecorded" flag in
design §2.2 — the group name is confirmed correct.)

#### Downloads

| Asset | URL | Result |
|-------|-----|--------|
| `lua-5.4.2_Win64_bin.zip` | `https://downloads.sourceforge.net/project/luabinaries/5.4.2/Tools%20Executables/lua-5.4.2_Win64_bin.zip` | 373,984 bytes, magic OK (corrected from 374,272 — see Stage 2 discrepancy note; SHA-256 authoritative) |
| `luarocks-3.13.0-windows-64.zip` | `https://luarocks.github.io/luarocks/releases/luarocks-3.13.0-windows-64.zip` | 3,796,234 bytes, magic OK |

#### Lua version used

**5.4.2** — the design §2.5 feed sample already pinned this version. The LuaBinaries
project page shows 5.4.2 as the latest 5.4-line Win64 Tools Executables release
available (5.4.6 exists in `Executables only`, not `Tools Executables`; 5.4.2 is the
newest with `lua54.exe` + `lua54.dll` + `luac54.exe` + `wlua54.exe` bundled).

#### SHA-256 pins (recorded, filled into `toolchain-feed.json`)

| Kind | Asset | SHA-256 |
|------|-------|---------|
| `lua` `windows` | `lua-5.4.2_Win64_bin.zip` | `5f1e1385ed95a3643f7ed67c4f3767942b2f0f388b66f63e5667e9c3d96293f5` |
| `luarocks` `windows` | `luarocks-3.13.0-windows-64.zip` | `0897ade5d459d55cd1962a948153745a6749feb345403c68aaa9207388557ab9` |

#### Extracted asset names (exact)

From `lua-5.4.2_Win64_bin.zip` (flat archive, no root prefix):
- `lua54.exe` — 122,006 bytes (PE32+ executable)
- `lua54.dll` — 356,234 bytes (DLL)
- `luac54.exe` — 299,268 bytes
- `wlua54.exe` — 125,374 bytes (Windows GUI variant)

From `luarocks-3.13.0-windows-64.zip` (root prefix `luarocks-3.13.0-windows-64/`, stripped):
- `luarocks.exe` — 5,149,202 bytes (PE32+ executable)
- `luarocks-admin.exe` — 5,148,711 bytes

The `rootPrefix` for the LuaRocks feed entry must be `""` after stripping (the
`Decompressor.Zip.removePrefixPath("luarocks-3.13.0-windows-64")` call handles this
in TOOLING-04).

#### Layout assertion results

```
=== Layout assertions ===
  [PASS] lua54.exe                       122006 bytes
  [PASS] lua54.dll                       356234 bytes
  [PASS] luarocks.exe                    5149202 bytes

=== RESULT: PASS ===
    lua 5.4.2: lua54.exe + lua54.dll present and non-empty.
    luarocks 3.13.0: luarocks.exe present and non-empty.
    SourceForge group confirmed: 'Tools Executables'
    Layout ready for Stage 2 (live VM execution — supervisor over VNC).
```

#### Magic-bytes guard (design §4)

Both archives pass the `PK\x03\x04` check before hashing. The guard was verified to
fire correctly: when the SourceForge `/download` redirect returned an HTML page, the
guard aborted with:

```
[ERROR] lua-5.4.2_Win64_bin.zip: magic-bytes check FAILED
        Expected: 504b0304 (PK ZIP signature)
        Got:      3c68746d
        The server returned an HTML error page instead of a ZIP.
```

This demonstrates the guard provides a clear diagnostic for the SourceForge anti-bot
failure mode. Production code (TOOLING-04) should use `downloads.sourceforge.net`
directly rather than the `/download` redirect.

#### Feed update

`src/main/resources/toolchain/toolchain-feed.json` — both `<pin recorded by TOOLING-00-02>`
placeholders replaced with real SHA-256 values above. Feed re-parses cleanly:

```
JSON valid; items: 4
 - lua * 4f18ddae154e793e...
 - lua windows 5f1e1385ed95a364...
 - luarocks windows 0897ade5d459d55c...
 - stylua linux <self-pinned: ...>
```

---

## Stage 2 — Live Windows VM execution (VNC)

**Date**: 2026-07-06
**Verdict**: PASS (core question) — with a **new production-acquisition risk** recorded (Cloudflare, see below)
**Environment**: `win11` KVM VM (`qemu:///system`), `IDE Installed` snapshot, driven over VNC (`127.0.0.1:5900`).
Guest `TESTING\tester`. Windows build `10.0.26200.8457`.

> **Snapshot note**: the plan preferred the `Fresh Install` snapshot for a pristine SmartScreen
> baseline. This run used `IDE Installed` (per operator direction). That snapshot has Chrome/Edge
> already present — which turned out to be *necessary* (see the Cloudflare finding), since the
> programmatic download path is now blocked and a real browser was required to acquire the Lua zip.
> Consequence for the baseline: SmartScreen reputation state is not pristine, but the AV/SmartScreen
> observations below were still conclusive.

### Questions answered
1. Do the prebuilt `lua54.exe` and `luarocks.exe` actually run on real Windows (both shells)?
2. Does SmartScreen / Defender block or prompt on first run of the unsigned binaries?
3. What is the real Mark-of-the-Web (MOTW) behaviour across download → extract → execute?

### TC 2 acceptance — MET

`lua54.exe -v` and `luarocks.exe --version` were run in **both** PowerShell and CMD:

| Shell | `lua54.exe -v` | `luarocks.exe --version` |
|-------|----------------|--------------------------|
| PowerShell | `Lua 5.4.2  Copyright (C) 1994-2020 Lua.org, PUC-Rio` | `luarocks 3.13.0` / `LuaRocks main command-line interface` |
| CMD | `Lua 5.4.2  Copyright (C) 1994-2020 Lua.org, PUC-Rio` | `luarocks 3.13.0` / `LuaRocks main command-line interface` |

No compiler present; the toolchain was assembled purely from prebuilt downloads. **The core
TOOLING-00-02 question is answered YES.**

### Content integrity — SHA-256 matches the Stage-1 pin

The browser-acquired `lua-5.4.2_Win64_bin.zip` hashed to
`5F1E1385ED95A3643F7ED67C4F3767942B2F0F388B66F63E5667E9C3D96293F5`, an **exact match** to the
Stage-1 pin `5f1e1385…96293f5`. Independent acquisition path, identical bytes → the pin is
trustworthy.

> **Discrepancy flagged**: the on-disk size in the VM was **373,984 bytes** (`Get-Item .Length`),
> whereas Stage 1 recorded 374,272 bytes (line 50). Identical SHA-256 means identical content, so
> the two byte-counts cannot both be right — Stage 1's figure is a transcription slip. The SHA-256
> is authoritative; content integrity holds. Stage 1's table above has been corrected to `373,984`.

### Finding A (blocker, worked around) — SourceForge is now behind a Cloudflare JS challenge

The plan's programmatic path — PowerShell `Invoke-WebRequest` against
`https://downloads.sourceforge.net/project/luabinaries/5.4.2/Tools%20Executables/lua-5.4.2_Win64_bin.zip`
— **did not return the ZIP**. It returned a 138,001-byte HTML page (magic bytes `3C 21 64 6F` =
`<!doctype`). Retrying with a browser `User-Agent` surfaced the real gate: a **Cloudflare managed
JavaScript challenge** (`_cf_chl_opt`, `/cdn-cgi/challenge-platform/h/b/orchestrate/chl_page/v1`),
and IWR threw a `WebException`. A plain HTTP client cannot pass it — only a JS-executing browser can.

- **luarocks** (GitHub Pages) has **no** such gate — `Invoke-WebRequest` fetched it fine.
- The Lua zip was ultimately acquired by driving **Microsoft Edge** to the SourceForge download URL;
  Edge solved the Cloudflare challenge and downloaded via the NetActuate mirror. No SmartScreen
  prompt on the download itself.

**Why this matters (production risk for TOOLING-04):** Lunar's own downloader is programmatic
(JVM/HTTP), i.e. exactly the client class that Cloudflare challenged here. The Stage-1
"resolution" (use `downloads.sourceforge.net` directly) **worked from the Linux workstation via
`curl` earlier the same day but was Cloudflare-challenged from the Windows VM via IWR** — so the
block is **heuristic/intermittent** (IP reputation, TLS fingerprint, UA), not a stable
always-on gate. That intermittency *is* the risk: the SourceForge acquisition can fail
unpredictably in production. Mitigations to weigh in TOOLING-04: a non-SourceForge mirror,
bundling the Windows binaries, or a retry-with-clear-error path. The existing `PK\x03\x04`
magic-bytes guard already fails closed on the HTML challenge page (aborts rather than installing
garbage) — that guard is validated as necessary, not merely defensive.

> **Decision (2026-07-06)**: SourceForge is **dropped** as the Lua-binary source — for this
> reliability finding *and* trust (SourceForge's adware-bundling history). Windows/macOS Lua
> binaries will be **self-built from official lua.org source via MinGW cross-compile in CI**
> (self-hosted / bundled), with [dyne/luabinaries](https://github.com/dyne/luabinaries) as the
> GitHub-hosted interim. Provisioning goes **prebuilt-first on Windows and macOS**; user-side
> source-build auto-install is **limited to Linux**. Full rationale + feed impact in the epic
> [Gap 2.2 / Gap 2.1 / Risk 1.1](../../tooling-risks-and-gaps.md).

### Finding B — MOTW behaviour (download → extract → execute)

| Step | Mark-of-the-Web result |
|------|------------------------|
| `Invoke-WebRequest -OutFile` (programmatic) | **No** `Zone.Identifier` stream written |
| Browser (Edge) download | **MOTW present** — `[ZoneTransfer] ZoneId=3`, with sourceforge/netactuate Referrer/HostUrl |
| `Expand-Archive` of the MOTW-tagged zip | **Strips MOTW** — extracted `lua54.exe` had no `Zone.Identifier` |
| Manual re-tag + shell launch (worst case) | see Finding C |

The plan's inline comment ("Invoke-WebRequest writes Mark-of-the-Web") is **incorrect**: IWR
bypasses the Attachment Execution Service that browsers/Outlook use, so it writes no MOTW.
Net effect for Lunar: its programmatic download **and** programmatic extraction both leave the
binaries **un-tagged**, so SmartScreen has nothing to evaluate on execution.

### Finding C — SmartScreen / Defender did NOT block the unsigned binaries

- **Console launch** (`& $lua -v`, and the CMD runs): no prompt (expected — no MOTW, and console
  `CreateProcess` doesn't hit SmartScreen's shell gate). This is Lunar's actual execution path.
- **Worst case forced**: a genuine `ZoneId=3` MOTW was written onto `lua54.exe` and it was launched
  via **`Start-Process` (ShellExecute — the double-click path)**. It launched **straight into the
  Lua 5.4.2 REPL with no "Windows protected your PC" modal**.
- **Configuration context (so "no block" is meaningful)**:
  - Defender: `AMRunningMode=Normal`, `RealTimeProtectionEnabled=True`, `AntivirusEnabled=True`,
    `IsTamperProtected=True` — AV fully active; it did **not** flag or quarantine either binary.
  - SmartScreen: legacy `Explorer\SmartScreenEnabled` value **unset** (OS default, not disabled);
    **no** `EnableSmartScreen` / `ShellSmartScreenLevel` Group-Policy override. Not policy-disabled.

**Conclusion:** neither Defender nor SmartScreen objected to the unsigned Lua/luarocks binaries,
even in the forced MOTW + shell-launch worst case. For Lunar's real path (programmatic download →
programmatic extract → console launch) there is no MOTW at any stage, so SmartScreen has no trigger
and code signing is **not** required for the interpreter to run.

### VNC tooling note

Executed with the current VNC MCP server, which — unlike the prior/buggy server flagged in the
handoff — applies Shift correctly (verified typing the full shifted-character set: `$ : " ' % ( )
{ } < > # @ ~ | ^ & !`) and positions mouse clicks accurately. Shifted-key injection was the gate;
the hard input blocker is resolved.

---

## Stage 3 — Self-built Windows binary (Option 1 proof — SourceForge-free)

**Date**: 2026-07-06
**Verdict**: PASS
**Spike**: `tooling/spikes/tooling-00/build-lua-windows-mingw.sh`

Proves the [Gap 2.2 decision](../../tooling-risks-and-gaps.md): Lunar can build its **own** Windows
Lua binary from **official lua.org source** via **MinGW cross-compile** — no SourceForge, no
third-party binary, no user-side compiler. The recipe reproduces
[dyne/luabinaries](https://github.com/dyne/luabinaries)' proven `make mingw` cross-build (their root
Makefile), with two deliberate departures: source is SHA-256-pinned from lua.org (canonical, the
same `4f18dda…29ae` pin as the POSIX spike), and **no UPX packing** (dyne's CI runs `upx -9`, a
known AV false-positive trigger — we ship the plain stripped binary).

### Build (on the gce-builder Ubuntu VM, `gcc-mingw-w64-x86-64` = GCC 12)

| Artifact | Size | `file` |
|----------|------|--------|
| `lua54.exe` | 48,640 B | PE32+ console x86-64 |
| `luac54.exe` | 265,216 B | PE32+ console x86-64 |
| `lua54.dll` | 345,065 B | PE32+ DLL x86-64 |

`lua54.dll` (345 KB) ≈ the official LuaBinaries `lua54.dll` (356 KB), as expected for an unpacked
MinGW build. SHA-256 (self-pinned into the feed regardless of source):
`lua54.exe` = `cf4588…d880ee`, `lua54.dll` = `f47118…cdb81`, `luac54.exe` = `8dec07…8621`.

**One recipe fix vs. dyne**: modern binutils `ar` needs an explicit operation, so the `luac` step
requires `AR="x86_64-w64-mingw32-ar rcu"` (dyne passes bare `ar`, which now errors
`libdeps specified more than once`; their Makefile hides it behind error-ignoring `-` prefixes).

### Live execution on the `win11` VM (fetched from the host over the local bridge)

The three binaries were served from the host (`python3 -m http.server` on the bridged
`10.254.239.1`) and pulled into the VM with `Invoke-WebRequest` — a plain local server, **no
Cloudflare** (contrast Finding A). Downloaded hash matched the build exactly
(`CF4588…D880EE`). Then, from PowerShell:

```
.\lua54.exe -v          → Lua 5.4.8  Copyright (C) 1994-2025 Lua.org, PUC-Rio
.\lua54.exe -e "..."    → exec-ok: 2 Lua 5.4        (executes Lua code, not just the banner)
.\luac54.exe            → runs (prints usage)
```

**Key result — no missing-DLL error.** The binary loaded `lua54.dll` and ran on a clean Windows 11
box, confirming the static linking (`-l:libpthread.a -lssp`) left **no** unresolved
`libgcc_s_seh-1.dll` / `libwinpthread-1.dll` / `libssp-0.dll` runtime dependency — the classic MinGW
redistribution trap. No SmartScreen/AV prompt (IWR download → no MOTW, per Finding B/C).

**Conclusion**: Option 1 is validated end-to-end — we can produce a working, SourceForge-free,
signed-not-required Windows Lua 5.4.8 from canonical source. TOOLING-04 wires this into CI
(self-host the artifact or bundle it); [dyne/luabinaries](https://github.com/dyne/luabinaries)
remains the drop-in interim.
