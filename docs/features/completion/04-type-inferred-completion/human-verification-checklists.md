---
folders:
  - "[[features/completion/04-type-inferred-completion/requirements|requirements]]"
title: "Verification Checklists"
---

# COMP-04: Human Verification Checklists

## Manual Verification Steps

### Literal Table Member Completion
- [ ] Create a table `local t = { foo = 1, bar = function() end }`.
- [ ] Type `t.` and verify `foo` and `bar` are suggested.
- [ ] Type `t:` and verify only `bar` is suggested.

### Class Member Completion (Inheritance)
- [ ] Define `@class Base @field baseField number`.
- [ ] Define `@class Derived : Base @field childField string`.
- [ ] Declare `---@type Derived; local d`.
- [ ] Type `d.` and verify both `baseField` and `childField` are suggested.

### LuaCATS Field Completion
- [ ] Define `@class M @field x number @field y number`.
- [ ] Declare `---@type M; local m`.
- [ ] Type `m.` and verify `x` and `y` are suggested.

### Union Type Completion
- [ ] Declare `---@type string|number; local v`.
- [ ] Type `v.` and verify that common members (if any) or all members are suggested.

### Method Completion
- [ ] Define `function myClass:method() end`.
- [ ] Declare `---@type myClass; local o`.
- [ ] Type `o:` and verify `method` is suggested.
