---
id: "RUN-08"
title: "RUN-08: Lua Test Runner Integration"
type: "feature"
status: "in_progress"
priority: "medium"
parent_id: "DEBUG/RUN"
folders:
  - "[[features/debug/requirements|requirements]]"
---

# RUN-08: Lua Test Runner Integration

## Overview
Lunar provides a unified, user-facing test runner that integrates with the standard IntelliJ Platform Test Runner (SMTestRunner) window. This feature allows developers to run and debug Lua unit tests (specifically Busted and Lunity), view real-time test execution trees, examine failure diffs, rerun failed tests, collect and view code coverage (via Luacov), and navigate directly from test results to the test code.

## Scope

### In Scope
- **Run Configuration**: A dedicated "Lua Tests" Run Configuration with options for Busted and Lunity test frameworks.
- **Framework Support**:
  - **Busted**: Standard Lua testing framework running in local interpreter or LuaRocks environment.
  - **Lunity**: A lightweight testing framework, specifically supporting custom builds that emit JSON test execution events.
- **SMTestRunner Integration**: Streaming output parser that translates test runner output into IntelliJ test events (test started, test finished, test failed, test ignored, test suite started/finished, duration).
- **Code Coverage Integration**: Run tests with coverage collection using `luacov`, parse execution count files, and highlight covered/uncovered lines in the editor gutter.
- **Navigation to Source**: Direct navigation from a test node in the Test Results tree to the corresponding file and line of the test definition.
- **Run/Debug from Gutter/Context Menu**: Gutter icons and right-click actions (via `RunConfigurationProducer`) to run or debug a directory, file, or individual test case/describe block.
- **Failed Test Rerunning**: Capability to rerun only the subset of tests that failed in the previous execution run.

### Out of Scope
- Support for other test frameworks (like LuaUnit or Telescope) in this phase.
- Automatic installation of test frameworks (user must have Busted installed or Lunity embedded in their project).
- Running tests on remote devices/microcontrollers (deferred to target configuration / remote debug).

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| RUN-08-01 | **Test Run Configuration** | M | Full | Provide a new Run Configuration Type "Lua Tests" with fields for Test Framework (Busted, Lunity), Test Target (Directory, File, or Test Name Pattern), SDK/Interpreter, Working Directory, Env Variables, and Extra Arguments. |
| RUN-08-02 | **Process Execution** | M | Full | Execute the test process using the selected Lua SDK interpreter or local `busted` binary with appropriate arguments. |
| RUN-08-03 | **SMTestRunner Event Translation** | M | Full | Parse stdout/stderr stream from the test execution process in real-time and translate it into IntelliJ IDE test runner events. |
| RUN-08-04 | **Lunity JSON Protocol Support** | M | Full | Specifically support parsing JSON-line output from a custom-built Lunity framework run, mapping JSON fields to suite/test life cycle and results. |
| RUN-08-05 | **Busted Protocol Support** | M | Full | Support parsing Busted test output. The integration should preferably use Busted's built-in JSON output formatter or TeamCity service message formatter (if available) to ensure reliable parse results. |
| RUN-08-06 | **Test Tree Visualization** | M | Full | Display nested test suites (e.g., `describe`/`context` blocks in Busted, or test groups in Lunity) as a hierarchical tree inside the Test Results window. |
| RUN-08-07 | **Navigation to Code** | S | Full | Resolve test names/locations to direct source code lines. Clicking on a test node should jump to the `it(...)` or test function declaration in the editor. |
| RUN-08-08 | **Gutter Run/Debug Producers** | S | Full | Register a `RunConfigurationProducer` that detects tests in files and adds run/debug gutter icons next to Busted `describe`/`it` calls and Lunity test functions. |
| RUN-08-09 | **Rerun Failed Tests** | S | Full | Implement `AbstractRerunFailedTestsAction` to extract failed test names and execute only those failed tests. |
| RUN-08-10 | **Failure Diffs** | C | Full | For assertions comparing expected vs. actual values, parse the assertion message to display IntelliJ's comparative diff viewer dialog. |
| RUN-08-11 | **Coverage Execution** | S | Partial | Provide a "Run with Coverage" runner that automatically executes tests with the `luacov` module loaded (e.g., via `-lluacov` argument or loading it in a custom runner helper). |
| RUN-08-12 | **Coverage Stats/Report Parsing** | S | Full | Parse the `luacov.stats.out` and/or `luacov.report.out` files produced by the test run to extract line execution hit counts for each Lua source file. |
| RUN-08-13 | **Standard Coverage UI Rendering** | S | Partial | Integrate with the IntelliJ Coverage engine API to display file coverage statistics (percentages) in the project tree and colored gutter indicators (green/red line overlays) in the editor. |
| RUN-08-14 | **Luacov Tool Discovery** | S | Full | Register `luacov` command in the Tool Inventory Management (`TOOL`) registry, allowing auto-discovery in local/system paths and custom overrides. |
| RUN-08-15 | **LuaRocks Coverage Installation** | S | Partial | Surface a balloon notification with a quick link to execute `luarocks install luacov` if `luacov` cannot be located when starting a Coverage run. |
| RUN-08-16 | **Import Luacov Report File** | S | Full | Provide a user action to import/select an existing `luacov.report.out` file, parsing its contents to render coverage highlights directly onto the project's source files in the editor. |
| RUN-08-17 | **Report Editor Syntax Highlighting** | C | Partial | Provide custom syntax highlighting for opened `luacov.report.out` files, showing execution count prefixes in distinct colors (red/green) and syntax-highlighting the code portion as Lua. |
| RUN-08-18 | **Coverage Editor Banner Action** | C | Partial | Display an action banner at the top of the editor when opening `luacov.report.out` files, offering a one-click action to load/import the coverage overlays onto project source files. |

