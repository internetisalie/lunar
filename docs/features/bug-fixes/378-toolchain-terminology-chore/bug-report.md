---
id: "BUG-378"
title: "Terminology unification pass across TOOLING/ROCKS/REDIS user-facing strings (chore)"
type: "bug"
parent_id: "BUG"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-378: Terminology unification pass across user-facing strings (chore)

This is a **chore** (naming/UX consistency pass), not a defect — no behavior changes, no
class renames. It is **string-only** work.

> **GATE — the sweep must NOT start until the owner ratifies the vocabulary in §5.**
> §5's decision table is **PROPOSED — awaiting owner ratification**. The audit (§1) and the
> scope mechanics (§6) are ready now; the rename itself is blocked on sign-off.

## 1. Reproduction / Audit

Walk the toolchain UI surface end to end: *Settings → Languages & Frameworks → Lua → Toolchain*
and *Lua Project*, *Tools → Lua Toolchain*, the status-bar widget, a Lua run configuration, the
New Project wizard, and the batch/provision dialogs. Five overlapping nouns name (roughly) the
same machinery. Every string below re-verified in code 2026-07-17.

### 1a. Bundle vs. hardcoded — the migration baseline

- A resource bundle exists: `src/main/resources/net/internetisalie/lunar/LuaBundle.properties`
  (101 keys) with accessor `net.internetisalie.lunar.LuaBundle` (`LuaBundle.kt:15`).
- **None** of the affected keys are toolchain/rocks/redis terminology keys — the whole surface
  is **hardcoded string literals**. Across `toolchain/`, `provision/`, `rocks/`, `run/`, `ui/`,
  `health/` there are only **4** `LuaBundle.message(...)` calls, and they are for unrelated
  luacheck/application labels. Every string this chore touches is a literal in Kotlin or a
  `text=`/`displayName=` attribute in `plugin.xml`.
- **Migration opportunity (noted, NOT bundled in — see §6):** these strings should eventually
  move into `LuaBundle.properties`. That is a separate refactor; this chore edits the literals
  in place so the diff is a pure rename and is reviewable string-by-string.

### 1b. Per-surface term inventory (verified `file:line`)

