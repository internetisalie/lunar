---
id: ANALYSIS-04
title: "04: Luacheck Output Parsing"
type: feature
status: "done"
vf_icon: ✅
priority: "medium"
parent_id: ANALYSIS
folders: ["[[features/analysis/requirements|requirements]]"]
---

# 04: Luacheck Output Parsing

Turn the bytes luacheck writes on stdout — and the exit code it leaves behind — into IDE
diagnostics with the right text, the right range and the right severity.

## How these requirements were derived

**Not from Lunar's parser.** A specification read off its own regex cannot fail, which is the defect
that left [[DEBUG-07]] marked shipped for months (see [[BUG-450]] §4). Luacheck publishes its own
contract, so the rows below come from it and from running it:

1. **Luacheck's documented stable interface for tools** — `test/luacheck/docsrc/cli.rst`,
   *"Stable interface for editor plugins and tools"*, guaranteed since 0.11.0. Six bullets: run from
   the file's directory; feed stdin as `-` with `--filename`; use the **plain** formatter; `--ranges`
   gives `:<line>:<start_column>-<end_column>:`; `--codes` gives a parenthesised `E`/`W` plus three
   digits, and *"lack of such substring indicates a fatal error"*; the rest of the line is the
   message. Each bullet is a requirement.
2. **The formatter source** — `test/luacheck/src/luacheck/format.lua`. It defines the location
   format, the 4-space indent and `Checking …` / `Total: …` framing of the *default* formatter, the
   fatal-report line shape, and the ANSI colour encoding (`ESC [ <n> m`, emitted whenever
   `not is_windows` and `NO_COLOR` is unset — **it never tests for a TTY**).
3. **The code taxonomy** — `test/luacheck/docsrc/warnings.rst` plus the `message_format` tables in
   `src/luacheck/stages/*.lua`. Codes beginning `0` are errors, the rest warnings
   (`format.lua:event_code`), which is the only available basis for an IDE severity. The message
   formats also settle format-forced questions: `W582`'s text contains a colon; `W631`'s contains
   parentheses and `>`; `{name!}` renders as `'name'` without colour and as escape-wrapped text with
   it.
4. **The exit-code table** — `src/luacheck/main.lua:11-17`:
   `ok=0, warnings=1, errors=2, fatals=3, critical=4`. **`2` means luacheck found errors — a lint
   result, not a malfunction.** Only `3` (I/O) and `4` (bad invocation) are not lint results.
