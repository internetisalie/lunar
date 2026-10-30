---
id: "MAINT-49-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-49"
folders:
  - "[[features/maint/49-fixture-tests-run-in-ci/requirements|requirements]]"
---

# Technical Design: MAINT-49 — Fixture Tests Run in CI

Line numbers are at `adf755f5`. Edits are given as the text to find, so they survive drift.

## 1. Architecture Overview

### Current State
`build.gradle.kts:397-415` excludes four classes under `-PexcludeExternalFixtureTests`, and every
CI suite invocation passes it (`.gitea/workflows/build-plugin.yml:238,260,566,584`,
`.github/workflows/build-plugin.yml:127`). The `corpus` job alone provisions `test/`
(`.gitea/workflows/build-plugin.yml:398-415`).

### Prior Art in This Repo
| Component | Disposition |
|---|---|
| `tooling/corpus/fetch-corpus.py` | **Reused unchanged** by the new provisioning steps. It fetches all five members and the definition archives; DR-04 shows no subset is worth adding. |
| `tooling/corpus/fetch-luac.py` | **Replaced in body** (§3.1). Its CLI, env overrides `LUNAR_LUAC_MANIFEST` / `LUNAR_LUAC_ROOT`, install path `test/luac/<version>/luac` and `.luac-sha` stamp contract are kept, so `ParseOracle` is unchanged in code. |
| `ParseOracle.requireBinary` / `assertStamped` (`ParseOracle.kt:52-114`) | **Unchanged.** It compares `.luac-sha` with the manifest `sha256`; that field now holds the archive digest. |
| `ParseOracleTest.testChecksumMismatchInstallsNothing` (`:123-141`) and `runFetchScript` (`:244-257`) | **Replaced** by `FetchLuacScriptTest` (§2.3). Its decoy is not a tar archive, so it stays green with the digest check deleted (DR-05). |
| The `corpus` job's provisioning step | **Pattern extended** to `build`: same symlink re-point to `/cache/lunar/test`. |
| `gce-builder.sh` `check_luac_glibc` (`:104-127`, call `:157`) | **Removed** (§2.8). |

### Target State
Every lane that runs the unit suite fetches the corpus and the downloaded oracles first, and no
lane excludes a class. The two tests that read hand-placed files read pinned corpus members.

## 2. Components

### 2.1 `net.internetisalie.lunar.lang.LuaRecursiveReferenceTest` (edit)
In `testLuacheckParserRecursiveCall` (`:103`) replace `"test/luacheck/src/luacheck/parser.lua"` with
`"test/corpus/luacheck/src/luacheck/parser.lua"`. The two files are byte-identical and the test
passes unchanged otherwise (DR-01), so the line constants 620 and 612 stay.

### 2.2 `net.internetisalie.lunar.lang.LuaDescriptionIndexTest` (edit)
Replace the body of `testIndexRealWorldProjectAndMeasureSize` (`:138-184`) with §3.2. Add
`import java.io.File` (`GlobalSearchScope` and `DescriptionRecord` are already imported, `:3` and
`:6`) and a `companion object` holding
`SIZE_MEMBER = "test/corpus/lazynvim"`, `PINNED_LINES = 14_327` (the line count of that member at
its `corpus.json` pin, DR-02) and `MAX_BYTES_PER_LINE = 10`. New private helpers:
```kotlin
private fun addLuaFiles(root: File): Int        // §3.2 step 2; returns total lines
private fun projectIndexBytes(): Int            // §3.2 step 4
```
Threading: as today, the test body runs on the EDT that `BasePlatformTestCase` provides; the probe
that measured §3.2 ran the same way (DR-02).

### 2.3 `tooling/corpus/fetch-luac.py` (rewrite) and `net.internetisalie.lunar.corpus.FetchLuacScriptTest` (new)
Script functions: `log`, `die`, `sha256_of` (kept verbatim), `member_of(url: str) -> str`,
`install_one(entry: dict) -> None` (replaces `build_one`), `main()` (calls `install_one`). Delete
`MAKE_TARGETS` and the `make` loop. Add `ARCHIVE_SUFFIX = ".tar.gz"`. Docstring: "Install the
pinned `luac` binaries listed in luac.json into <repo>/test/luac/<version>/luac, downloaded from the
TOOLING-09 release archives." followed by the existing stamp and refusal paragraphs, with
"tarball" read as "archive".