| Term | Surface | Current string | Location |
|------|---------|----------------|----------|
| **toolchain** | Settings page title | `displayName="Toolchain"` | `META-INF/plugin.xml:564`; also literal `displayName = "Toolchain"` in `toolchain/ui/LuaToolchainConfigurable.kt:31` |
| **toolchain** | Tools-menu group | `text="Lua Toolchain"` | `META-INF/plugin.xml:790` |
| **toolchain** | Tools-menu action | `text="Provision Lua Toolchain…"` | `META-INF/plugin.xml:794` |
| **toolchain** | Tools-menu action | `text="Change Toolchain Versions…"` | `META-INF/plugin.xml:799` |
| **toolchain** | Tools-menu diagnostics action | `text="Lua: Toolchain Diagnostics"` | `META-INF/plugin.xml:750` |
| **toolchain** | Provision dialog title | `title = "Provision Lua Toolchain"` | `toolchain/provision/LuaProvisionDialog.kt:52` |
| **toolchain** | Recreate confirm-dialog title | `"Recreate Lua Toolchain"` | `toolchain/provision/LuaToolchainActions.kt:58` |
| **toolchain** | Remove confirm-dialog body+title | `"Remove the Lua toolchain environment at …"` / `"Remove Lua Toolchain"` | `toolchain/provision/LuaToolchainActions.kt:87,89` |
| **environment** | Status-bar widget factory name | `getDisplayName() = "Lua Environment"` | `toolchain/ui/LuaEnvStatusBarWidgetFactory.kt:13` |
| **environment** | Status-bar popup title | `BaseListPopupStep<Any>("Lua Environment", …)` | `toolchain/ui/LuaEnvStatusBarWidget.kt:82` |
| **environment** | Status-bar empty text | `NO_ENV_TEXT = "No Lua env"` | `toolchain/ui/LuaEnvStatusBarWidget.kt:113` |
| **environment** | Status-bar add item | `ADD_ENV_TEXT = "Add environment…"` | `toolchain/ui/LuaEnvStatusBarWidget.kt:114` |
| **environment** | Provision dialog browse title | `.withTitle("Environment Directory")` | `toolchain/provision/LuaProvisionDialog.kt:104` |
| **environment** | Recreate action menu text | `text="Recreate Environment"` / `DumbAwareAction("Recreate Environment")` | `META-INF/plugin.xml:802`; `toolchain/provision/LuaToolchainActions.kt:45` |
| **environment** | Remove action menu text | `text="Remove Environment"` / `DumbAwareAction("Remove Environment")` | `META-INF/plugin.xml:806`; `toolchain/provision/LuaToolchainActions.kt:75` |
| **environment** | Matrix action warning | `"No Lua environments to run the matrix against"` | `rocks/matrix/RunMatrixAction.kt:38` |
| **runtime** | Lua Project page group+row | `group("Resolved Runtime")` / `row("Runtime:")` | `toolchain/ui/LuaProjectConfigurable.kt:84,85` |
| **runtime** | Provision dialog row | `row("Runtime:")` | `toolchain/provision/LuaProvisionDialog.kt:164` |
| **runtime** | Batch dialog column | `ColumnInfo<…>("Runtime")` | `toolchain/provision/LuaBatchProvisionDialog.kt:76` |
| **interpreter** | Run-config editor labels | `addLabeledComponent("Interpreter", …)` + `("Interpreter arguments", …)` | `run/LuaRunConfiguration.kt:325,330` |
| **interpreter** | Test-config editor label | `addLabeledComponent("Interpreter", …)` | `run/test/LuaTestRunConfiguration.kt:294` |
| **interpreter** | Test-config validation | `RuntimeConfigurationException("Interpreter is not defined")` | `run/test/LuaTestRunConfiguration.kt:259` |
| **interpreter** | Wizard group title | `group("Interpreter")` (child row is `row("Runtime:")` — collides in-surface) | `rocks/init/LuaRocksGeneratorPeer.kt:68,69` |
| **interpreter** | Runtime combo renderer | `append("Unknown Interpreter ", …)` (in a class literally named `LuaRuntimeComboBox`) | `toolchain/ui/LuaRuntimeComboBox.kt:177` |
| **tool** | Inventory empty text | `EMPTY_TEXT = "No tools registered — use Add, Auto-Discover or Provision"` | `toolchain/ui/LuaToolchainInventoryTable.kt:38` |
| **tool** | Inventory browse title | `.withTitle("Select Lua Tool Binary")` | `toolchain/ui/LuaToolchainInventoryTable.kt:81` |

### 1c. In-surface collisions (the sharpest friction)

- **"Environment" means two things in the same dialog family.** `LuaRecreateToolchainAction`'s
  menu text is `"Recreate Environment"` (`LuaToolchainActions.kt:45`) but its own confirm dialog
  is titled `"Recreate Lua Toolchain"` (`:58`). Same split on Remove (`:75` vs `:89`).
- **"Interpreter" wraps "Runtime" in the wizard.** `LuaRocksGeneratorPeer.kt:68-69` nests
  `row("Runtime:")` inside `group("Interpreter")`.
- **The `run/` env-vars label collides with the toolchain "environment".** The run-config editor
  labels the env-**vars** field `"Environment"` (`LuaRunConfiguration.kt:329`; test-config
  `LuaTestRunConfiguration.kt:297`), colliding with the toolchain sense of "environment" (a
  provisioned rock tree). The platform-standard fix is `"Environment variables"`.

## 2. Expected vs Actual Behavior

- **Expected**: one coherent vocabulary — the same thing is called the same word on every
  surface, and no single word means two different things in one dialog.
- **Actual**: five interchangeable nouns across settings, menus, dialogs, widgets, run configs,
  and the wizard; the lifecycle actions even mix two of them between the menu item and its own
  confirmation dialog.

## 3. Context / Environment

