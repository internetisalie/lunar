---
id: "BUG-526"
title: "Running busted specs shows an empty test tree: every node is sent without a parent id, and test names are split on a separator busted never writes"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-526: running busted specs shows an empty test tree

## 1. Reproduction

Found in the MAINT-21 Phase 3 live check, with the same result on GoLand 2026.1.3 and 2026.2.3.

1. Create `spec/arith_spec.lua`:
   ```lua
   describe("arith", function()
     it("adds", function() assert.are.equal(2, 1 + 1) end)
     it("subtracts", function() assert.are.equal(1, 3 - 1) end)
   end)
   ```
2. Run the context action *Run 'Lua Tests: arith_spec.lua'*. busted 2.3.0 runs as
   `busted --output=json …/spec/arith_spec.lua` and exits 1.
3. The Run window shows an empty tree and "Test framework quit unexpectedly". `idea.log` has:
   ```
   SEVERE - GeneralTestEventsProcessor - [LuaTest]: Parent node id should be defined:
     TestStartedEvent{name='arith adds', id='test_1', …}
   ```
   The stack runs through `LuaTestOutputToEventsConverter.handleBustedItemStatus` into
   `GeneralIdBasedToSMTRunnerEventsConvertor.onTestStarted`.

## 2. Expected vs Actual
- **Expected**: the tree shows the spec file, the `arith` suite, `adds` passed and `subtracts` failed,
  with the assertion diff.
- **Actual**: no nodes. The platform rejects every node the converter sends.

## 3. Root cause

`LuaTestOutputToEventsConverter` (`run/test/`) has three defects. The first two each empty the tree
on their own.

**A. Every top-level node is sent with `parentId = null`.** `LuaTestConsoleProperties` calls
`setIdBasedTestTree(true)`, so events go to `GeneralIdBasedToSMTRunnerEventsConvertor`. Its
`findValidParentNode` calls `logProblem("Parent node id should be defined…", true)` for a null parent
and drops the node. The tree root's id is `TreeNodeEvent.ROOT_NODE_ID` (`"0"`). The converter never
used it. Busted's `getOrCreateSuites` started with `parentId = null`, and the three Lunity handlers
used `lunitySuiteStack.lastOrNull()`. So even a correctly split busted name would have its top-level
suite rejected, and Lunity's top-level suites and tests are rejected the same way.

**B. Busted names are split on `" → "`, which busted never writes.** busted 2.3.0's
`outputHandlers/base.lua` builds `name` like this:
```lua
handler.getFullName = function(context)
  ...
  return table_concat(names, ' ')
```
The describe/it path is joined by single spaces. `processBustedJson` called
`fullName.split(" → ")`, which gives one part, so no suite was opened and the test went out with
`parentId = null`. That is the live log line: `name='arith adds'`, the whole path used as the test
name.

**C. A file-level error kills the whole report.** When a spec file does not load, busted writes
`"trace": []`, because an empty Lua table encodes as a JSON array. `item.get("trace")?.asJsonObject`
then throws `IllegalStateException: Not a JSON Object: []`, and the catch in `processBustedOutput`
discards every entry. Two smaller field errors are in the same code: it read the stack trace from
`trace.trace` and the duration from the entry's top level. busted writes `trace.traceback` and
`element.duration`.

**Where `" → "` came from, and whether this ever worked.** The separator was invented in
RUN-05 `design.md` §3.2, which showed `"name": "suite → test name"`. It was implemented as written in
`97554d8b` (feat(run-08), 2026-06-20). The `LuaTestRunnerTest` fixtures were written to that design
rather than captured from busted, and they assert through `RecordingTestEventsProcessor`, which
accepts a null parent. `git log` shows no later change to the busted path except MAINT-24 Phase 6
(`6bf312a4`: JSON scanner and live output). The busted tree has therefore never been seen working
against real busted output. busted's `getFullName` has used `' '` throughout 2.x. The busted
version is not a factor.

**Is there structure to use instead of splitting the name?** Partly. Each entry carries `element`, a
copy of the `it` block without its parent chain:
- `element.name` is the leaf name.
- `element.descriptor` is `it`, `describe`, or `file`.
- `element.trace` holds the `it` call site.

Nothing in the report records the describe levels. Because describe and it names may contain
spaces, the prefix `name − " " + element.name` is the describe path, but it cannot be split into its
levels. For example, `"a b c"` could be describe `a` › describe `b` › it `c`, or describe `a b` ›
it `c`.

Shapes captured from real busted 2.3.0 on the builder (`/usr/local/bin/busted`). They are committed
under `src/test/resources/busted/`:

| Fixture | Spec | What it shows |
|---|---|---|
| `arith.json` | §1 above, captured in the MAINT-21 live check | flat describe, one pass, one failure |
| `nested.json` | below | nested describes, failure, error, a bare top-level `it` |
| `broken-describe.json` | below | error entry with `element.descriptor = "describe"` |
| `syntax-error.json` | below: a spec with an unclosed `it(` | error entry with `element.descriptor = "file"`, `trace = []` |