## Detailed Specifications

### RUN-08-04: Lunity JSON Protocol Support
Lunity custom builds emit structured JSON lines to standard output during execution. The parser must process these events line-by-line as they stream in.

#### JSON Event Format
Each line is a self-contained JSON object containing:
- `event`: String indicating event type (`suite_start`, `suite_end`, `test_start`, `test_end`, `test_fail`, `test_error`, `test_pass`, `test_ignore`).
- `name`: Name of the test or suite.
- `suite`: (Optional) Name of the parent suite.
- `duration`: (Optional) Duration of the test in milliseconds.
- `message`: (Optional) Error/failure message.
- `trace`: (Optional) Stack trace for failures/errors.
- `file`: (Optional) Path to the test file.
- `line`: (Optional) Line number of the test.

#### Event Mapping to SMTestRunner
- `suite_start` -> `testSuiteStarted(name)`
- `suite_end` -> `testSuiteFinished(name)`
- `test_start` -> `testStarted(name)`
- `test_pass` -> `testFinished(name, duration)`
- `test_fail`/`test_error` -> `testFailed(name, message, trace, duration)`
- `test_ignore` -> `testIgnored(name)`

### RUN-08-05: Busted Protocol Support
For Busted, the plugin will run Busted with `--output=json` or a custom JSON/TeamCity output handler bundled with the plugin resources.
- **Busted JSON output structure**: Normally Busted writes a final JSON summary. However, to support real-time execution trees, a custom reporter/formatter written in Lua will be injected via Busted's command-line arguments (e.g., `--helper` or custom `--output`) that outputs TeamCity service messages or JSON events on the fly.

### RUN-08-07: Navigation to Code
To support navigation, the event translation must associate each test with a Location URL.
- **URL Scheme**: `lua://<absolute_file_path>:<line_number>` or `file://<absolute_file_path>:<line_number>`.
- A custom `SMTestLocator` or `LocationProvider` will parse this URL and return a `PsiLocation` pointing to the exact line inside the `PsiFile`.
- Gutter/Context producers will use the AST parser to scan the current file:
  - For Busted: Look for `describe(...)` and `it(...)` function calls.
  - For Lunity: Look for test function definitions (e.g., functions starting with `test_` or declared inside a Lunity test suite table).

### RUN-08-12: Coverage Collection & Parsing (Luacov)
To collect coverage, the runner will utilize the standard Lua coverage library, `luacov`.
1. **Runner Configuration**: When executing via the "Run with Coverage" action (implementing `CoverageEngine` and `CoverageRunner`), the plugin will automatically append `-lluacov` to the interpreter arguments (or configure `luacov` in the environment).
2. **Coverage Report File**: Upon successful completion of the test process, `luacov` generates a statistics file (typically `luacov.stats.out`).
3. **Report Parsing**:
   - The plugin will parse `luacov.stats.out` or run `luacov` to generate and parse `luacov.report.out` line-by-line.
   - The stats file contains keys representing the file path (e.g. `["/path/to/file.lua"]`) mapping to line execution counts.
   - The parsed results are passed to JetBrains' `CoverageSuite` database, highlighting lines in the editor:
     - **Green Overlay**: Lines with execution counts > 0.
     - **Red Overlay**: Executable code lines with execution count = 0.
     - **No Overlay**: Non-executable lines (comments, whitespace, function declaration headers, etc.).

