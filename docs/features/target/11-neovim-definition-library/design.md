---
id: "TARGET-11-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "TARGET-11"
folders:
  - "[[features/target/11-neovim-definition-library/requirements|requirements]]"
---

# Technical Design: TARGET-11 — Neovim (`vim.*`) Definition Library

## 1. Architecture Overview

### Current State
The catalog (`src/main/resources/definitions/lunar-definitions-catalog.json`) holds `busted`,
`luassert`, `love2d` and `openresty`, all LuaCATS addon repositories whose definitions sit in
`<repo>-<sha>/library`. `LuaDefinitionCatalogLoaderTest.everyBundledEntryIsPinnedAndAttributed`
(`src/test/kotlin/net/internetisalie/lunar/definitions/LuaDefinitionCatalogLoaderTest.kt:58-69`)
asserts that every `rootPrefix` ends `/library`.

### Prior Art in This Repo
| Component | Role here | Decision |
| :-- | :-- | :-- |
| `LuaDefinitionCatalogLoader`, `LuaDefinitionLibraryFetcher`, `LuaArchiveExtractor`, `LuaDefinitionLibraryProvider` (`net.internetisalie.lunar.definitions`, `…toolchain.provision`) | Parse, fetch, extract under `rootPrefix` (`LuaArchiveExtractor.kt:30`, `removePrefixPath`), register | **Unchanged.** A multi-segment `rootPrefix` already works (DR-03, DR-07) |
| `LuaDefinitionCatalogLoaderTest.everyBundledEntryIsPinnedAndAttributed` | The `/library` rule | **Replaced** by §2.2 |
| `DefinitionPinProvisioningTest` (`src/test/kotlin/net/internetisalie/lunar/corpus/`) | Synthetic archive at a catalog entry's real `rootPrefix`, provisioned through `CorpusDefinitionLibraries.provision` | **Pattern reused** by §2.4; that class is not edited |
| `LuaDefinitionCatalog.kt:49-51` KDoc on `rootPrefix` | Says addons put definitions in `<repo>-<sha>/library` | **Extended** by §2.3 |

### Target State
One more catalog entry, one generalised test assertion, one new test class, one KDoc sentence.

## 2. Core Components

### 2.1 The catalog entry
Appended as the last element of `libraries` in `lunar-definitions-catalog.json`, verbatim:

```json
    {
      "id": "neovim",
      "displayName": "Neovim (vim.*)",
      "version": "779c36969e31080503be2dfccbcc3f999e289606",
      "urls": [
        "https://github.com/neovim/neovim/archive/779c36969e31080503be2dfccbcc3f999e289606.tar.gz"
      ],
      "sha256": "912c4e695f0496acf39b7c3b8877e312539ffdd067b2950015140125c41e29f3",
      "size": 13736994,
      "rootPrefix": "neovim-779c36969e31080503be2dfccbcc3f999e289606/runtime/lua",
      "license": "Apache-2.0 AND Vim AND MIT",
      "attributionUrl": "https://github.com/neovim/neovim",
      "requires": []
    }
```

- `version` is the commit of tag `v0.12.6`, and the URL is the commit archive, so the content is
  addressed by hash as for every other entry (`LuaDefinitionCatalog.kt` KDoc on `version`).
- `rootPrefix` is `runtime/lua`: the directory Neovim puts on its own module path, and the one
  lazydev.nvim registers (DR-01). `require("vim.fs")` therefore names `vim/fs.lua` under the root.
- `license`: Neovim's `LICENSE.txt` applies Apache-2.0 except to parts contributed under the Vim
  licence and to bundled third-party code; the subtree includes MIT-licensed files (DR-01). Lunar
  ships only this metadata, so no `THIRD-PARTY.md` row is added (its scope is source in the tree,
  `THIRD-PARTY.md:12-14`).

### 2.2 `LuaDefinitionCatalogLoaderTest.everyBundledEntryIsPinnedAndAttributed`
Replace the `assertTrue(entry.rootPrefix.endsWith("/library"), …)` call (`:64-67`) with:

