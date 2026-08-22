---
id: "BUG-451"
title: "Lunar's unconditional `--std` overrides the project's own `.luacheckrc`, producing false warnings on every rockspec and spec file"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-451: `--std` is sent on every run, and luacheck config is its lowest priority

Found 2026-08-22 while writing retroactive requirements for [[ANALYSIS-01]] and [[ANALYSIS-05]].
Both agents reached it independently, and both confirmed it by **running** the vendored luacheck
(`test/luacheck`, v1.2.0) with Lunar's exact argument vector rather than by reading Lunar's code.

## 1. Reproduction

1. Open any project containing a `.rockspec`, or a busted suite under `spec/`.
2. Look at the editor for `foo-1.0-1.rockspec`, or any `spec/**/*_spec.lua`.

## 2. Expected vs actual

- **Expected**: luacheck applies its own per-path defaults — `rockspec` files get the `rockspec`
  std, `*_spec.lua` files get `busted` — and any `std` the project sets in `.luacheckrc` wins.
- **Actual**: every rockspec reports 5+ undefined-global warnings (`package`, `version`, `source`,
  `dependencies`, `build`), and every spec file reports `describe`, `it` and `assert` as undefined.
  Both files are clean when luacheck is run by hand.

## 3. Root cause

`analysis/luacheck/LuaCheckCommandLine.kt` appends `--std <target>` unconditionally — `lua54` for
the default Standard 5.4 target, so this fires on a default configuration, not an exotic one.

luacheck's precedence is the whole problem: **command line beats config file**, and a CLI `--std`
does not merely replace the config's `std`, it disables the per-path override walk in
`options.lua` that supplies the `rockspec` and `busted` defaults. One flag removes two mechanisms.

Four consequences, all measured:

- a project's own `std = "min"` in `.luacheckrc` is silently replaced;
- luacheck's per-path defaults for rockspecs and spec files stop applying;
- `.luacheckrc` is itself a registered Lua file in Lunar, so it gets linted with the wrong std and
  reports three false warnings on its own option assignments;
- custom named `stds` are definable but unreachable, because both selectors sit below the CLI.

## 4. The user cannot work around it

Lunar appends its `--std` **after** the user's configured arguments, and repeated `--std` is
last-wins (probed in both orders). Typing `--std max` in settings ships `--std max --std lua54`,
and the user's value is dead.

`LuaCheckCommandLineTest` TC1 asserts the parameter list *contains* `--std max` — which passes
while the value is being overridden two tokens later. See [[BUG-461]].

## 5. Fix strategy

**Send `--std` only when Lunar has a reason to.** The target's std is a sensible default *in the
absence of project configuration*, not an override of it. Options, in preference order:

1. **Omit `--std` when a `.luacheckrc` is discoverable** from the working directory. Cheapest, and
   it restores every one of the four behaviours above at once.
2. Pass the target std through a mechanism that config can still beat. luacheck has no such flag,
   so this reduces to option 1.
3. Keep `--std` and additionally pass per-path overrides — reimplementing luacheck's own defaults
   inside Lunar, which is the wrong side of the tool boundary.

Whatever the choice, the user's configured arguments must be appended **last**, so an explicit
`--std` from settings wins over Lunar's.

## 6. Test strategy

No test in `src/test/kotlin` places a `.luacheckrc` on disk, asserts the working directory, or
asserts the *effective* std. That is why this survived. A regression test must run luacheck for
real against a fixture project containing a `.luacheckrc` and a rockspec, and assert warning
counts — asserting the argument vector is what produced the false confidence in the first place.
