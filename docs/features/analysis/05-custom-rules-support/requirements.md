---
id: ANALYSIS-05
title: "05: Custom Rules Support"
type: feature
status: "done"
vf_icon: ✅
priority: "medium"
parent_id: ANALYSIS
folders: ["[[features/analysis/requirements|requirements]]"]
---

# 05: Custom Rules Support

Let a project decide, in its own version-controlled `.luacheckrc`, which globals exist, which
warnings matter and which files are checked — and have the IDE honour that decision rather than
overrule it.

## What "custom rules" means here — and what it does not

The phrase is ambiguous and four readings are defensible. This feature covers the first two;
the third is owned elsewhere; the fourth is declined.

| Reading | Verdict | Why |
| :--- | :--- | :--- |
| **(a)** luacheck reads the project's `.luacheckrc` and Lunar must not get in the way | **In scope — the substance of the feature.** | The epic row says exactly this ("Support project-specific Luacheck configuration files"). Almost all of it is behaviour Lunar *inherits* by launching luacheck correctly, not behaviour Lunar implements. The real requirements are therefore about the process contract — working directory, `--filename`, which flags Lunar adds — because those are the only places Lunar can break it. |
| **(b)** the user supplies their own luacheck flags / globals / std from Lunar's own settings | **In scope, shared.** | [[ANALYSIS-02]] owns the settings *surface* (the field, its scoping, its persistence). ANALYSIS-05 owns what those flags **mean once they meet a `.luacheckrc`** — i.e. precedence. That question belongs to neither feature alone and is where the defects are. |
| **(c)** first-class editing of `.luacheckrc` itself — completion, validation, navigation of its option table | **Out of scope; owned by [[SCHEMA-03]].** | Already shipped as `LuacheckrcSchemaProvider` (`lang/schema/providers/`), backed by `jsonschema/luacheck-config.schema.json`, plus the `fileNames=".luacheckrc;.busted"` registration in `plugin.xml`. One row below records its residue so this document is not silent about it, and does not restate it. |
| **(d)** user-authored inspection rules native to Lunar (a rule DSL) | **Won't.** | No such mechanism exists or is planned, and Lua has no established one to borrow. In this ecosystem a user's "custom rule" *is* a luacheck rule; inventing a second, Lunar-only rule language would split the vocabulary a project already versions in `.luacheckrc`. Recorded as `ANALYSIS-05-25` rather than left implicit. |

The one place Lunar *does* interpret a user-authored luacheck rule itself is inline
`-- luacheck: ignore` comments, which it re-implements to suppress its own native inspections.
That is [[INSP-01]]'s code; `ANALYSIS-05-22` records only how far it diverges from luacheck's
documented semantics, because a rule that means two different things in the same editor is this
feature's problem even if the code is not.

## How these requirements were derived

**Not from Lunar's code.** A specification read off its own implementation cannot fail — the defect
that left `RUN-07` (`docs/features/debug/07-lazy-remote-stack-evaluation/`) marked shipped for
months ([[BUG-450]] §4) — and this file was one of the same 16 bulk-created placeholders. The rows
below come from three external sources, in this order:

1. **luacheck's own configuration surface** — the authority. `docsrc/config.rst`, `docsrc/cli.rst`
   and `docsrc/inline.rst` of the vendored luacheck (`test/luacheck`, v1.2.0) enumerate every
   configuration mechanism a project can use: the option table, custom `stds`, per-path `files[…]`
   overrides, the default per-path overrides, discovery by upward search, and the CLI/inline
   precedence ladder. Each mechanism is one row, answered "we honour it" or "we do not".
2. **`docsrc/cli.rst` § *Stable interface for editor plugins and tools*** — luacheck publishes a
   contract specifically for tools like Lunar: start from the checked file's directory, feed the
   buffer on stdin with `--filename`, use the `plain` formatter, add `--codes` / `--ranges`. That
   section is a ready-made conformance checklist and several rows are simply its clauses.
3. **Executed probes, 2026-08-22.** Every behavioural claim below was produced by running the
   vendored luacheck against a fixture project, reproducing Lunar's exact argument vector, and is
   quoted from that output. Reading `LuaCheckCommandLine.kt` would have shown a `--std` being
   appended; only running it shows what that costs.

