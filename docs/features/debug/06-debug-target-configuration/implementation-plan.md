---
id: "DEBUG-06-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "RUN-06"
folders:
  - "[[features/debug/06-debug-target-configuration/requirements|requirements]]"
---

# DEBUG-06: Implementation Plan

Sequences [design.md](design.md) into six phases. Every task names the file it creates or edits and
the design section it realizes; no task requires a design decision.

**Global preconditions.**
- Build and test only through `tooling/gce-builder/gce-builder.sh run …`; never `./gradlew` locally
  (`CLAUDE.md` → Build & test).
- `ktlintCheck` is a real gate. Format on the VM and rsync the result back — **never**
  `run "ktlintFormat ktlintCheck"` (BUG-445: the pairing cannot fail).
- Every new test method needs `@Test`. `BaseDocumentTest` is a plain `open class`, so an
  un-annotated `test*` method is never collected and never reported as skipped ([[BUG-461]] §1).
- The engineering contract's ≤3-argument cap applies to **private helpers too**. Every function
  introduced below is specified at ≤3 arguments; if a helper needs more state, it takes a
  `LuaTargetSpec`.

---

### Phase 1: The validation pipeline (no behaviour change) [Must]

- **Goal**: land the mechanism with the *existing* two conditions and *existing* severities, so the
  suite stays green and the diff that changes behaviour is Phase 2's alone.
