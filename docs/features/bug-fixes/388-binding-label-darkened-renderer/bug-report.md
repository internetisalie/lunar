---
id: "BUG-388"
title: "Signal inherited/no-default bindings with dimmed text instead of a parenthetical (backlog)"
type: "bug"
parent_id: "BUG"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-388: Dimmed-text binding-state renderer (backlog enhancement)

**Status: BACKLOG — not implemented.** UX polish on top of [[../387-toolchain-binding-label-clarity/bug-report|BUG-387]]
(which clarified the wording: `Inherit (none)` → `No default` / `Inherit (nothing resolved)`).

## Idea

The tool-binding combos currently convey "not explicitly set here / inherited" **in words**
(a parenthetical). The more idiomatic IntelliJ approach is to convey it **visually** with dimmed
text, so the parenthetical can be dropped:

- Swap the text-only `SimpleListCellRenderer.create("") { … }` at both binding-combo sites
  (`toolchain/ui/LuaToolchainConfigurable.kt` newBindingCombo, `toolchain/ui/LuaProjectConfigurable.kt`
  newBindingCombo) for a `ColoredListCellRenderer<LuaBindingItem>`.
- Render the **inherited / no-default** state with `SimpleTextAttributes.GRAYED_ATTRIBUTES` (dimmed),
  and an **explicitly-bound tool** with `REGULAR_ATTRIBUTES` (normal).

| State | Rendering |
| :--- | :--- |
| Global, unset | dim `No default` |
| Project, inherits a tool | dim `/usr/bin/lua5.4 — 5.4.7` (still computed via the resolver) |
| Project, nothing resolves | dim `none` |
| Explicitly bound tool | normal `/usr/bin/lua5.4 — 5.4.7` |

The inventory already uses this idiom (`ERROR_ATTRIBUTES` for the "Unknown Runtime" renderer).

## Why backlogged (not done now)

- Bigger than the BUG-387 string swap: a new renderer object at both sites, and the project side must
  still *compute* the inherited value to show it dimmed.
- **Accessibility:** keep a minimal word ("No default" / "none") — color alone is not conveyed to
  screen-readers or colorblind users, so dimming should *reinforce* text, not replace it.
- Prefer `GRAYED_ATTRIBUTES` (platform) over a raw grey `JBColor` so contrast flips correctly on a
  selected (blue-background) row.

## Scope / invariants

Label/rendering only — resolution semantics unchanged (`LuaToolResolver` precedence: active env →
project binding → global binding → inventory fallback). No test currently asserts the combo label
text (grep `Inherit (none)` / `No default` across `src/test`), but a renderer test could assert the
attributes.
