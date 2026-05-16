---
folders:
  - "[[features/navigation/requirements|requirements]]"
priority: medium
status: done
vf_icon: ✅
title: "07: Reference Contributors"
---
# Specification: NAV-07 Reference Contributors

This document outlines the requirements for custom reference contributors.

## 1. Functional Requirements

| ID | Feature | Expected Behavior | Priority | Status |
| :--- | :--- | :--- | :---: | :--- |
| `NAV-07-01` | **Label References** | Resolve `goto labelName` references to their corresponding `::labelName::` declarations. | **S** | Full |
| `NAV-07-02` | **String References (Require)** | Resolve strings inside `require("...")` to the actual file path in the project or libraries. | **S** | Partial |

## 2. Technical Details
- Register via `PsiReferenceContributor`.
- Enable standard navigation, renaming, and find usages for the injected references.