- **Tasks**:
  - [ ] Create `src/main/kotlin/net/internetisalie/lunar/run/validation/LuaTargetSpec.kt` —
        the `LuaTargetSpec` data class and its two `of(...)` factories — realizes design §2.1.
  - [ ] Create `.../validation/LuaTargetProblem.kt` — `LuaTargetSeverity` (with `rank`) and
        `LuaTargetProblem` — realizes design §2.2.
  - [ ] Create `.../validation/LuaTargetCheck.kt` — the `fun interface` — realizes design §2.3.
  - [ ] Create `.../validation/LuaTargetValidator.kt` — `validate(spec, checks)` per §3.1 steps 1–4
        and `asException(problem)` — realizes design §3.1.
  - [ ] Create `.../validation/LuaTargetMessages.kt` with `RUNTIME_KIND_ID = "lua"` and the message
        table — realizes design §3.3. **Read the `runtime-capability` trap in §3.3 before writing
        `noRuntimeConfigured()`.**
  - [ ] Create `.../validation/LuaTargetChecks.kt` containing, for now, only `RUNTIME_MISSING`
        (severity `ADVISORY`, i.e. the *current* bare tier) and `SCRIPT_UNSET` (`WARNING`), plus
        `LOCAL_SCRIPT` and `TEST_TARGET` lists — realizes design §3.2 rows 1 and 3.
  - [ ] Edit `run/LuaRunConfiguration.kt` — land the **`envFilePaths` declaration only**, all three
        parts of design §2.8's first two code blocks: on `LuaRunConfigurationOptions` (alongside
        `:95-99`) the private `myEnvFilePaths` `StoredProperty` **and** the public
        `var envFilePaths: MutableList<String>` accessor; on `LuaRunConfiguration` the
        `EnvFilesOptions` supertype and its `override var envFilePaths: List<String>`.
        **This is a Phase 1 task, not a Phase 5 one**: §2.1's `LuaTargetSpec.of(configuration:
        LuaRunConfiguration)` reads `configuration.envFilePaths`, the property does not exist in
        `src/main` today (`grep -rn 'envFilePaths\|EnvFilesOptions' src/main/kotlin` → 0 hits), and
        without it Phase 1 does not compile. Do **not** substitute `envFilePaths = emptyList()` in the
        factory as a stopgap — TC-06-17b would then pass against a spec that silently discards the
        user's configuration.
        It is behaviour-neutral here: the default is an empty list, `CollectionStoredProperty.isEqualToDefault()`
        is `value.isEmpty()` (`platform/object-serializer/src/stateProperties/CollectionStoredProperty.kt:25-27`)
        and `KotlinAwareBeanBinding` skips every property equal to its default (`:83`), so nothing new
        is written to the persisted state until Phase 5 gives the user a way to set it.
        **Everything else in §2.8 stays in Phase 5** — the widget swap, `resetEditorFrom`/`applyEditorTo`,
        the §2.8.1 label fix, `startProcess()`'s `envFileVariables()` and check 8. Phase 1 declares the
        property; Phase 5 wires it.
  - [ ] Edit `run/LuaRunConfiguration.kt:275-285` — replace the body with the one-line delegation of
        design §2.6.
  - [ ] Create `src/test/kotlin/net/internetisalie/lunar/run/validation/LuaTargetValidationTest.kt`
        with the shared fixture helpers of design §9 and **TC-06-04a** only.
- **Exit criteria**: TC-06-04a passes; the pre-existing
  `TestLuaRunConfiguration.testCheckConfigurationThrowsWithoutRuntime` (`:177-187`) still passes
  unchanged (it asserts the supertype, so an `ADVISORY` still satisfies it); `testOptionsPersistence`
  in `TestLuaRunConfiguration.kt` (`:57-68`) still passes — the new `StoredProperty` must not disturb
  existing serialization; full suite green.

### Phase 2: The severity ladder — the headline [Must]

- **Goal**: `RuntimeConfigurationError` enters the codebase and a broken target refuses to launch.
  Closes [[BUG-455]].
- **Tasks**:
  - [ ] Edit `.../validation/LuaTargetChecks.kt` — raise `RUNTIME_MISSING` to `FATAL`; add
        `RUNTIME_UNUSABLE` (`FATAL`, fires on `!isUsable`, with the **three-way** message rule of
        design §3.2 — `!fileExists` → `runtimeMissing`, else `!executable` → `runtimeNotExecutable`,
        else `runtimeProbeFailed(path, health.reason)`; a two-way `if/else` mislabels a
        probe-failed tool as "not executable") — realizes design §3.2 rows 1–2.
  - [ ] Create `.../validation/LuaToolchainSettingsQuickFix.kt` and attach it to `RUNTIME_MISSING` —
        realizes design §3.8.
  - [ ] Edit `run/test/LuaTestRunConfiguration.kt:287-294` — route the runtime branch through
        `LuaTargetValidator` per design §2.6; leave the `testTarget` branch alone. **Expect the test
        configuration's behaviour to move, not just its message** — design §2.6's table: an empty
        `interpreter` with a project default now validates clean (today it errors), and an explicit
        path that does not exist now raises `FATAL` (today it passes). Both are intended
        (`DEBUG-06-06`, `-07`).
  - [ ] Edit `src/test/kotlin/net/internetisalie/lunar/run/TestLuaRunConfiguration.kt:177-187` —
        tighten `testCheckConfigurationThrowsWithoutRuntime` from
        `assertFailsWith<RuntimeConfigurationException>` to an exact-class assertion. **This is a
        required edit, not an optional one**: the existing assertion passes at every rung of the
        ladder and is blind to the requirement it claims to cover (requirements.md, Verification).
  - [ ] Add **TC-06-02a, TC-06-02b, TC-06-03a, TC-06-07a, TC-06-07b, TC-06-07c, TC-06-20a, TC-06-22a** to
        `LuaTargetValidationTest.kt` — design §9.
  - [ ] Check `src/test/kotlin/net/internetisalie/lunar/run/test/LuaTestRunConfigurationTest.kt:154`
        for an assertion on the old `"Runtime is not defined"` literal and update it to the shared
        message if present.
- **Exit criteria**: all eight TCs pass; `grep -rn RuntimeConfigurationError src/main` is non-empty
  for the first time; full suite green.

### Phase 3: Path and level checks, bounded [Must]

- **Goal**: the target's files are checked, and the per-keystroke I/O has a stated bound.
- **Tasks**:
  - [ ] Create `.../validation/LuaPathFacts.kt` — the `@Service(Service.Level.APP)` memo, `LuaPathFact`,
        `TTL_NANOS = 2_000_000_000L`, `MAX_ENTRIES = 64`, `of(path, nowNanos)`, `clear()` — realizes
        design §2.4 and §3.4 steps 1–5.
  - [ ] Edit `run/LuaRuntimeResolution.kt:41-48` — source `fileExists`/`executable` from
        `LuaPathFacts` instead of `File.exists()`/`File.canExecute()` — realizes design §2.4, closes
        `DEBUG-06-05`.
  - [ ] Edit `.../validation/LuaTargetChecks.kt` — add `SCRIPT_MISSING` (`FATAL`),
        `WORKDIR_MISSING` (`WARNING`), `SCRIPT_REACHABLE` (`WARNING`), `RUNTIME_LEVEL` (`WARNING`)
        with **every short-circuit listed in design §3.2**, the §3.5 comparison and the §3.6
        predicate. Fix the `LOCAL_SCRIPT` declaration order to §3.2's exact 1–8.
  - [ ] Add **TC-06-05a, TC-06-08a, TC-06-08b, TC-06-10a, TC-06-11a, TC-06-11b, TC-06-12a, TC-06-12b**
        to `LuaTargetValidationTest.kt` — design §9.
- **Exit criteria**: all eight TCs pass. `LuaTargetValidationTest` cleans `scratch` and calls
  `LuaPathFacts.getInstance().clear()` in `@AfterEach` (the memo is an **app**-level service and
  leaks across tests otherwise). Full suite green.

### Phase 4: The pre-spawn debug gate [Should]

- **Goal**: a debug launch onto a busy port or a broken plugin install fails before a process exists.
- **Tasks**:
  - [ ] Create `.../validation/LuaDebugPortProbe.kt` — realizes design §3.7. Use
        `reuseAddress = false` as documented, but **do not** rely on it as the mechanism (§3.7,
        measured).
  - [ ] Edit `run/LuaDebugRunner.kt:69-83` — add `internal fun checkDebugTargetReady(debugPort: Int,
        pluginLuaDirectory: VirtualFile?)` and call it as the first statement of `doExecute`, with
        `doExecute` doing the resolving (`environment.runProfile as? LuaRunConfiguration)?.debugPort`
        and `LuaFileUtil.getPluginVirtualDirectoryChild("lua")`) — realizes design §2.7. **The
        visibility is `internal`, matching design §2.7's declaration, and the two arguments are
        values not an `ExecutionEnvironment`** — that shape is what makes TC-06-13a/b/c and TC-06-15b
        constructible without `ExecutionEnvironmentBuilder`.
  - [ ] Edit `run/LuaDebuggerController.kt` — add `fun port(): Int = serverPort` next to
        `workingDirectory()` (`:72`) — realizes design §3.7.
  - [ ] Edit `run/LuaDebugProcess.kt:122` — `log.error` → `log.warn`; edit `:127` to name the port —
        realizes design §3.7.
  - [ ] Add **TC-06-13a, TC-06-13b, TC-06-13c, TC-06-15a, TC-06-15b** to `LuaTargetValidationTest.kt`
        — design §9. (`internal` is module-scoped, so the cross-package call compiles; prior art
        `PublishRockAuthFailureTest.kt:14`.)
- **Exit criteria**: all five TCs pass, closing `DEBUG-06-13` — a `Must` — with real coverage rather
  than a checklist item. RUN-04-03's existing `startProcess()` asset checks
  (`run/LuaRunConfiguration.kt:320-326`) are **unchanged** — confirm with `git diff` that those
  lines are untouched. Full suite green.

### Phase 5: Environment files [Should]

- **Goal**: env-file paths chosen in the editor survive, are validated, and reach the process.
- **Tasks**:
  - [ ] **The `envFilePaths` declaration already exists** — the `LuaRunConfigurationOptions`
        `StoredProperty` + accessor and `LuaRunConfiguration`'s `EnvFilesOptions` override landed in
        Phase 1, because §2.1's factory reads them. Do not re-add them; confirm with
        `grep -rn 'envFilePaths' src/main/kotlin` (non-empty — it was 0 hits before Phase 1) before
        starting. Phase 5 owns only the wiring below.
  - [ ] Edit `run/LuaRunConfiguration.kt:377` — swap the **deprecated** no-`Project`
        `EnvironmentVariablesTextFieldWithBrowseButton()` for `EnvironmentVariablesComponent(project)`.
        The raw widget's `getEnvFilePaths()` is package-private and will not compile from Lunar
        (design §2.8) — the component is the only public route.
  - [ ] Edit `run/LuaRunConfiguration.kt` `init` (before the `FormBuilder` chain at `:395-407`) — add
        `environmentVariablesField.labelLocation = BorderLayout.WEST` and
        `environmentVariablesField.text = ""`, then change `:402` to
        `.addLabeledComponent("Environment variables:", environmentVariablesField)`. **Both halves are
        required**: `EnvironmentVariablesComponent` is a `LabeledComponent` that sets its own title
        (`EnvironmentVariablesComponent.java:26,53`), so without the clear the row renders the label
        twice. This is design §2.8.1's decision, already taken — do not re-decide it. Platform
        precedent: `ShRunConfigurationEditor.java:76-78`.
  - [ ] Edit `run/LuaRunConfiguration.kt:414` and `:425` — reset/apply both `envData` and
        `envFilePaths`. `setEnvFilePaths(...)` must be called even with an empty list, or the
        disk-icon chooser is never installed (design §2.8).
  - [ ] Edit `run/LuaRunConfiguration.kt` `startProcess()` — add `envFileVariables()` and apply it
        **before** `environmentVariables?.configureCommandLine(...)` (`:317`) so explicit table
        entries win — realizes design §4.1.
  - [ ] Edit `.../validation/LuaTargetChecks.kt` — add `ENV_FILES` (`ADVISORY`) as check 8.
  - [ ] Add **TC-06-17a** to `src/test/kotlin/net/internetisalie/lunar/run/TestLuaRunConfiguration.kt`
        and **TC-06-17b** to `LuaTargetValidationTest.kt` — design §9. The split is fixed there; do
        not re-decide it.
- **Exit criteria**: both TCs pass (TC-06-17a in `TestLuaRunConfiguration.kt`, TC-06-17b in
  `LuaTargetValidationTest.kt`); the pre-existing editor round-trips
  `testSourcePathRoundTripsThroughEditor` and `testDebugPortRoundTripsThroughEditor`
  (`TestLuaRunConfiguration.kt:121-140`, `:155-175`) still pass — this phase swaps the widget they
  drive through `LuaRunSettingsEditor`. Full suite green.
- **Note**: `LuaRunSettingsEditor` is a `FormBuilder` editor. `docs/engineering-contract.md:164-166`
  (`- **SCOPE:**`, a top-level bullet governing the whole UI section, not a rider on the
  screenshot-gate bullet) reads: *"These bind **new and restructured** UI. Do not open a retroactive
  sweep; the surviving `FormBuilder` run-config editors are acceptable until touched. Fix a surface
  when you are already editing it."* So: no `FormBuilder` → Kotlin-UI-DSL migration is in scope, the
  `"Environment variables"` row **is** being edited and therefore gets both its colon (§6 COLONS) and
  the double-label fix (§2.8.1), and the other seven labels in this editor are **not** swept here —
  that is [risks-and-gaps.md](risks-and-gaps.md) TBD-3.

### Phase 6: Create a target from context [Should]

- **Goal**: *Run 'main.lua'* / *Debug 'main.lua'* appears on the context menu, so a debug target
  stops being something the user hand-types.
- **Tasks**:
  - [ ] Create `src/main/kotlin/net/internetisalie/lunar/run/LuaRunConfigurationProducer.kt` —
        realizes design §2.9 and **§3.9, which specifies all three overrides as numbered predicates**:
        §3.9.1 `setupConfigurationFromContext` (nine steps, including the REDIS decline at step 5 and
        the `name`/`scriptName` assignments at steps 6–7), §3.9.2 `isConfigurationFromContext`,
        §3.9.3 `isPreferredConfiguration` + the `internal fun yieldsTo`. Mirror
        `LuaTestRunConfigurationProducer.kt:21-42` for structure, `LuaRedisRunConfigurationProducer.kt:30`
        for the file-type guard and `:48-53` for the target helper. Do **not** re-derive
        `isTestFile` (`LuaTestRunConfigurationProducer.kt:141-152`); §3.9.3 yields instead.
  - [ ] Edit `src/main/resources/META-INF/plugin.xml` — insert the `<runConfigurationProducer>` of
        design §7 immediately after the existing test producer at `:606-607`.
  - [ ] Create `src/test/kotlin/net/internetisalie/lunar/run/LuaRunConfigurationProducerTest.kt`
        with **TC-06-18a, TC-06-18b, TC-06-18c, TC-06-18d** — design §9.
- **Exit criteria**: all four TCs pass. **TC-06-18c is the exit criterion for Risk 1.6**, not
  `TestLuaRedisRunConfigurationProducer`: that suite instantiates `LuaRedisRunConfigurationProducer()`
  directly (`src/test/kotlin/net/internetisalie/lunar/redis/run/TestLuaRedisRunConfigurationProducer.kt:28,43`)
  and never touches `LuaRunConfigurationProducer`, so it stays green whether or not the new producer
  also offers on a REDIS target. It must still pass — it just proves nothing about this phase. Full
  suite green.

---

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
| :-- | :-: | :-- |
| `DEBUG-06-01` | M | Phase 1 |
| `DEBUG-06-02` | M | Phase 2 |
| `DEBUG-06-03` | S | Phase 1 (§3.1 mechanism) + Phase 2 (tiers) |
| `DEBUG-06-04` | M | Phase 1 |
| `DEBUG-06-05` | S | Phase 3 |
| `DEBUG-06-06` | M | Phase 2 |
| `DEBUG-06-07` | M | Phase 2 |
| `DEBUG-06-08` | C | Phase 3 |
| `DEBUG-06-09` | M | Phase 1 (preserved verbatim) |
| `DEBUG-06-10` | M | Phase 3 |
| `DEBUG-06-11` | S | Phase 3 |
| `DEBUG-06-12` | S | Phase 3 |
| `DEBUG-06-13` | M | Phase 4 |
| `DEBUG-06-14` | S | — already `Full`; no code (design §6) |
| `DEBUG-06-15` | S | Phase 4 |
| `DEBUG-06-16` | C | — `Won't` (design §10.2) |
| `DEBUG-06-17` | S | Phase 5 |
| `DEBUG-06-18` | S | Phase 6 |
| `DEBUG-06-19` | C | — `Won't`; extensibility only (design §2.10) |
| `DEBUG-06-20` | C | Phase 2 |
| `DEBUG-06-21` | S | Phase 1 (vocabulary preserved by §3.3) |
| `DEBUG-06-22` | C | Phase 1 (messages) + Phase 2 (test config rerouted) |
| `DEBUG-06-23` | S | Phase 1 + Phase 2 |

