---
id: "TOOLING-RESEARCH-PLATFORM"
title: "Research: IntelliJ Platform Provisioning Idioms"
type: "spec"
parent_id: "TOOLING"
folders:
  - "[[features/tooling/requirements|requirements]]"
---

# Research: IntelliJ Platform Provisioning Idioms

## Overview

TOOLING-04 needs to download, verify, extract, and register toolchains inside the IDE. This
research maps the platform APIs and the closest production exemplar (the JDK downloader) in
the local `~/Documents/src/lua/intellij-community` checkout (paths below are repo-relative
there). Investigated 2026-07-05.

## Findings / Key Components

### 1. The JDK downloader — the production exemplar

- EP `com.intellij.sdkDownload`
  (`platform/lang-impl/src/com/intellij/openapi/roots/ui/configuration/projectRoot/SdkDownload.java`);
  implementation `JdkDownload`
  (`platform/lang-impl/src/com/intellij/openapi/projectRoots/impl/jdkDownloader/JdkDownload.kt:57`).
- Orchestration in `JdkInstaller`
  (`.../jdkDownloader/JdkInstaller.kt:117`): prepare target dir + marker file → download →
  size check → SHA-256 → extract → register in `ProjectJdkTable`; listener EP
  `com.intellij.jdkDownloader.jdkInstallerListener` (`JdkInstaller.kt:98`).
- **Feed**: JSON manifest parsed by `JdkList.kt` — per-OS/arch items with `url`, `sha256`,
  `archiveSize`, `packageType`, `packageRootPrefix`. The model for Lunar's version/checksum
  table (see hererocks dossier: we must own the version pins).
- Default install dirs: `~/.jdks` (property override `jdk.downloader.home`) — precedent for
  a `~/.lunar-toolchains`-style default outside the project.
- `SdkDownloadTask` (`.../projectRoot/SdkDownloadTask.java:22`): `getSuggestedSdkName()` /
  `getPlannedHomeDir()` / `getPlannedVersion()` / `doDownload(ProgressIndicator)` —
  background download tracked by `SdkDownloadTracker`.

### 2. Download

- `com.intellij.util.io.HttpRequests` (`platform/ide-core/src/com/intellij/util/io/HttpRequests.java:72`):
  `HttpRequests.request(url).productNameAsUserAgent().saveToFile(path, indicator)` — streams
  with progress + cancellation; defaults: connect 10s, read 60s, redirect limit 10. Honors
  IDE proxy settings. This is how `JdkInstaller.kt:353` downloads.
- Higher-level parallel batch: `DownloadableFileService` / `FileDownloader`
  (`platform/ide-core/src/com/intellij/util/download/DownloadableFileService.java`;
  impl `platform/ide-core-impl/.../FileDownloaderImpl.java:48`) — per-core parallel
  downloads with `ConcurrentTasksProgressManager`. Useful if provisioning fans out multiple
  assets at once; `HttpRequests` suffices otherwise.

### 3. Extraction

`com.intellij.util.io.Decompressor` (`platform/util/src/com/intellij/util/io/Decompressor.java:43`):
- `Decompressor.Tar` — auto-detects .tar.gz/.bz2/.xz via commons-compress; **preserves POSIX
  mode bits and symlinks** (critical: lua/luarocks trees carry exec bits; LuaRocks Linux
  standalone zip does NOT carry them — chmod after extract).
- `Decompressor.Zip(path).withZipExtensions()` — enables Unix attributes + symlinks for zips.
- `.entryFilter { indicator.checkCanceled(); true }` for cancellation;
  `.removePrefixPath("lua-5.4.8")` strips the archive root (JdkInstaller.kt:400 pattern) —
  exactly what Lua/LuaJIT/LuaRocks tarballs need.

### 4. Checksum

JdkInstaller verifies size first, then SHA-256 via Guava
(`com.google.common.io.Files.asByteSource(f).hash(Hashing.sha256())`, `JdkInstaller.kt:379`);
mismatch → hard failure. Same policy as hererocks. (`DigestUtil` in `platform/util` is the
non-Guava alternative.)

### 5. Other exemplars

- Python: `python/installer/src/com/jetbrains/python/sdk/installer/BinaryInstaller.kt` —
  `Release`/`Binary`/`Resource` data classes + per-tool `BinaryInstaller` implementations
  behind one `installBinary()` entry (`Task.WithResult`). A good shape for per-kind
  provisioning strategies.
- SDK UI: `SdkComboBoxModel` / `SdkListModelBuilder`
  (`platform/lang-impl/.../ui/configuration/SdkListModelBuilder.java:50`) model
  detected-vs-registered-vs-downloadable items (`SdkListItem`: `SdkItem`, `SuggestedItem`,
  `ActionItem` "Download..."), with background `SdkDetector`. Directly reusable pattern for
  the Toolchain inventory + run-config interpreter dropdown.
- `JdkDownloadDialog.kt:169` — Kotlin UI DSL dialog (version combo, vendor combo, location
  field): the template for the Provision Toolchain dialog.

### 6. Remote/WSL (out of v1 scope)

`JdkInstaller` routes remote targets through `EelApi` / `WSLDistribution`
(`JdkInstaller.kt:130,392`). Lunar v1 is local-only (PRD non-goal); noting the seam exists.

## Recommendations

- Use `HttpRequests` + `Decompressor` + SHA-256-before-extract (JdkInstaller flow) as the
  literal skeleton of `LuaToolProvisioner` download/extract steps; no new HTTP or archive
  code.
- Ship the version/checksum table as a bundled JSON resource shaped like the JdkList feed
  (item: kind, version, os, arch, url, sha256, size, packageType, rootPrefix, strategy).
- Do **not** adopt the platform `SdkType`/`ProjectJdkTable` machinery for Lua tools — Lunar
  already has its own registry/settings model (architecture contract §2/§7); borrow the
  UI/list patterns only.
- Copy the marker-file (`.<dir>.intellij`-style) idea as the provisioned-tree manifest
  (merged with the hererocks manifest concept).

## Open Questions

None blocking — API surface confirmed present in the target platform baseline (2026.1).
De-risking spike TOOLING-00 should compile-check the imports against the plugin's actual
classpath (some classes live in `platform/lang-impl`).