### RUN-08-14: Luacov Tool Discovery
- The `luacov` command (the generator script) will be registered inside the Tool Inventory Management (`TOOL`) registry as `luacov`.
- Auto-discovery will scan standard paths (e.g., `/usr/bin/luacov`, `/usr/local/bin/luacov`, and the active LuaRocks bin folders such as `.luarocks/bin/luacov` or `lua_modules/bin/luacov`).
- If a custom `luacov` command path is specified by the user in `LuaToolInventoryPanel`, that path will take precedence.

### RUN-08-15: LuaRocks Coverage Installation
- When the user starts a test configuration with the "Run with Coverage" action, the runner checks if the `luacov` module is available in the selected interpreter SDK (by executing a quick check, e.g., `lua -e "require('luacov')"` or using `LuaToolManager` to locate it).
- If the tool/library is missing, the execution fails, and a balloon notification is displayed:
  `Code coverage library 'luacov' is not installed in the current SDK. [Install luacov via LuaRocks]`
- Clicking the link triggers a background task running `luarocks install luacov` using the environment configuration of the project's LuaRocks manager. Upon successful installation, the coverage execution automatically retries.

### RUN-08-16: Import Luacov Report File
- **User Action**: The user can invoke an action (e.g., `Analyze -> Show Lua Coverage Data...`) and select a `luacov.report.out` file from the filesystem.
- **Parsing Algorithm**:
  - The parser scans the file line-by-line using a state machine:
    - **State 1 (Search Header)**: Look for a boundary line consisting of a sequence of equals signs (e.g. `===+`). When found, read the next line as the target file path, and verify the subsequent line is another boundary line. Move to State 2.
    - **State 2 (Parse Lines)**: Read lines and match their prefixes:
      - If it matches `^\*\*\*0\s(.*)` (uncovered): Mark the line as executable with **0 hits** (uncovered).
      - If it matches `^\s*([1-9][0-9]*)\s(.*)` (covered): Mark the line as executable with **hit count = $1** (covered).
      - If it matches `^\s{5}(.*)` or other spacing: Skip (non-executable).
      - If it matches a new boundary line `===+`: transition back to State 1 to parse the next file.
  - **Path Resolution**: Resolve relative paths in the report (e.g., `initrd/usr/bin/tests.lua` or `src/foo.lua`) relative to the project root directory.
  - **IDE Overlay**: Construct a `CoverageSuite` and populate its data to trigger the standard IDE coverage overlays.

### RUN-08-17: Report Editor Syntax Highlighting
- When a `luacov.report.out` file is opened, a custom syntax highlighter will style the file content:
  - **Header boundaries (`===+`) and File Paths**: Render in a distinct, bold section-header style.
  - **Execution prefixes**:
    - Style `***0` with a **bright/soft red foreground or background** to draw immediate attention.
    - Style non-zero hit counts (e.g., `   1`, `  10`) with a **soft green foreground** or neutral gray.
  - **Lua Code Segment**: Strip the prefix characters (first 5 characters for standard execution line) and highlight the rest of the line as standard Lua code reusing the `LuaSyntaxHighlighter`.

### RUN-08-18: Coverage Editor Banner Action
- When the IDE detects a `luacov.report.out` file is opened in the editor, it registers a `FileEditorAssociation` and shows a notification banner at the top of the editor:
  `"This is a LuaCov coverage report. [Load Coverage onto Project Files] [Dismiss]"`
- Clicking `[Load Coverage onto Project Files]` automatically triggers the `RUN-08-16` parser to load the coverage data on actual project files.

