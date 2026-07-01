---
id: "MAINT-08-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-08"
folders:
  - "[[features/maint/08-luacheck-grouping/requirements|requirements]]"
---

# Technical Design: MAINT-08 — LuaCheck UI Grouping

## 1. Architecture Overview

### Current State
LuaCheck runs only as `net.internetisalie.lunar.analysis.luacheck.LuaCheckAnnotator`
(`ExternalAnnotator<Info, Results>`), registered as:

```xml
<!-- plugin.xml:262 -->
<externalAnnotator language="Lua"
        implementationClass="net.internetisalie.lunar.analysis.luacheck.LuaCheckAnnotator"/>
```

`ExternalAnnotator.getPairedBatchInspectionShortName()` returns `null` by default
(`platform/analysis-api/.../ExternalAnnotator.java:90`), so LuaCheck has **no** node in the
Inspections settings tree. Every other Lua inspection is a `<localInspection>` with a flat
`groupName="Lua"` (`plugin.xml:164-253`), so they sit directly under a single "Lua" group.

### Prior Art in This Repo
Searched `plugin.xml` and `src/main/kotlin/.../analysis/` (grep `externalAnnotator`,
`localInspection`, `groupPath`, `ExternalAnnotatorBatchInspection`):
- **`LuaCheckAnnotator`** (`analysis/luacheck/LuaCheckAnnotator.kt:11`) — the external
  annotator. This design **extends** it: adds a `getPairedBatchInspectionShortName()`
  override only; its `collectInformation`/`doAnnotate`/`apply` are untouched.
- **`LuaCheckSettings`** (application service, `plugin.xml:512`) and **`LuaCheckSettingsPanel`**
  (`applicationConfigurable` "LuaCheck" under `groupId="tools"`, `plugin.xml:506-509`) — the
  separate Tools settings page. **Not** touched; unrelated to the Inspections tree.
- No existing class implements `ExternalAnnotatorBatchInspection` and no `<localInspection>`
  uses `groupPath` — grep returned zero hits in `src/`. So `LuaCheckInspection` is **new**,
  not a duplicate.
- Reference idiom (not in this repo): intellij-community `ShShellcheckInspection` +
  `ShShellcheckExternalAnnotator` (`plugins/sh/backend/.../shellcheck/`) — the canonical
  external-linter → paired-inspection pattern this design mirrors.

### Target State
A new `LuaCheckInspection` (`LocalInspectionTool` + `ExternalAnnotatorBatchInspection`) is
registered as `<localInspection shortName="LuaCheck" groupPath="Lua" groupName="Luacheck">`.
`LuaCheckAnnotator` is paired to it by short name. The Inspections tree then shows
**Lua ▸ Luacheck ▸ LuaCheck**; toggling that node gates the annotator; "Inspect Code…"
dispatches through the inherited `checkFile`.

```
Settings ▸ Editor ▸ Inspections
  └─ Lua                      (existing flat group: type assignability, unused local, …)
  └─ Lua ▸ Luacheck ▸ LuaCheck   (NEW — this feature)   ⇄ paired ⇄  LuaCheckAnnotator
```

## 2. Core Components

### 2.1 net.internetisalie.lunar.analysis.luacheck.LuaCheckInspection  (NEW)
- **Responsibility**: represent LuaCheck in the Inspections settings tree and act as the
  batch inspection paired with `LuaCheckAnnotator`.
- **Threading**: none added. `getShortName()` returns a constant. The inherited
  `checkFile` runs under the platform's batch context (off EDT), delegating to the annotator.
- **Collaborators**: `LuaCheckAnnotator` (paired by short name); platform
  `ExternalAnnotatorInspectionVisitor` (invoked by the default `checkFile`).
- **Key API**:
  ```kotlin
  package net.internetisalie.lunar.analysis.luacheck

  import com.intellij.codeInspection.LocalInspectionTool
  import com.intellij.codeInspection.ex.ExternalAnnotatorBatchInspection

  class LuaCheckInspection :
      LocalInspectionTool(),
      ExternalAnnotatorBatchInspection {

      override fun getShortName(): String = SHORT_NAME

      companion object {
          const val SHORT_NAME: String = "LuaCheck"
      }
  }
  ```
  - No `buildVisitor` override: the inspection is declared `unfair="true"`; problem
    discovery stays in the annotator. `checkFile` is inherited from
    `ExternalAnnotatorBatchInspection` (default in
    `platform/analysis-impl/.../ExternalAnnotatorBatchInspection.java:28`) and needs no
    override — it locates the paired annotator by matching `SHORT_NAME` and calls
    `ExternalAnnotatorInspectionVisitor.checkFileWithExternalAnnotator`.

### 2.2 net.internetisalie.lunar.analysis.luacheck.LuaCheckAnnotator  (EXISTING — 1 addition)
- **Responsibility**: unchanged (collect → invoke CLI → apply annotations).
- **Change**: add the pairing override so the platform links it to `LuaCheckInspection`.
- **Key API** (added method):
  ```kotlin
  override fun getPairedBatchInspectionShortName(): String = LuaCheckInspection.SHORT_NAME
  ```
  This overrides `ExternalAnnotator.getPairedBatchInspectionShortName()` (base returns null).

## 3. Algorithms

