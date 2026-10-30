---
id: "TOOLING-09"
title: "09: Prebuilt Lua Binaries"
type: "feature"
status: "todo"
priority: "medium"
parent_id: "TOOLING"
folders:
  - "[[features/tooling/requirements|requirements]]"
---

# TOOLING-09: Prebuilt Lua Binaries

## The gap

The native provisioner (`toolchain/provision/`) tries `ReleaseBinaryStrategy` before
`SourceBuildStrategy` (`LuaProvisionEngine.defaultStrategies()`), but the bundled feed
(`src/main/resources/toolchain/lunar-toolchain-feed.json`) carries a prebuilt asset for one PUC
version only: 5.4.2 for Windows x86_64, from LuaBinaries on SourceForge. Every Linux and macOS
install is a source build and needs a C compiler (`LuaCompilerProbe`).

That one asset is also fragile. SourceForge's `…/download` URLs sit behind a Cloudflare challenge
that a JVM client cannot pass: TOOLING-00's Windows spike was challenged on the same day a Linux
`curl` succeeded (`docs/features/tooling/00-de-risking/results/windows-prebuilt.md`, Finding A).
SourceForge publishes no checksums, so the pinned digest is our own.

The test suite has the same need. `tooling/corpus/fetch-luac.py` compiles five PUC releases for
the MAINT-35 parse oracle, which is why CI excludes `ParseOracleTest` ([[MAINT-49]]).

## Scope

- **A build repository of our own.** A public repository under `internetisalie` that builds
  `lua` and `luac` from checksummed lua.org tarballs, statically linked, for one release per
  minor version: 5.1.5, 5.2.4, 5.3.6, 5.4.8 and 5.5.0 (the feed's alias targets). Platforms:
  Linux x86_64 and arm64 (musl, static), Windows x86_64 (MinGW), macOS x86_64 and arm64.
  Written from scratch rather than forked: `dyne/luabinaries` takes the same approach but
  declares no license for its build scripts. No UPX compression.
- **Releases.** Each release is tagged, carries a `SHA256SUMS` per platform and a `LICENSE`
  holding the Lua and musl notices (MIT requires the notice with every copy), and is smoke-tested
  on each platform: `lua -v`, and `luac -p` on a sample file.
- **Feed assets.** Add `single-binary` assets for `lua` and `luac` to the five alias-target
  versions, pinned to a release tag and each file's sha256. Older patch releases stay source-only.
  Replace the SourceForge Windows 5.4.2 entry.
- **Linux x86_64 first.** The parse oracle needs only Linux x86_64 `luac`, so that target ships
  in the first release and unblocks [[MAINT-49]]; the other platforms can follow.

## Not established

- **The repository's name and home.** GitHub, so users' IDEs can reach it; the Gitea instance is
  LAN-facing.
- **macOS deployment target.** `MACOSX_DEPLOYMENT_TARGET` decides the oldest macOS the binaries
  run on; the runners are macOS 15.
- **C modules.** A static `lua` built without dlopen cannot `require` C rocks. Whether a
  provisioned environment needs that, and so whether `lua` (as opposed to `luac`) must stay a
  source build on some platforms, is open.
- **`luac` beside `lua`.** Whether `single-binary` registers a second binary as an extra, or
  needs a small layout that installs both.
- **Unsigned binaries.** Windows SmartScreen and macOS Gatekeeper behaviour for binaries
  downloaded by the IDE rather than a browser.
