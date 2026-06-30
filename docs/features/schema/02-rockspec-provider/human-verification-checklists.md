---
id: "SCHEMA-02-QA"
title: "Human Verification"
type: "qa"
parent_id: "SCHEMA-02"
folders:
  - "[[features/schema/02-rockspec-provider/requirements|requirements]]"
---

# SCHEMA-02: Human Verification Checklists

## Setup
1. Launch the IDE with the Lunar plugin installed.
2. Open a test project.

## Verification Checklist

### 1. Rockspec v3.0 Validation
- [ ] Create a file named `test-30.rockspec`.
- [ ] Omit `rockspec_format` (defaults to v3.0).
- [ ] Add an invalid key at the top level, e.g. `invalid_key = true`.
- [ ] Verify that a schema compliance warning appears for `invalid_key` (not permitted by v3.0 schema).
- [ ] Add a `dependencies` table and type `l` inside a string.
- [ ] Trigger autocompletion and verify completion is provided.

### 2. Rockspec v3.1 Validation
- [ ] Create a file named `test-31.rockspec`.
- [ ] Add `rockspec_format = "3.1"`.
- [ ] Add a top-level `test_dependencies = {}` table.
- [ ] Verify that no warning appears (permitted in v3.1 but not v3.0).
- [ ] Add an invalid key and verify the warning appears.

### 3. Hover Documentation
- [ ] Hover over standard rockspec fields like `package` or `version`.
- [ ] Verify that Quick Documentation shows the description pulled from the JSON schema.