`FetchLuacScriptTest` is plain JUnit 4 (`org.junit.Test`, `kotlin.test` assertions), one method per
requirements cases 7–11, named with backticks. No `Corpus` in any name (`build.gradle.kts:429-431`).
Helpers, each ≤3 parameters:
```kotlin
private fun archive(work: File, luacText: String?): File   // stage lua-5.1.5-linux-x86_64/bin/{luac,lua}, LICENSE; tar -czf
private fun manifest(work: File, archive: File, sha256: String): File
private fun runFetch(manifest: File, luacRoot: File): Pair<Int, String>  // ProcessBuilder("python3", script), env as ParseOracleTest:250-251
private fun sha256(file: File): String                     // MessageDigest SHA-256, lowercase hex
```
`archive` always writes `LICENSE` (any text) and `bin/lua` = `#!/bin/sh\necho "Lua 5.1.5 interpreter"\n`,
and writes `bin/luac` = `#!/bin/sh\necho "<luacText>"\n` unless `luacText` is null (case 11's
archive: `LICENSE` and `bin/lua`, no `bin/luac`). It sets the scripts executable and runs
`tar -czf <work>/lua-5.1.5-linux-x86_64.tar.gz -C <stage> lua-5.1.5-linux-x86_64`. The manifest is
`{"builds":[{"level":"LUA51","version":"5.1.5","url":"<archive.toURI()>","sha256":"<sha256>"}]}`.
Temp dirs come from `kotlin.io.path.createTempDirectory`. Delete
`testChecksumMismatchInstallsNothing` and `runFetchScript` from `ParseOracleTest`.

### 2.4 `tooling/corpus/luac.json` and `ParseOracleTest`
`luac.json` per §4.1. In `ParseOracleTest.testPinnedVersionsComeFromTheManifest` (`:90`) replace
`"5.5.1"` with `"5.5.0"`. `ParseOracle.kt` prose (code unchanged): `:19` "built from a
sha256-pinned upstream tarball" → "downloaded from a sha256-pinned TOOLING-09 release archive";
`:81-82` "is built from source, not installed from a package" → "is a pinned release binary, not a
distro package"; `:88` "is built from pinned source, not installed from a package" → "is downloaded
from a pinned release archive, not installed from a package"; `:97` "the digest of the tarball it
was built from" → "the digest of the archive it came from"; `:116` "its tarball digest" → "its
archive digest".

### 2.5 Gitea `build` job (`.gitea/workflows/build-plugin.yml`)
New step between "Install loom-cli" (`:184-189`) and the `# No -PpluginVersion` comment (`:191`):
```yaml
      - name: Provision pinned fixtures (corpus checkouts, definition archives, luac oracles, torture archive)
        # MAINT-49: the external-fixture classes need test/. Same re-point as the corpus job, so a
        # warm VM shares its stamped fixtures. Failure is RECORDED, as the test step does it: the
        # build, lint and suite still run, the store step refuses, and the final step fails the job.
        run: |
          mkdir -p /cache/lunar/test
          rm -f test
          ln -sfn /cache/lunar/test test
          python3 tooling/corpus/fetch-corpus.py && python3 tooling/corpus/fetch-luac.py && python3 tooling/corpus/fetch-torture.py && echo "fixtures ok" || touch /tmp/lunar-fixtures-failed
```
The last line is one command list, so its status is the step's verdict (`:371-375`) and the fetchers'
exits reach it. "Store unit-test results" (`:250-251`) becomes:
```yaml
        run: |
          if [ -f /tmp/lunar-fixtures-failed ]; then
            echo "not storing: the pinned fixtures were not provisioned, so this run is not this commit's result"
          else
            python3 tooling/ctrf/junit_ctrf.py store --results build/test-results/test --filter build/test-results/test-filter.json --out build/unit-ctrf/unit.ctrf.json || touch /tmp/lunar-unit-store-failed
          fi
```
"Report lint + test result" (`:308-321`) gains, before `if [ "$verdict" -ne 0 ]`:
```sh
          if [ -f /tmp/lunar-fixtures-failed ]; then echo "::error::provisioning the pinned fixtures failed — see the Provision pinned fixtures step"; verdict=1; fi
```
Why refuse the store: records are keep-first (MAINT-48 §10), so an environmental fetch failure
stored once would stand as the commit's baseline with four classes failed.

