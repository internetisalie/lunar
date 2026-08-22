---
id: ANALYSIS-02
title: "02: Settings Panel Integration"
type: feature
status: "superseded"
priority: "medium"
parent_id: ANALYSIS
folders: ["[[features/analysis/requirements|requirements]]"]
---

# 02: Settings Panel Integration

> **⚠️ Superseded (2026-07-09) — shipped, then removed.** ANALYSIS-02's deliverable was a dedicated
> **LuaCheck** settings page (`analysis/luacheck/LuaCheckSettingsPanel`). That page was deleted by
> [TOOLING-06](../../tooling/06-settings-ui/requirements.md) — whose `design.md:41` names it
> **replaced** ("whole page folds in") in its §1 prior-art disposition list, and whose
> `TOOLING-06-08` made its absence a *tested invariant* — after [TOOLING-05](../../tooling/05-consumer-migration/requirements.md) had already
> deleted its backing `LuaCheckSettings` service. Every user-facing capability it carried survives
> somewhere else, so this is a supersession and not a regression; the residue that did **not**
> survive is enumerated below rather than left implied. Counted out of remaining work.

Configure Luacheck from the IDE: where the binary is, what arguments it gets, which `--std` it
checks against, whether it runs at all, and at what severity its findings surface.

## The scope verdict — (b) superseded, with a named residue

The brief offered three readings. The evidence settles it, and the attribution needs one correction.

**Not (a) "delivered inside the toolchain configurable".** There is no Luacheck settings panel. The
word *Luacheck* appears in the settings surface as a **group title on two other pages** — one row
each — and nothing else:

- `toolchain/ui/LuaToolchainConfigurable.kt:58-68` — `group(LuaBundle.message("luacheck.name"))`
  with a single *Arguments* `expandableTextField`, on *Settings | Languages & Frameworks | Lua |
  Toolchain*.
- `toolchain/ui/LuaProjectConfigurable.kt:91-93` — `group("Luacheck")` with the project override
  field, on *… | Lua | Lua Project*.

A "settings panel" whose entire surface is one text field on someone else's page is not the
chartered feature; calling it delivered would be reading the requirement off whatever the code
happens to do.

**(b) superseded is the right verdict, but by TOOLING-05/06, not TOOLING-08.** The brief points at
[TOOLING-08](../../tooling/08-settings-restructure/requirements.md); the deletions predate it:

| Commit | Date | What it removed |
| :-- | :-- | :-- |
| `9cb049bc` (TOOLING-05 Ph. 1) | 2026-07-09 | `LuaCheckSettings.kt` — the `@State("LuaCheckSettings")` app service holding `executablePath` + `arguments`, and its `applicationService` registration. Panel slimmed to the arguments row. |
| `e9bcd9fb` (TOOLING-06 Ph. 3) | 2026-07-09 | `LuaCheckSettingsPanel.kt` and its `<applicationConfigurable>` registration ("remove legacy Lua Tools/Rocks/Check pages"). |

TOOLING-08 only *restructured* what TOOLING-06 had already absorbed (common/advanced binding split,
platform-target control, inherit placeholders). Recording ANALYSIS-02 as superseded-by-TOOLING-08
would misdate the event by two features.

**(c) "partly lost" is real but subordinate.** Four things left the product with nothing taking
their place, and none is recorded in any ANALYSIS document: the CLI-options documentation link
(`-14`), a settings-search route to the arguments field (`-13`), migration of previously-saved
Luacheck settings (`-08`), and three now-orphaned `LuaBundle` keys (`luacheck.executable`,
`luacheck.download`, `luacheck.settings.execution` — `LuaBundle.properties:81-86`, no remaining
reference in `src/main/kotlin`). Each is a row below, not a separate verdict.

