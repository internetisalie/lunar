---
id: NAVIGATION-12
title: "12: Member Field Resolution"
type: feature
parent_id: NAV
status: "planned"
priority: "medium"
folders:
  - "[[features/navigation/requirements|requirements]]"
---
# Specification: NAV-12 Member Field Resolution

Completes the **Partial** `NAV-01-03` (table-field navigation) and fixes field **quick documentation**.
Today a dotted member field access (`package.path`, `obj.field`) has **no resolution target**: after the
member-reference fix ([[../../bug-fixes|the same-short-name fix]]) a qualified member resolves only
through its receiver-qualified name, and field *assignments* (`receiver.field = value`) are not indexed,
so `resolve()` returns nothing and quick-doc shows "No documentation found".

## 1. Functional Requirements

| ID | Feature | Expected Behavior | Priority | Status |
| :--- | :--- | :--- | :---: | :--- |
| `NAV-12-01` | **Field Declaration Index** | Index every `receiver.field = value` declaration (and the `---@type`/`---@field`/doc comment riding it) by its qualified name `receiver.field`, across files. | **M** | Not Implemented |
| `NAV-12-02` | **Go to Field Declaration** | `Go to Declaration` on `a.b` navigates to the `a.b = …` declaration (e.g. `package.path` → the stdlib stub's `package.path = ""`). Elevates `NAV-01-03` to **Full**. | **M** | Not Implemented |
| `NAV-12-03` | **Field Quick Documentation** | Quick documentation on `a.b` renders the field's doc comment and inferred/declared type, instead of "No documentation found" or an unrelated symbol. | **S** | Not Implemented |
| `NAV-12-04` | **No Cross-Namespace Collisions** | A field lookup is keyed by the full `receiver.field`, never the bare member name, so `package.path` never resolves to an unrelated `path.*` module symbol (regression guard for the member-reference fix). | **M** | Not Implemented |

## 2. Non-Goals
- Receiver **type-narrowing** resolution (resolving `local p = package; p.path` via `p`'s inferred type
  rather than the literal receiver text `p`). Reference resolution keys on receiver text today; type-aware
  receiver resolution is tracked separately and is **out of scope** here.
- Resolving fields of **anonymous** table literals assigned to locals without a stable qualified name.

## 3. Acceptance
- `package.path` (with the bundled stdlib stub) resolves to the stub's `package.path = ""` and shows its
  doc; it resolves to **zero** `path.*` module functions.
- `NAV-01-03` status updated to **Full**; existing navigation/resolution/quick-doc suites stay green.
