---
id: "BUG-449"
title: "The LuaRocks browser's shared detail pane is re-parented by the Installed tab, so the Marketplace tab's detail half is permanently blank"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-449: one `PackageDetailPane` cannot be the `secondComponent` of two splitters

Found during the live UI audit on 2026-08-22 (see [[bug-report|BUG-448]]). This is **not** a styling
defect — it is a Swing parenting error with a visual symptom, which is why the test suite never caught
it. Present since ROCKS-16-01 introduced the panel in `472e456c` (2026-07-17).

## 1. Reproduction

1. Open a Lua project and show the **LuaRocks Packages** tool window (defaults to the *Marketplace* tab).
2. Register a `luarocks` tool (*Settings ▸ Languages & Frameworks ▸ Lua ▸ Toolchain ▸ Auto-Discover*)
   so searches return results.
3. Type a query (`penlight`) and press Enter — the result list populates.
4. Click any result.

## 2. Expected vs actual

- **Expected**: the right-hand half of the splitter shows the package detail (summary, version,
  dependencies), or — with nothing selected — the pane's own `"No package selected"` empty text.
- **Actual**: the right-hand half is **empty background**. Not the detail, and not the empty text
  either. Measured from a screenshot 30s after selection: the region is 99.7% two flat colours with
  96 distinct values — nothing is painted there at all.

## 3. Root cause

`LuaRocksBrowserPanel` holds **one** shared detail pane:

```kotlin
private val detailPane = PackageDetailPane(project, model).also { Disposer.register(this, it) }   // :39
```

and hands that same instance to **two** splitters, one per tab:

```kotlin
tabs.addTab("Marketplace", buildMarketplaceTab())   // :51  → splitter(left)
tabs.addTab("Installed", buildInstalledTab())       // :52  → splitter(left)

private fun splitter(left: Component): OnePixelSplitter =
    OnePixelSplitter(false, 0.38f).apply {
        firstComponent = left as? javax.swing.JComponent
        secondComponent = detailPane                                   // :103
    }
```

**A Swing component has exactly one parent.** `Container.add` removes the component from its previous
parent first, so line 52's splitter *steals* `detailPane` out of line 51's splitter. The Marketplace
tab — the default tab, and the one users land on — is left with `secondComponent == null` for the
lifetime of the panel.

The selection listener still fires and still calls into the pane:

```kotlin
val row = marketList.selectedValue ?: return@addListSelectionListener detailPane.showEmpty()   // :111
detailPane.showPackage(row, listOf(row.pkg.version))                                           // :112
```

so the model updates correctly — it is simply updating a component that is not in the Marketplace
tab's hierarchy. That is why the pane shows neither content nor its `"No package selected"` card
(`PackageDetailPane.kt:64`): both live on a component parented to the *Installed* tab.

The class KDoc states the intent explicitly — *"each an `OnePixelSplitter` list-over-detail split
that reuses one shared `PackageDetailPane`"* (`:27-28`) — so the sharing is deliberate in design and
invalid in Swing. The design is the bug, not a slip in the implementation.

## 4. Fix strategy

Two viable shapes; prefer the first.

1. **One splitter, swap the list.** Keep a single `OnePixelSplitter` with the shared `detailPane` as
   `secondComponent`, and switch only `firstComponent` when the tab changes. This preserves the
   "one shared pane" intent that ROCKS-16 wanted (a selection in either tab drives the same detail
   view) and is a small change to `buildMarketplaceTab`/`buildInstalledTab`/`splitter`.
2. **Two panes.** Give each tab its own `PackageDetailPane`, each `Disposer.register`ed. Simpler to
   reason about, but doubles the metadata-fetch wiring and diverges from the ROCKS-16 design note.

Either way, drop the shared-instance claim from the class KDoc so the next reader is not told that an
impossible arrangement is the design.

## 5. Test strategy

A unit test that constructs `LuaRocksBrowserPanel` and asserts, for **each** tab index, that the tab
component's `secondComponent` is non-null and is a `PackageDetailPane`:

```kotlin
val panel = LuaRocksBrowserPanel(project, ...)
(0 until panel.tabCount).forEach { i ->
    val splitter = panel.tabComponentAt(i) as OnePixelSplitter
    assertNotNull("tab $i lost its detail pane", splitter.secondComponent)
}
```

This fails today on tab 0 and passes after either fix. It needs no screenshot and no IDE — the
parenting is observable from the component tree, which is exactly the class of defect a light
`BasePlatformTestCase` can hold.

**Mutation-check it**: re-introduce the double-`add` and confirm the test goes red, per the
`mutation-proof` skill. A test that only ever sees one tab would pass vacuously.

## 6. Notes

- **BUG-367** ("detail panel shows a blank form instead of a proper empty state") was recorded as
  absorbed by ROCKS-16 and fixed. The empty-state *code* did land (`PackageDetailPane.kt:64`); it has
  simply never been visible on the Marketplace tab. The original report's symptom is still reproducible.
- **The Installed tab was never opened.** That it holds the pane follows from `Container.add`
  semantics and the build order at `:51-52`, but its behaviour was not observed — the audit
  project had no installed rocks. Do not assume that tab is working; verify it alongside the fix.
- Screenshots: `~/.cache/claude-scratch/lunar/90c40f9b/shots/pairs/07-rocks-detail-pane.png`.