5. **Executed probes, 2026-08-22**, driving the vendored luacheck 1.3.1 library
   (`test/luacheck`, `glimmer/luacheck` `redis` branch — it does not patch `format.lua` or
   `main.lua`, so the format is upstream's) through `format.format`, and replaying the resulting
   bytes through `LuaCheckInvoker`'s two regexes under `java`. Every status below that says
   *Partial* or *Not Implemented* was observed, not inferred.

Where behaviour is defensible-but-different from the documented interface the row says so rather
than manufacturing a defect: Lunar reads the *default* formatter's indented lines successfully, but
it does so by accident of `Regex.find`, not by asking for the format the contract names.

### The output Lunar actually receives

`LuaCheckCommandLine.DEFAULT_ARGS` is `--codes --ranges`; there is no `--formatter plain` and no
`--no-color`. Probe output for a three-line file, escape bytes shown as `<ESC>`:

```
Checking bad.lua                                  <ESC>[0m<ESC>[31m<ESC>[1m4 warnings<ESC>[0m

    bad.lua:1:7-12: (W211) unused variable <ESC>[0m<ESC>[1mhelper<ESC>[0m
    bad.lua:2:1-15: (W113) accessing undefined variable <ESC>[0m<ESC>[1mundefinedGlobal<ESC>[0m
    bad.lua:3:4-13: (W582) Error prone negation: negation is executed before relational operator.
    bad.lua:3:8-8: (W113) accessing undefined variable <ESC>[0m<ESC>[1ma<ESC>[0m

Total: <ESC>[0m<ESC>[31m<ESC>[1m4<ESC>[0m warnings / <ESC>[0m<ESC>[0m<ESC>[1m0<ESC>[0m errors in 1 file
```

The documented interface (`--formatter plain --no-color --codes --ranges`) would instead give:

```
bad.lua:1:7-12: (W211) unused variable 'helper'
bad.lua:2:1-15: (W113) accessing undefined variable 'undefinedGlobal'
bad.lua:3:4-13: (W582) Error prone negation: negation is executed before relational operator.
bad.lua:3:8-8: (W113) accessing undefined variable 'a'
```

## Requirements & Status

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `ANALYSIS-04-01` | **Location prefix parsed** | **M** | **Full** | `LINE_PATTERN` reads `<file>:<line>:<colStart>-<colEnd>:` — the shape `--ranges` guarantees. Replayed against real formatter bytes: `bad.lua:1:7-12:` yields line 1, columns 7–12. |
| `ANALYSIS-04-02` | **1-based converted to 0-based** | **M** | **Full** | Line and both columns are decremented once. Luacheck's own words: *"Numbering starts from 1."* |
| `ANALYSIS-04-03` | **Non-diagnostic lines ignored** | **M** | **Full** | The `Checking … 4 warnings` header, the blank separators and `Total: 4 warnings / 0 errors in 1 file` all fail `find()` — verified by replay, not assumed. `Total:` survives only because a space, not a digit, follows its colon. |
| `ANALYSIS-04-04` | **Default formatter's indent tolerated** | **M** | **Full** | The four leading spaces are absorbed by the lazy `(.+?)` file group, which is then discarded in favour of the editor's own file name. |
| `ANALYSIS-04-05` | **A message containing a colon is not truncated** | **M** | **Full** | Group 5 is greedy `(.+)`. `W582` — *"Error prone negation: negation is executed before relational operator."* — is a real luacheck message with an embedded colon and survives intact. |
| `ANALYSIS-04-06` | **Messages are single-line** | **M** | **Full** | Not a parser feature — a guarantee. `lexer.lua:710` quotes offending tokens through `get_printable_substring`, which hex-escapes everything outside `\32-\126`, so a newline inside a long string reaches the message as `\x0A`. Confirmed by probing a syntax error inside a multi-line long string: one output line. No continuation-joining logic is needed. |
| `ANALYSIS-04-07` | **stdin is fed, with the real name** | **M** | **Full** | `--filename <name> -`, matching bullets 2 and 4 of the stable interface, so the unsaved buffer is checked and the reported name is the editor's, not luacheck's `"stdin"` fallback (`main.lua:324`). Stdin is written as UTF-8, which is what luacheck's `decoder` expects. |
| `ANALYSIS-04-08` | **Run from the file's directory** | **M** | **Full** | `workDir = virtualFile.parent`, satisfying bullet 1. |
| `ANALYSIS-04-09` | **A clean file yields no diagnostics** | **M** | **Full** | Probed: a zero-warning file produces only `Checking good.lua … OK` and a `Total:` line, neither of which matches, and exit 0. Result is `Problems(emptyList())` — not a failure banner. |
| `ANALYSIS-04-10` | **ANSI colour removed from the message** | **M** | **Partial** | `ANSI_PATTERN = Regex("\\[[;\\d]*m")` **omits the ESC byte**, so it strips `[0m` and leaves the bare escape behind. Replay of the real bytes: `unused variable <ESC>[0m<ESC>[1mhelper<ESC>[0m` becomes `unused variable <ESC><ESC>helper<ESC>` — three stray control characters in the tooltip. Every `{name!}` message is affected, i.e. most of the 1xx/2xx/3xx families. The pattern also over-matches: a message quoting Lua source such as `near 'x[1m]'` loses `[1m`, verified by replay. |
| `ANALYSIS-04-11` | **Colour is switched off at the source** | **M** | **Not Implemented** | Neither `--no-color` (`main.lua:256`) nor `NO_COLOR` (`format.lua`, upstream #102) is used. `format.lua` decides colour from `not utils.is_windows` alone — **it never checks whether stdout is a terminal** — so on Linux and macOS every run emits escapes into Lunar's pipe. Windows is accidentally clean (`color_support` is false without `ANSICON`), which means `-10` reproduces per-platform. Setting either flag would delete `-10`'s regex problem outright rather than repairing it. *Escapes were observed in formatter output; they have not been observed arriving through `CapturingProcessHandler` in a running IDE.* |
| `ANALYSIS-04-12` | **The plain formatter is requested** | **M** | **Not Implemented** | Bullet 3 of the stable interface: *"Plain formatter should be used. It outputs one issue per line."* `DEFAULT_ARGS` omits `--formatter plain`, so Lunar parses the default formatter's decorated report and depends on the header and summary lines never resembling a location. That holds today by inspection, and is a coincidence rather than a contract. |
| `ANALYSIS-04-13` | **The warning code is extracted** | **M** | **Not Implemented** | `--codes` is passed, so every line carries `(W211)` / `(E011)`, but nothing parses it. `Problem` (`LuaCheckModel.kt`) has no `code` field; the parenthesised code is left inside the display text and the message keeps the leading space that follows the location colon. Without a code there is no per-code suppression, no code-specific quick fix and no basis for `-14`. |
| `ANALYSIS-04-14` | **Errors are more severe than warnings** | **M** | **Not Implemented** | `format.lua:event_code` prefixes `E` when the code starts with `0`; `warnings.rst` lists `011` (syntax error), `021`–`023` (bad inline options) and `033` in that class. `LuaCheckAnnotator.applyProblem` hard-codes `HighlightSeverity.WARNING` for everything, so `E011 expected expression near <eof>` renders identically to `W612 line contains trailing whitespace`. |
| `ANALYSIS-04-15` | **A run that found errors is not a crash** | **M** | **Not Implemented** | `main.lua:11-17` defines exit 2 as `errors` — *luacheck found error-class findings*. `LuaCheckInvoker.FATAL_EXIT_CODE = 2` treats it as `FailureKind.CRASHED`: every parsed problem in stdout is **discarded** and a file-wide banner reading `luacheck exited with code 2` replaces them, because a lint run writes nothing to stderr. Probed input `local x =` yields `syn.lua:2:1-1: (E011) expected expression near <eof>` on stdout and exit 2 — so any file that is momentarily unparseable while being typed loses all luacheck output and gains a false crash report. The threshold should be 3. |
| `ANALYSIS-04-16` | **A genuine failure is never a clean pass** | **M** | **Full** | Exit 3 (`fatals`, I/O) and 4 (`critical`, bad arguments or an unloadable formatter) both exceed the threshold and become `Failure(CRASHED, …)`, so a broken run cannot read as green. This is the property [[MAINT-26]] set out to obtain; `-15` is the collateral of obtaining it with the wrong constant. |
| `ANALYSIS-04-17` | **Exit 0 and 1 parse normally** | **M** | **Full** | Below the threshold, so both go to `parseProblems`. |
| `ANALYSIS-04-18` | **The fatal report's text is preserved** | **S** | **Not Implemented** | Luacheck writes the whole report — fatals included — to **stdout** (`main.lua:336`); only `critical()` uses stderr. The fatal line is `    <file>: <msg>` (default) or `<file>: I/O error (<msg>)` (plain); neither matches `LINE_PATTERN`, and `completedOutcome` builds its detail from **stderr**, which is empty. The user is told `luacheck exited with code 3` instead of `No such file or directory`. |
| `ANALYSIS-04-19` | **Absence of a code marks a fatal** | **S** | **Not Implemented** | Bullet 5: *"Lack of such substring indicates a fatal error (e.g. I/O error)."* With `--codes` in force this is a free, formatter-independent fatal detector; nothing uses it. |
| `ANALYSIS-04-20` | **Two findings differing only in column both survive** | **M** | **Partial** | Parsing keeps both; `LuaCheckAnnotator.apply` then applies `distinctBy { it.lineStart to it.message }` and drops one. This is reachable in ordinary code — probed `if not aa == 3 then print(aa) end` emits `d.lua:1:8-9` and `d.lua:1:27-28`, both *"accessing undefined variable 'aa'"*, and only the first is annotated. A key including the columns would fix it; de-duplication is needed only because Lunar re-runs `collectInformation` inside `apply`. |
| `ANALYSIS-04-21` | **Columns land on the right characters** | **C** | **Partial** | Luacheck counts **Unicode codepoints** (`decoder.lua`'s `UnicodeChars`), IntelliJ documents count UTF-16 units. Measured: with three 2-byte characters ahead of it a variable is reported at column 23, exactly as with three ASCII characters — so bytes are not the unit; with one astral character (U+1F600) ahead of it, column 21 where the document offset is 22. Ranges therefore shift one unit left per preceding astral character. BMP text of any script is unaffected. |
| `ANALYSIS-04-22` | **A filename containing a colon is handled** | **S** | **Partial** | The lazy file group plus the digit anchors recover for ordinary awkward names — replay of `weird:12:34-5.lua:7:1-3: …` correctly yields line 7. It fails only when the name *itself* contains a complete location, e.g. `q:1:2-3:z.lua`, which is parsed as line 1 with the remainder as the message. Bounded damage: the file group is discarded anyway, so only line, columns and message are wrong. |
| `ANALYSIS-04-23` | **A CRLF-terminated line parses** | **C** | **Full** | Replayed: a trailing `\r` is absorbed by the greedy message group. It ends up inside the message text, which the platform renders harmlessly. |
| `ANALYSIS-04-24` | **User arguments cannot silently blind the parser** | **S** | **Not Implemented** | `resolveArguments` puts `LuaKindOptionKeys.LUACHECK_ARGUMENTS` **before** `DEFAULT_ARGS`, and `--formatter` is not in `DEFAULT_ARGS`, so `--formatter JUnit` (XML) or `-qqq` produces output that matches nothing. The result is `Problems(emptyList())` with exit 1 — indistinguishable from a clean file. Nothing validates the arguments and nothing reports "output produced, none of it understood". |
| `ANALYSIS-04-25` | **Alternative formatters supported** | **C** | **Won't** | `TAP`, `JUnit` and `visual_studio` exist, but the stable interface names `plain` and every other consumer of a luacheck formatter is a CI reporter, not an editor. Supporting them would add parsers with no user. |
| `ANALYSIS-04-26` | **Old luacheck (&lt;0.11) tolerated** | **W** | **Won't** | The stable interface begins at 0.11.0 and [[BUG-403]] already records that Lunar depends on a specific fork. Version-sniffing `luacheck --help` for a pre-2015 layout is cost without a beneficiary. |

## Verification

Three unit classes touch this feature today, all under
`src/test/kotlin/net/internetisalie/lunar/analysis/luacheck/`:

- **`LuaCheckInvokerClassifyTest`** — `TC10` (launch failure), `TC11` (exit 2), `TC12` (exit 1 with
  two problem lines and a summary line). Covers `-01`…`-03`, `-16`, `-17`.
- **`LuaCheckAnnotatorTest`** — `TC3` (range on an unsaved buffer), `TC4` (clamping past the end of
  the document), same-line/same-message de-duplication, `TC5` (missing binary banner). Covers the
  offset math behind `-21` and pins the current behaviour of `-20`.
- **`LuaCheckCommandLineTest`** — `TC1`/`TC2` and the `dedupePairs` cases. Confirms `--codes` and
  `--ranges` are present, and by omission confirms `-11` and `-12`.

**Two limits of that suite, both load-bearing:**

1. **Every fixture is synthetic and written in the plain, uncoloured format Lunar does not ask
   for.** `TC12` feeds `f.lua:1:7-7: (W211) unused variable 'x'`; `TC3` feeds
   `test.lua:1:7-7: (W211) unused variable 'x'`. Neither carries an ANSI escape or a leading indent,
   so no test can observe `-10`. A fixture captured from a real run would fail today.
2. **`TC11` asserts the defect in `-15` as intended behaviour** — it fixes exit 2 to `CRASHED`.
   Fixing `-15` requires changing that test, which is the tell that the constant was chosen from
   [[MAINT-26]] §2.5.6's framing ("exit ≥ 2 read as clean") rather than from luacheck's exit-code
   table.

No test exercises a real luacheck binary; `src/test/resources/corpus/luacheck.baseline` is a
corpus-parse baseline for Lunar's own parser and is unrelated to this feature.

**Found by writing this table and recorded nowhere else in the repo:** `-10` (ANSI pattern missing
the ESC byte, while [[MAINT-26]]-02 claims "ANSI-strip" is Full), `-11`, `-12`, `-13`, `-14`, `-15`
(exit 2 is a lint result), `-18`, `-19`, `-20`, `-21`, `-22` and `-24`. None has a bug report.
`-15` and `-10` are the two worth filing first: the first silently destroys all output for any file
with a syntax error, the second corrupts the text of most warnings on every non-Windows host.

**The `done` status is optimistic.** Five `Must` rows (`-11`, `-12`, `-13`, `-14`, `-15`) are unmet
and two more (`-10`, `-20`) are partial. The feature ships and works on the common path, so the
status is left alone here rather than changed unilaterally — but it should be revisited once the
bug reports exist.
