---
id: "COMP-02-QA"
title: "Verification Checklists"
type: "qa"
parent_id: "COMP-02"
priority: "high"
folders:
  - "[[features/completion/02-symbol-completion/requirements|requirements]]"
---

# COMP-02: Human Verification Checklists

## Manual Verification Steps

### Local Variable Completion
- [ ] Create a local variable `local test_var = 10`.
- [ ] On the next line, type `te` and verify `test_var` is suggested.
- [ ] Select `test_var` and ensure it completes correctly.

### Function Parameter Completion
- [ ] Define a function `function test_func(param_name)`.
- [ ] Inside the function body, type `pa` and verify `param_name` is suggested.

### Shadowing Verification
- [ ] Define `local x = 1` in an outer scope.
- [ ] Define `local x = 2` in an inner block.
- [ ] Inside the inner block, trigger completion for `x` and verify it refers to the inner definition (e.g. by checking documentation or type if available).

### Global Symbol Completion (Same File)
- [ ] Define a global variable `global_var = 20` (without `local`).
- [ ] In a different part of the file, type `gl` and verify `global_var` is suggested.