### 2.6 Gitea `build-release` and GitHub `build`
**Phase 2:** in both, the "Provision headless test environment (fonts + Lua toolchain)" step's
`apt-get install` line gains `python3 git`, because `FetchLuacScriptTest` (Phase 2) runs `python3`
in the suite those lanes run. Neither workflow installs `python3` today; the Gitea `build` lane's
`lunar-ci` image already has it (`tooling/ci-image/Dockerfile:79`, checked at `:113`, used at
`build-plugin.yml:251`). `tar` is an Essential package on all three lanes: the `lunar-ci` image is `debian:13-slim`
(`tooling/ci-image/Dockerfile:35`) and the other two run Ubuntu.

**Phase 3:** both get the same new step directly after that step:
```yaml
      - name: Provision pinned fixtures (corpus checkouts, definition archives, luac oracles, torture archive)
        # MAINT-49: the external-fixture classes run in this lane too; a fetch failure fails the job.
        run: |
          rm -f test
          mkdir -p test
          python3 tooling/corpus/fetch-corpus.py
          python3 tooling/corpus/fetch-luac.py
          python3 tooling/corpus/fetch-torture.py
```
These runners execute `run:` under `bash -e`, so a failing fetcher fails the step and the job.
Neither lane stores records (MAINT-48 Out of Scope), so no store guard is needed. Equivalent in
effect to §2.5: a lane that could not provision never reports green.

### 2.7 Removing the exclusion
- `build.gradle.kts`: delete `:397-415`, from `// CI checkouts lack the out-of-repo` through the
  `}` closing `if (project.hasProperty("excludeExternalFixtureTests"))`.
- Delete ` -PexcludeExternalFixtureTests` (with its leading space) from
  `.gitea/workflows/build-plugin.yml:238`, `:260`, `:566`, `:584` and
  `.github/workflows/build-plugin.yml:127`.
- Rename the step `Unit tests (excludes :integrationTest and out-of-repo fixture tests)` to
  `Unit tests (excludes :integrationTest)` at Gitea `:215`, `:558` and GitHub `:108`.
- Replace the two comment lines that begin `` # `test` does not trigger :integrationTest ``
  (Gitea `:559-560`, GitHub `:109-110`) with the one line
  `` # `test` does not trigger :integrationTest (a separate task). ``

### 2.8 `tooling/gce-builder/gce-builder.sh` and its README
Delete `:104-128` (the comment, `check_luac_glibc` and the blank line after it) and `:152-157` (the
`# PREFLIGHT:` comment and the call). In `tooling/gce-builder/README.md` replace `:82-140` (from
`## The corpus sweep and the pinned` to the line before `### Bootstrap changes needed`) with:
```markdown
## The corpus sweep and the pinned `luac` oracles

The oracles are static builds downloaded by `tooling/corpus/fetch-luac.py` (MAINT-49). They need no
glibc, so any builder image runs them.
```
DR-05 shows `objdump -T` on the downloaded `luac` lists no `GLIBC_` symbol.

