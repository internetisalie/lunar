---
id: TARGET-12-PLAN
parent_id: TARGET-12
type: plan
folders:
  - "[[features/target/12-luajit-runtime-stubs/requirements|requirements]]"
title: "Implementation Plan"
---

# TARGET-12: Implementation Plan

**Precondition:** [[BUG-513]] is `done` on the branch's base, so `lua-5.1/` matches the Lua 5.1
manual set in that report's §3–§4.

Every phase ends with the full suite green, and each exit check uses only tests that exist by then.
Run builds through `tooling/gce-builder/gce-builder.sh` or the `builder-run` wrapper, never through
a local `./gradlew`. The full-suite gate is `run "test --rerun --no-build-cache"`.

### Phase 1: Test isolation [Must]
- [x] Make the design §7.5 edits to `RuntimeLibraryProviderTest` (repoint and rename),
      `LuaSettingsNotificationTest`, `LuaRocksInterpreterInitializerTest` and the
      `RedisAmbientTypingTest` comment. Nothing under `runtime/` changes yet.
- **Exit**: the full suite is green.

### Phase 2: Stdlib copies [Must]
- [x] For `v` in `2.0` and `2.1`, copy each stdlib file listed in design §3 from
      `src/main/resources/runtime/standard/lua-5.1/` to `src/main/resources/runtime/luajit/luajit-<v>/`.
      Delete `src/main/resources/runtime/luajit/.gitkeep`.
- [x] Apply design §5's E1–E9 to the versions each one names, using the Edit tool. If an `OLD`
      anchor does not match exactly once, stop and report it.
- [x] Create `LuaJitStubResourcesTest` (design §7.1) with `STDLIB_EDITS`, `rebuild` and TC 11
      (design §7.3), and `LuaJitRuntimeStubsTest` with the `useTarget` helper and TC 4.
- **Exit**: TC 4 and TC 11 pass, and so does the full suite.
  `git diff --no-index src/main/resources/runtime/standard/lua-5.1/<f> src/main/resources/runtime/luajit/luajit-<v>/<f>`
  shows only the §5 edits for that file and version.

### Phase 3: LuaJIT-specific stubs and the module rule [Must]
- [x] Create every LuaJIT-specific file in design §3 from §4.1–§4.7, each starting with the §6
      `HEADER`. `bit.lua` is the header, an empty line, then the unmodified bytes of
      `src/main/resources/runtime/redis/redis-7/bit.lua`. For 2.0, leave out `jit.security`,
      `ffi.typeinfo`, `jit/profile.lua`, `string/buffer.lua` and `table/`, and add the `jit.util`
      field (§4.1).
- [x] Implement design §1.1 in `LuaModuleFileResolver.kt`, with `runtimeRootVerdict` as `internal`.
- [x] Add TCs 1–3, 5–10, 12, 13, 15, 16, 18 and 19 to the two test classes.
- [x] Mutation proof for TC 1, TC 7, TC 8, TC 18 and TC 19. Apply each named mutation, watch the test go
      red, then revert it with `git show HEAD:<path> > <path>` (or from your own copy, for a file
      that is not yet committed). Record the red lines in the PR description.
- **Exit**: every test case except TC 17 passes, the full suite is green, and so is `ktlintCheck`.
  Format with `run ktlintFormat` followed by the documented rsync pull, never
  `ktlintFormat ktlintCheck` in one run.

### Phase 4: Attribution [Must]
- [x] Make the design §6 `THIRD-PARTY.md` edits, and add TC 17 to `LuaJitStubResourcesTest`.
- **Exit**: TC 17 and the full suite are green, and `python3 scripts/lint_docs.py docs` passes.

### Phase 5: Live check [Should]
- [x] In the verify-in-ide sandbox, set *Lua Project → Target* to LuaJIT 2.1 and open a file
      containing `local ffi = require("ffi"); print(jit.version, bit.band(1,2), ffi.new)`. Confirm
      that:
      - no "Undeclared variable" warning appears;
      - Ctrl+click on `version` opens `luajit-2.1/jit.lua`;
      - `jit.` offers `version`.
- **Exit**: a screenshot is attached to the PR. This phase replaces a separate
  `human-verification-checklists.md`, because it is the feature's only manual check.
- Result (2026-10-27, GoLand 2026.2.3): no warning in the file, Ctrl+click on `version` opens
  `luajit-2.1/jit.lua`, and `jit.ver` offers `version` and `version_num`. Screenshots in
  `~/.cache/claude-scratch/lunar/target-12/hv/`.

## Notes
- Requirements → phases:
  - -11 → Phase 1;
  - -01, -07 and -08 → Phase 2;
  - -02 to -06, -09, -12 and -13 → Phase 3;
  - -10 → Phases 3 and 4.
- The corpus sweep is not needed. No corpus member uses a LuaJIT target until [[MAINT-39]] wires
  one (design §8). The §1.1 rule only changes candidates under the active runtime root (Risk 1.2).
