---
id: "BUG-496"
title: "The corpus sweep leaks penlight's module root into every member swept after it"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-496: The corpus sweep leaks penlight's module root into every member swept after it

`CorpusSweep.applyModuleRoot` writes the shared light project's `package.path` for any corpus entry
that declares `moduleRoot`, and nothing ever restores it. Every member swept afterwards in the same
JVM inherits it, through two paths:

- **The search path is replaced, not extended.** `PathConfiguration.getStaticSourcePathPatterns`
  (`SourcePathPattern.kt:37-44`) uses the setting as the whole `package.path` and falls back to
  `DEFAULT_SOURCE_PATH` — which includes `$PROJECT_DIR$/?.lua` — only when the setting is empty. A
  later member's `require(...)` therefore tries penlight's `lua/` patterns *instead of* the default
  ones; its own files are then reached only through the `FilenameIndex` fallback in
  `resolveModuleCandidates` (`LuaModuleFileResolver.kt:37-49`).
- **The path can become an indexed library.** `PlatformLibraryProvider.getExternalLibraries`
  (`PlatformLibraryProvider.kt:54-73`, registered at `plugin.xml:537`) turns every absolute leading
  path outside the project base into a "Search Trees" library root whenever roots are recomputed.
  Any roots change while penlight's value is set therefore indexes `test/corpus/penlight/lua` for
  every later member — and the `FilenameIndex` fallback's condition accepts any indexed `init.lua`
  for an undotted module name (`LuaModuleFileResolver.kt:42-48`; read, not executed).

Found while planning [[MAINT-37]], whose tearDown has to reset per-member project state and so had
to inventory what else the sweep leaves behind. MAINT-37 delivers the fix (its MAINT-37-09,
Phase 3b) before it pins anything, because from its Phase 4 on, each pinned member's library
`release` fires exactly the roots change described above, straight after penlight.

## Reproduction (from the code; the count effect is not measured)

1. `CorpusSweep.kt:146` — `LuaProjectSettings.getInstance(fixture.project).state.sourcePath = "$base/?.lua;$base/?/init.lua"`,
   reached only when `entry.moduleRoot != null`. In `tooling/corpus/corpus.json` only `penlight`
   sets it (`"moduleRoot": "lua"`).
2. `LuaCorpusSweepTest` has no `tearDown`, and `git grep -n sourcePath src/test/kotlin/net/internetisalie/lunar/corpus`
   returns that one write and a comment — no reset.
3. `BasePlatformTestCase`'s light project, and with it `LuaProjectSettings`, is shared across test
   methods and classes (`LuaDefinitionLibraryProviderTest.kt:27-29` documents the same fact for the
   enable list).
4. `LuaCorpusSweepTest` declares no `@FixMethodOrder`, so JUnit 4 orders methods by
   `String.hashCode()` of the name. Computed (Java's hash, executed 2026-10-10):
   ```
   -44580566 testLuacheckCorpus
   577942864 testLuarocksCorpus
   1023308535 testPenlightCorpus
   1637429974 testZerobraneCorpus
   ```
   So `testZerobraneCorpus` runs after `testPenlightCorpus` and inherits its `sourcePath`.

## Expected

Each member's sweep runs with the project settings its manifest entry describes — a member that
declares no `moduleRoot` resolves from its own checkout root only, whatever ran before it.

## Actual

zerobrane, in the gate's `--tests '*Corpus*'` shape, sweeps with `sourcePath` pointing at
`test/corpus/penlight/lua` and without the default patterns. Whether that moves any zerobrane count
today is **not measured**: none of zerobrane's `require` targets is a `pl.*` module; the copied
fixture files live in the in-memory `temp://` filesystem, which the `LocalFileSystem`-only
`findByPath` never reaches under either path (`CorpusSweep.kt:134-137`); and whether anything fires a roots change between
penlight's write and zerobrane's sweep today is not established. Whether the library path fires in a
light fixture at all is MAINT-37's DR-06. It is one concrete instance of the "cross-member JVM
state" BUG-418 measured and left unexplained.

## Impact

Low today: recording and gating share the invocation shape, so the ratchet compares like with like
(BUG-418). It matters to anyone attributing a count change to a single member, and it will bite the
first time a member *after* penlight in hash order declares its own `moduleRoot` — or none, and
expects the default. It becomes material the moment anything adds a roots change after penlight,
which MAINT-37's pins do. Fixing it will move zerobrane's baseline if the leak has any effect, so
the fix is its own commit and re-baseline, separate from MAINT-37's pins.

## Fix

Planned in [MAINT-37](../../maint/37-pinned-definition-libraries/requirements.md) as MAINT-37-09:
`LuaCorpusSweepTest.setUp` snapshots `sourcePath`; `tearDown` restores it before releasing
definition libraries and refreshes roots when it changed (MAINT-37 design §2.13, §3.10;
TC 33–35). The Resolution section is added by MAINT-37 Phase 3b, with zerobrane's measured baseline
diff.