- **Confidence**: high — every string above re-verified at the cited `file:line` (2026-07-17).
- User-reported friction ("what is the difference between my toolchain, my environment, and my
  runtime?"), not a functional bug.

## 4. Cross-reference scope boundaries (corrected 2026-07-17)

- **ROCKS-16** (`docs/features/rocks/16-package-browser-redesign/`) is **`done`** — it already
  landed the browser redesign, so the old browser strings this report's earlier draft cited
  ("Enter a package name to search.", "No packages found", "N package(s) found.", "(no package
  selected)") **no longer exist** as user-visible literals. `grep` now finds "No packages found"
  only in a *comment* (`rocks/browser/BrowserCliError.kt:8`). **Package/rock browser strings are
  out of scope — do not touch them.**
- **TOOLING-08** (`docs/features/tooling/08-settings-restructure/`) is **`done`** — the settings
  restructure shipped. The remaining settings-page literals (`displayName="Toolchain"`,
  `"Resolved Runtime"`, `"Runtime:"`, inventory `EMPTY_TEXT`) were **not** renamed by it and are
  therefore in scope for this chore.
- **Still-live ROCKS proper nouns** (keep — they reference luarocks.org concepts): "Publish Rock
  to LuaRocks…" (`rocks/publish/PublishRockAction.kt:28,53,62`). No change.
- **Related label bugs** that should ride the same ratified vocabulary once approved:
  [[../370-provision-dialog-raw-kind-labels/bug-report|BUG-370]] (raw kind ids in the provision
  dialog), [[../373-lls-kind-missing-from-registry/bug-report|BUG-373]] (missing LLS kind → raw
  id fallback). Coordinate wording, but they are separate fixes.

## 5. PROPOSED canonical vocabulary — **awaiting owner ratification**

> This table is a **proposal**. **Do not begin the §6 sweep until the owner approves it.**
> Ratification (or an amended table) is the explicit precondition for this chore.

### 5a. The five canonical nouns

| Canonical term | Definition | Replaces (today) |
|----------------|------------|------------------|
| **runtime** | the Lua interpreter executing code (`lua`, `luajit`, `tarantool`) + its version/level | "interpreter" everywhere user-visible |
| **tool** | one external binary (luarocks / luacheck / stylua / busted / LLS / redis-server) | (already correct — keep) |
| **toolchain** | the collective registry/bindings of tools + runtimes (the settings facility, Tools-menu group) | (already correct — keep) |
| **environment** | a provisioned per-project rock tree / env (root dir + runtime + tools, the TOOLING-02 record) | (already correct — keep; disambiguate the run-config env-vars collision) |
| **package / rock** | a LuaRocks artifact ("rock" only in luarocks.org proper nouns) | (browser owned by ROCKS-16 — out of scope) |

### 5b. Per-surface before → after (only rows that change)

| Surface | Location | Before | After (proposed) |
|---------|----------|--------|------------------|
| Run-config editor | `run/LuaRunConfiguration.kt:325` | `"Interpreter"` | `"Runtime"` |
| Run-config editor | `run/LuaRunConfiguration.kt:330` | `"Interpreter arguments"` | `"Runtime arguments"` |
| Run-config editor (env-vars collision) | `run/LuaRunConfiguration.kt:329` | `"Environment"` | `"Environment variables"` |
| Test-config editor | `run/test/LuaTestRunConfiguration.kt:294` | `"Interpreter"` | `"Runtime"` |
| Test-config validation | `run/test/LuaTestRunConfiguration.kt:259` | `"Interpreter is not defined"` | `"Runtime is not defined"` |
| Test-config (env-vars collision) | `run/test/LuaTestRunConfiguration.kt:297` | `"Environment"` | `"Environment variables"` |
| Wizard group | `rocks/init/LuaRocksGeneratorPeer.kt:68` | `group("Interpreter")` | `group("Runtime")` (child row `"Runtime:"` at :69 already correct) |
| Runtime combo renderer | `toolchain/ui/LuaRuntimeComboBox.kt:177` | `"Unknown Interpreter "` | `"Unknown Runtime "` |
| Recreate action (menu vs dialog split) | `plugin.xml:802` + `LuaToolchainActions.kt:45` | `"Recreate Environment"` | keep `"Recreate Environment"` **and** align dialog title `:58` from `"Recreate Lua Toolchain"` → `"Recreate Environment"` |
| Remove action (menu vs dialog split) | `plugin.xml:806` + `LuaToolchainActions.kt:75` | `"Remove Environment"` | keep `"Remove Environment"` **and** align dialog title `:89` from `"Remove Lua Toolchain"` → `"Remove Environment"` |

Surfaces that already match the canonical table and **do not change**: the settings page title
`"Toolchain"` (toolchain sense), the Tools-menu group `"Lua Toolchain"`, provision-dialog title
`"Provision Lua Toolchain"`, the `"Resolved Runtime"` group and `"Runtime:"` rows, batch dialog
`"Runtime"` column, inventory `"No tools registered…"` / `"Select Lua Tool Binary"`, and the
status-bar `"Lua Environment"` / `"No Lua env"` / `"Add environment…"` strings.

## 6. Sweep mechanics (post-ratification)

1. **String-only** — no class/file/action-id renames. `LuaRuntimeComboBox`, `LuaProvisionDialog`,
   `LuaRecreateToolchainAction`, action `id=`s stay as-is. Only `text=`/`displayName=`/`title`/
   `withTitle(...)`/`addLabeledComponent(...)`/`group(...)`/literal-message arguments change.
2. **LuaBundle migration** — a follow-up, **NOT bundled into this chore** (§1a). Keeping the edits
   as in-place literal changes makes each rename reviewable against §5b line-by-line.
3. **Estimated diff** — ~10 changed literals across 6 files: `run/LuaRunConfiguration.kt` (3),
   `run/test/LuaTestRunConfiguration.kt` (3), `rocks/init/LuaRocksGeneratorPeer.kt` (1),
   `toolchain/ui/LuaRuntimeComboBox.kt` (1), `toolchain/provision/LuaToolchainActions.kt` (2 dialog
   titles). Roughly one-line-per-surface edits; ~1–2 h including build.
4. **Test impact — LOW.** `grep` across `src/test`, `src/integrationTest`, `src/redisIntegrationTest`
   for every affected literal ("Provision Lua Toolchain", "Recreate Environment", "Remove
   Environment", "No Lua env", "Add environment", "Interpreter is not defined", "No tools
   registered", "Select Lua Tool Binary", "Unknown Interpreter", "Lua Environment", "Environment
   Directory") returns **0 assertions** — no test currently pins any of these strings, so the
   rename breaks no test. Any new UI test written meanwhile must assert on the post-rename strings.
5. **Docs impact** — `human-verification-checklists.md` / `design.md` under `docs/features/rocks/14`
   and `/15`, and `docs/features/tooling/`, quote some old strings; update the ones that quote a
   changed literal (e.g. any "Recreate Lua Toolchain" checklist step). ROCKS-16-owned browser
   strings stay untouched.

## 7. Other Notes

- Same "capability/label lives in code, mismatched with its own dialog" shape as
  [[../370-provision-dialog-raw-kind-labels/bug-report|BUG-370]] and
  [[../373-lls-kind-missing-from-registry/bug-report|BUG-373]] — worth a single coordinated wording
  pass once the vocabulary is ratified.


## 8. Resolution (2026-07-18)

**Done.** Owner ratified the §5 vocabulary as-is; the §6 string sweep landed **10 literal changes across 5 files** (all `interpreter`→`runtime` user-visible, plus the two `Environment`→`Environment variables` run-config disambiguations and the two Recreate/Remove dialog-title alignments):
- `run/LuaRunConfiguration.kt` — `Runtime` / `Runtime arguments` / `Environment variables`
- `run/test/LuaTestRunConfiguration.kt` — `Runtime` / `Runtime is not defined` / `Environment variables`
- `rocks/init/LuaRocksGeneratorPeer.kt` — `group("Runtime")`
- `toolchain/ui/LuaRuntimeComboBox.kt` — `Unknown Runtime`
- `toolchain/provision/LuaToolchainActions.kt` — dialog titles `Recreate Environment` / `Remove Environment`

Docs: one tester-facing checklist label updated (`run-02 human-verification-checklists.md`, "Interpreter arguments"→"Runtime arguments"). String-only, no class/action-id renames, no LuaBundle migration (deferred per §6.2). Gate: full cache-defeated suite **2224/0/1 unchanged** (zero test impact, as predicted — no test pinned any changed literal).

**Out of scope / observed:** the RUN-04/RUN-02/api-reference docs still quote `ExecutionException("Interpreter is not defined")` for the **main** `LuaRunConfiguration` — a different code path that MAINT-24 already superseded with "No Lua runtime is configured…"; that is pre-existing MAINT-24 doc drift, not this chore.