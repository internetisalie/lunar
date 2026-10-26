---
id: "BUG-530"
title: "The LuaRocks New Project page never renders: its radio buttons sit outside a UI DSL `buttonsGroup`"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-530: The LuaRocks New Project page never renders

## 1. Reproduction

Seen in MAINT-44's live check on GoLand 2026.2.3 (`runIde`).

1. In a sandbox IDE with Lunar installed, open **File → New → Project…**.
2. Select **LuaRocks** under *Other*.

## 2. Expected

The LuaRocks settings page shows the project name, the *Library* / *Application* project-type
radio buttons (exactly one selectable), the runtime controls and the options checkboxes.

## 3. Actual

The list selection moves to LuaRocks but the right-hand side keeps showing the previously selected
generator's page (Go), and **Create** would create the wrong kind of project. `idea.log` records:

```
SEVERE - #c.i.i.p.PluginManager - Button group must be defined before using radio button
com.intellij.ui.dsl.UiDslException: Button group must be defined before using radio button
  at com.intellij.ui.dsl.builder.impl.RowImpl.registerRadioButton(RowImpl.kt:486)
  at com.intellij.ui.dsl.builder.impl.RowImpl.cellImpl(RowImpl.kt:499)
  at com.intellij.ui.dsl.builder.impl.RowImpl.cell(RowImpl.kt:137)
  at net.internetisalie.lunar.rocks.init.LuaRocksGeneratorPeer.panel_delegate$lambda$0$0$1$0(LuaRocksGeneratorPeer.kt:68)
  ...
  at net.internetisalie.lunar.rocks.init.LuaRocksGeneratorPeer.getComponent(LuaRocksGeneratorPeer.kt:129)
  at com.intellij.ide.util.projectWizard.ProjectSettingsStepBase.createAdvancedSettings(ProjectSettingsStepBase.java:352)
SEVERE - ... Plugin to blame: lunar
```

## 4. Root cause

`LuaRocksGeneratorPeer` (`src/main/kotlin/net/internetisalie/lunar/rocks/init/LuaRocksGeneratorPeer.kt:64-83`)
builds its page with the Kotlin UI DSL and places the two `JBRadioButton`s with `cell(...)` directly
inside `group("Project type")`. It joins them to a Swing `ButtonGroup` only in `init`, outside the DSL.

The UI DSL refuses any `JRadioButton` cell that is not inside a DSL `buttonsGroup`.
`RowImpl.cellImpl` checks `component is JRadioButton` and calls `registerRadioButton`, which throws
when `dialogPanelConfig.context.getButtonsGroup()` is null
(`intellij-community/platform/platform-impl/src/com/intellij/ui/dsl/builder/impl/RowImpl.kt:462-477`):

```kotlin
private fun registerRadioButton(cell: CellImpl<out JRadioButton>, value: Any?) {
  val buttonsGroup = dialogPanelConfig.context.getButtonsGroup() ?: throw UiDslException(
    "Button group must be defined before using radio button")
  buttonsGroup.add(cell, value)
}
```

The exception escapes the peer's `by lazy` panel initialiser, so `getComponent` throws and the
wizard keeps the previous page.

### Not a 2026.2 regression: 2026.1 is affected too

The suspicion that MAINT-21's platform bump introduced this is wrong. The check dates from 2022
(`intellij-community` commit `84050836a152`, "Port Android Module Steps to Kotlin UI DSL 2"), and
`javap -c -l` of `com.intellij.ui.dsl.builder.impl.RowImpl` in both shipped jars shows the same
bytecode:

| IDE (build) | `cellImpl` | `registerRadioButton` |
|---|---|---|
| GoLand 2026.1.3 (`GO-261.25134.147`), `lib/intellij.platform.ide.impl.jar` | `instanceof JRadioButton` → `registerRadioButton` | `getButtonsGroup()`; on null, `athrow` "Button group must be defined before using radio button" (line 454) |
| GoLand 2026.2.3 (`GO-262.10968.67`), `lib/intellij.platform.ide.impl.jar` | same | same (line 486, matching the trace) |