## Behavior Rules
1. **Asynchronous Execution**: The test process must run completely on a background thread. Standard progress bars should indicate run state, and the EDT must never be blocked.
2. **Graceful Terminations**: If the user cancels the test run, the background process must be terminated gracefully (e.g., sending SIGINT/SIGTERM) and the test tree updated to reflect terminated status.
3. **Fallback to Console Output**: If output parsing fails or emits unexpected syntax, the raw output must still be dumped to the Run console tab to avoid swallow-masking failures.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | RUN-08-04 | Lunity output line: `{"event": "test_start", "name": "test_addition"}` | Parser processes the line | Emits `testStarted("test_addition")` to the test events processor. |
| 2 | RUN-08-04 | Lunity output line: `{"event": "test_fail", "name": "test_addition", "message": "expected 4 but got 5", "duration": 12}` | Parser processes the line | Emits `testFailed("test_addition", "expected 4 but got 5", ...)` and `testFinished("test_addition", 12)`. |
| 3 | RUN-08-01 | A project using Lunity | User opens Run Configurations -> Add New -> Lua Tests | The "Lua Tests" form is displayed, showing fields for Framework, Target, and Interpreter. |
| 4 | RUN-08-07 | A test result node with URL `file:///src/my_spec.lua:15` | User double-clicks the node in the Test Results tree | The editor opens `my_spec.lua` and positions the cursor on line 15. |
| 5 | RUN-08-08 | Editor showing a Busted test file with `it("calculates sums", function() ... end)` | User looks at the editor gutter | A green "Play" run icon is displayed next to the line containing `it("calculates sums", ...)`. |
| 6 | RUN-08-12 | A `luacov.stats.out` containing: `["/src/math.lua"] = { [1]=0, [2]=5, [3]=nil }` | Coverage parser processes the stats file | Identifies `math.lua` line 1 as uncovered (red gutter indicator), line 2 as covered (green gutter indicator, 5 hits), and line 3 as non-executable (no indicator). |
| 7 | RUN-08-15 | Coverage runner executes, but `require('luacov')` fails in Lua | Test session starts in Coverage mode | The runner aborts and displays a balloon notification with the text: "Code coverage library 'luacov' is not installed..." and a clickable link to install it. |
| 8 | RUN-08-16 | A `luacov.report.out` with file header `initrd/usr/bin/tests.lua` and lines `   1 local x = 1` and `***0 local y = 2` | User runs "Import LuaCov Report..." and selects the file | Resolves path to `/src/initrd/usr/bin/tests.lua` relative to project root, highlights line 1 as green, and highlights line 2 as red. |
| 9 | RUN-08-17, RUN-08-18 | A `luacov.report.out` file containing a report is opened in the editor | User views the opened report file in the editor | The editor displays red highlight on `***0`, green on hit counts, Lua syntax highlighting on the code, and a banner at the top to load coverage. |
| 10 | RUN-08-04 | Lunity JSON lines: `{"event":"suite_start","name":"math tests"}`, `{"event":"test_start","name":"test_add","file":"t.lua","line":5}`, `{"event":"test_pass","name":"test_add","duration":3}`, `{"event":"test_start","name":"test_pending"}`, `{"event":"test_ignore","name":"test_pending"}`, `{"event":"suite_end","name":"math tests"}` | Lines processed sequentially by Lunity JSON parser | Emits in order: `testSuiteStarted("math tests")`, `testStarted("test_add", "lua://t.lua:5")`, `testFinished("test_add", 3)`, `testStarted("test_pending")`, `testIgnored("test_pending")`, `testSuiteFinished("math tests")`. |
| 11 | RUN-08-05 | Busted JSON on stdout after process exit: `{"successes":[{"name":"Calculator → addition → adds two positives","trace":{"source":"@/project/calc_spec.lua","currentline":12},"duration":0.005}],"failures":[],"errors":[],"pendings":[]}` | Converter parses JSON blob | Emits: `testSuiteStarted("Calculator")`, `testSuiteStarted("addition")`, `testStarted("adds two positives", "lua:///project/calc_spec.lua:12")`, `testFinished("adds two positives", 5)`, `testSuiteFinished("addition")`, `testSuiteFinished("Calculator")`. |
| 12 | RUN-08-05 | Busted JSON with failure + error: `{"failures":[{"name":"Math → divide","message":"Expected 4 but got 5","trace":{"source":"@f.lua","currentline":8},"duration":0.01}],"errors":[{"name":"Math → crash","message":"attempt to index nil (local 'x')","trace":{"source":"@f.lua","currentline":14}}],...}` | Converter parses JSON blob | For failure: emits `testSuiteStarted("Math")`, `testStarted("divide")`, `testFailed("divide", "Expected 4 but got 5", traceFromSource("@f.lua:8"), duration=10)`, `testFinished("divide", 10)`. For error: emits `testStarted("crash")`, `testFailed("crash", "attempt to index nil (local 'x')", traceFromSource("@f.lua:14"), isTestError=true)`, `testFinished("crash", 0)`. Both share `testSuiteFinished("Math")`. |
| 13 | RUN-08-06 | Busted JSON with 3-level nested name: `"name":"math → operators → addition → adds_twelve_and_seven"` | `testSuiteStarted`/`testStarted` events with decomposed suite hierarchy arrive at SMTestRunner | Test Results tree renders as: `math` (expandable folder) > `operators` (expandable folder) > `addition` (expandable folder) > `adds_twelve_and_seven` (leaf node with green check icon). |
| 14 | RUN-08-06 | Lunity JSON lines with nested suites: `{"event":"suite_start","name":"DB tests"}`, `{"event":"suite_start","name":"connection"}`, `{"event":"test_start","name":"test_connect"}`, `{"event":"test_pass","name":"test_connect"}`, `{"event":"suite_end","name":"connection"}`, `{"event":"suite_end","name":"DB tests"}` | SMTestRunner processes events in order | Test tree renders as: `DB tests` > `connection` > `test_connect`. Suite `connection` is parented under suite `DB tests`. Leaf `test_connect` appears under suite `connection` with green check icon. |
| 15 | RUN-08-02 | Config: framework=BUSTED, busted at `/usr/bin/busted`, interpreter=`/usr/bin/lua5.4`, testTarget=`/project/spec/calc_spec.lua`, testTargetType=FILE, workingDirectory=`/project` | `buildCommandLine()` called | Returns `GeneralCommandLine { exePath="/usr/bin/busted", workDirectory="/project", parameters=["--output=json", "/project/spec/calc_spec.lua"] }`. |
| 16 | RUN-08-02 | Config: framework=LUNITY, interpreter=`/usr/bin/lua5.4`, testTarget=`/project/tests/lunity_runner.lua`, testTargetType=FILE, workingDirectory=`/project` | `buildCommandLine()` called | Returns `GeneralCommandLine { exePath="/usr/bin/lua5.4", workDirectory="/project", parameters=["/project/tests/lunity_runner.lua"] }`. No `--output=json` argument. |
| 17 | RUN-08-03 | Process stdout contains only raw unparseable text: `"lua: segfault at 0x00\nstack traceback:\n\t[C]: ?\n"`, framework=BUSTED | Process exits; `LuaTestOutputToEventsConverter` attempts to parse buffered stdout as Busted JSON | `JsonSyntaxException` caught; raw text dumped to Run console tab; no `testStarted`/`testFinished` events emitted; test tree shows empty (or a root node indicating parse failure). |
| 18 | RUN-08-03 | Mixed stdout stream: `"DEBUG: loading test modules...\n"` then valid Busted JSON blob: `{"successes":[{"name":"tests → foo","trace":{"source":"@t.lua","currentline":1},"duration":0}]}` then `"Done.\n"` | Process exits; converter processes buffered output | Non-JSON lines (`"DEBUG: loading test modules...\n"` and `"Done.\n"`) passed through to raw Run console tab. JSON blob parsed: `testSuiteStarted("tests")`, `testStarted("foo")`, `testFinished("foo",0)`, `testSuiteFinished("tests")`. Test Results tree shows `tests > foo`. |

