---
id: ANALYSIS-01
title: "01: Luacheck Integration"
type: feature
status: "done"
vf_icon: ✅
priority: "medium"
parent_id: ANALYSIS
folders: ["[[features/analysis/requirements|requirements]]"]
---

# 01: Luacheck Integration

Run the external `luacheck` linter over the file the user is editing and put its findings in front of
them — inline in the editor, and in **Analyze | Inspect Code** — with the standard globals, the
project's `.luacheckrc` and the user's own arguments all in force.

## How these requirements were derived

**Not from Lunar's code.** A specification read off its own implementation cannot fail, which is the
defect that left [[DEBUG-07]] marked shipped for months (see [[BUG-450]] §4). The rows below come
from four external sources:

1. **Luacheck's own documented surface**, from the vendored copy at `test/luacheck` (version
   **1.2.0**, confirmed by `luacheck --version`). Its `docsrc/cli.rst` closes with a section titled
   *"Stable interface for editor plugins and tools"* — a seven-point contract Luacheck guarantees to
   integrations exactly like this one. That section, plus `docsrc/warnings.rst` (the 0xx–6xx code
   taxonomy), `docsrc/config.rst` (`.luacheckrc` discovery, per-path overrides, default per-path
   `std`) and `docsrc/inline.rst` (`-- luacheck: ignore`), is the backbone of the table.
2. **Executed probes against that vendored Luacheck** (2026-08-22, §Evidence below), because several
   claims here are about *behaviour*, and reading the source would have got two of them wrong.
3. **The IntelliJ platform contract for external linters** — `ExternalAnnotator`'s three-phase
   protocol and its threading rule (`collectInformation` in a read action, `doAnnotate` **outside**
   one, `apply` back in a read action), `ExternalAnnotatorBatchInspection` /
   `ExternalAnnotatorInspectionVisitor` for the batch path, and the platform's own two reference
   linter integrations, `ShShellcheckExternalAnnotator` and `Pep8ExternalAnnotator`, which
   demonstrate what a first-class integration does that a minimal one does not.
4. **[[plugin-feature-comparison]]** — the *Luacheck integration* row (✔ for Lunar,
   IntelliJ-EmmyLua and Luanalysis; ✗ for lua-for-idea and EmmyLua2). That row records only
   presence, so the comparative detail here comes from reading Luanalysis's
   `com.tang.intellij.lua.luacheck` package directly: theirs is an on-demand whole-project run into
   a dedicated tool window, not an on-the-fly annotator.

Where a capability is unimplemented, the row distinguishes **Lunar does not implement it** from
**the user cannot do it**: the platform supplies working defaults for several (`-14`, `-15`, `-19`),
and conflating the two would manufacture gaps that are not there.

> **Provenance note.** Lunar's `DEFAULT_ARGS = arrayOf("--codes", "--ranges")` and its output regex
> `(.+?):(\d+):(\d+)-(\d+):(.+)` are character-for-character the same as Luanalysis's
> `LuaCheckInvoker` (2017). The integration was seeded from a competitor's implementation rather
> than from Luacheck's editor-interface contract, which is the common cause of `-03`, `-04` and
> `-06`. Luanalysis never hit the colour problem because Luacheck's `color_support` is
> `not is_windows or ANSICON`, and that project is developed on Windows.

### Evidence (executed, 2026-08-22)

Run against the vendored Luacheck 1.2.0 with a stubbed `lfs`, replicating Lunar's exact command
line — working directory set to the file's own directory, content on stdin.

**Lunar's command line vs. the documented one.** Piped through `cat -v`, so `^[` below is a real
ESC byte in the output stream:

```text
$ luacheck --std lua54 --codes --ranges --filename target.lua -      # what Lunar sends
Checking target.lua                               ^[[0m^[[31m^[[1m1 warning^[[0m

    target.lua:1:7-12: (W211) unused variable ^[[0m^[[1mhelper^[[0m

Total: ^[[0m^[[31m^[[1m1^[[0m warning / ^[[0m^[[0m^[[1m0^[[0m errors in 1 file

$ luacheck --std lua54 --codes --ranges --formatter plain --filename target.lua -
target.lua:1:7-12: (W211) unused variable 'helper'
```