### 2.9 Prose that becomes false
| File | Edit |
|---|---|
| `tooling/corpus/corpus.json:9-12` | delete the three `NB: test/luacheck …` strings and the empty string after them |
| `tooling/corpus/README.md:238-242` | delete the `## Note on test/luacheck` section |
| `tooling/corpus/luac.json` `$comment` | §4.1 |
| `src/test/.../corpus/ParseOracleTest.kt:14-16` | replace with "They need the pinned binaries that `tooling/corpus/fetch-luac.py` downloads into the out-of-repo `test/` tree." |
| `src/test/.../lang/types/LuaTypeGoldenMemberFilesTest.kt:7` | end the KDoc at "real pinned-corpus files." |
| `src/test/.../lang/indexing/LuaDescriptionIndexValueSizeTest.kt:11-12` | delete the sentence beginning "Kept out of" |
| `src/test/.../UncollectedTestMethodGuardTest.kt:19` | `` (`*Corpus*`, `-PexcludeExternalFixtureTests`) `` → `` (`*Corpus*`) `` |
| `LuaDescriptionIndexTest` comment `:175-177` | replaced by §3.2's KDoc |
| `tooling/gce-builder/gce-builder.sh:145` | `(e.g. test/luacheck and test/*.lua)` → `(e.g. test/corpus and test/luac)` |
| `tooling/corpus/fetch-luac.py:29-30` | `(ParseOracleTest.testChecksumMismatchInstallsNothing points it at a local file:// entry)` → `(FetchLuacScriptTest points it at local file:// archives)` |
| `.gitea/workflows/build-plugin.yml:336-337` | `(commit SHA / tarball sha256 / archive sha256)` → `(commit SHA / archive sha256)`; `a cold VM pays ~3 min once` → `a cold VM pays seconds once (MAINT-49 DR-04)` |

## 3. Algorithms

### 3.1 `install_one(entry)` — `fetch-luac.py`
Input: one manifest entry `{level, version, url, sha256}`. Output: `LUAC_ROOT/<version>/luac` and
`.luac-sha`, or `die()` (exit 1) with nothing new installed.
1. `dest = LUAC_ROOT / version`; `binary = dest / "luac"`; `stamp = dest / ".luac-sha"`.
2. If `binary.is_file()` and `stamp.is_file()` and `stamp.read_text().strip() == sha256`: log
   `{version} already installed — skipping` and return. Nothing is downloaded.
3. `member = member_of(url)`: `name = url.rsplit("/", 1)[-1]`; if it does not end with
   `ARCHIVE_SUFFIX`, die `{url}: not a .tar.gz archive`; else return
   `f"{name[:-len(ARCHIVE_SUFFIX)]}/bin/luac"`.
4. In a `tempfile.TemporaryDirectory()` `work`: download `url` with `urllib.request.urlopen` to
   `work / "archive.tar.gz"`.
5. If `sha256_of(archive) != sha256`: die with the existing mismatch text, "refusing to build" read
   as "refusing to install". The archive is never opened.
6. Open with `tarfile.open`; if `member not in bundle.getnames()`, die
   `{version}: archive lacks {member}`; else `bundle.extract(member, work, filter="data")`.
7. Run `[work/member, "-v"]`; unless exit 0 and `version` occurs in stdout+stderr, die
   `{version}: downloaded luac does not report its own version: …`.
8. `dest.mkdir(parents=True, exist_ok=True)`; `shutil.copy2` to `binary`; `chmod 0o755`; write
   `sha256 + "\n"` to `stamp` last, so an interruption re-downloads rather than trusting a partial
   install.

A pin change re-installs by step 2's stamp mismatch, so a warm `/cache` migrates itself. A prototype
of exactly these steps ran against the real release and against cases 7–11 (DR-05).

### 3.2 The index-size guard
1. `memberDir = File(System.getProperty("user.dir"), SIZE_MEMBER)`;
   `assertTrue("corpus not fetched: $SIZE_MEMBER (tooling/corpus/fetch-corpus.py)", memberDir.isDirectory)`.
2. `addLuaFiles`: for each `file` in `memberDir.walkTopDown()` with `isFile` and extension `lua`,
   `myFixture.addFileToProject(file.relativeTo(memberDir).path, text)` and add
   `text.lineSequence().count()` to the total.
3. `assertEquals("$SIZE_MEMBER is not the pinned checkout the budget was measured on; re-measure (MAINT-49 DR-02)", PINNED_LINES, totalLines)`.
   This holds the walk root: any other root walks a different line count.