Lunar's implementation was consulted **last and only to check**, not to enumerate.

### The probe

Vendored luacheck 1.2.0 run from a fixture with `.luacheckrc` at the project root:

```lua
std = "min"
globals = {"myGlobal"}
exclude_files = {"vendor/**"}
files["sub"] = {ignore = {"212"}}
```

reproducing Lunar's invocation — CWD = the checked file's own directory, document on stdin,
`--filename <name>`, `--codes --ranges`, plus the `--std <target>` Lunar appends:

```
$ cd proj/sub && luacheck --std lua54 --codes --ranges --filename warn.lua - < warn.lua
Checking warn.lua    OK
Total: 0 warnings / 0 errors in 1 file

$ cd proj/sub && luacheck --codes --ranges --filename warn.lua - < warn.lua
warn.lua:1:1-4: (W113) accessing undefined variable 'warn'
Total: 1 warning / 0 errors in 1 file
```

The project asked for `std = "min"`. With Lunar's argument vector it gets `lua54`.

## Requirements & Status

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `ANALYSIS-05-01` | **Config in the file's own directory is found** | **M** | **Full** | luacheck searches from its working directory; `LuaCheckAnnotator.collectInformation` sets `workDir = virtualFile.parent`, which is precisely what cli.rst's editor-plugin contract prescribes. **Lunar parses nothing** — the tool does, and Lunar stays out of the way. Recorded as a requirement anyway because it is one line away from being wrong: a project-root working directory would look equally reasonable and would break `-03`, `-06` and every relative path in the config. |
| `ANALYSIS-05-02` | **Config in an ancestor directory is found** | **M** | **Full** | Same mechanism — luacheck walks parents to the filesystem root. Probed: CWD `proj/sub`, config at `proj/.luacheckrc` declaring `globals = {"myGlobal"}`; `myGlobal = 1` in `proj/sub/g.lua` produced no warning. |
| `ANALYSIS-05-03` | **Per-path `files[<glob>]` overrides select on the real path** | **M** | **Full** | luacheck resolves the override key against `normalize(join(cwd, --filename))` (`src/luacheck/config.lua:503`), so the bare `--filename foo.lua` Lunar passes is only correct *because* the working directory is the file's own. Probed: `files["sub"] = {ignore = {"212"}}` suppressed the unused-argument warning in `proj/sub/foo.lua`, and did not suppress it elsewhere. Both halves of the contract are load-bearing and neither is asserted by a test. |
| `ANALYSIS-05-04` | **`globals` / `read_globals` / `not_globals` honoured** | **M** | **Full** | Tool-owned. Probed via `myGlobal`. Lunar adds no globals of its own to the command line. |
| `ANALYSIS-05-05` | **`ignore` / `only` / `enable` patterns honoured** | **M** | **Full** | Tool-owned; probed through the `files["sub"].ignore` override in `-03`. |
| `ANALYSIS-05-06` | **`exclude_files` / `include_files` honoured** | **S** | **Full** | Probed: with `exclude_files = {"vendor/**"}`, checking `proj/vendor/foo.lua` yields `Total: 0 warnings / 0 errors in 0 files`, exit 0 — luacheck drops the input entirely (`src/luacheck/runner.lua:100`) and Lunar therefore shows nothing, which is the correct outcome. Works only because `--filename` is passed; an unnamed stdin input bypasses the filter. |
| `ANALYSIS-05-07` | **Remaining scalar options honoured** (`max_*_line_length`, `unused`, `redefined`, `self`, `module`, `allow_defined`, `compat`, `operators`, `max_cyclomatic_complexity`) | **S** | **Full** | Tool-owned by the same mechanism as `-04`/`-05`; Lunar passes none of them and so cannot override them. Not probed individually — the mechanism is, and no Lunar code path distinguishes these keys. |
| `ANALYSIS-05-08` | **A project's `std` is honoured** | **M** | **Not Implemented** | `LuaCheckCommandLine.resolveArguments` appends `--std <target.getLuacheckStd()>` on every run (`lua54` for the default Standard 5.4 target; the registry supplies one for every Lua, LuaJIT and Redis version). config.rst: *"Options loaded from config have the lowest priority"*. Probe above: `std = "min"` in `.luacheckrc` is silently replaced by `lua54`. The project's declared dialect is the one thing a `.luacheckrc` most often exists to state, and it is the one thing Lunar overrules. |
| `ANALYSIS-05-09` | **luacheck's default per-path std overrides survive** (`+busted`, `+rockspec`, `+luacheckrc`, `+ldoc`) | **M** | **Not Implemented** | Same cause, worse symptom. cli.rst: *"Setting `std` on the commandline removes these default overrides"* — confirmed in `src/luacheck/options.lua:118-152`, where a non-additive `std` sets the base and **breaks** the reverse walk before lower-priority entries are reached. Probed on `proj/spec/thing_spec.lua`: with `--std lua54`, `describe` and `it` are reported as undefined variables; without it, the file is clean. **Every busted spec file in every project reports two-or-more false positives per test block.** |
| `ANALYSIS-05-10` | **`.luacheckrc` is itself checked with the `+luacheckrc` std** | **S** | **Not Implemented** | `.luacheckrc` is registered as a Lua file (`plugin.xml` `fileNames`), so `LuaCheckAnnotator` claims it and the same `--std` applies. Probed against a three-line real config: `setting non-standard global variable 'std'` (W111), `mutating non-standard global variable 'files'` (W112), `mutating non-standard global variable 'stds'` (W112). Without `--std`: clean. The feature whose subject is the config file puts three false warnings on the config file. |
| `ANALYSIS-05-11` | **Custom named `stds` defined in the config are usable** | **S** | **Not Implemented** | A custom set is *definable* but not *selectable*: it is chosen by a `std` assignment at config level or in a `files[…]` override, and both sit below the CLI in the precedence ladder. Probed: `stds.mylib = {read_globals = {"mylib"}}` + `std = "lua54+mylib"` is clean without `--std` and reports `accessing undefined variable 'mylib'` with it. This is the intended extension point for LÖVE, OpenResty and in-house frameworks, and Lunar closes it. |
| `ANALYSIS-05-12` | **A `formatter` set in the config does not silence the integration** | **M** | **Not Implemented** | Lunar never passes `--formatter`, so a project's choice reaches the output Lunar must parse. Probed with `formatter = "JUnit"`: luacheck emits XML whose `message="a.lua:1:7: …"` lacks the column *range* `LuaCheckInvoker.LINE_PATTERN` requires, so **zero** warnings are extracted and the file appears clean. cli.rst's editor contract says *"Plain formatter should be used"*; `--formatter plain` is one token and Lunar does not send it. (`TAP` survives by luck — its `not ok 1 ` prefix is absorbed by the pattern's lazy first group.) |
| `ANALYSIS-05-13` | **A `quiet` level set in the config does not suppress annotations** | **S** | **Not Implemented** | Same cause, same fix. Probed with `quiet = 3`: output is the summary line alone, so no annotation is produced. There is no `--no-quiet` flag; `--formatter plain` is the remedy and was probed to defeat `quiet = 3` (`a.lua:1:7-7: (W211) unused variable 'x'`). |
| `ANALYSIS-05-14` | **A `color` setting in the config does not corrupt messages** | **S** | **Full** | luacheck colourises by default and Lunar never passes `--no-color`; `LuaCheckInvoker.ANSI_PATTERN` strips the escapes from the message before it becomes an annotation. Honest caveat: this works by post-hoc cleanup rather than by asking for plain output, so it is one formatter change away from `-12`. |
| `ANALYSIS-05-15` | **A broken or invalid config is reported to the user** | **M** | **Partial** | luacheck exits **4** with a single stderr line — probed: `Critical error: Couldn't load configuration from .luacheckrc: syntax error (line 1: unexpected symbol near '=')` and `Critical error: in config loaded from .luacheckrc: invalid value of option 'std': unknown std 'nosuchstd'`. `LuaCheckInvoker.completedOutcome` treats any exit ≥ 2 as fatal and surfaces that line, so the user is not left guessing. But it lands as a whole-file **WARNING** banner on *the Lua file being edited*, repeated in every open file, is not navigable to the offending `.luacheckrc` line, and is not an error. |
| `ANALYSIS-05-16` | **The user can see which config is in effect** | **C** | **Not Implemented** | Nothing reports the resolved config path, the effective `std`, or that a `.luacheckrc` was found at all. `LuaCheckCommandLine` builds the argument vector and discards it; `LuaCheckInvoker` logs only at `debug`, plus a `warn` on failure. With `-08`…`-11` in play, "why is my `.luacheckrc` being ignored" is currently undiagnosable from inside the IDE. |
| `ANALYSIS-05-17` | **A config change takes effect without restarting the IDE** | **S** | **Partial** | Each `doAnnotate` re-executes luacheck, which re-reads the config from disk; nothing caches the argument vector or the config. But the checked file arrives on **stdin** while `.luacheckrc` is read from **disk**, so edits to an open, unsaved `.luacheckrc` have no effect — an asymmetry the user is given no cue about. No `DaemonCodeAnalyzer.restart()` exists anywhere under `analysis/` or `toolchain/`, so re-highlighting of already-open files after a save is incidental, not arranged. |
| `ANALYSIS-05-18` | **The user can supply arbitrary luacheck flags** | **M** | **Full** | `LUACHECK_ARGUMENTS` (`LuaKindOptionKeys`) is split with `ParametersListUtil.parseToArray` and prepended; `effectiveKindOption` prefers the project value over the application value. Surface owned by [[ANALYSIS-02]]. |
| `ANALYSIS-05-19` | **`--config` / `--no-config` are reachable** | **S** | **Full** | Through the same field. Non-obvious and worth stating: `--config <name>` does not resolve as a fixed relative path — it participates in the *same upward search*, so a bare `--config .luacheckrc` in the shared arguments field is stable across files at different depths. Probed from `proj/sub`. |
| `ANALYSIS-05-20` | **A `--std` the user typed outranks the target's** | **S** | **Not Implemented** | Lunar appends the target's `--std` **after** the user's arguments, and `dedupePairs` keeps both pairs because their values differ. Probed: repeated `--std` is last-wins — `--std min --std lua54` accepts `warn`, `--std lua54 --std min` rejects it. So a user who reaches for the settings field to work around `-08` still loses. |
| `ANALYSIS-05-21` | **Inline `-- luacheck:` options are honoured for luacheck's own warnings** | **M** | **Full** | Tool-owned and top of the precedence ladder (inline.rst). Better than it needs to be, and by construction rather than accident: Lunar sends the **document text** on stdin, so an inline option typed a moment ago applies before the file is saved. |
| `ANALYSIS-05-22` | **Inline `-- luacheck: ignore` is honoured for Lunar's *native* inspections** | **C** | **Partial** | `LuaInspectionSuppression` (owned by [[INSP-01]], consumed by `LuaUndeclaredVariableInspection`, `LuaGlobalCreationInspection` and the Redis inspections) re-implements one verb of one inline option. It always produces a single-line range, whereas inline.rst specifies that an option on a line of its own applies *to the end of the enclosing closure*; `push`/`pop`, `globals`, `std`, `only`, `enable` and the `no <option>` forms are unsupported. The same comment therefore means two different things depending on which analyser reads it. |
| `ANALYSIS-05-23` | **`.luacheckrc` gets editing support (completion, validation)** | **C** | **Partial** | Shipped by [[SCHEMA-03]] — `LuacheckrcSchemaProvider` → `jsonschema/luacheck-config.schema.json`. Residue recorded here only so this feature is not silent about it: the schema describes 24 keys and omits `stds`, `new_globals`, `new_read_globals`, `not_globals`, `operators`, `formatter`, `quiet`, `color`, `codes`, `ranges` and `jobs`, and sets `additionalProperties: true`, so those neither complete nor validate. Any change belongs to SCHEMA-03. |
| `ANALYSIS-05-24` | **Quick fix: add an undefined global to `.luacheckrc`** | **C** | **Not Implemented** | The obvious closing of the loop — luacheck reports `accessing undefined variable 'x'`, and the fix is one line in a file the IDE already knows how to find, parse and complete. No intention or quick fix exists; the annotator creates annotations with no fixes attached at all. |
| `ANALYSIS-05-25` | **User-authored inspection rules native to Lunar** | **W** | **Won't** | Reading (d) above. A second, Lunar-only rule language would compete with the `.luacheckrc` a project already versions and shares with its CI. If Lunar-native rules are ever wanted, the route is Lunar inspections with settings, not a DSL. |
| `ANALYSIS-05-26` | **Navigate from a warning to the config rule that produced it** | **W** | **Won't** | luacheck's output carries no provenance — the plain/default formatters emit code, position and message and nothing about which option, per-path override or std admitted or suppressed it. Deriving it would mean re-implementing the precedence ladder in Kotlin against a config Lunar deliberately does not parse (`-01`). |
| `ANALYSIS-05-27` | **`cache` and `jobs` config options** | **W** | **Won't** | Both target batch runs over many files. Lunar checks one buffer per invocation over stdin; probed with `cache = true`, no cache file is written. Nothing to honour and nothing to break. |

## The honest summary

**Fifteen of these twenty-seven rows are satisfied by luacheck, not by Lunar** — `-01` through
`-07`, `-14`, `-19` and `-21` are all "the tool already does this and we correctly stay out of the
way". That is a legitimate **Full** and the right architecture: Lunar does not parse `.luacheckrc`
and should not.

But the staying-out-of-the-way is not free, and Lunar does not manage it. Two decisions —
appending `--std` unconditionally (`-08`…`-11`, `-20`) and never asking for the `plain` formatter
(`-12`, `-13`) — take back a large part of what the tool gives, and both are decisions *about the
argument vector*, which is the only thing this feature owns. A config file whose `std`, whose
custom `stds` and whose `_spec.lua` handling are all overruled is not fully "supported", and a run
that produced no annotations at all (`-12`, `-13`) is indistinguishable from a clean file.

**On the `done` in the front matter.** It stays `done`, and it now means something it did not
before: the integration shipped and `-01` through `-07` are evidenced above rather than asserted —
which is more than could be said when this file was one of the 16 bulk-created placeholders
([[BUG-450]] §4). It does **not** mean every row is Full. `-08` through `-13` and `-20` are live
defects on a shipped feature and belong in bug reports, not in a status downgrade; `-24` is unbuilt
scope. Downgrading the feature instead would say the `.luacheckrc` story does not work, which the
probes show is untrue for most of it.

## Verification

| Requirement | Covered by |
| :--- | :--- |
| `-18`, argument assembly and de-duplication | `LuaCheckCommandLineTest` |
| `-15` (classification of a fatal exit) | `LuaCheckInvokerClassifyTest` — `TC11` asserts exit ≥ 2 → `Failure(CRASHED)` carrying the first stderr line, which is the path a broken `.luacheckrc` (exit 4) takes |
| `-15` (the banner as rendered) | `LuaCheckAnnotatorTest` — `TC5`, whole-file WARNING banner |
| `-23` | `LuacheckrcSchemaTest` |
| `.luacheckrc` being claimed as a Lua file at all (the premise of `-10` and `-23`) | `LuaFileTypeRegistrationIndexTest` |

**Nothing tests the config contract.** No test in `src/test/kotlin` places a `.luacheckrc` on disk,
and none asserts the working directory, the `--filename` value, or the effective `std` — the three
things `-01` through `-03` depend on and `-08` through `-11` break. `LuaCheckCommandLineTest`'s
`TC1` seeds `--std max` into the arguments field and asserts the parameter list *contains* it; with
the default Standard 5.4 target also emitting `--std lua54` after it, that assertion passes while
the user's value is dead (`-20`). It is a test that cannot fail on the thing that matters.

**`-08` through `-13`, `-16`, `-17`, `-20`, `-22` and `-24` were found by writing this table and are
recorded nowhere else in the repo** — `git grep` over `docs/` finds no mention of the `--std`
override, and neither `BUG-403` nor `BUG-405` (the two open luacheck rows in [[roadmap]]) touches it.
None has a bug report yet. `-08`/`-09`/`-11`/`-20` share a single cause and a single fix; `-12` and
`-13` share a second, one token long.