**`--std` wipes Luacheck's default per-path `std` overrides** (`docsrc/config.rst`: *"Setting `std`
on the commandline removes these default overrides"*). Lunar registers `.rockspec` and `.luacheckrc`
as Lua files, so the annotator runs on them:

```text
$ luacheck --std lua54 --codes --ranges --formatter plain --filename my.rockspec -   ; EXIT=1
my.rockspec:1:1-7: (W121) setting read-only global variable 'package'
my.rockspec:2:1-7: (W111) setting non-standard global variable 'version'
my.rockspec:3:1-6: (W111) setting non-standard global variable 'source'
my.rockspec:4:1-12: (W111) setting non-standard global variable 'dependencies'
my.rockspec:5:1-5: (W111) setting non-standard global variable 'build'

$ luacheck --codes --ranges --formatter plain --filename my.rockspec -               ; EXIT=0
```

The same happens to every `spec/**/*_spec.lua`: with `--std lua54`, `describe`, `it` and
`assert.is_true` are all reported undefined; without it, Luacheck's `+busted` default applies and
the file is clean.

**A syntax error is a normal reported issue, not a fatal one, and stdout is written before the
exit** (`src/luacheck/main.lua` writes `output`, then selects the exit code):

```text
probe.lua:2:10-10: (E011) expected '(' near 'f'       -> exit 2
probe.lua:1:1-34: (E021) unknown inline option '...'  -> exit 2
```

**Per-path `.luacheckrc` overrides do work with a bare basename `--filename`**, because Luacheck
resolves it against the working directory before matching. Verified non-vacuously — `files["sub"]`
suppressed W212, `files["other"]` did not:

```text
files["sub"]   = {ignore={"212"}}   ->  W211 only
files["other"] = {ignore={"212"}}   ->  W211 + W212
--no-config                         ->  W211 + W212
```

**Columns are codepoint columns, not bytes.** For `local s = "<rocket>" local unusedName = 1` with a
single supplementary-plane character, Luacheck reports column 21; the byte offset is 26 and the
UTF-16 offset is 22.

