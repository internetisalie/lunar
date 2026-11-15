---
id: "BUG-106"
title: "One call in the luacheck type audit resolves its callee in some runs and not in others"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-106: One luacheck audit call resolves its callee in some runs only

## 1. Reproduction
Run, at one commit, several times:

```bash
tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache -PwithTypeAudit -PrecordConformance --tests '*LuaTypeFalseNegativeAuditTest.testLuacheckMember'"
```

Compare `coverage` in `build/type-audit/luacheck.json`. At `353974b7`, three runs gave
`unresolvedCallee` 1777, 1776 and 1777, with `noTag` 1975, 1976 and 1975. At `8dad422c` they gave
1776, 1777 and 1777. `cells` and `records` were identical in all six runs.

## 2. Expected vs actual
- **Expected**: every run at one commit resolves the same callees, so `luacheck.json` is identical.
- **Actual**: one call's callee resolves in some runs and not in others. When it resolves, it moves
  from `unresolvedCallee` to `noTag`.

## 3. Root cause
Not identified. The call has not been located. Candidates are an order dependence in resolution
(compare BUG-102), or the type-check time cutoff (BUG-104).

## 4. Fix strategy
First find the call: log the unresolved callees from two runs and diff them. Then fix the
dependence it shows.

## 5. Test strategy
A test that resolves the located callee after each of the reading orders that differ. Then three
audit runs whose `luacheck.json` files are identical.