```kotlin
val wrapper = entry.rootPrefix.substringBefore('/')
assertTrue(
    wrapper.endsWith(entry.version) &&
        entry.rootPrefix.length > wrapper.length + 1 &&
        !entry.rootPrefix.endsWith("/"),
    "${entry.id}: rootPrefix must name a directory below the archive's '<repo>-<version>' " +
        "wrapper, was '${entry.rootPrefix}'",
)
```

Every current entry and `neovim` satisfy it; the wrapper alone, the wrapper with a trailing `/`,
a wrapper of another version and a trailing `/` on the full prefix all fail it (DR-06).

Add to the same class:

```kotlin
/** TARGET-11 TC 1. */
@Test
fun neovimEntryIsPinnedToNeovim0126() {
    val neovim = assertNotNull(LuaDefinitionCatalogLoader.load().entry("neovim"))
    assertEquals(
        LuaDefinitionEntry(
            id = "neovim",
            displayName = "Neovim (vim.*)",
            version = "779c36969e31080503be2dfccbcc3f999e289606",
            urls = listOf("https://github.com/neovim/neovim/archive/779c36969e31080503be2dfccbcc3f999e289606.tar.gz"),
            sha256 = "912c4e695f0496acf39b7c3b8877e312539ffdd067b2950015140125c41e29f3",
            size = 13736994L,
            rootPrefix = "neovim-779c36969e31080503be2dfccbcc3f999e289606/runtime/lua",
            license = "Apache-2.0 AND Vim AND MIT",
            attributionUrl = "https://github.com/neovim/neovim",
            requires = emptyList(),
        ),
        neovim,
    )
}
```

### 2.3 KDoc on `LuaDefinitionEntry.rootPrefix`
In `src/main/kotlin/net/internetisalie/lunar/definitions/LuaDefinitionCatalog.kt` (`:49-51`), after
the sentence ending "…would index that `config.json` as project content." add: "Not every library is an
addon: Neovim's definitions are its own `runtime/lua`, so its prefix is
`neovim-<sha>/runtime/lua`." No code changes.

### 2.4 `net.internetisalie.lunar.definitions.NeovimDefinitionLibraryTest`
`src/test/kotlin/net/internetisalie/lunar/definitions/NeovimDefinitionLibraryTest.kt`, a
`BasePlatformTestCase`, as run in DR-07.

- **Imports** beyond the platform's: `net.internetisalie.lunar.corpus.CorpusDefinitionLibraries`,
  `net.internetisalie.lunar.corpus.CorpusEntry`, `net.internetisalie.lunar.lang.LuaLanguageLevel`,
  `net.internetisalie.lunar.settings.LuaProjectSettings`,
  `net.internetisalie.lunar.analysis.inspections.LuaUndeclaredVariableInspection`; platform:
  `com.intellij.openapi.util.io.FileUtil`, `com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess`,
  `com.intellij.testFramework.fixtures.BasePlatformTestCase`, `com.intellij.util.io.Compressor`,
  `java.io.File`, `java.nio.file.Path`.
- **Fields:** `provisioned: List<Path> = emptyList()`, `lateinit var repoRoot: File`.
- **`setUp`:** `super.setUp()`; `CorpusDefinitionLibraries.requireSettingsSubscriber(project)`;
  `repoRoot = FileUtil.createTempDirectory("target11-neovim", null)`;
  `VfsRootAccess.allowRootAccess(testRootDisposable, LuaDefinitionLibraryFetcher.defaultCacheRoot().toString(), repoRoot.canonicalPath)`;
  `LuaProjectSettings.getInstance(project).state.enabledDefinitionLibraries = mutableListOf()`;
  `myFixture.enableInspections(LuaUndeclaredVariableInspection())`.
- **`tearDown`:** `CorpusDefinitionLibraries.release(project, provisioned)`, delete `repoRoot`
  if initialised, then `super.tearDown()` in `finally` (as `DefinitionPinProvisioningTest.tearDown`).