4. `projectIndexBytes`: `index = FileBasedIndex.getInstance()`,
   `scope = GlobalSearchScope.projectScope(project)`. For each key of
   `index.getAllKeys(LuaDescriptionIndex.KEY, project)`: `values = index.getValues(LuaDescriptionIndex.KEY, key, scope)`;
   skip the key if `values` is empty; else add `key` UTF-8 size + 2, and for each value add its UTF-8
   size + 2 and, for each `record` in `DescriptionRecord.parseAll(value)`,
   `assertFalse("bundled definition counted: ${record.fileUrl}", record.fileUrl.startsWith("jar:"))`.
   This holds the scope: `allScope` returns the plugin jar's bundled definitions, whose URLs are
   `jar://` (DR-02).
5. `limit = totalLines * MAX_BYTES_PER_LINE`;
   `assertTrue("Estimated size of index ($bytes bytes) exceeds limit ($limit bytes) for $totalLines lines of $SIZE_MEMBER", bytes < limit)`.

Why each choice: the bundled definitions are the same for every member and not re-indexed by an
indexer change, so step 4 excludes them; lazynvim has the most project-scope entries, and luacheck
has none (DR-02); the budget sits between the measured value and the full-text mutant's (DR-02).
Neither the root nor the scope can be left to the budget, since walking all of `test/` or using
`allScope` both stay under it (DR-02), hence steps 3 and 4. A `corpus.json` re-pin of lazynvim
fails step 3 by design: the budget was measured on that checkout. KDoc for the method:
"Regression guardrail: the description index of a pinned, LuaCATS-heavy corpus member stays under
MAX_BYTES_PER_LINE bytes per line of that member (measured in MAINT-49 DR-02)."

## 4. External Data

### 4.1 `tooling/corpus/luac.json`
Fields per entry, unchanged in name so `ParseOracle.pinnedBuild` (`:129-140`) and
`ParseOracleTest.stagedOracle` (`:190-212`) read it as before: `level`, `version`, `url` (a release
asset), `sha256` (that archive's digest, from the release's `SHA256SUMS`).

| level | version | asset (`https://github.com/internetisalie/lua-binaries/releases/download/v2026.10.30/<asset>`) | sha256 |
|---|---|---|---|
| LUA51 | 5.1.5 | `lua-5.1.5-linux-x86_64.tar.gz` | `5a2e150570541c36d0d6e41da7c94e2ad5bafcc65ebb5116e124a635d8fb98f4` |
| LUA52 | 5.2.4 | `lua-5.2.4-linux-x86_64.tar.gz` | `39b8557d1f009c3935ff8803955b978d675670937b9fcd99262f851edfc65351` |
| LUA53 | 5.3.6 | `lua-5.3.6-linux-x86_64.tar.gz` | `8e1c7f0507cfc104c254eedad255178bb173c1ddcd09d026b3282ce683e253dd` |
| LUA54 | 5.4.8 | `lua-5.4.8-linux-x86_64.tar.gz` | `a00fe5fea782ef214c340b7db32db9bedd81bf5d47836667c3bb77e0837eff29` |
| LUA55 | 5.5.0 | `lua-5.5.0-linux-x86_64.tar.gz` | `bb70bfe007a4c64457a51c927844b7e25343b69f98294dcaf5a68a9ca7a92dec` |

