---
id: "SCHEMA-03-VERIFICATION"
title: "Verification Checklist"
type: "qa"
status: "planned"
parent_id: "SCHEMA-03"
folders:
  - "[[features/schema/03-luacheckrc-provider/requirements|requirements]]"
---

# Verification Checklist: SCHEMA-03 — Luacheckrc Schema Provider

### V-01: `.luacheckrc` file recognition
1. Open a project in the sandbox IDE.
2. Create a file named `.luacheckrc`.
3. Verify that typing `g` at the top level suggests `globals` in the completion list.
4. Verify that pressing `Ctrl+Q` (Quick Documentation) on `globals` shows its schema description.

### V-02: `.luacheckrc.lua` file recognition
1. Rename the file to `.luacheckrc.lua`.
2. Verify that schema completion and validation still apply.

### V-03: Type validation
1. In `.luacheckrc`, type `globals = true`.
2. Verify that the editor highlights `true` with a warning indicating a type mismatch (expected array).

### V-04: Plain `.lua` isolation
1. Create a file named `main.lua`.
2. Type `globals = true`.
3. Verify that no schema warnings appear.