- **`neovim()`** = `checkNotNull(LuaDefinitionCatalogLoader.load().entry("neovim"))`.
- **`writeArchive()`**: `library = neovim()`, `wrapper = "neovim-${library.version}"`,
  target `CorpusDefinitionLibraries.archiveFile(repoRoot, library)` (parent dirs created), written
  with `Compressor.Tar(target.toPath(), Compressor.Tar.Compression.GZIP)`, members:

  | Member | Content |
  | :-- | :-- |
  | `$wrapper/runtime/lua/vim/_core/shared.lua` | `_G.vim = _G.vim or {}\n\nfunction vim.list_extend(dst, src) end\n` |
  | `$wrapper/runtime/lua/vim/_meta/builtin.lua` | `---@meta\nfunction vim.schedule(fn) end\n` |
  | `$wrapper/runtime/plugin/outside.lua` | `function outside_root() end\n` |

  Member paths are literal upstream paths and are **never derived from `rootPrefix`**: a fixture
  built from `rootPrefix` moves with a mutated prefix, and the mutation goes undetected (DR-07).
  The definition files copy upstream declaration shapes at `779c3696`
  (`runtime/lua/vim/_core/shared.lua:10` and `:825`, `runtime/lua/vim/_meta/builtin.lua:184`).
- **`provisionNeovim()`**: `writeArchive()`, then `provisioned = CorpusDefinitionLibraries.provision(project, CorpusEntry("neovim-fixture", "0", emptyList(), LuaLanguageLevel.LUA51, null, listOf("neovim")), repoRoot)`.
- **`undeclared(text)`**: `configureByText("consumer.lua", text)`, `doHighlighting()`, keep
  descriptions starting `Undeclared variable '`.
- **Cases** (TC numbers from requirements):
  - `testVimIsDeclaredOnceProvisioned` (TC 3): `provisionNeovim()`;
    `assertDoesntContain(undeclared("vim.schedule(function() end)\nvim.list_extend({}, {})\n"), "Undeclared variable 'vim'")`.
  - `testVimIsUndeclaredWithoutTheLibrary` (TC 4): `writeArchive()` only;
    `assertContainsElements(undeclared("vim.schedule(function() end)\n"), "Undeclared variable 'vim'")`.
  - `testVimCompletesItsDirectMembers` (TC 5): `provisionNeovim()`; `configureByText("consumer.lua", "vim.<caret>\n")`;
    `completeBasic()`; `assertContainsElements(myFixture.lookupElementStrings.orEmpty(), "list_extend", "schedule")`.
  - `testFilesOutsideRuntimeLuaAreNotRegistered` (TC 6): `provisionNeovim()`;
    `assertContainsElements(undeclared("outside_root()\n"), "Undeclared variable 'outside_root'")`.

The class name avoids the word `Corpus`, so the routine suite runs it (the sweep filter is
`'*Corpus*'`).

**Threading.** Every case runs on the test EDT; `CorpusDefinitionLibraries` settles indexing
itself (`CorpusDefinitionLibraries.kt`, `settle`).

## 3. Algorithms
None. §2.2's rule is the only logic and is given as code.

## 4. External Data & Parsing

### 4.1 The pinned archive (TC 7)
A gzip tarball whose members all begin `neovim-779c36969e31080503be2dfccbcc3f999e289606/`. TARGET-08's
extractor keeps members under `rootPrefix` and strips the prefix. TC 7 checks the real bytes, from the
worktree root (network required; `$SCRATCH` any empty directory):

```bash
python3 -c 'import sys, tarfile; from pathlib import Path; sys.path.insert(0, "tooling/corpus"); import definition_pins as d; lib = d.load_catalog(d.CATALOG)["neovim"]; a = d.fetch_library(lib, Path(sys.argv[1])); p = lib["rootPrefix"] + "/"; ms = tarfile.open(a, "r:gz").getmembers(); n = [m.name for m in ms if m.isfile() and m.name.startswith(p)]; print(len(n), all(x.endswith(".lua") for x in n), p + "vim/_meta/builtin.lua" in n, p + "uv/_meta.lua" in n, sum(m.issym() or m.islnk() for m in ms))' "$SCRATCH"
```