`$comment`, replacing the current one: what the file pins (one TOOLING-09 `linux-x86_64` archive per
level, the parse oracle's `luac`); that `fetch-luac.py` refuses on a digest mismatch and
`ParseOracle` never consults `PATH`; the existing "Why not a distro package" paragraph; that the
digests are the release's own `SHA256SUMS`, so trust rests on the TOOLING-09 build repository;
"Policy: pin the release TOOLING-09 publishes for each minor version"; and the existing `LUA50`
line.

### 4.2 Release archive layout
`lua-<version>-linux-x86_64.tar.gz` holds `lua-<version>-linux-x86_64/{bin/lua,bin/luac,LICENSE}`;
the binaries are static (DR-05 `tar tzvf`, `file`). §3.1 step 3 derives the member from this.

## 5. Consequences for MAINT-48
The `build` job's `a3` record carries the filter that ran (MAINT-48 design §3.3). When Phase 3 merges,
`excludePatterns` loses `*LuaDescriptionIndexTest`, `*LuaRecursiveReferenceTest`,
`*LuaTypeGoldenMemberFilesTest` and `*ParseOracleTest`, so the test-set identity changes at that
commit. No tool changes: `fetch` accepts any identity, and a `diff` against a pre-merge baseline
lists the four classes' tests as `OUT_OF_SET after`, exiting 1 only if one fails (MAINT-48 §3.8).

## 6. Edge Cases
- **Warm VM, old stamps.** A `/cache/lunar/test/luac/5.1.5/.luac-sha` holding the old tarball digest
  mismatches the new pin, so §3.1 re-installs. `5.5.1/` stays behind, unread.
- **`build` and `corpus` on one VM.** Per-VM concurrency is 1, so they never share `/cache` at once;
  the fetchers are idempotent across jobs.
- **Reading `/cache/lunar/test` on CI (BUG-539).** The corpus sweep needed `VfsRootAccess.allowRootAccess`
  for that path (`LuaCorpusSweepTest.kt:46-56`, CI run 9735) because it resolves requires through
  the VFS. None of the four classes reads the fixture tree through the VFS: `copyFileToProject`
  loads its source with `FileUtil.loadFileBytes(File)` (`CodeInsightTestFixtureImpl.copyContent` in
  `intellij-community`), `TypeGoldenFixtures.memberFiles` and §3.2 use `File.readText`, and
  `ParseOracle` runs `luac` by `ProcessBuilder` (`ParseOracle.kt:247`). Case 1 on the Phase 3 PR is
  the executed check.
- **A non-`linux-x86_64` host.** Only that target is pinned; elsewhere step 7 fails and nothing is
  installed (risks Gap 2.3).

## 7. Integration Points
No `plugin.xml`, extension point, service or production code changes. Gradle: one filter block
removed. CI: §2.5–§2.7.

## 8. Requirement Coverage
| Requirement | Priority | Implemented by |
|---|---|---|
| MAINT-49-01 | M | §2.1 |
| MAINT-49-02 | M | §2.2, §3.2 |
| MAINT-49-03 | M | §2.3, §3.1 |
| MAINT-49-04 | M | §2.4, §4.1 |
| MAINT-49-05 | M | §2.5 |
| MAINT-49-06 | M | §2.6 |
| MAINT-49-07 | M | §2.7 |
| MAINT-49-08 | S | §2.8 |
| MAINT-49-09 | S | §2.9 |

## 9. Alternatives Considered
- **Fetch only the members the four tests read.** Rejected: a cold corpus fetch costs seconds
  (DR-04), and a subset flag would be a second list of members to keep in step with
  `TypeGoldenFixtures.MEMBER_FILES`.
- **Keep compiling `luac` in CI.** It is most of a cold provision (DR-04) and needs a C toolchain in
  every lane; downloading is a fraction of it (DR-05).
- **Keep the 500 KB / 10 k-line budget.** It passes the full-text mutant (DR-02).
- **A shared provisioning script under `tooling/ci/`.** The steps are four commands and differ by
  lane (cache re-point and sentinel on `lunar-ci`, plain `bash -e` elsewhere); a script would carry
  that branching instead of removing it.

## 10. Premises
- **`luac` must be provisioned for CI.** Chosen: the owner's TOOLING-09 release removes the compile,
  which was the only reason for the exclusion (`build.gradle.kts:405-409`).
- **The 5.5 oracle is 5.5.0, not the newer 5.5.1.** Chosen: the release TOOLING-09 publishes. No
  verdict differs between them on any input judged (DR-03).
- **Trust in the digests.** Changed from lua.org's published tarball sums to our own release's
  `SHA256SUMS`: the archive is our build, so its digest pins our artefact.
- **`lunar-ci` step semantics** (no step `if:`, the last command is the verdict, job-level env,
  dash): fixed by the executor (`build-plugin.yml:117-131`).
- **The `test` symlink re-point** is reused from the `corpus` job because it is what lets `build`
  share that job's stamped fixtures on a warm VM, not because it exists.
- **Corpus member choice for the size guard** is measured, not inherited (DR-02).

## 11. Open Questions
_None — each unknown is a DR row in risks-and-gaps.md, all executed._
