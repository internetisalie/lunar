---
id: "TOOLING-00-05-RESULTS"
title: "Download-infra Classpath Check + Feed Format — Spike Results"
type: "results"
parent_id: "TOOLING-00"
priority: "high"
folders:
  - "[[features/tooling/00-de-risking/requirements|requirements]]"
---

# TOOLING-00-05 — Download-infra Classpath Check + Feed Format (Spike Results)

**Deliverable for:** `TOOLING-00-05` (design §2.5) · **Test:**
`LuaProvisioningClasspathSpikeTest`
(`src/test/kotlin/net/internetisalie/lunar/toolchain/LuaProvisioningClasspathSpikeTest.kt`) ·
**Feed sample:** `src/main/resources/toolchain/toolchain-feed.json`

## Question

Are `com.intellij.util.io.HttpRequests`, `com.intellij.util.io.Decompressor`
(`Tar` + `Zip(...).withZipExtensions()`), and Guava `com.google.common.hash.Hashing`
present on the plugin's compile/test classpath (some platform download classes live in
`platform/lang-impl`), do they link at runtime, and what is the bundled version-feed
format?

## Verdict — PASS

All four probed APIs **compile** (classpath presence) and **execute** (runtime linkage)
against local fixture archives with **no network**. The feed sample parses and carries the
four design §2.5 items with all required fields.

Gate:
`tooling/gce-builder/gce-builder.sh run "test --tests *LuaProvisioningClasspathSpikeTest* --tests *LuaToolchainSerializationSpikeTest*"`
→ `BUILD SUCCESSFUL` (5 tests in this class green; suite green overall).

## Which classes resolved, and from which module

| API | Fully-qualified symbol | Origin (platform artifact) | Exercised how |
|-----|------------------------|----------------------------|---------------|
| HTTP client | `com.intellij.util.io.HttpRequests.request(String)` | platform util (`util.jar` / `app.jar`) | builder constructed only; **no `connect()`** — proves classpath + factory linkage without network |
| Tar extract | `com.intellij.util.io.Decompressor.Tar(Path).extract(Path)` | platform util | extracts a fixture `.tar.gz` whose sole file is mode `0755`; asserts extracted file `canExecute()` (exec-bit preservation) |
| Zip extract | `com.intellij.util.io.Decompressor.Zip(Path).withZipExtensions().extract(Path)` | platform util | extracts a fixture `.zip`; asserts content |
| SHA-256 | `com.google.common.io.Files.asByteSource(File).hash(com.google.common.hash.Hashing.sha256())` | bundled Guava (`app.jar` / 3rd-party) | hashes a fixture file; asserts equality with the precomputed hex pin (the `JdkInstaller.kt:379` idiom) |

None of these are imported anywhere under `src/main/kotlin` today (verified by grep,
2026-07-05), so TOOLING-04 breaks new ground with no overlap — but the classpath is
confirmed to carry them, so the download/verify/extract skeleton is buildable without
adding a dependency. The non-deprecated `Path` constructors of `Decompressor.Tar`/`Zip`
were used (the `File` constructors are `@Deprecated`).

## JSON parser used

**`com.google.gson.Gson`** (platform-bundled). Recorded rationale: Gson is already used in
`src/main` (`net.internetisalie.lunar.rocks.RockspecBridge`,
`net.internetisalie.lunar.run.test.LuaTestOutputToEventsConverter`), so it is unambiguously
on the classpath and is the natural choice for TOOLING-04 to productionize. The test parses
the committed feed via `Gson().fromJson(text, JsonObject::class.java)` and asserts, per item,
the nine universal fields (`kind`, `version`, `os`, `arch`, `strategy`, `url`, `size`,
`packageType`, `rootPrefix`) plus a checksum — `sha256` for downloadable assets, or `gitRef`
for `git` entries — plus that `strategy` ∈
`{SOURCE_BUILD, RELEASE_BINARY, LUAROCKS_INSTALL}` and `packageType` ∈ `{tar.gz, zip, git}`.

### Classpath-URL caveat (recorded for TOOLING-04)

`src/main/resources/toolchain/toolchain-feed.json` is packaged into the composed plugin jar,
so on the test classpath its `getResource(...).toURI()` is a non-hierarchical `jar:` URL —
`File(uri)` throws `IllegalArgumentException`. The feed must be read as a **stream**
(`getResourceAsStream(...)`), which the test does. (The fixture archives live in
`src/test/resources` and remain directory-backed, so `File`/`Path` access is fine for them.)

## Feed format (normative sample committed)

The JdkList-style schema and its four concrete items (lua source-build 5.4.8; lua windows
5.4.2 `RELEASE_BINARY`; luarocks 3.13.0 windows; stylua 2.5.2 linux) are committed at
`src/main/resources/toolchain/toolchain-feed.json`, verbatim from design §2.5. The `sha256`
values retain their `<pin recorded by …>` / `<self-pinned …>` placeholders — Phases 2–3
download the assets and replace them with real digests. The resource is **inert**: nothing
reads it until TOOLING-04.

## Fixtures (committed under `src/test/resources/toolchain/`)

- `fixture.tar.gz` — one file `spike-exec.sh`, mode `0755` (deterministic `tar`: fixed mtime,
  numeric owner 0/0).
- `fixture.zip` — one file `spike-content.txt` with known content.
- `hashme.txt` — payload whose committed sha256 pin is
  `69b30e92143264aa140001cf7af42e67642cf645e24cafbb27f1c3505cbbaa39` (recomputable via
  `sha256sum src/test/resources/toolchain/hashme.txt`).

## Hands to

TOOLING-04 download/verify/extract skeleton + the productionized version feed.
