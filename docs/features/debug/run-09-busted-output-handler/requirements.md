---
id: "RUN-09"
title: "RUN-09: Lunar-shipped busted output handler"
type: "feature"
status: "todo"
priority: "medium"
parent_id: "DEBUG/RUN"
folders:
  - "[[features/debug/requirements|requirements]]"
---

# RUN-09: Lunar-shipped busted output handler

## Overview
Lunar runs busted with `--output=json`. That report arrives once, when the process exits, and joins
each test's describe/it path with spaces, so the levels cannot be recovered (BUG-526). The test tree
therefore shows nested describes as one suite named by the joined path, and shows nothing until the
run ends. This feature ships a busted output handler with the plugin, passed as `-o <handler.lua>`,
that emits structured events while the run is in progress. It closes the RUN-05-06 residual and the
streaming that RUN-05 `design.md` §3.2 already asks for.

## Scope

### In Scope
- A Lua output handler bundled as a plugin resource, made available to busted at run time,
  including on remote or containerised targets.
- Events that carry the describe chain as a list, plus the file, line, status, message, traceback
  and duration of each test.
- Streaming those events as busted runs, so the tree builds live.

### Out of Scope
- Lunity output (RUN-05-04).
- Working around the busted 2.3.0 JSON encoder failure on `pending(...)` (BUG-527). A handler that
  does not use busted's JSON encoder would avoid it, but that is a side effect, not a requirement.

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| RUN-09-01 | Nested describes | M | Not Implemented | Each `describe` level appears as its own suite in the test tree, in source nesting order (RUN-05-06, TC #13). |
| RUN-09-02 | Live results | S | Not Implemented | Test nodes appear and change state while busted runs, not only at process exit. |
| RUN-09-03 | Parity with the JSON path | M | Not Implemented | Pass, failure, error and pending states, assertion diffs, tracebacks, durations and `lua://` locations match or improve on what the `--output=json` path shows today. |
| RUN-09-04 | Handler delivery | M | Not Implemented | The handler resolves on every target Lunar runs busted on, including when the plugin is installed from a jar. |
