---
id: "TOOLING-00-06-RESULTS"
title: "Clean-break Serialization — Spike Results"
type: "results"
parent_id: "TOOLING-00"
priority: "high"
folders:
  - "[[features/tooling/00-de-risking/requirements|requirements]]"
---

# TOOLING-00-06 — Clean-break Serialization (Spike Results)

**Deliverable for:** `TOOLING-00-06` (design §2.6) · **Test:**
`LuaToolchainSerializationSpikeTest`
(`src/test/kotlin/net/internetisalie/lunar/toolchain/LuaToolchainSerializationSpikeTest.kt`)

## Question

Do the contract-§7 new state classes round-trip through `XmlSerializer`, and does a
legacy `lunar.xml` fragment (today's real tags) load into them **without exception** with
every stale tag dropped on re-serialization?

## Verdict — PASS

Both states round-trip with field-by-field deep equality; both a legacy app fragment and a
legacy project fragment deserialize into the **new** classes with no exception; and
re-serialization emits **none** of the eleven deleted legacy tag names. All 6 tests in the
class are green.

Gate:
`tooling/gce-builder/gce-builder.sh run "test --tests *LuaToolchainSerializationSpikeTest*"`
→ `BUILD SUCCESSFUL`.

## Method (as executed)

The spike state classes live **in the test file** (throwaway; class + field names verbatim
from design §2.6 / contract §7): `RegisteredToolState`; `LuaToolchainAppState{tools,
globalBindings}`; `ToolEnvironmentState`; `LuaToolchainProjectState{bindings, environments,
activeEnvironmentId, luacheckArguments, rocksServerUrl}`. `var` + defaults per the sanctioned
XML-serializer exception.

1. **Round-trip (deep equality):** populate both states (≥2 tools, ≥1 env, ≥1 binding),
   `XmlSerializer.serialize` → `XmlSerializer.deserialize`, then assert field-by-field
   (tools compared member-by-member; env compared field-by-field; maps compared by content).
2. **Legacy tolerance:** `JDOMUtil.load(...)` two fixture strings modeling today's real
   serialized shape. The **app** fixture carries `interpreters` (list of `LuaInterpreter`
   beans), `toolInventory` (old `LuaTool` shape), and `globalToolBindings`
   (`LuaApplicationSettings.State:39-53`). The **project** fixture carries `interpreter`,
   `interpreterMode`, `interpreterModeMigrated`, `explicitInterpreter`, `explicitTarget`,
   `hererocksEnv` (singular deprecated — `LuaProjectSettings.State:117`), `hererocksEnvs`,
   `activeEnvId`, and `projectToolBindings` (`LuaProjectSettings.State:53-130`). Both were
   built faithfully from the real field shapes read out of those two source files.
3. Deserialize each fixture into the **new** classes → assert **no exception** and that the
   inventory/collections load empty (`tools`, `globalBindings`, `environments`, `bindings`).
4. Re-serialize the loaded state; assert the emitted XML contains **none** of:
   `interpreters`, `toolInventory`, `globalToolBindings`, `hererocksEnv`, `hererocksEnvs`,
   `interpreterMode`, `interpreterModeMigrated`, `explicitInterpreter`, `explicitTarget`,
   `activeEnvId`, `projectToolBindings`.

## Recorded serializer behavior for unknown legacy tags

The clean break is safe precisely because **no legacy tag shares a name with a new field**
— the app inventory was renamed to `tools` for exactly this reason (contract §7). Observed
behavior of `com.intellij.util.xmlb.XmlSerializer.deserialize` on the legacy fixtures:

- **No exception** is thrown for any of the eleven unknown `<option>`/child tags — the
  serializer maps only the `<option name="…">` entries whose names match a bean field and
  **silently ignores** the rest. This is the whole point of the "clean break": legacy tags
  are inert on load.
- **`tools` (and every other new collection) loads empty** — the legacy `toolInventory`
  tag does not collide with `tools`, so it is dropped rather than mis-mapped.
- **Warnings:** the round-trip did not surface deserialization warnings in the test output.
  Per design §2.6 step 5 this is informational — **warnings are acceptable, exceptions are
  not**; the verdict rests on "no exception + stale tags dropped", both of which hold.
- On re-serialize the emitted XML is composed **only** from the new bean fields, so none of
  the eleven legacy names can reappear (asserted directly on the serialized text).

## Hands to

TOOLING-01 (registry persistence shape), TOOLING-02 (env/binding persistence),
TOOLING-05 (legacy-deletion safety — a real `lunar.xml` upgrades cleanly with stale tags
dropped on first save).