The spec sources below are the exact files busted ran, so the fixture line numbers reproduce. For
`arith.json`, the spec in §1 is verbatim.

`spec/nested_spec.lua` (`nested.json`):
```lua
describe("outer group", function()
  it("top level of outer", function()
    assert.are.equal(1, 1)
  end)
  describe("inner group", function()
    it("adds two numbers", function()
      assert.are.equal(3, 1 + 2)
    end)
    it("fails on purpose", function()
      assert.are.equal(1, 2)
    end)
    it("errors on purpose", function()
      error("boom")
    end)
  end)
end)

it("bare top-level test", function()
  assert.is_true(true)
end)
```
Its names are `"outer group inner group adds two numbers"`, …, `"bare top-level test"`.

`spec/broken_spec.lua` (`broken-describe.json`):
```lua
describe("broken describe", function()
  error("describe body blew up")
end)
```

`spec/syntax_spec.lua` (`syntax-error.json`). The file ends after line 2:
```lua
describe("x", function()
  it("y"
```

## 4. Fix strategy

- New `LuaBustedReportEntry` (`run/test/`) reads one report entry from busted's own fields. The test
  name is `element.name`. The describe path is `name` with the suffix `" " + element.name` removed,
  kept whole. File and line come from `element.trace`, falling back to `trace`. The duration comes
  from `element.duration`, and the stack trace from `trace.traceback`. Every object field is read
  defensively, so `[]` and other non-object values count as absent. No string heuristic is used.
- `processBustedJson` opens one suite per spec file, whose parent is `ROOT_NODE_ID` and whose
  location is `lua://<file>:1`. Inside it, the converter opens one suite per distinct describe path.
  A test with no describe path hangs off its file suite. A test with no known file hangs off the
  root. Every node gets a defined parent.
- The Lunity handlers fall back to `ROOT_NODE_ID` when the suite stack is empty (defect A).
- `handleBustedItemStatus` took 8 arguments, which breaks the ≤3-argument tripwire. It is replaced by
  `emitBustedTest(entry, status, parentId)`.
- Docs: RUN-05 `design.md` §3.2 now describes the real JSON shape. RUN-05-06 goes from Full to
  **Partial**, because nested busted describes show as one suite named by the joined path.

## 5. Test strategy

`LuaTestReportTreeTest` sends output through the converter into the platform's own
`GeneralIdBasedToSMTRunnerEventsConvertor`, the processor the Run window uses, and asserts the
resulting `SMTestProxy` tree: names, pass/fail/error state, and location URLs. The platform's
`logProblem(…, true)` goes through `LOG.error`, which throws in tests, so a null or unknown parent
fails here exactly as it does live. There are four busted cases, one per fixture above, plus one
Lunity case: a top-level suite and a top-level test.

The `LuaTestRunnerTest` busted fixtures were rewritten to the real shape: space-joined `name` plus
`element`. Their expected events now include the file suite and `parentId=0`.

## 6. Out of scope

- **True nesting of busted describes**, filed as [[RUN-09]]. The JSON report does not carry it.
  Getting it means Lunar shipping its own busted output handler (`-o <handler.lua>`) that emits the
  describe chain. That handler would also stream results live, which RUN-05 §3.2 already asks for.
- **`pending(...)` breaks busted's JSON output**, filed as [[BUG-527]]. With busted 2.3.0, a spec
  that calls `pending("…")` prints `Failed to encode test results to json: type 'function' is not
  supported by JSON.` instead of a report, so no tree can be built. It is likely an upstream busted
  defect. `it("name")` without a function produced no entry at all.
- **Failing and erroring tests show 0 ms.** busted writes `element.duration` only for passing tests.
- **Lunity's real wire format** has not been checked against a real Lunity run. Only the null-parent
  defect is fixed for Lunity.

## Resolution

Fixed as in §4. Red: before the fix, all five `LuaTestReportTreeTest` cases failed on the builder.
- The arith, nested and broken-describe cases failed with the live message,
  `[LuaTest]: Parent node id should be defined: TestStartedEvent{name='arith adds', id='test_1', …}`.
- The Lunity case failed with `… TestSuiteStartedEvent{name='DB tests', id='suite_1', …}`.
- The file-error case gave an empty tree after `IllegalStateException: Not a JSON Object: []`.

Green, from the libvirt builder's JUnit XML after the fix:

| Run | Result |
|---|---|
| `test --rerun --tests '*LuaTestReportTreeTest*' --tests '*LuaTestRunnerTest*'` | `LuaTestReportTreeTest` tests=5 failures=0 errors=0; `LuaTestRunnerTest` tests=14 failures=0 errors=0 |
| `test --rerun` (full suite) | 504 result files, every one written after the run started. Summed `<testsuite>` attributes: tests=3231, skipped=4, failures=0, errors=0. `BUILD SUCCESSFUL in 10m 18s` |
| `ktlintCheck` | `BUILD SUCCESSFUL` |

`status: done` is conditional on the MAINT-21 TC-15/16 live check, which is re-run in the IDE after
merge. If that check still shows an empty or broken tree, reopen this report.
