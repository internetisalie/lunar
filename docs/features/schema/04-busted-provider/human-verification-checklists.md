---
id: "SCHEMA-04-HVC"
title: "Human Verification Checklists"
type: "qa"
status: "planned"
parent_id: "SCHEMA-04"
folders:
  - "[[features/schema/04-busted-provider/requirements|requirements]]"
---

# SCHEMA-04: Human Verification Checklists

## 1. Provider Registration & Validation
**Goal**: Verify that `.busted` files receive schema-driven completion and validation.

**Prerequisites**:
- GoLand with the Lunar plugin installed and SCHEMA-01 + SCHEMA-04 features implemented.

**Steps**:
1. Open a test project.
2. Create a file named `.busted`.
3. Type `return { default = { ` and hit `<Ctrl+Space>`.
   - **Expected**: Completion popup suggests busted options like `output`, `verbose`, `coverage`, `pattern`, `ROOT`, `tags`.
4. Type an invalid key, e.g., `bogus = true`.
   - **Expected**: The key is highlighted with a warning that the property is not allowed.
5. Type `verbose = "yes"`.
   - **Expected**: Type mismatch warning (boolean expected, got string).
6. Hover over a valid key (e.g., `verbose`).
   - **Expected**: Quick documentation shows the schema description for the `verbose` option.