**What the feature's own docs said meanwhile.** Until this rewrite, `requirements.md` was three
lines of placeholder at `status: done`, and the epic's table still read *"### ANALYSIS-02 — Status:
**Implemented**"* while the class it once named had been deleted for six weeks. TOOLING-06 never
back-referenced ANALYSIS-02 — `git grep ANALYSIS-02 docs/` finds only this feature and its epic.
That silence, not the deletion, is the actual defect this document closes.

## How these requirements were derived

**Not from Lunar's implementation.** A specification read off its own code cannot fail — the defect
that left [[DEBUG-07]] marked shipped for months ([[BUG-450]] §4). The rows come from four external
sources, and Lunar was checked against them afterwards:

1. **The IntelliJ settings contract.** Every obligation `Configurable` / `SearchableConfigurable` /
   `BoundConfigurable` impose is a question this feature must answer: buffered editing
   (`isModified` / `apply` / `reset`), teardown (`disposeUIResources`), identity and search
   (`getId`, the `searchableOptions` index), help (`getHelpTopic`), and the app-vs-project scope
   split expressed by `<applicationConfigurable>` / `<projectConfigurable>` over a
   `PersistentStateComponent`. Gating behaviour was read from platform source, not assumed —
   `ExternalToolPass.java:100-115` (intellij-community) filters external annotators by
   `profile.isToolEnabled(HighlightDisplayKey.find(pairedShortName))`, which is what makes `-09`
   a *delivered* requirement rather than a missing control.
2. **What Luacheck exposes that a user would want to set.** Enumerated from the option surface this
   repo already vendors for `.luacheckrc` (`resources/jsonschema/luacheck-config.schema.json`):
   `std`, `globals` / `read_globals`, `ignore` / `enable`, `exclude_files` / `include_files`,
   `max_line_length` and friends, `cache`, `unused*`, `compat`, `module`. Plus the CLI-only knobs:
   binary location, free-form extra arguments, `--config PATH`.
3. **The prior art this feature was copied from.** EmmyLua's LuaCheck page, still recoverable at
   `git show 1a0ced89^:src/main/java/net/internetisalie/lunar/analysis/luacheck/LuaCheckSettingsPanel.java`
   (Apache-2.0, tangzx header): a `TextFieldWithBrowseButton` for the executable, a
   `RawCommandLineEditor` for arguments, a *Download LuaCheck binary* link and a *command-line
   options* link to `luacheck.readthedocs.io`. That is the concrete bar ANALYSIS-02 was written to.
4. **[`docs/engineering-contract.md`](../../../engineering-contract.md) §6 (USER INTERFACE)** for
   the acceptance criteria on label text, casing, colons, mnemonics and the screenshot gate.

Where a capability is unimplemented, the rows distinguish **Lunar has no control for it** from
**the user cannot do it** — several are delivered by the platform or by an adjacent Lunar surface,
and conflating the two would manufacture gaps that are not there.