There is **no** non-trivial algorithm in this feature — it is registration + a constant
pairing. The only logic worth pinning down is how the platform derives the tree group path
from the `<localInspection>` attributes; it is platform code, reproduced here so the
implementer chooses the attribute values correctly.

### 3.1 Group-path resolution (platform, `InspectionEP.getGroupPath()`)
- **Input → Output**: `(groupPath: String?, groupName: String)` → `String[]` tree path.
- **Steps** (from `platform/analysis-api/.../InspectionEP.java:146-160`):
  1. `name = getGroupDisplayName()` (the `groupName` attribute) → `"Luacheck"`.
  2. `path = groupPath` (the `groupPath` attribute) → `"Lua"`.
  3. If `path != null`: return `path.split(",")` with `name` appended →
     `["Lua"] + ["Luacheck"] = ["Lua", "Luacheck"]`.
- **Rules / edge handling**: `groupPath` splits on `,` (comma); a single ancestor uses no
  comma. If `groupPath` were omitted the result would be just `["Luacheck"]` (a flat
  top-level group — violates MAINT-08-02), so `groupPath="Lua"` is required.
- **Chosen values**: `groupPath="Lua"`, `groupName="Luacheck"`, `displayName="LuaCheck"`.

## 4. External Data & Parsing
No new external/unstructured input is consumed by this feature. LuaCheck CLI output parsing
lives unchanged in `LuaCheckInvoker` (regex `(.+?):(\d+):(\d+)-(\d+):(.+)\n`,
`LuaCheckInvoker.kt`). This feature only adds settings-tree registration.

## 5. Data Flow

### Example 1: User disables LuaCheck in the Inspections tree
1. User unchecks **Settings ▸ Editor ▸ Inspections ▸ Lua ▸ Luacheck ▸ LuaCheck**.
2. The profile marks short name `LuaCheck` disabled.
3. On the next highlighting pass, `ExternalToolPass` sees
   `LuaCheckAnnotator.getPairedBatchInspectionShortName() == "LuaCheck"` is disabled and
   skips the annotator → no LuaCheck squiggles.

### Example 2: Batch "Inspect Code…" with LuaCheck enabled
1. User runs **Analyze ▸ Inspect Code…**.
2. Platform invokes `LuaCheckInspection.checkFile(file, context, manager)` (inherited).
3. The default `checkFile` finds the annotator whose `pairedBatchInspectionShortName`
   equals `"LuaCheck"` and runs it via `ExternalAnnotatorInspectionVisitor`, producing
   `ProblemDescriptor`s reported under the **Lua ▸ Luacheck ▸ LuaCheck** node.

## 6. Edge Cases
- **Short-name drift**: if `<localInspection shortName>`, `SHORT_NAME`, and the annotator's
  paired name diverge, pairing silently breaks. Mitigation: annotator and inspection both
  reference the single `LuaCheckInspection.SHORT_NAME` constant; the XML string is asserted
  by TC3/TC4.
- **`getShortName` default**: `LocalInspectionTool` would derive `"LuaCheck"` from the class
  name (`LuaCheckInspection` minus `Inspection`), but `ExternalAnnotatorBatchInspection`
  re-declares `getShortName()` as abstract, so an explicit override is required (and makes
  the value unambiguous).
- **`unfair` attribute**: omitting `unfair="true"` makes the platform expect a real local
  visitor and log a warning for a paired/unfair tool; it must be present.

## 7. Integration Points

Edit `src/main/resources/META-INF/plugin.xml`. Add the `<localInspection>` immediately
after the existing `<externalAnnotator>` (`plugin.xml:262-264`):

```xml
<!-- plugin.xml (com.intellij extensions) -->
<externalAnnotator
        language="Lua"
        implementationClass="net.internetisalie.lunar.analysis.luacheck.LuaCheckAnnotator"/>

<localInspection
        language="Lua"
        shortName="LuaCheck"
        displayName="LuaCheck"
        groupPath="Lua"
        groupName="Luacheck"
        enabledByDefault="true"
        level="WARNING"
        unfair="true"
        implementationClass="net.internetisalie.lunar.analysis.luacheck.LuaCheckInspection"/>
```

No changes to any `applicationService`, Configurable, or index registration.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| MAINT-08-01 | M | §2.1, §7 (`shortName="LuaCheck"`) |
| MAINT-08-02 | M | §3.1, §7 (`groupPath`/`groupName`) |
| MAINT-08-03 | M | §2.2 (`getPairedBatchInspectionShortName`) |
| MAINT-08-04 | S | §2.1 (inherited `checkFile`), §5 Example 2 |
| MAINT-08-05 | S | §7 (`enabledByDefault="true"`, `level="WARNING"`) |

## 9. Alternatives Considered
- **`groupKey`/`bundle` (Shellcheck style)**: use a message-bundle key for the group name.
  Rejected — Lunar's inspections use inline `groupName="Lua"` (no bundle); staying inline
  matches existing style and avoids introducing a resource bundle just for one node.
- **Regroup all Lua inspections under nested paths**: out of scope; would change five+
  unrelated registrations and their profile keys. This feature only adds the LuaCheck node.
- **`GlobalSimpleInspectionTool`** base instead of `LocalInspectionTool`: both are permitted
  by `ExternalAnnotatorBatchInspection`; `LocalInspectionTool` is the Shellcheck-proven,
  lighter choice and integrates with on-the-fly gating.

## 10. Open Questions

_None — feature has cleared the planning bar._