Output fields: files under the prefix, whether all are `.lua`, whether each named definition file
is present, and the count of links in the whole archive. Expected line, the one home of this value
(TC 7, plan Phase 2): `128 True True True 0` (DR-01).

### 4.2 The cost check (TC 9)
This is DR-08's measurement, run with the repository's own corpus tooling. It needs the network and
a builder reached through `tooling/gce-builder/gce-builder.sh run`, either backend (`builder-run`
wraps it with the builder lock).

The two corpus entries and the probe methods below are **temporary**. Apply them with the
`temporary-edits` skill, restore them from `HEAD` afterwards, and never commit them. [[MAINT-39]]
adds the permanent lazy.nvim member, with its own name, baseline and test method. The temporary
names `target11control` and `target11neovim` cannot collide with that member (`CorpusManifest`
rejects duplicate names), so TC 9 runs the same way before or after MAINT-39. Neither feature waits
for the other.

Steps 1 and 2 need the Phase 1 tree, whose catalog carries `neovim`.

1. Append to the `corpora` array of `tooling/corpus/corpus.json`:

   ```json
   {
     "name": "target11control",
     "url": "https://github.com/folke/lazy.nvim.git",
     "commit": "85c7ff3711b730b4030d03144f6db6375044ae82",
     "roots": ["lua"],
     "luaLevel": "LUA51",
     "moduleRoot": "lua"
   },
   {
     "name": "target11neovim",
     "url": "https://github.com/folke/lazy.nvim.git",
     "commit": "85c7ff3711b730b4030d03144f6db6375044ae82",
     "roots": ["lua"],
     "luaLevel": "LUA51",
     "moduleRoot": "lua",
     "definitionLibraries": ["neovim"]
   }
   ```

   - `luaLevel` must be `LUA51`: it selects the stdlib globals, so it moves the counts. Neovim
     embeds LuaJIT, which is 5.1.
   - `moduleRoot` must be `lua`, because lazy.nvim's `require("lazy.…")` names resolve under `lua/`.
   - The two entries differ only in `definitionLibraries`.
   - These are DR-08's probe inputs. Fetched this way, both checkouts are byte-identical to the trees
     DR-08 measured, `.corpus-sha` included (DR-08 "Inputs").
2. From the worktree root, run `python3 tooling/corpus/fetch-corpus.py`, then
   `python3 tooling/corpus/fetch-luac.py`.
   - The first fetches lazy.nvim at the pin into `test/corpus/target11control` and
     `test/corpus/target11neovim`, each stamped with `.corpus-sha`, which `CorpusGuards` checks.
   - It also fetches the `neovim` archive into `test/corpus/.definitions/` and verifies it against
     the catalog's `sha256` and `size`.
   - The second builds the pinned `luac` parse oracles in `test/luac/`, as every sweep needs
     (`tooling/corpus/luac.json`). It does nothing if they are already built.
   - Both exit `1` on any failure. The builder sync pushes `test/` dereferenced, so the trees reach
     whichever backend runs step 4.
3. In `src/test/kotlin/net/internetisalie/lunar/corpus/LuaCorpusSweepTest.kt`, insert after
   `testPenlightCorpus`:

   ```kotlin
   @Test
   fun testProbeNvControl() = probeArm("target11control")

   @Test
   fun testProbeNvLibrary() = probeArm("target11neovim")

   private fun probeArm(name: String) {
       val repoRoot = File(testDataPath)
       val entry = CorpusManifest.entry(repoRoot, name)
       CorpusGuards.assertCorpusFetched(repoRoot, entry)
       LuaProjectSettings.getInstance(myFixture.project).state.languageLevel = entry.luaLevel
       refreshFor(null, entry, repoRoot)
       measure(name, entry, repoRoot)
   }
   ```
4. Run `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache -PwithCorpus --tests
   'net.internetisalie.lunar.corpus.LuaCorpusSweepTest.testProbeNv<Arm>'"` four times, one arm per
   run, in the order Control, Library, Control, Library.