## Requirements & Status

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `ANALYSIS-02-01` | **A discoverable Luacheck configuration surface** | **M** | **Superseded** | The dedicated page is gone (`e9bcd9fb`) and its absence is asserted by `LuaToolchainConfigurableTest.testLegacyConfigurablesAbsent_TC2` via `LEGACY_CONFIGURABLE_IDS`. Configuration now lives as a one-row *Luacheck* group on two general pages. Deliberate, and the trade is real — but nothing in Settings is named for Luacheck any more except the *inspection*. |
| `ANALYSIS-02-02` | **Executable location configurable** | **M** | **Full** | Strictly better than the superseded single path field: the *Toolchain* inventory table registers `luacheck` binaries by Add / Auto-Discover / Provision… with kind, version, origin and health, and *Lua Project* binds one per project. `LuaToolResolver.resolve(project, "luacheck")` is the only lookup `newLuaCheckCommandLine` performs. |
| `ANALYSIS-02-03` | **Extra arguments configurable (app default)** | **M** | **Full** | *Toolchain* → *Luacheck* → *Arguments*, an `expandableTextField` bound to the kind option `luacheck.arguments` (`LuaKindOptionKeys.LUACHECK_ARGUMENTS`). Tokenized with `ParametersListUtil.parseToArray`, so quoting behaves like a real command line. |
| `ANALYSIS-02-04` | **Per-project override with visible inheritance** | **S** | **Full** | *Lua Project* → *Luacheck* → *Arguments*; empty means inherit. `effectiveKindOption` resolves project-over-app, and the app default is surfaced as the field's `emptyText` rather than silently applied. This capability did **not** exist before the move — the superseded page was application-scoped only. |
| `ANALYSIS-02-05` | **Buffered edit semantics** | **M** | **Full** | Both pages are `BoundSearchableConfigurable`; each override of `isModified` / `apply` / `reset` is exercised for the Luacheck field specifically. Apply publishes on `LuaToolchainListener.TOPIC` exactly once, so a changed argument re-runs analysis without an IDE restart. |
| `ANALYSIS-02-06` | **UI resources released** | **M** | **Full** | Both pages subscribe with `messageBus.connect(panelDisposable)`, so the toolchain-event subscription dies with `disposeUIResources()`. No hard `Project` / `Editor` reference is retained past the panel — contract §4 satisfied. |
| `ANALYSIS-02-07` | **Settings persist, project scope is VCS-shareable** | **M** | **Full** | `LuaToolchainRegistry` (`@State`, app) and `LuaToolchainProjectSettings` (`@State`, project → `.idea/lunar.xml`). Note the serialization hazard the contract warns about is *dodged by construction*: the value lives in a `MutableMap<String, String>` under the **string** key `"luacheck.arguments"`, so renaming the Kotlin constant `LUACHECK_ARGUMENTS` is safe — changing its literal value is what would orphan saved arguments. |
| `ANALYSIS-02-08` | **Previously-saved settings survive the move** | **S** | **Won't** | `LuaCheckSettings` was `@State(name = "LuaCheckSettings", storages = [Storage("lunar.xml")])` with `executablePath` (default `/usr/local/bin/luacheck`) and `arguments`. It was deleted in `9cb049bc` with **no migration** — `git grep LuaCheckSettings src/main` returns nothing. Those elements are silently ignored on load (`LuaSettingsSerializationTest.projectStateToleratesStaleXmlTags` covers tolerance, not transfer). Deliberate: TOOLING-05 Ph. 5 declared a clean break, and there is no external install base. Recorded because "Won't" and "nobody noticed" are different claims. |
| `ANALYSIS-02-09` | **Luacheck can be turned off** | **M** | **Full** | *Not implemented by Lunar, and not missing.* `LuaCheckInspection` (`shortName="LuaCheck"`, `unfair="true"`, `enabledByDefault="true"`) implements `ExternalAnnotatorBatchInspection`, and `LuaCheckAnnotator.getPairedBatchInspectionShortName()` returns its short name — so `ExternalToolPass` skips the annotator when the inspection is off in the profile. The user gets scope-aware enablement (per profile, per scope) that the old page's absent checkbox never offered. |
| `ANALYSIS-02-10` | **`--std` follows the project's Lua target** | **M** | **Partial** | `resolveArguments` appends `--std <target.getLuacheckStd()>` from `LuaProjectSettings.state.getTarget()` (TARGET-05) — 5.1→`lua51` … 5.5→`lua54`, LuaJIT→`luajit`, Redis→`redis7`. Right behaviour, no direct control, and it is appended **after** the user's arguments: typing `--std max` yields `--std max --std lua54` on the command line, because `dedupePairs` collapses only *identical* pairs (`LuaCheckCommandLineTest."test dedupePairs keeps repeated value tokens across distinct pairs"`). One of the two is silently ignored. **Which one requires running Luacheck to settle** — argparse last-wins is expected, not verified; no Luacheck binary is present on this host. |
| `ANALYSIS-02-11` | **Finding severity configurable** | **S** | **Not Implemented** | `LuaCheckAnnotator.applyProblem` hardcodes `HighlightSeverity.WARNING`, and `applyFailure` does the same for launch/timeout/crash. The platform idiom is to read the profile: `Pep8ExternalAnnotator.collectInformation` passes `profile.getErrorLevel(key, psiFile)` into its results. Consequence: changing the LuaCheck inspection's severity in *Settings \| Inspections* has **no effect**, and Luacheck's own `E`-codes (syntax errors) render identically to `W`-codes. |
| `ANALYSIS-02-12` | **Per-code enable / ignore list** | **S** | **Not Implemented** | Luacheck's `ignore` / `enable` / `unused` / `unused_args` are among the most-adjusted knobs it has. `LuaCheckInspection` is a bare `LocalInspectionTool` with no `getOptionsPane()`, so there is no UI; the only route is typing `--ignore 611` into the Arguments field, which is application- or project-wide and invisible from the inspection that produced the warning. The comparator ships one: `Pep8ExternalAnnotator` reads `inspection.ignoredErrors`. |
| `ANALYSIS-02-13` | **The setting is findable by search** | **M** | **Not Implemented** | Measured live, 2026-07-09 (TOOLING-06 `implementation-plan.md:126-128`): page-level search finds *Toolchain*, but **field-level search for "luacheck arguments" jumps to the LuaCheck *inspection*, not the Arguments field**. Cause is stated in `build.gradle.kts:92-96` — `buildSearchableOptions = false`, disabled because the headless index build is flaky in CI. The superseded page was a `SearchableConfigurable` whose `getId()` was literally `"luacheck"`. This is the sharpest cost of the move. |
| `ANALYSIS-02-14` | **Links to Luacheck's own documentation** | **C** | **Not Implemented** | The old page carried two: *Download LuaCheck binary* → `github.com/lunarmodules/luacheck`, and *command-line options* → `luacheck.readthedocs.io/en/stable/cli.html`. TOOLING-06 `requirements.md:107-109` deliberately dropped the download link ("*Provision…* supersedes it") — sound, `LuaToolCatalog` provisions luacheck. It says nothing about the **options** link, which is the one that matters for a free-form Arguments field and is now nowhere in the product. A `comment()` under the field is the platform-idiomatic home. |
| `ANALYSIS-02-15` | **Discoverable when Luacheck is absent or broken** | **S** | **Full** *(live-unverified)* | `LuaToolEditorNotificationProvider` treats `luacheck` as *engaged* when the inspection is enabled (`isLuaCheckInspectionEnabled`) and shows a warning banner naming the kind and reason, with a link that opens `LuaToolchainConfigurable`. Covered headlessly by `LuaToolEditorNotificationProviderTest.testBrokenEngagedLuaCheckBanner` / `testNotEngagedNoBanner`. That the banner *renders* — and that its link lands on the page containing the Arguments field — is a screenshot-gate claim, not a unit-test one. |
| `ANALYSIS-02-16` | **Panel text conforms to contract §6** | **S** | **Partial** | Three divergences, all in surfaces this feature owns. (i) The row label is `LuaBundle.luacheck.arguments = "Arguments"` — **no colon**, against §6 COLONS; the Kotlin UI DSL does not add one (`PanelImpl.row(String)` → `createLabel` → `JLabel(replaceMnemonicAmpersand(text))`), and every sibling row on both pages (`Active environment:`, `Runtime:`, `Default server URL:`) has one. (ii) No mnemonic (`&`), against §6 MNEMONICS. (iii) The two pages spell the product differently — `LuaBundle.luacheck.name = "LuaCheck"` on *Toolchain*, hardcoded `group("Luacheck")` on *Lua Project*; upstream is `Luacheck`. |
| `ANALYSIS-02-17` | **Help button resolves** | **C** | **Not Implemented** *(live-unverified)* | Both pages pass `helpTopic = "settings.lua.toolchain"` / `"settings.lua.project"` to `BoundSearchableConfigurable`, which makes the Settings dialog's **?** button live, but no help topic or `WebHelpProvider` is registered anywhere in `META-INF/*.xml`. The button is therefore expected to resolve to nothing. Marked unverified deliberately: the platform's fallback depends on the host IDE's help configuration and must be observed, not inferred. |
| `ANALYSIS-02-18` | **Point Luacheck at a specific config file** | **C** | **Won't** | `--config PATH` / `--no-config`. Luacheck already discovers `.luacheckrc` upward from its working directory, and `newLuaCheckCommandLine` sets `withWorkDirectory(file.parent)`, which is the behaviour users expect from every other editor integration. An explicit picker would create a second, silently-diverging source of truth against [ANALYSIS-05](../05-custom-rules-support/requirements.md). The escape hatch (type it into Arguments) exists. |
| `ANALYSIS-02-19` | **Structured editing of `.luacheckrc` options** | **C** | **Won't** | Controls for `globals`, `read_globals`, `exclude_files`, `max_line_length`, `compat`, `module`. These belong in the file, not in IDE settings: they are project properties that must travel in VCS and apply outside the IDE. Lunar already serves them where they live — `LuacheckrcSchemaProvider` gives `.luacheckrc` JSON-schema-backed completion and docs. Duplicating them in a panel would fork the configuration. |

