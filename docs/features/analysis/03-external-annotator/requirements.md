---
id: ANALYSIS-03
title: "03: External Annotator"
type: feature
status: "done"
vf_icon: ✅
priority: "medium"
parent_id: ANALYSIS
folders: ["[[features/analysis/requirements|requirements]]"]
---

# 03: External Annotator

The platform plumbing that turns a luacheck run into editor highlights: registration on the
external-annotator extension point, the three-stage `collect` → `doAnnotate` → `apply` lifecycle
with its threading rules, the pairing with a batch inspection, and the mapping from a linter
result to a `HighlightSeverity`, a `TextRange`, a tooltip and a quick fix.

## Scope boundary

This feature is **only the annotator**. Two siblings own the halves either side of it and are
cross-referenced, never restated:

- [[ANALYSIS-01]] — luacheck integration as a whole: tool resolution, binding, the command line,
  `.luacheckrc` discovery, the settings that feed it.
- [[ANALYSIS-04]] — turning luacheck's stdout into `Problem` records: the line format, ANSI
  stripping, exit-code classification.
- [[MAINT-26]] — the correctness pass over that pipeline (command-line fidelity, stdin feeding,
  offset clamping, failure classification). Where a row below is `Full` because MAINT-26 made it
  so, it says so rather than re-deriving the fix.

## How these requirements were derived

**Not from `LuaCheckAnnotator.kt`.** A specification read off its own implementation cannot fail,
which is the defect that left [[DEBUG-07]] marked shipped for months (see [[BUG-450]] §4). The rows
below come from three sources outside this repository, and Lunar was then checked against them:

1. **The `ExternalAnnotator` contract** —
   `platform/analysis-api/src/com/intellij/lang/annotation/ExternalAnnotator.java` and the pass that
   drives it,
   `platform/lang-impl/src/com/intellij/codeInsight/daemon/impl/ExternalToolPass.java`. Every stage,
   every `AnnotationBuilder` capability and every guarantee the pass makes (or declines to make) is a
   question this feature must answer — "we provide it" or "we deliberately do not". That list exists
   independently of Lunar and is the backbone of the table.
2. **The two reference linter annotators in the platform** — `ShShellcheckExternalAnnotator`
   (`plugins/sh/backend/…/shellcheck/`) and `Pep8ExternalAnnotator` (`python/src/…/validation/`).
   They are the closest analogues in existence: an external process, fed the editor buffer over
   stdin, whose line/column output becomes annotations. What *they* do that Lunar does not is
   evidence of a gap, not of a different taste.
3. **[`docs/engineering-contract.md`](../../../engineering-contract.md) §1** — the threading
   segregation rule. `doAnnotate` running outside a read action is the platform's hardest constraint
   here, and this repo has just fixed a violation of the same shape in [[BUG-414]].

Where a capability is unimplemented, the row distinguishes **Lunar does not implement it** from
**the user cannot do it**: the platform supplies working defaults for several (the syntax-error
gate, the indexing gate, the staleness guard, the 300 ms coalescing), and conflating the two would
manufacture gaps that are not there.

Rows marked **[live]** state something about *rendering* and were verified only by reading the
platform's rendering path; they need a live IDE to settle.

