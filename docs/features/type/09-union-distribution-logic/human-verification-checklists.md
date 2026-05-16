---
folders:
  - "[[features/type/09-union-distribution-logic/requirements|requirements]]"
title: "Verification Checklists"
---

# TYPE-09: Union Distribution Logic - Human Verification Checklist

## Functional Verification
- [ ] **Simple OR**: Variable typed `string|number` accepts a number without error.
- [ ] **Simple AND**: Function expecting `number` rejects a variable typed `string|number`.
- [ ] **Nested Unions**: `string|(number|boolean)` accepts `true`.
- [ ] **Transitive Union**: `local x = p; local y = x` where `p` is `string|number` correctly propagates the union type.

## IDE Integration
- [ ] **Inlay Hints**: Hovering over a union-typed variable shows the full union string.
- [ ] **Diagnostics**: Error messages for union mismatches are descriptive (e.g., "string is not assignable to number").
- [ ] **Error Highlighting**: Red squiggles appear on the correct element (the assignment or the union member).

## Performance
- [ ] **No Lag**: Typing in a file with several union definitions does not cause noticeable delay in analysis.
- [ ] **Large Unions**: Test with a union of 20+ types (e.g., a "Result" union with many error variants) to ensure stability.