## Verification

**Headless coverage of the surviving surface is real but narrow.** `LuaToolchainConfigurableTest`
covers `-03` and `-05` (`testLuacheckArgumentsApplyFiresTopicOnce_TC5`,
`testResetRestoresAppliedArguments_TC6`) and `-01`'s tested-absence invariant
(`testLegacyConfigurablesAbsent_TC2`). `LuaProjectConfigurableTest` covers `-04`
(`testProjectLuacheckOverrideRoundTrips_TC14`, `testInheritPlaceholdersRenderAppDefaults_TC10`,
`testInheritPlaceholdersWhenNoAppDefault_TC10b`). `LuaCheckCommandLineTest` covers `-02`/`-03`
end-to-end (`TC1`, `TC2`) and the dedupe rule underpinning `-10`. `LuaCheckInspectionGroupingTest`
covers `-09`'s registration and defaults (`testDefaultLevelWarningAndEnabled`,
`testAnnotatorPairedShortName`). `LuaToolEditorNotificationProviderTest` covers `-15`'s logic.

**None of that shows a control renders, aligns, or persists across a restart.** Contract §6 is
explicit — *"unit tests cannot observe alignment, spacing, elision, casing, or a component that was
silently re-parented"*, and every defect in the BUG-448/449 audit shipped through a green suite.
These rows need the `verify-in-ide` screenshot gate, not a test run: `-01` (is the *Luacheck* group
findable at all by a user who does not already know where it went), `-13` (search), `-15` (banner
rendering and link destination), `-16` (colon, mnemonic, casing, measured against the platform's own
page as §6 requires — not against judgement), `-17` (help button). The only live evidence on record
is the 2026-07-09 TOOLING-06 VNC pass, which signed off the *page structure* and explicitly logged
`-13` as a known miss; it never inspected the Luacheck group's own text.

**`-10` needs an executed check, not a live one.** Whether `--std max --std lua54` resolves to the
user's value or the target's is a property of Luacheck's argparse. No Luacheck binary exists on this
host (`which luacheck` → not found), so the row states the collision — which the code and the dedupe
test do establish — and stops short of naming the winner.

**Recorded nowhere else in the repo, and with no bug report:** `-10` (double `--std`), `-11`
(hardcoded severity), `-12` (no ignore-list UI), `-14` (the dropped options link), `-16` (label and
casing divergences), `-17` (dangling help topic), and the three orphaned `LuaBundle` keys. `-13` is
recorded, but only inside TOOLING-06's implementation plan as a non-blocking note. `-08` is recorded
only as a commit message.