## Acceptance Criteria
- [ ] Users can create, save, and execute a "Lua Tests" run configuration.
- [ ] Busted tests run and output results directly into the IntelliJ Test Results window.
- [ ] Custom Lunity JSON-line output is successfully parsed streamingly and mapped to the test tree.
- [ ] Double-clicking a failed/passed test in the Test Results tree navigates to the source code file and line.
- [ ] Right-clicking a test file or directory offers a "Run 'Lua Tests in...'" option, creating the configuration on the fly.
- [ ] The "Rerun Failed Tests" action correctly extracts failed tests and reruns only those.
- [ ] Running tests via "Run with Coverage" generates coverage data and highlights covered/uncovered lines in the editor gutter.
- [ ] Coverage statistics (percentages of covered lines) are aggregated and displayed in the project tree view.
- [ ] The `luacov` tool path is auto-discovered and configurable via the `TOOL` settings inventory registry.
- [ ] If `luacov` is missing during a Coverage run, the plugin prompts the user to install it via a LuaRocks link and performs the installation in the background.
- [ ] Users can manually import existing `luacov.report.out` files, which are parsed and rendered as coverage overlays on project files.
- [ ] Opened `luacov.report.out` files have custom syntax highlighting styling hit prefixes (green/red) and syntax-highlighting Lua code on each line.
- [ ] Opening `luacov.report.out` displays an editor notification banner with a clickable "Load Coverage onto Project Files" button.

## Non-Functional Requirements
- **Performance**: Stream parsing must run in `O(1)` memory relative to the size of test logs by parsing line-by-line rather than reading the entire output into memory.
- **Latency**: Test tree updates in the IDE should be near-instantaneous (within 100ms of the test process emitting the event).
- **Concurrency**: Background execution must use JetBrains' `Task.Backgroundable` and run outside the EDT, keeping the IDE UI fully responsive.

## Dependencies
- Run/Debug Framework: Relies on `LuaInterpreter` and `LuaRunConfiguration` base implementations ([RUN-02](file:///home/mini/Documents/src/lua/lunar/docs/features/debug/requirements.md#L25)).

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