## Requirements & Status

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `ANALYSIS-03-01` | **Registered on the external-annotator EP** | **M** | **Full** | `<externalAnnotator language="Lua" implementationClass="…luacheck.LuaCheckAnnotator"/>` (`plugin.xml:333-335`). The `language` attribute is mandatory per the EP's own javadoc; it is present. Exactly one external annotator is registered for Lua, so there is no double-linting. |
| `ANALYSIS-03-02` | **`collectInformation` gathers everything the tool needs** | **M** | **Full** | Called inside a read action by the pass. `Info` captures `fileName`, `workDir`, `documentText`, `project`, `documentLineCount`, `lineStartOffsets` — so nothing PSI- or document-shaped is left for stage 2. Returns `null` (→ the pass skips the annotator entirely) for a file with no `VirtualFile`, a non-Lua file type, or no parent directory. |
| `ANALYSIS-03-03` | **`doAnnotate` touches no PSI, VFS or index** | **M** | **Full** | The contract says this stage runs *outside* a read action; engineering contract §1 says a PSI read there is a violation. `LuaCheckInvoker.invoke` reads only `Info` fields plus settings services, then launches the process. This is the same class of defect as [[BUG-414]] and is currently clean. **No test asserts it** — see Verification. |
| `ANALYSIS-03-04` | **The linter sees the editor buffer, not the disk file** | **M** | **Full** | Both reference annotators pipe the buffer to stdin precisely so offsets index the document the user is looking at. Lunar does the same (`--filename <name> -`). Delivered by MAINT-26-03; not re-specified here. |
| `ANALYSIS-03-05` | **`apply` is read-action-only and driven by the result** | **M** | **Full** | `apply` receives only `(file, result, holder)` — the platform does **not** hand back the collected info — so recomputing the line-offset table from the current document is the idiomatic workaround, and both reference annotators do the equivalent via `PsiDocumentManager.getDocument`. Caveat: Lunar recomputes by re-invoking `collectInformation`, which re-applies the whole applicability gate, so a file that lost its parent directory between the two stages discards every annotation silently rather than just skipping the range math. |
| `ANALYSIS-03-06` | **Every problem gets a precise, in-document range** | **M** | **Full** | Not a whole-line highlight: the range is `lineStart + columnStart` .. `lineStart + columnEnd + 1`, clamped so the line index stays inside the document, the offsets stay inside their line, the end never exceeds the text length, and the range is never empty. |
| `ANALYSIS-03-07` | **No stage may throw** | **M** | **Full** | `ExternalToolPass.processError` converts any throwable from any stage into `LOG.error(PluginException)` — an "IDE internal error" balloon, repeated per pass. The clamping in `-06` is what keeps an `IndexOutOfBoundsException` out of that path when the linter reports a line the buffer no longer has. |
| `ANALYSIS-03-08` | **Paired with a batch inspection** | **M** | **Full** | `getPairedBatchInspectionShortName()` returns `"LuaCheck"`; `LuaCheckInspection : LocalInspectionTool, ExternalAnnotatorBatchInspection` is registered as `<localInspection shortName="LuaCheck" … unfair="true" level="WARNING" enabledByDefault="true" groupPath="Lua" groupName="Luacheck">` with an `inspectionDescriptions/LuaCheck.html`. Two consequences the platform gives for free: turning the inspection off in the profile stops the annotator (`ExternalToolPass` filters on `profile.isToolEnabled`), and **Code → Inspect Code** routes through `ExternalAnnotatorInspectionVisitor.checkFileWithExternalAnnotator`. Omitting the paired inspection would have made the pass log `"Paired tool 'LuaCheck' not found"` and skip the annotator. |
| `ANALYSIS-03-09` | **Typing cancels the run and kills the process** | **M** | **Full** | `ExternalToolPass.runChangeAware` installs a document listener that cancels the indicator on any edit. `LuaToolExecutionService.capture` picks up the ambient `ProgressManager` indicator and `CapturingProcessRunner` polls it every 10 ms, calling `destroyProcess()` and setting `isCancelled`; that becomes `LuaCheckOutcome.NotApplicable`, which `apply` treats as a no-op. No orphaned luacheck process, no stale annotations. |
| `ANALYSIS-03-10` | **A stale result is never applied** | **M** | **Full** | The platform, not Lunar, owns this: `ExternalToolPass` snapshots the document modification stamp before queueing and re-checks it both before `doAnnotate` and again before `doApply`. **Lunar implements nothing here and correctly needs nothing.** The batch path (`checkFileWithExternalAnnotator`) has no such guard, but it runs three read actions over a file nobody is typing into. |
| `ANALYSIS-03-11` | **Severity honours the configured inspection level** | **S** | **Not Implemented** | `HighlightSeverity.WARNING` is hard-coded at both annotation sites; nothing reads `profile.getErrorLevel(HighlightDisplayKey.find("LuaCheck"), file)`. `Pep8ExternalAnnotator` captures exactly that in `collectInformation` and carries it into `apply` — because `HighlightInfo.fromAnnotation` takes the severity straight off the annotation, the profile is otherwise ignored. The EP declares `level="WARNING"`, so the default agrees and nothing looks wrong; the defect is that **Settings → Editor → Inspections → Lua → Luacheck → Severity is inert.** Recorded nowhere else in the repo. |
| `ANALYSIS-03-12` | **Linter errors distinguished from warnings** | **S** | **Not Implemented** | luacheck is invoked with `--codes`, which prefixes each message with an `E`/`W` code (`(E011)` vs `(W211)`); syntax and fatal problems carry `E`. Everything is emitted as `WARNING`, so a real error reads with the same weight as an unused local. The code is not extracted into a field (see [[ANALYSIS-04]], which establishes the taxonomy: codes beginning `0` are errors, the rest warnings) — it survives only inside the message string, so the severity mapping has nothing to key on today. |
| `ANALYSIS-03-13` | **Rule code and documentation link in the tooltip** | **C** | **Not Implemented** | No `.tooltip(...)` call, so the tooltip is the plain message. Because `--codes` is on, the user does see `(W211)` as literal text — this is **not** a case of the information being unavailable. What is missing is shellcheck's treatment: an HTML tooltip linking the code to its documentation page. |
| `ANALYSIS-03-14` | **Quick fixes on a luacheck annotation** | **S** | **Not Implemented** | No `withFix`, and no `problemGroup`. Both reference annotators attach at least a suppress action, a "disable this inspection" action and (shellcheck) an apply-the-linter's-own-fix action. Without any registered fix the annotation is attributed to the generic `Annotator` display key rather than to `LuaCheck`, so Alt+Enter offers no LuaCheck-specific option. **[live]** — the exact Alt+Enter contents need an IDE to confirm. Recorded nowhere else in the repo. |
| `ANALYSIS-03-15` | **IDE-side suppression comments respected** | **S** | **Not Implemented** | `LuaInspectionSuppression` (which already parses both `---@diagnostic disable…` and `-- luacheck: ignore`) is consumed only by Lunar's own `LocalInspectionTool`s; the annotator never calls it, and its `isSuppressed` API keys on a `LuaNameRef`, which `apply` does not have. **The user is not stuck**: `-- luacheck: ignore` still works, because luacheck itself parses it out of the piped buffer. Only the LuaCATS `---@diagnostic` form fails to suppress a luacheck warning. Already tracked as the `com.intellij.lang.inspectionSuppressor` row in [[EP-BACKLOG]] §3. |
| `ANALYSIS-03-16` | **A linter that cannot run is never a clean pass** | **M** | **Full** | Every `LuaCheckOutcome.Failure` reaches the user as a warning carrying the detail, plus a `LOG.warn` — so a missing binary, a 30 s hang or a non-lint exit can never be mistaken for a green file. The annotator's obligation is to *surface* the failure and it does; **which** exit codes deserve that treatment is [[ANALYSIS-04]]'s question, and it records that Lunar's `FATAL_EXIT_CODE = 2` threshold misclassifies luacheck's "errors found" code. Delivered by MAINT-26-06. |
| `ANALYSIS-03-17` | **A tool failure is presented as a file-level notice, once** | **C** | **Partial** | The failure annotation is created over `TextRange(0, textLength)` rather than with `AnnotationBuilder.fileLevel()`, which is the platform's dedicated affordance for "file-wide messages" and renders as a sticky notice at the top of the editor instead of a range highlight. It is also recreated — with a fresh `LOG.warn` — on every pass; `Pep8ExternalAnnotator` keeps a one-shot `myReportedMissingInterpreter` guard for exactly this. **[live]** — how a whole-document warning range renders needs an IDE to settle. |
| `ANALYSIS-03-18` | **Skipped while the file has syntax errors** | **S** | **Full** | *Not* implemented by Lunar. The three-argument `collectInformation(file, editor, hasErrors)` is left at its default, which returns `null` when a preceding pass already found errors, so luacheck does not pile onto a file that is mid-edit and unparseable. Recorded as Full because the requirement is about the user, and as a caveat because overriding the overload later would silently replace working behaviour. |
| `ANALYSIS-03-19` | **Skipped during indexing** | **C** | **Full** | *Not* implemented by Lunar. `LuaCheckAnnotator` does not implement `DumbAware`, so `ExternalToolPass` skips it while indexes are unavailable. That is the correct outcome — tool resolution reads project settings and registry state — and it comes free from `PossiblyDumbAware`. |
| `ANALYSIS-03-20` | **Cost per pass is bounded** | **C** | **Partial** | The platform bounds the *frequency*: `ExternalAnnotatorManager` merges updates keyed per file on a 300 ms queue, so typing cannot launch a process per keystroke. What Lunar contributes is unbounded: no result cache keyed on the document stamp, so an untouched file is re-linted from scratch on every daemon restart, and the timeout budget is `LuaExecTimeout.FORMAT` (30 s) — a bucket named for formatters, reused here on the highlighting path, against 10 s in both reference annotators. Cost unmeasured. |
| `ANALYSIS-03-21` | **Injected or templated Lua roots** | **C** | **Won't** | `ExternalToolPass` iterates `viewProvider.getAllFiles()`, and shellcheck consequently skips ranges covered by `OuterLanguageElement`. Lunar registers no `multiHostInjector` and ships no template language over Lua, so a Lua view provider has exactly one root and there is nothing to skip. Revisit only if Lua injection is ever added. |
| `ANALYSIS-03-22` | **Third-party opt-out via `ExternalAnnotatorsFilter`** | **W** | **Won't** | The EP exists so *another* plugin can suppress ours for a given file. Nothing is implementable on this side; the requirement is listed only so the contract's surface is fully accounted for. |