The defect arrived with the peer's move from `FormBuilder` to the UI DSL in TOOLING-08-05
(`3b7595b2`, 2026-07-17, BUG-369). Every release from `v0.19.0` through `v0.21.12` ships it, on both
supported platform lines. No test built the panel: `LuaRocksGeneratorPeerTest` only exercises the
widget state, which works without the panel because the panel is lazy.

### Radio-button sites in the plugin

`grep -rn -i radio src/main` (Kotlin, Java, generated sources and resources, including any GUI
Designer `.form`) finds exactly one site:

| Site | Placement | Verdict |
|---|---|---|
| `LuaRocksGeneratorPeer` `libraryButton` / `applicationButton` | `cell(...)` in `group("Project type")`, no `buttonsGroup` | **defect**: wrap the rows in `buttonsGroup { }` |

There are no other `JRadioButton`/`JBRadioButton`, `radioButton(` or `buttonsGroup` uses in `src/main`.

## 5. Fix strategy

In `LuaRocksGeneratorPeer`'s panel, nest the two radio-button rows in an unbound DSL
`buttonsGroup { }` inside the existing `group("Project type")`, so the page keeps its current
layout. The DSL then registers both cells and, in `ButtonsGroupImpl.postInitUnbound`, joins them
to its own `ButtonGroup`.

The Swing `ButtonGroup` in `init` stays, so that the buttons are mutually exclusive from
construction. The peer's widget state remains testable without building the panel, which is what
the lazy panel exists for. When the panel is built, the DSL's `ButtonGroup.add` moves each button's
model into the DSL group and keeps the current selection (*Library*), so exclusivity holds before
and after. The binding API (`getSettings` reading `applicationButton.isSelected`) is unchanged.

## 6. Test strategy

New `LuaRocksGeneratorPeerPanelTest` (light fixture, so it runs on the EDT, as the UI DSL requires):

- builds the page through `getComponent(...)` and asserts it does not throw. Before the fix this
  throws `UiDslException`, so the test fails.
- finds the page's two `JRadioButton`s, asserts that exactly one is selected, then clicks
  *Application* and asserts that *Library* is deselected and `settings.type` is `APPLICATION`.

Related existing tests: `LuaRocksGeneratorPeerTest`, `LuaRocksProjectGenerator*`.

Live check (`verify-in-ide`) on `runIde` (2026.2.3) and `runIde261` (2026.1.3): the LuaRocks page
renders with both radio buttons, only one can be selected, and creating a library project leaves
no SEVERE from lunar in `idea.log`.

## 7. Verification

- `LuaRocksGeneratorPeerPanelTest`: both tests fail with `UiDslException` before the fix (2026.2.3
  platform) and pass after it.
- Live, on `runIde` (2026.2.3) and `runIde261` (2026.1.3): the LuaRocks page renders, selecting
  *Application* deselects *Library* and back, and *Create* produces a library project (`builtin`
  rockspec, `src/<name>.lua`). Neither `idea.log` contains a `UiDslException`.

### Found during verification, not fixed here

- On 2026.1.3, the first Lua file indexed in a fresh session logs lunar SEVEREs. They say
  *"All stub element types should be created before index initialization is complete. Please add
  the class … LuaLocalVarStubElementType … to "stubElementTypeHolder" extension"* (also
  `LuaFuncStubElementType` and `LuaLocalFuncStubElementType`), followed by *"Failed to build index
  'Stubs'"* (`StubSerializerEnumerator.getSerializerId` NPE). `plugin.xml` declares no
  `stubElementTypeHolder`. This is unrelated to the wizard, and the 2026.2.3 run did not show it.
- The peer ignores `getComponent`'s `checkValid` callback, so typing a project name does not clear
  *"Project name must not be empty"* until another field the platform watches changes. The project
  name field is also very narrow.
