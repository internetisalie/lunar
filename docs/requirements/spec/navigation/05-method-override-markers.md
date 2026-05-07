# Specification: NAV-05 Method Override Markers

This document defines the requirements for displaying gutter icons for overridden or implemented methods.

## 1. Functional Requirements

| ID | Feature | Expected Behavior | Priority | Status |
| :--- | :--- | :--- | :---: | :--- |
| `NAV-05-01` | **Override Marker (Gutter)** | Display `AllIcons.Gutter.OverridingMethod` next to a method definition that overrides a method in a parent class. | **S** | Not Implemented |
| `NAV-05-02` | **Implement Marker (Gutter)** | Display `AllIcons.Gutter.ImplementingMethod` next to a method that implements a method defined in an interface/parent class. | **S** | Not Implemented |
| `NAV-05-03` | **Navigation** | Clicking the marker must open a popup or navigate directly to the super/parent method definition. | **S** | Not Implemented |
| `NAV-05-04` | **Go to Super Method** | Support the standard "Go to Super Method" action (Ctrl+U / Cmd+U). | **S** | Not Implemented |

## 2. Technical Details
- Requires resolving the current function's containing table/class and querying its parent class via LuaCATS `@class` inheritance hierarchy.
- Needs a `LineMarkerProvider` or `RelatedItemLineMarkerProvider`.
- Performance: Hierarchy resolution must be fast, utilizing caches where necessary.