## Verification

| Rows | Covered by |
| :--- | :--- |
| `-05`, `-06`, `-07`, `-16` | `LuaCheckAnnotatorTest` — drives the real `apply` through `AnnotationSessionImpl` + `applyExternalAnnotatorWithContext` against a `configureByText` buffer: exact `TextRange(6,7)` placement, an out-of-range line clamped without an exception, same-line/same-message de-duplication, and the launch-failure warning. |
| `-01`, `-08`, `-11` (default only) | `LuaCheckInspectionGroupingTest` — asserts the `localInspection` EP resolves, the group path is `Lua / Luacheck`, `pairedBatchInspectionShortName == shortName == "LuaCheck"`, the description resource loads, and the default level is `WARNING` and enabled. It pins the *default*; it does not exercise a changed profile level, which is why `-11` is Not Implemented rather than untested. |
| `-04`, `-12`, `-16` (parse side) | `LuaCheckInvokerClassifyTest`, `LuaCheckCommandLineTest` — outcome classification and command-line assembly. These belong to [[ANALYSIS-04]] / [[ANALYSIS-01]] and are listed only to show which rows here lean on them. |
| `-09` (transport half) | `LuaToolExecutionServiceTest.testCaptureCancelledViaIndicator` — proves `capture` destroys the process promptly on indicator cancel. |

**Not covered by any test:**

- `-03` — nothing asserts that `doAnnotate` performs no PSI/VFS access. The property currently holds
  by construction (`Info` is a flat data carrier), and a future field that lazily reaches back into
  PSI would break it silently. A threading assertion in `LuaCheckInvoker.invoke` would close it.
- `-09` (wiring half) — `LuaCheckInvoker` calls `capture` with no explicit indicator and relies on
  the daemon's ambient one. The transport test supplies its own indicator, so the *wiring* is
  unasserted.
- `-10`, `-18`, `-19`, `-20` — platform-owned behaviour with no Lunar-side code to test.
- `-14`, `-17` — the `[live]` rows; they need the VNC IDE loop, not a headless fixture.

**`-11` and `-14` were found by writing this table** and are recorded nowhere else in the repo.
Neither has a bug report yet. `-15` is already tracked in [[EP-BACKLOG]]; `-17` and `-20` are
observations rather than defects until the rendering and the cost are measured.
