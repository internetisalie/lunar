---
id: "BUG-462"
title: "The package detail card strands its loading placeholders, and its description and dependency regions never render"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-462: `Homepage: (loading)` is permanent, and two thirds of the detail card is missing

Found while verifying [[bug-report|BUG-449]] on 2026-08-22 — the first time anyone has seen the
Marketplace detail pane render, because until that fix it was not in the tab at all. Two defects in
`PackageDetailPane`, filed together because they share a surface. **They are recorded at different
confidence: §3.1 has a proven root cause, §3.2 is an observation whose mechanism is not established.**

## 1. Reproduction

1. Register a `luarocks` tool (*Settings ▸ Languages & Frameworks ▸ Lua ▸ Toolchain ▸ Auto-Discover*).
2. Open **LuaRocks Packages**, search `penlight`, select any result.

## 2. Expected vs actual

- **Expected**: the card shows a summary, `License: MIT`, a clickable homepage link, and a
  `Dependencies:` list — or, if metadata cannot be fetched, says so and clears the placeholders.
- **Actual**: the strip reads `License:` (nothing) `Homepage: (loading)` and stays that way
  indefinitely. The description area is blank and there is no `Dependencies:` label at all.

## 3. Root cause

### 3.1 The failure path returns before clearing the placeholders — **confirmed**

`resetDetailBody` (`PackageDetailPane.kt:295`) seeds the loading state:

```kotlin
description.text = UIUtil.toHtml("Loading…")
licenseLabel.text = ""
homepageButton.text = "(loading)"
```

`applyMetadata` then **early-returns on failure, before touching either control**:

```kotlin
if (meta == null) {
    description.text = UIUtil.toHtml("Could not load metadata.")
    return                      // licenseLabel stays "", homepageButton stays "(loading)"
}
```

So a `luarocks show` that returns nothing leaves `(loading)` on screen forever. Note what
`(loading)` actually *is*: not a status message but the **caption of the homepage link button**
(`:298`), built into a four-component `HorizontalLayout` strip (`:157-163`) as
`JBLabel("License:") licenseLabel JBLabel("Homepage:") homepageButton`. That is why the row reads as
one run-on field, and why `License:` renders as a label pointing at empty space.

The link is also **inert while it says `(loading)`** — `openHomepage()` (`:303`) early-returns
because `currentHomepage` is null — so it presents as clickable and does nothing.

### 3.2 The description and dependency regions do not render — **observed, mechanism unproven**

The detail card is a `BorderLayout` (`:150-155`) with the meta strip NORTH, the description scroll
pane CENTER, and the deps pane SOUTH. On screen only NORTH appears: **neither `Loading…` nor
`Could not load metadata.` is ever visible**, and the `Dependencies:` label (`:168`) is absent.

Both strings are set unconditionally on the paths that run, so their absence is not explained by the
fetch outcome. Something about the card's layout or the `JBHtmlPane` is at fault. **This was not
diagnosed** — it is recorded from a zoomed screenshot, and anyone picking this up should start by
proving where the CENTER and SOUTH regions went rather than trusting this guess.

## 4. Fix strategy

1. **Clear the placeholders on every terminal path** (§3.1). Move the reset out of the `meta == null`
   early-return, or give the card an explicit failure state that sets `licenseLabel` and
   `homepageButton` alongside the description.
2. **Stop using a control's caption as a loading indicator.** A link labelled `(loading)` is a
   platform UX violation on two counts — see the engineering contract §6, *no identifiers or
   placeholders as display text* and *empty states are written, not defaulted*. Prefer disabling the
   link and showing a real progress indicator, and render an absent licence as `—`/`Unknown` rather
   than a dangling `License:` label.
3. **Diagnose §3.2 before touching the layout.** It may be one cause with §3.1 or entirely separate.

## 5. Test strategy

§3.1 is unit-testable without an IDE: drive `applyMetadata(null, token)` on a constructed pane and
assert `homepageButton.text` is no longer `"(loading)"` and `licenseLabel.text` is not blank. Mutation-
check by restoring the early return — the test must go red, per the `mutation-proof` skill.

§3.2 needs the `verify-in-ide` screenshot pass; a green unit test cannot see a region that does not
paint. Note that BUG-449's regression test had to dispose its panel inside the test to avoid
perturbing later tests — any new test constructing `PackageDetailPane` should do the same.

## 6. Notes

- **This blocks retro-verification of two closed bugs.** [[bug-report|BUG-363]] (summary font) and
  [[bug-report|BUG-368]] (dependencies as a `JBList`) were closed on code evidence with a screenshot
  still owed; both live in the regions §3.2 says never render, so that debt cannot be discharged
  until this is fixed.
- Screenshots: `~/.cache/claude-scratch/lunar/90c40f9b/shots/zoom-loading.png` and
  `shots/after/09-detail-loaded.png`.