## Requirements & Status

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `ANALYSIS-01-01` | **Run from the checked file's directory** | **M** | **Full** | Stable-interface point 1. `newLuaCheckCommandLine` sets `withWorkDirectory(virtualFile.parent.path)`; this is also what makes `-11` work. |
| `ANALYSIS-01-02` | **Lint the editor buffer, not the file on disk** | **M** | **Full** | Stable-interface point 2. The document text is captured in `collectInformation` and piped to `-` with `--filename <name>`; a never-saved buffer is linted at its current content. |
| `ANALYSIS-01-03` | **Consume a machine-readable format** | **M** | **Not Implemented** | Stable-interface point 3 asks for `--formatter plain` — one issue per line, no header, no summary, colour forced off. Lunar passes no `--formatter`, so it parses the *human* formatter and depends on the regex failing to match the `Checking …` header and the `Total: …` footer. |
| `ANALYSIS-01-04` | **No colour in the parsed text** | **M** | **Partial** | `--no-color` is never passed, and Luacheck's `color` defaults to `true` with no TTY detection on Linux, so every message arrives colourised. `LuaCheckInvoker.ANSI_PATTERN = Regex("\\[[;\\d]*m")` strips the bracket sequence but **not the leading ESC byte**, leaving stray control characters inside the annotation text. `--formatter plain` removes the need for the regex entirely (it sets `opts.color = false`). |
| `ANALYSIS-01-05` | **Precise column ranges** | **M** | **Full** | Stable-interface point 4. `--ranges` is always sent; `line:startCol-endCol` is decoded 1-based-inclusive to 0-based-exclusive and clamped to the current document. |
| `ANALYSIS-01-06` | **Warning code available as data** | **M** | **Partial** | Stable-interface point 5. `--codes` is sent and the user *sees* the code, but only because it survives as a literal `(W211) ` prefix inside the message; `Problem` has no `code` field. Everything that needs the code as data — `-08`, `-09`, `-20`, `-21`, `-22` — is blocked on this row. |
| `ANALYSIS-01-07` | **Exit codes interpreted per the CLI contract** | **M** | **Partial** | `0` and `1` are correct. `LuaCheckInvoker.completedOutcome` treats **every** code `>= 2` as `CRASHED` and discards stdout — but `2` means *syntax errors or invalid inline options were reported*, and `main.lua` writes the full report before exiting. A file with a bad `-- luacheck:` directive loses its `E021` and gets a whole-file banner reading `luacheck exited with code 2` instead. `3` (unreadable file) and `4` (bad CLI/config) are the codes this branch is actually right for. |
| `ANALYSIS-01-08` | **Fatal per-file report recognised** | **S** | **Not Implemented** | The contract's tell for a fatal is *"lack of a `(code)` substring"*; the plain formatter emits `<file>: <type> (<msg>)`. Lunar's regex requires `:<line>:<col>-<col>:`, so such a line never matches and is dropped silently. |
| `ANALYSIS-01-09` | **Errors distinguished from warnings** | **M** | **Not Implemented** | `docsrc/warnings.rst`: codes starting with zero are errors and *"can not be ignored"*; the formatter prefixes them `E`, warnings `W`. `LuaCheckAnnotator.applyProblem` hardcodes `HighlightSeverity.WARNING` for all of them. `ShShellcheckExternalAnnotator.severity()` is the platform's own three-way `ERROR`/`WARNING`/`WEAK_WARNING` mapping; the 6xx formatting family is the natural `WEAK_WARNING` tier. |
| `ANALYSIS-01-10` | **Severity follows the inspection profile** | **S** | **Not Implemented** | `Pep8ExternalAnnotator` reads `profile.getErrorLevel(HighlightDisplayKey.find(shortName), file)` in `collectInformation` and applies `level.getSeverity()` in `apply`. Lunar consults the profile only for enablement, so re-rating **LuaCheck** to *Error* or *Weak Warning* changes batch results but leaves editor annotations at WARNING. The platform does **not** supply this by default — an annotator's severity is whatever it passes to `newAnnotation`. |
| `ANALYSIS-01-11` | **`.luacheckrc` discovery and per-path overrides** | **M** | **Full** | Luacheck walks up from the working directory to the filesystem root, and resolves `--filename` against that directory before matching `files[<glob>]`. Both halves verified by probe above; the bare-basename `--filename` is **not** the defect it looks like. This is the row that substantiates what `ANALYSIS-05` claims. |
| `ANALYSIS-01-12` | **Standard globals follow the project target** | **M** | **Partial** | `--std` is derived from `Target.getLuacheckStd()` and is non-null for every plain-Lua, LuaJIT, Redis and Valkey version, so it is sent by default. Two consequences: (a) it **wipes Luacheck's default per-path `std` overrides**, so every `.rockspec`, `.luacheckrc` and `spec/**/*_spec.lua` is annotated with false undefined-global warnings (probe above) — and Lunar deliberately registers the first two as Lua files; (b) Lua 5.5 maps to `lua54` because Luacheck 1.2.0 ships no `lua55` std (its set ends at `lua54c`), which is a forced approximation rather than a defect. NGX/Tarantool/Pandoc map to `null` — see [[BUG-405]]. |
| `ANALYSIS-01-13` | **User-supplied arguments are honoured** | **S** | **Full** | `LuaKindOptionKeys.LUACHECK_ARGUMENTS`, parsed with `ParametersListUtil` and merged ahead of the defaults; `dedupePairs` collapses a duplicated flag or flag/value pair so a user re-specifying `--std` or `--codes` does not double it. The settings surface itself is `ANALYSIS-02`. |
| `ANALYSIS-01-14` | **Whole-project / batch run** | **S** | **Full** | *Not* implemented by Lunar beyond one line: `getPairedBatchInspectionShortName()` returns `LuaCheckInspection.SHORT_NAME`, and `ExternalAnnotatorBatchInspection.checkFile` then drives `collectInformation`/`doAnnotate`/`apply` per file under **Analyze \| Inspect Code**. Recorded as Full because the requirement is about the user: this is the capability Luanalysis spends a bespoke tool window, view, node model and action set on. Note the batch path calls the **single-argument** `collectInformation(PsiFile)` overload — which Lunar does override, so the path is live rather than silently empty. |
| `ANALYSIS-01-15` | **The integration can be switched off** | **M** | **Full** | Also platform-supplied: an `ExternalAnnotator` whose paired short name matches a disabled inspection is skipped by `ExternalToolPass`. Registration is complete — `<externalAnnotator language="Lua">`, `<localInspection shortName="LuaCheck" groupPath="Lua" groupName="Luacheck" level="WARNING" unfair="true">`, and an `inspectionDescriptions/LuaCheck.html` that renders. |
| `ANALYSIS-01-16` | **A missing luacheck is silent** | **S** | **Full** | `LuaToolResolver.resolve(project, "luacheck")` returning null yields `LuaCheckOutcome.NotApplicable` and no annotation. Deliberate: a user with no linter installed is not nagged on every keystroke, and the toolchain health surface is where absence belongs. |
| `ANALYSIS-01-17` | **A failed run is visible, not silently green** | **S** | **Partial** | `LuaCheckOutcome.Failure` exists precisely so a launch failure or fatal exit cannot read as a clean pass, and it is logged. But it is rendered as a `WARNING` annotation over `TextRange(0, documentText.length)` — the whole file underlined, at the same severity as a real finding, with no distinguishing presentation. A tool-level failure is not a property of the code being edited. |
| `ANALYSIS-01-18` | **`doAnnotate` touches no PSI, VFS or index** | **M** | **Full** | The platform runs it outside a read action. `collectInformation` snapshots the text, line count and line start offsets; `doAnnotate` reaches only settings services (`LuaToolResolver`, `LuaToolchainProjectSettings`, `LuaProjectSettings`) and the process. Caveat: `Info` holds hard `Project` and `VirtualFile` references, against the engineering contract's memory rule — tolerable only because the platform discards it per pass. |
| `ANALYSIS-01-19` | **Not run during indexing, nor over already-broken files** | **S** | **Full** | Both are platform defaults Lunar correctly leaves alone: the annotator is not `DumbAware`, so it is skipped while indexing; and the un-overridden three-argument `collectInformation` returns null when `hasErrors`, so Luacheck does not pile onto a file whose own parse already failed. That is what keeps `-07`'s `E011` case mostly out of the editor — but *not* out of `-14`'s batch path, and not the `E021` case, where Lunar's parser sees nothing wrong. |
| `ANALYSIS-01-20` | **Suppress this code here** | **S** | **Not Implemented** | `docsrc/inline.rst` defines `-- luacheck: ignore <code>` and the `push`/`pop` pair, with line-scoped or closure-scoped effect. No quick fix offers to insert one. Both platform reference integrations ship the equivalent (`ShSuppressInspectionIntention`, Pep8's `IgnoreErrorFix`). Lunar already *reads* these comments — [[INSP-01]]-08 honours `-- luacheck: ignore` for its own undeclared-variable inspection — so only the write side is missing. |
| `ANALYSIS-01-21` | **Per-code exclusion from the inspection settings** | **C** | **Not Implemented** | Shellcheck stores `getDisabledInspections()` on its inspection and expands them into `--exclude=` per run; Pep8 does the same with `ignoredErrors` into `--ignore=`. Lunar's only equivalent is typing `--ignore 611` into the free-text argument box, which is project-wide, unvalidated and invisible from the inspection UI. |
| `ANALYSIS-01-22` | **Message links to the warning's documentation** | **C** | **Not Implemented** | Shellcheck builds an HTML tooltip linking `SC<code>` to its wiki page. Luacheck's equivalent is `warnings.rst`, published at `luacheck.readthedocs.io`, whose sections are per *family* (1xx…6xx) rather than per code — so the achievable version is a family-level anchor, not a per-code page. |
| `ANALYSIS-01-23` | **Duplicate findings suppressed** | **S** | **Partial** | Implemented as [[BUG-132]] specified it — `distinctBy { it.lineStart to it.message }`. The key is coarser than the data: two genuine occurrences of the same code on one line at different columns (`t.a = 1; t.b = 2` against an undefined `t`) collapse to one annotation, and the survivor's range decides which one the user sees. `Problem` already carries a column-aware `equals`/`hashCode`, so a plain `distinct()` was available; nothing records why the coarser key was chosen. |
| `ANALYSIS-01-24` | **Ranges align with editor offsets** | **S** | **Partial** | Luacheck reports **codepoint** columns (`decoder.lua` plus `CheckState:offset_to_column`), not bytes — so the common non-ASCII case is already correct, contrary to what its byte-oriented lexer suggests. Documents are indexed in UTF-16 code units, so only supplementary-plane characters diverge: each one earlier on the line shifts the highlight one unit left. Probe-confirmed on the Luacheck side; **the resulting misplacement in the editor has not been observed live**. |
| `ANALYSIS-01-25` | **Editing `.luacheckrc` re-analyses open files** | **S** | **Not Implemented** | `LuaSettingsChangeListener` restarts `DaemonCodeAnalyzer` when the *target* changes, so `-12` takes effect immediately. Nothing watches `.luacheckrc`, and it is not an input the daemon knows about, so already-open Lua files keep stale annotations until touched. **Confirming the user-visible half requires running the IDE**; the absence of any listener is certain, the exact refresh behaviour is not. |
| `ANALYSIS-01-26` | **"Previously defined here" secondary location** | **C** | **Won't** | The report format carries `prev_line`/`prev_column`/`prev_end_column` for the whole 4xx shadowing family and for `011`. They exist only in the **module** API — no CLI formatter prints them, and this integration is a CLI consumer. Reaching them would mean embedding a Lua interpreter, or writing a custom formatter module and shipping it alongside the user's own Luacheck install. |
| `ANALYSIS-01-27` | **Directories, rockspec targets, `--cache`, `-j`** | **W** | **Won't** | Luacheck's project-scale features — recursive directory walking, expanding a `.rockspec`'s file list, `.luacheckcache`, parallel jobs via LuaLanes. All presuppose checking files on disk; this integration exists to check the *buffer*, one file per pass, and the IDE already owns scheduling and incrementality. |
| `ANALYSIS-01-28` | **Auto-fix a finding** | **W** | **Won't** | Shellcheck emits machine-applicable `fix.replacements` and its annotator turns them into a quick fix. Luacheck emits no such thing in any formatter, so the only fixes available here are the suppression ones in `-20` and `-21`. |

## Verification

`LuaCheckCommandLineTest` covers `-13` (TC1 argument merge, both `dedupePairs` cases) and the
no-tool case behind `-16` (TC2). `LuaCheckInspectionGroupingTest` covers `-14` and `-15` end to end —
EP registration, group path, paired short name, and that `LuaCheck.html` loads.
`LuaCheckAnnotatorTest` covers the range math in `-05` (TC3 exact offsets, TC4 clamping against a
stale line number), the de-duplication in `-23`, and the launch-failure banner in `-17` (TC5, driven
through the real `capture` path at a non-existent binary). `LuaCheckInvokerClassifyTest` covers
`-17`'s classification (TC10) and the exit-1 parse (TC12).

**Two of these tests assert a shape production never takes**, which is why the suite cannot see
`-03`, `-04` or `-07`:

- `LuaCheckCommandLineTest` TC1 calls `newLuaCheckCommandLine(project, "a.lua", workDir)` — the
  default `useStdin = false`. It asserts `params.last() == "a.lua"`, the **path** form. The stdin
  form the annotator actually uses (`--filename a.lua -`) is untested, and so is the working
  directory that `-01` and `-11` both depend on.
- `LuaCheckInvokerClassifyTest` TC12 feeds `f.lua:1:7-7: (W211) unused variable 'x'` — that is
  **plain-formatter** output, hand-written. Production parses the default formatter's colourised,
  indented output. A test built from the format the plugin never requests cannot fail on the format
  it does.
- TC11 pins `exit 2 -> CRASHED` as the intended behaviour, so `-07` is not an oversight the suite
  would catch; it is a decision the suite protects.

Nothing covers `-02`, `-06`, `-09`, `-10`, `-11`, `-12`, `-20`, `-21`, `-22`, `-24` or `-25`. The
Luacheck-side behaviour of `-11`, `-12` and `-24` is settled by the probes in §Evidence; their
Lunar-side effect, and all of `-25`, need the IDE.

**`-03`, `-04`, `-07`, `-09`, `-10`, `-12`, `-23`, `-24` and `-25` were found by writing this
table** and are recorded nowhere else in the repo. None has a bug report yet. The two pre-existing
Luacheck items on the roadmap, [[BUG-403]] (undeclared dependency on the `glimmer/luacheck` fork)
and [[BUG-405]] (no `ngx_lua` std for the OpenResty target), are distinct from all of them; `-12`
is the same *area* as BUG-405 but the opposite failure — BUG-405 is a target that emits no `--std`,
`-12` is the damage done by emitting one.