## Verification Tasks

| # | Task | Covers |
| :-: | :-- | :-- |
| V1 | `LuaTargetValidationTest` — TC-06-02a/b, -03a, -04a, -05a, -07a/b/c, -08a/b, -10a, -11a/b, -12a/b, -13a/b/c, -15a/b, -17b, -20a, -22a (23 cases) | all M/S rows except `-18` and the `-17` round-trip (V3) |
| V2 | `LuaRunConfigurationProducerTest` — TC-06-18a/b/c/d (4 cases) | `-18`, Risk 1.6 |
| V3 | `TestLuaRunConfiguration` — tighten `testCheckConfigurationThrowsWithoutRuntime` (`:177-187`); add **TC-06-17a**, the env-file editor round-trip, next to `testDebugPortRoundTripsThroughEditor` (`:156-175`) whose fixture it reuses. This is TC-06-17a's only home; design §9's Files list says the same. | `-02`, `-17` |
| V4 | Full suite: `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"` after **every** phase. A green `--tests *Target*` proves nothing about the full suite (a JUnit3/JUnit5 collection difference has hidden failures here before). | all |
| V5 | `tooling/gce-builder/gce-builder.sh run ktlintCheck` — **check only**, after formatting on the VM and rsyncing back | all |
| V6 | `verify-in-ide` (VNC) pass — the rows a unit test cannot settle: red banner vs yellow banner, the quick-fix button, whether the wording is intelligible, *Debug 'main.lua'* on the context menu, **one** *Environment variables:* label rather than two (§2.8.1), and that the packaged plugin really ships `lua/debug.lua` | `-03`, `-13` (packaging only — its logic is TC-06-13a/b/c), `-17`, `-18`, `-20`, `-21`, `-23`; see `human-verification-checklists.md` (DR-03) |
| V7 | Corpus sweep **not required** — this feature touches no type engine, index, resolution or inspection path. Stated so the omission is deliberate, not forgotten. | — |

## Task Summary

| Phase | Status | Priority |
| :-- | :-- | :-- |
| Phase 1: The validation pipeline (no behaviour change) | todo | Must |
| Phase 2: The severity ladder — the headline | todo | Must |
| Phase 3: Path and level checks, bounded | todo | Must |
| Phase 4: The pre-spawn debug gate | todo | Should |
| Phase 5: Environment files | todo | Should |
| Phase 6: Create a target from context | todo | Should |

## Bug reports retired by this plan

| Report | Retired by | Requirement rows |
| :-- | :-- | :-- |
| [[BUG-455]] — no run configuration can block a launch | Phase 2 | `-02`, `-06`, `-07`; its §4 "related" items land in Phase 2 (`-07`, `-22`) and Phase 4 (`-15`) |
| [[BUG-461]] §1 (that `testCheckConfigurationThrowsWithoutRuntime` cannot fail) | Phase 2's mandatory edit to `TestLuaRunConfiguration.kt:177-187` | `-02` |

[[BUG-454]] (mid-run breakpoint toggle deadlocks the session) is **not** retired here and is not in
scope — see [risks-and-gaps.md](risks-and-gaps.md) Gap 2.5.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