5. After each run, read `elapsedMs=<n>` from the `[corpus:target11control]` or
   `[corpus:target11neovim]` line of
   `build/test-results/test/TEST-net.internetisalie.lunar.corpus.LuaCorpusSweepTest.xml` on the
   builder. The value is regex `elapsedMs=(\d+)`.
6. Compute the ratio as the mean of the Library values divided by the mean of the Control values.
   TC 9 passes at ≤ 1.5.
7. Restore `corpus.json` and `LuaCorpusSweepTest.kt` from `HEAD`. The checkouts under
   `test/corpus/target11*` are outside the repository and are read only by name, so leaving them
   affects no other sweep.

## 5. Data Flow
User ticks *Neovim (vim.\*)* on the Definition Libraries page → TARGET-08 fetches the archive in the
background, verifies it ADVISORY, extracts `runtime/lua` into `<system>/lunar/definitions/neovim-779c…/`
→ `LuaDefinitionLibraryProvider` registers it → `vim` resolves to the roots of the tree's
`function vim.<name>` declarations (DR-03, DR-05).

## 6. Edge Cases
- **A file-local table in the tree must not become a global.** Every Neovim module's
  `local M = {}` / `local log = {}`, a `local performResume` later defined by `function`, and a
  `---@class uv` on `local uv = {}` stay file-local since [[BUG-509]] and [[BUG-510]]; of the probed
  candidate names, the tree adds only `vim` to what user code can read (DR-11). `vim` has no `local vim` in `runtime/lua` (DR-01)
  and stays global.
- **`vim` has one resolution target per `function vim.<…>` declaration**, 779 of them (DR-03), so Go
  to Declaration on `vim` offers that many targets. Collapsing the fan-out does not close the
  highlighting gap (DR-08), and this feature leaves it as it is.
- **Implementation files ship with the definitions.** `runtime/lua` holds Neovim's Lua
  implementation as well as `_meta` files; both are what Neovim itself loads, so both are kept.

## 7. Integration Points
No `plugin.xml` change: the catalog is a resource read by the existing loader, and every extension
it reaches is TARGET-08's.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by |
| :-- | :-- | :-- |
| TARGET-11-01 | M | §2.1; TC 1 in §2.2 |
| TARGET-11-02 | M | §2.2 |
| TARGET-11-03 | M | §2.1; tested by §2.4 |
| TARGET-11-04 | M | §2.1 `rootPrefix`; tested by §2.4 |
| TARGET-11-05 | M | §4.1 |
| TARGET-11-06 | M | §2.1 (on merged BUG-503, BUG-504, BUG-509, BUG-510); human-verification-checklists.md |
| TARGET-11-09 | M | §4.2, after [[BUG-517]] |
| TARGET-11-07 | S | implementation-plan Phase 2 |
| TARGET-11-08 | W | BUG-505, BUG-507 |

## 9. Alternatives Considered
- **A trimmed, self-published archive** (the TARGET-10 route) would shrink the download to the
  registered subtree alone (DR-01 measures both). Rejected: it adds a repository to maintain and re-publish per Neovim release, to
  save a one-time download per user.
- **Only `runtime/lua/vim/_meta`.** Drops `uv/_meta.lua` and every `vim.<module>` file that
  `vim/_meta.lua` binds by `require`, so later engine fixes (BUG-507) could never reach them.
- **A `LuaCATS/neovim` repository.** None exists (DR-01).
- **A tag URL** (`archive/refs/tags/v0.12.6.tar.gz`). A tag can move; a commit cannot.
- **A release asset** (`releases/download/v0.12.6/nvim-linux-x86_64.tar.gz`). Uploaded assets are
  fixed bytes with a GitHub-published digest, which removes Risk 1.1. Rejected for now: the asset is a
  platform build whose `share/nvim/runtime/lua` holds every file of the commit archive's subtree,
  byte-identical, plus LuaJIT's `jit/*.lua` modules, and its wrapper `nvim-linux-x86_64` breaks §2.2's rule (DR-01). It stays the fallback if
  the commit archive's bytes ever change.

## 10. Open Questions
_None — every runtime premise is a DR row in [risks-and-gaps.md](risks-and-gaps.md)._
