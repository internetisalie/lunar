---
id: "ANALYSIS-07-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "ANALYSIS-07"
folders:
  - "[[features/analysis/07-value-constraints-over-the-cfg/requirements|requirements]]"
---

# Technical Design: ANALYSIS-07 — Value Constraints over the Control-Flow Graph

> **What this document designs, and what it deliberately does not.**
>
> `ANALYSIS-07-01` is *"decide the direction (A / B / C) **from a measurement**"*. That decision is
> not made. **§1–§4 and §7–§10 are complete and implementable**: they specify the Phase 0 spike, its
> five harnesses, every algorithm those harnesses run, the exact output format each produces, and an
> ordered decision procedure (§3.7) that maps their output to exactly one of six outcomes with no
> judgement call left over.
>
> **§5 and §6 — the implementations of `-02`, `-03` and `-04` — are marked DEFERRED**, each with a
> named DR as its gate and a written contract the eventual design must satisfy. Writing them now
> would mean choosing between three architectures from a reading, which is precisely the failure
> [[BUG-441]] recorded against itself. Its own words, after it re-measured its own mechanism:
>
> > So the order is forced for a different reason than recorded: **(1) is the root** … The "one
> > change" conclusion survives; the mechanism behind it did not.
> >
> > — `docs/features/bug-fixes/441-unknowns-are-omitted-not-represented/bug-report.md:84-86`,
> > correcting its own heading at `:47` ("They are ONE change, and the order is forced")
>
> **That block is quoted; nothing around it is.** An earlier revision of this paragraph presented a
> sentence — *"its 'one change, the order is forced' framing survived contact with a runtime probe,
> and its mechanism did not"* — as BUG-441's, and `grep -rn "survived contact"
> docs/features/bug-fixes/441-*/bug-report.md` returns nothing. It was **this plan's paraphrase**,
> and it now appears only where it belongs and marked as such, in
> [requirements.md](requirements.md) under "What a solution has to be".
>
> **How to check any quotation in these artifacts — a single-line `grep -F` will report several of
> them as fictional.** Every `.md` here is hard-wrapped, so a quoted sentence that crosses a wrap is
> present in the file and absent from `grep`'s line-at-a-time view. Collapse the newlines first:
>
> ```bash
> tr '\n' ' ' < <file> | tr -s ' ' | grep -o "<the quoted sentence>"
> ```
>
> Three quotations in this plan need it, all verified with exactly that command at `99b45f92`:
>
> | quotation | source | naive `grep -c` | wrap-tolerant |
> | :-- | :-- | --: | :-- |
> | *"So the order is forced … the mechanism behind it did not."* (the block above) | `441-…/bug-report.md:84-86` | `0` | matches |
> | *"the same call-site-union imprecision reported against the parameter itself"* (`risks-and-gaps.md` Premises, DR-11) | `428-…/bug-report.md:89-90` | `0` | matches |
> | *"`LuaCorpusSweepTest` instrumented to print `file:line`, message and source line"* (§2.6) | `428-…/bug-report.md:46-47` | `0` | matches |
>
> The same hazard applies to **source** counts, because ktlint's formatting is the house style and it
> wraps freely. Wherever this plan states a count it now names the pattern that produced it, and calls
> out the near-miss where one exists — `LuaScopeProcessor`'s tenth `result =` site (§3.1a, `:88` is a
> wrapped assignment, so `grep "result = "` **with** a trailing space returns 9), the class/supertype
> split on `LuaTypesSnapshot` (§2.2.1, `class` on `:55`, `: LuaTypes {` on `:64`), and
> `LuaFoldingVisitor`'s later-line supertype (requirements TC-5a (b)).
>
> A speculative §5 would look more complete and would be worth less. This document's claim is
> narrower and testable: **Phase 0 can be executed by a non-frontier model from §2–§4 alone, and its
> output determines §5 mechanically.**

## 1. Architecture Overview

### 1.1 Current State — the grounded facts

Every row below is `grep`-verified at `99b45f92`. Nothing here is inferred.

| # | Fact | Evidence |
| :-- | :-- | :-- |
| F1 | The CFG package is 4 files / 440 lines total | `analysis/controlflow/{LuaControlFlowBuilder.kt:368, LuaControlFlow.kt:36, LuaInstruction.kt:23, ControlFlowCache.kt:13}` |
| F2 | It has **exactly one** production consumer | `grep -rn "analysis.controlflow" src/` → importers are `LuaUnreachableCodeInspection.kt:13-15` and the test `LuaControlFlowTest.kt:5`; nothing else |
| F3 | The only instruction subtype is `LuaReadWriteInstruction(builder, element, variableName: String, accessType: AccessType)` | `LuaInstruction.kt:15-23`; `AccessType` is `{READ, WRITE}` at `:10-13` |
| F4 | **The CFG performs no scope/binding resolution.** Every instruction keys on `nameRef.text` | **11** construction sites — `grep -c "LuaReadWriteInstruction(" LuaControlFlowBuilder.kt` → `11`, at `:38,46,54,220,246,309,319,330,337,342,361`. And `grep -n "scope\|Scope" LuaControlFlowBuilder.kt` returns **exactly one line**: `31:    fun build(owner: ScopeOwner): ControlFlow {`. **`resolveGoto` (`:86-93`) contains no match** — it is a *label* resolver keyed on `LabelKey(targetName, block)` over `PsiTreeUtil.getParentOfType(…, LuaBlock)`, which is block nesting, not name binding, and an earlier revision of this row wrongly listed it as a second grep hit |
| F5 | **The file-level CFG does not descend into function bodies.** `visitFuncDecl`/`visitLocalFuncDecl` emit only a WRITE for the *name*; `visitFuncDef` has an empty body | `LuaControlFlowBuilder.kt:335-347`. The inspection compensates by enumerating owners itself (`LuaUnreachableCodeInspection.kt:64-70`) |
| F6 | There is **no condition-carrying instruction**. Every `if`/`while`/`repeat` condition becomes a plain `builder.startNode(expr)` | **Three** sites, each passing the condition expression itself: `LuaControlFlowBuilder.kt:127` (`if`/`elseif`, `startNode(expr)`), `:171` (`while`, `startNode(whileStatement.getExpr())`) and `:201` (`repeat … until`, `startNode(cond)`). An earlier revision also listed **`:144`, which is not a condition**: it is `builder.startNode(blockList[i])`, the **else-block** node — the `expr == null` arm of the same loop (`:124-159`) — and it anchors a `LuaBlock`, not an expression. The platform's `ConditionalInstruction` (`getCondition()`/`getResult()`) and `startConditionalNode` are **unused**: `grep -rn "ConditionalInstruction\|startConditionalNode\|TransparentInstruction" src/` → no matches. `LuaBranchInstruction` existed and was deleted by MAINT-31 (`docs/features/maint/31-dead-code-sweep/design.md:40-41` — the sentence *"`LuaBranchInstruction` is deleted — MAINT-29 can reintroduce it if it uses it for condition nodes."* starts at `:40`; `:39` is the same bullet's `DebugCommandKind.EXIT` clause) |
| F7 | Neither the builder nor the cache calls `ProgressManager.checkCanceled()` | `grep -rn "checkCanceled\|ProgressManager" src/main/kotlin/net/internetisalie/lunar/analysis/controlflow/` → no matches |
| F8 | The CFG is cached per `ScopeOwner` on `owner.containingFile` | `ControlFlowCache.kt:6-12` — `CachedValuesManager.getCachedValue(owner) { … Result.create(flow, owner.containingFile) }` |
| F9 | The type graph is built **per file, in one recursive tree walk**, spanning every nested function | `LuaTypesVisitor : LuaRecursiveVisitor()` (`:21`); `LuaRecursiveVisitor.visitElement` calls `acceptChildren` (`LuaRecursiveVisitor.kt:9-11`); entry is `LuaTypesVisitor.buildSnapshot(file)` → `file.accept(visitor); visitor.graph.checkTypes()` (`:1533-1545`) |
| F10 | Name→node binding in the type engine is a lexical `LuaScope` chain, 10 `declare` + 4 `lookup` sites | `LuaScope.kt:20-36`; call sites counted in `LuaTypesVisitor.kt` |
| F11 | The visitor overlap is **12 shared, 8 type-only, 7 CFG-only** `visit*` overrides | `LuaTypesVisitor` has 20; `LuaControlFlowBuilder` has 19. Type-only: `visitBinOpExpr`, `visitFile`, `visitFuncCall`, `visitIndexExpr`, `visitTableConstructor`, `visitTerminalExpr`, `visitUnOpExpr`, `visitVarSuffix`. CFG-only: `visitBreakStatement`, `visitDoStatement`, `visitGotoStatement`, `visitLabel`, `visitPsiElement`, `visitRepeatStatement`, `visitWhileStatement` |
| F12 | BUG-441's gate is one expression over the whole `upSet`, computed once per variable per fixed-point iteration | `LuaTypeGraph.kt:352-353`: `val unknownProvenance = currentUpSet.any { it is ValueNode && isUnknown(it.write) }`; consumed at `:368` and gated at `:283` |
| F13 | The gate's only effect is a **severity tier**, never a suppression | `LuaTypeGraph.kt:282-284` — `if (declaredDemand && !unknownProvenance) ERROR else HYPOTHESIS`; `LuaTypeAssignabilityInspection.kt:32` drops `HYPOTHESIS` from the inspection, `LuaTypeHypothesisAnnotator` surfaces it as an intention |
| F14 | Every emission on the BUG-441 fixture came from **one** stack, pinned by probe | BUG-441 "Attempt 2", **line numbers against `c4c958ce`, not this tree**: `checkTypes:276 -> checkTypes:333 -> checkCompatibility$default -> checkCompatibility:514 -> reportIncompatible`. At `99b45f92` the same terminal call is `LuaTypeGraph.kt:560` (the last of four `reportIncompatible` sites in `checkCompatibility`: `:426`, `:494`, `:548`, `:560`) — **re-pin it before relying on it**, because a line number quoted across two commits is exactly the kind of claim this plan refuses elsewhere. `addEdge`'s value→use branch (`LuaTypeGraph.kt:132-142`, whose `checkCompatibility` call is `:135`) was ruled out for this shape |
| F15 | The platform ships a backward CFG walker with a per-node three-way verdict | `com.intellij.codeInsight.controlflow.ControlFlowUtil.iteratePrev(int, Instruction[], Function<Instruction, Operation>)` (`platform/core-impl/.../ControlFlowUtil.java:84-126`), `Operation ∈ {CONTINUE, BREAK, NEXT}` (`:129-142`) |
| F16 | `Instruction.num()` is the index into the instruction array | `InstructionImpl.java:11-21` (`myNumber = builder.instructionCount++`), `ControlFlowBuilder.addNode` appends in the same order (`:124-130`), and with zero transparent instructions (F6) `completeControlFlow()` returns the array unmodified (`:67-69`) |

### 1.2 Prior Art in This Repo

Searched: `analysis/controlflow/`, `analysis/inspections/`, `lang/psi/types/`, and every class matching
`*Flow*`, `*DataFlow*`, `*Reaching*`, `*Definition*` under `src/main/kotlin/`.

| Existing component | Relationship |
| :-- | :-- |
| `net.internetisalie.lunar.analysis.controlflow.LuaControlFlowBuilder` (`:10`) | **Extended, never duplicated.** Phase 0 reads its output only. Any change to it is Phase 1+ and belongs to whichever direction §3.7 selects |
| `net.internetisalie.lunar.analysis.controlflow.ControlFlowCache` (`:6`) | **Reused as-is** — Phase 0's harnesses obtain graphs through it, so they measure the cached path users get |
| `net.internetisalie.lunar.analysis.inspections.LuaUnreachableCodeInspection` (`:32`) | **Untouched.** Explicit non-goal (requirements → Out of Scope). It is also the *regression surface*: its 5 corpus sites are baselined and must not move |
| `net.internetisalie.lunar.lang.psi.types.LuaTypeGraph.checkTypes` (`:296`) | **Extended at one site.** `-02` replaces the `unknownProvenance` expression at `:352-353`; nothing else in the loop changes |
| `net.internetisalie.lunar.lang.psi.types.LuaTypesVisitor.injectNarrowedBinding` (`:464-478`) | **`-03`'s site, and possibly `-03`'s whole fix.** `ANALYSIS-07-00-DR-10` decides |
| `net.internetisalie.lunar.lang.LuaNameReference` (`:28`), attached by `LuaNameRefBaseImpl.getReference` (`LuaBaseElements.kt:98-105`) | **Reused as the binding oracle** (§3.2). It already answers "which declaration does this name refer to", which is exactly what F4 says the CFG lacks. No new resolver is written |
| `net.internetisalie.lunar.corpus.CorpusSweep` (`:28`) / `LuaCorpusSweepTest` (`:34`) | **Temporarily instrumented, then reverted** — the method BUG-428 used for its 2026-08-20 re-measurement. No new corpus class is added (see §2.6 for why) |
| `net.internetisalie.lunar.lang.types.LuaUnknownProvenanceTest` (`:26`) | **Extended, not replaced.** Its four cases are `-02`'s regression floor (requirements TC-2b, TC-2e) |
| `com.intellij.codeInsight.controlflow.ControlFlowUtil` | **Platform utility, used rather than reimplemented.** A hand-written backward walk in this feature would itself be a third flow analysis (`ANALYSIS-07-05`) |

**Nothing in this repo already computes reaching definitions.** `grep -rn "reachingDefinition\|ReachingDef\|dataFlow\|DataFlow" src/main/kotlin/` returns no matches; `LuaControlFlowImpl.isReachable` (`LuaControlFlow.kt:18-35`) is a forward BFS answering *reachability of an instruction*, not *which definitions reach a use*, and it discards edges rather than accumulating facts.

### 1.3 Target State

Phase 0 adds **five test-only harness classes, one abstract test-only base class and two temporary
instrumentation patches** — six files, five harnesses.

**The invariant is that no production change SURVIVES Phase 0, not that none is made.** Two files are
patched and both are reverted:

| patched file | tree | task | reverted by |
| :-- | :-- | :-- | :-- |
| `src/test/kotlin/net/internetisalie/lunar/corpus/CorpusSweep.kt` (`accumulateHits`, `:263-287`) | test | T0.7, §2.6/§3.6 | T0.10 |
| `src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeGraph.kt` (`:352-353`) — the throwaway `unknownProvenance` | **production** | T0.9, §3.6 | T0.10 |

Both are snapshotted and restored with the **`temporary-edits`** skill; **never** `git checkout` /
`git restore` / `git stash`, which discard every uncommitted change under the path rather than only
the probe's. The enforced end-state is `git status --short` and `git diff` both **empty**
(`implementation-plan.md` T0.10 and exit criterion 4). An earlier revision of this sentence, of §7.1,
and of `risks-and-gaps.md` Risk 1.4 all said Phase 0 *"changes no production file"*, which T0.9
contradicts.

**The five harnesses are §2.1–§2.5, and there is no sixth.** The sixth *file* is
`AnalysisSevenFixtureBase` (§2.0.1), which is abstract, declares no `@Test`, and exists because the
three harnesses that read corpus files must carry their own fixture setup (§2.0). The only other
`AnalysisSeven*` **name** this document mentions is `AnalysisSevenCorpusDumpTest`, which §2.6 and §9
alt. 5 **reject** — the per-site corpus dump is the temporary patch, not a class. (An earlier
revision said "six harnesses" here and in §2 and §7.3 while specifying five; five is the count that
governs, and the file/harness distinction is now stated wherever a number appears.)

Phase 0's output is a set of numbers. §3.7 turns those numbers into exactly one of six verdicts —
five outcomes and one gate:

- **DX** — **not an outcome, a gate**: the probe measured itself wrong (`NEW_SUPPRESSED > 0` or
  `UNRESOLVED_TARGET > 0`). Fix the probe, re-run the affected harness, evaluate again. Phase 0 does
  not exit and no direction is selected.
- **D0** — re-scope: `-02`'s payoff measures zero, and the feature shrinks to whatever DR-10/DR-11 found.
- **D1** — **direction C**: a reaching-definitions query over the shipped CFG, consulted at
  `LuaTypeGraph.kt:352-353`.
- **D2** — **direction A**: the CFG grows a value domain; its first increment is repairing F4/F5,
  after which C's query becomes correct as a by-product.
- **D3** — **direction B**: the type engine consumes the CFG.
- **D4** — TYPE-08 §9 **confirmed by measurement**; recorded with the number, and `-02` either ships
  as C with its imprecision documented or is deferred.

Only after §3.7 fires does §5 get written.

## 2. Core Components

Every class below is **test-only and throwaway**, in `src/test/kotlin/net/internetisalie/lunar/analysis/`.
They follow the COMP-09 precedent for spikes that must not become permanent fixtures. The sentence
that states the rule is **`COMP-09-00-DR-18`'s**, verbatim — *"run and reverted — it is in no
commit, and nothing may instruct re-using it; the pasted output in design §4.8a is the evidence"*
(`docs/features/completion/09-member-enumeration/risks-and-gaps.md:504`). `COMP-09-00-DR-21` (`:505`)
and `-22` (`:506`) apply the same rule in **their own, different words**; an earlier revision of this
paragraph attributed DR-18's sentence to them. Use the **`temporary-edits`** skill to snapshot before
writing and to restore after: **never** `git checkout`/`git restore`/`git stash`.

All five are `com.intellij.testFramework.fixtures.BasePlatformTestCase` (engineering contract §5:
light fixtures) and are annotated `@RunWith(JUnit4::class)`, matching `LuaControlFlowTest.kt:11-12`.
Two of them (§2.1, §2.4) extend it directly; the three that read corpus files (§2.2, §2.3, §2.5)
extend **`AnalysisSevenFixtureBase`** (§2.0.1), which extends it. That base is abstract and has no
`@Test` method, so the file count is six and the **harness** count is five — the number §1.3, §7.3
and `implementation-plan.md` T0.1–T0.5 all use.

**Threading, identical for all five**: fixtures are configured with `myFixture.configureByText(...)`
on the test thread; every PSI/CFG read is inside `com.intellij.openapi.application.runReadAction { }`
— `ControlFlowCache.getControlFlow` goes through `CachedValuesManager` and touches PSI, and
`LuaTypesSnapshot.forFile` parses. This is the idiom `LuaUnknownProvenanceTest.kt:106` already uses.
No write actions, no EDT marshalling, no background threads.

### 2.0 The corpus fixture setup — specified once, shared by §2.2, §2.3 and §2.5

**Three of the five harnesses read pinned corpus files** (`AnalysisSevenCoverageSpikeTest`,
`AnalysisSevenJoinSpikeTest`, and `AnalysisSevenDescopeSpikeTest`'s DR-11 half), and §7.3 puts all
five **outside** the `*Corpus*` filter. §2.6 names the four things a corpus-reading class outside
that filter must therefore carry for itself — *its own corpus fetch, `VfsRootAccess` grant,
module-root application and language-level pin* — and an earlier revision of this document named
them without specifying any of them, which left T0.2/T0.3/T0.5 unexecutable. **This section is the
specification. It is not optional and it is not paraphrasable: a harness that omits any part of it
measures a different tree than the sweep does.**

Why that is not pedantry: omitting the `VfsRootAccess` grant alone is **measured** to change the
result. `LuaCorpusSweepTest.kt:39-49` records that without it the platform's test guard throws
`VfsRootAccessNotAllowedError` out of require resolution, giving *"23 penlight parse-crashes,
requires 100 → 43, identity refusal — CI run 9735"*. A coverage census taken on 23 crashed penlight
files would be a number, and it would be a number about nothing.

**Restructuring these harnesses to avoid the corpus was considered and rejected.** DR-06 and DR-07
size *"how much of the type engine's value domain has no CFG instruction"* over real code; on
synthetic fixtures they would measure the fixtures. DR-11's whole subject is two specific lines of
`penlight`. Synthetic inputs here would clear the review defect by deleting the measurement.

#### 2.0.1 The base class every corpus-reading harness extends

Written once, in
`src/test/kotlin/net/internetisalie/lunar/analysis/AnalysisSevenFixtureBase.kt`.

**The name deliberately contains no `Corpus` substring.** `build.gradle.kts:274` excludes
`*Corpus*`, and while an abstract class carrying no `@Test` would not itself be selected by that
filter, §7.3's guarantee is that *nothing* this feature adds can be drawn into the sweeps' JVM — and
a guarantee that depends on a subtlety about abstract-class selection is weaker than one that
depends on the name. It is **abstract and declares no `@Test` method**, so it is a file, not a
harness: **five harnesses (§2.1–§2.5), six files.** `@RunWith(JUnit4::class)` goes on the concrete
subclasses, matching `LuaControlFlowTest.kt:11-12`; the base carries none. It declares:

```kotlin
package net.internetisalie.lunar.analysis

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.corpus.CorpusEntry
import net.internetisalie.lunar.corpus.CorpusGuards
import net.internetisalie.lunar.corpus.CorpusManifest
import net.internetisalie.lunar.settings.LuaProjectSettings
import java.io.File

/** §2.0. The four things a corpus reader outside the `*Corpus*` filter must do for itself. */
abstract class AnalysisSevenFixtureBase : BasePlatformTestCase() {
    /** (1) of 4. Identical to `LuaCorpusSweepTest.kt:35` and `LuaInspectionParityTest.kt:43`. */
    override fun getTestDataPath(): String = System.getProperty("user.dir")

    override fun setUp() {
        super.setUp()
        // (2) of 4. Identical to `LuaCorpusSweepTest.kt:46-49`.
        VfsRootAccess.allowRootAccess(
            testRootDisposable,
            File(System.getProperty("user.dir"), CorpusManifest.CORPUS_DIR).canonicalPath,
        )
    }

    /**
     * The one entry point. Copies [root] of [corpusName] into the fixture project exactly as the
     * sweep does, applies (3) and (4), and hands back the PSI file at [relativePath].
     */
    protected fun corpusFile(
        corpusName: String,
        root: String,
        relativePath: String,
    ): PsiFile {
        val repoRoot = File(testDataPath)
        val entry = CorpusManifest.entry(repoRoot, corpusName)
        CorpusGuards.assertCorpusFetched(repoRoot, entry)          // the fetch check
        applyModuleRoot(entry, repoRoot)                            // (3) of 4
        LuaProjectSettings.getInstance(myFixture.project).state.languageLevel = entry.luaLevel  // (4) of 4
        val copied: VirtualFile =
            myFixture.copyDirectoryToProject("${CorpusManifest.CORPUS_DIR}/$corpusName/$root", root)
        val target =
            checkNotNull(copied.findFileByRelativePath(relativePath)) {
                "no $relativePath under $corpusName/$root — the corpus is fetched but not the pin " +
                    "this probe was written against"
            }
        return checkNotNull(PsiManager.getInstance(myFixture.project).findFile(target)) {
            "no PsiFile for ${target.path}"
        }
    }

    /** (3) of 4 — `CorpusSweep.applyModuleRoot` is `private`, so its three lines are restated here. */
    private fun applyModuleRoot(
        entry: CorpusEntry,
        repoRoot: File,
    ) {
        val moduleRoot = entry.moduleRoot ?: return
        val base = File(CorpusManifest.checkoutDir(repoRoot, entry.name), moduleRoot).canonicalPath
        LuaProjectSettings.getInstance(myFixture.project).state.sourcePath =
            "$base/?.lua;$base/?/init.lua"
    }
}
```

#### 2.0.2 Every symbol above, grounded

| symbol | where it is defined | note |
| :-- | :-- | :-- |
| `CorpusManifest.CORPUS_DIR` = `"test/corpus"` | `CorpusManifest.kt:39` | `const val` on a public `object` |
| `CorpusManifest.entry(repoRoot, name)` | `CorpusManifest.kt:53-63` | returns `CorpusEntry`, errors if absent or duplicated |
| `CorpusManifest.checkoutDir(repoRoot, name)` | `CorpusManifest.kt:72-75` | `<repoRoot>/test/corpus/<name>` |
| `CorpusEntry.luaLevel` (`:18`) / `.moduleRoot` (`:25`) / `.name` (`:15`) | `CorpusManifest.kt:14-26` | public `data class`. The three §2.0.1 reads directly. `.commit` (`:16`) is read **for** it, by `CorpusGuards.assertCorpusFetched` (`CorpusGuards.kt:26-30`), which is what makes §2.0.3's size guard an assertion about the *file* rather than the checkout |
| `CorpusEntry.roots` — **listed as NOT required** | `CorpusManifest.kt:17` | An earlier revision cited it as needed; §2.0.1 never reads it. `root` arrives as `corpusFile`'s second parameter, pinned per harness in §2.0.3 (`"lua"` for penlight, `"src"` for luarocks) and read there from `corpus.json`. A wrong value is caught by `copyDirectoryToProject` failing on a path that does not exist, so nothing is lost by not consulting the field |
| `CorpusGuards.assertCorpusFetched(repoRoot, entry)` | `CorpusGuards.kt:17-31` | `internal fun` on `internal object` — reachable, same test module |
| `LuaProjectSettings.getInstance(project).state.languageLevel` | `LuaProjectSettings.kt:52` | `var`, default `LUA54` |
| `LuaProjectSettings.getInstance(project).state.sourcePath` | `LuaProjectSettings.kt:73` | `var` |
| `VirtualFile.findFileByRelativePath(relPath)` | platform, `core-api/…/vfs/VirtualFile.java:329` | `@Nullable`, hence `checkNotNull` |
| `CodeInsightTestFixture.copyDirectoryToProject` | platform; used at `CorpusSweep.kt:214` and `LuaInspectionParityTest.kt:73` | returns the copied root `VirtualFile` |

**`CorpusSweep.applyModuleRoot` is `private` (`CorpusSweep.kt:139`), so it cannot be called** — its
**three** statements, one per line at `:144-146`, are restated above rather than invoked:

```kotlin
// CorpusSweep.kt:144-146, verbatim
val moduleRoot = entry.moduleRoot ?: return
val base = File(checkoutDir, moduleRoot).canonicalPath
LuaProjectSettings.getInstance(fixture.project).state.sourcePath = "$base/?.lua;$base/?/init.lua"
```

Three statements, three lines — the count is stated once here and in §2.0.1's KDoc, which an earlier
revision had disagreeing (*"two"* against *"three"*). The only difference in the restatement is the
source of the project handle (`myFixture.project` for `fixture.project`) and of the checkout dir
(`CorpusManifest.checkoutDir(repoRoot, entry.name)` for the `checkoutDir` parameter). §2.6's
objection to a standalone corpus class (*"a second copy of `LuaCorpusSweepTest.setUp` … that can
drift from the real one"*) applies to this in miniature. It is accepted here and not there for a
stated reason: what is duplicated is three lines with no branching, not a whole sweep, and §2.0.3
pins the one value that duplication could get wrong.

#### 2.0.3 The pinned inputs, and the assertions that stop a silent drift

| harness | corpus | `root` | `relativePath` | `entry.luaLevel` | `entry.moduleRoot` |
| :-- | :-- | :-- | :-- | :-- | :-- |
| §2.2, §2.3 | `penlight` | `lua` | `pl/stringx.lua` | `LUA51` | `lua` |
| §2.2, §2.3 | `luarocks` | `src` | `luarocks/fs/lua.lua` | `LUA51` | *(none)* |
| §2.5 DR-11 | `penlight` | `lua` | `pl/config.lua` **and** `pl/stringx.lua` | `LUA51` | `lua` |

All six values are read from `tooling/corpus/corpus.json` at `99b45f92`, not assumed: penlight is
`"luaLevel": "LUA51", "roots": ["lua","spec"], "moduleRoot": "lua"`; luarocks is
`"luaLevel": "LUA51", "roots": ["src","spec"]` with **no** `moduleRoot` key. **Note that penlight
does declare a `moduleRoot` and luarocks does not** — which is why (3) is a real step and not
ceremony, and why the harness must read `entry.moduleRoot` rather than hard-code a path. (The
manifest's own `$comment` block and `CorpusEntry.moduleRoot`'s KDoc both still say every pinned
project omits it; both are stale against the file they document. Not this feature's to fix — recorded
in risks-and-gaps as an observation so the next reader does not trust the comment over the data.)

Two guards, because a fixture that quietly measures the wrong thing is this plan's recurring enemy
(`LuaInspectionParityTest.assertAnchored`, KDoc `:85-92`, exists for exactly this):

1. **Size identity.** Each harness asserts the file it obtained is the file this design was written
   against, by length in characters:
   `assertEquals("corpus pin moved", 26_156, psiFile.textLength)` for `penlight/lua/pl/stringx.lua`
   and `40_093` for `luarocks/src/luarocks/fs/lua.lua`. Both figures are measured —
   `wc -c` reports `26156 test/corpus/penlight/lua/pl/stringx.lua` and
   `40093 test/corpus/luarocks/src/luarocks/fs/lua.lua` — and `CorpusEntry.commit` is already
   identity-checked by `assertCorpusFetched`, so this asserts the *file* rather than the checkout.
2. **Non-vacuity.** `assertTrue("empty type graph — the census would be vacuous",
   graphOf(LuaTypesSnapshot.forFile(psiFile)).nodes.isNotEmpty())` before any percentage is computed.
   A crashed or unparsed file yields an empty graph, and `0 of 0` renders as a coverage figure
   without ever looking wrong.

#### 2.0.4 What this setup costs, and what it does not

`copyDirectoryToProject` copies the whole declared root, not one file — **39 `.lua` files** under
`test/corpus/penlight/lua` and **104** under `test/corpus/luarocks/src`, counted with
`find … -name '*.lua' | wc -l`. That is setup, not measurement: §2.4's timings are taken inside the
harness with `medianMs` on `forFile`/CFG construction only, and no §4.n figure includes copy time.

A single-file `copyFileToProject` (the shape `LuaRecursiveReferenceTest.kt:103` uses) would be
cheaper and is **not** used: it would make these harnesses' input differ in kind from the sweep's,
which is the drift §2.6 rejects a standalone corpus class for. The directory copy is
`LuaInspectionParityTest.kt:73`'s shape exactly.

**One ordering constraint, because the settings are project-wide and the fixture is one project.**
`corpusFile` writes `sourcePath` and `languageLevel` on every call, so a harness that touches two
corpora must **finish** with one before calling `corpusFile` for the other. §2.2 and §2.3 therefore
structure each test as: `corpusFile("penlight", …)` → measure → print, then
`corpusFile("luarocks", …)` → measure → print. Interleaving them would measure luarocks under
penlight's `sourcePath`.

### 2.1 `net.internetisalie.lunar.analysis.AnalysisSevenReachingDefsSpikeTest`

- **Responsibility**: run §3.1's reaching-definitions walk over the shipped CFG on eight pinned
  fixtures and print the answer beside the type graph's `upSet`. Feeds DR-01, DR-03, DR-04, DR-05.
- **Threading**: read action per fixture.
- **Collaborators**: `ControlFlowCache.getControlFlow`, `ControlFlowUtil.iteratePrev`,
  `LuaReadWriteInstruction`, `AccessType`, `LuaNameReference` (via `PsiElement.getReference()`),
  `LuaTypesSnapshot.forFile`.
- **Key API**:

  ```kotlin
  @RunWith(JUnit4::class)
  class AnalysisSevenReachingDefsSpikeTest : BasePlatformTestCase() {
      /** One row of §4.1's RD dump. */
      private data class Probe(val id: String, val source: String, val readName: String)

      @Test fun testReachingDefinitionsAgainstUpSet()   // all eight fixtures, prints §4.1
      @Test fun testDiamondAndLoopSoundness()           // §3.1 step 7's shared-visited check

      /** §3.1. Returns the WRITE instructions reaching [target], nearest-first per path. */
      private fun reachingWrites(
          flow: LuaControlFlow,
          target: LuaReadWriteInstruction,
          sameBinding: (LuaReadWriteInstruction, LuaReadWriteInstruction) -> Boolean,
      ): List<LuaReadWriteInstruction>

      /** §3.2. The two match predicates the spike compares. */
      private fun matchesByName(write: LuaReadWriteInstruction, read: LuaReadWriteInstruction): Boolean
      private fun matchesByBinding(write: LuaReadWriteInstruction, read: LuaReadWriteInstruction): Boolean

      /** §3.3. Every scope owner whose CFG could contain an instruction for [name]. */
      private fun ownersFor(file: PsiFile): List<ScopeOwner>
  }
  ```

- **The eight fixtures** are fixed here so the harness is not a design decision. `count` is
  `---@param n number local function count(n) end` in every one; `cond`/`c`/`e` are undeclared
  globals, deliberately (they must not themselves be modelled).

  | id | source (after the `count` preamble) | why |
  | :-- | :-- | :-- |
  | `P1-killed-unknown` | `local d = wx.thing` / `d = "s"` / `count(d)` | requirements TC-2a. The false negative `-02` exists to fix |
  | `P2-live-unknown` | `local d = wx.thing` / `if cond then d = "s" end` / `count(d)` | TC-2b, the control. `LuaUnknownProvenanceTest.kt:29-45` verbatim |
  | `P3-shadowed` | `local x = 1` / `do local x = "s" end` / `count(x)` | TC-2c. F4's consequence |
  | `P4-closure` | `local d = 1` / `local function f() d = "s" end` / `f()` / `count(d)` | TC-2d. F5's consequence |
  | `P5-diamond` | `local d = 1` / `if cond then d = "s" else d = "t" end` / `count(d)` | §3.1 step 7 — both arms must reach, `1` must not |
  | `P6-loop` | `local d = 1` / `while cond do count(d) ; d = "s" end` | back-edge: both `1` and `"s"` reach |
  | `P7-goto` | `local d = 1` / `goto skip` / `d = "s"` / `::skip::` / `count(d)` | irreducible-ish flow; `resolveGoto` (`LuaControlFlowBuilder.kt:86-93`) is block-scoped |
  | `P8-param` | `local function g(p) count(p) end` | the parameter WRITE emitted at `LuaControlFlowBuilder.kt:36-40` must be found as a definition |

### 2.2 `net.internetisalie.lunar.analysis.AnalysisSevenCoverageSpikeTest`

- **Base class**: `AnalysisSevenFixtureBase` (§2.0.1). **It reads corpus files, so §2.0 is
  mandatory** — the two files are obtained as
  `corpusFile("penlight", "lua", "pl/stringx.lua")` and
  `corpusFile("luarocks", "src", "luarocks/fs/lua.lua")`, in that order and **not interleaved**
  (§2.0.4), with §2.0.3's two guards asserted before any percentage is printed.
- **Responsibility**: the census that sizes direction **A**. For one corpus file, count how many of
  the type graph's `ValueNode`s have a CFG instruction anchored at the same PSI element, and bucket
  the misses by PSI class. Feeds DR-06.
- **Threading**: read action.
- **Collaborators**: `LuaTypeGraph.nodes` (public, `LuaTypeGraph.kt:41`), `TypeNode.element`,
  `ControlFlowCache`, `Instruction.getElement()`.
- **Key API**:

  ```kotlin
  @Test fun testValueNodeCoverageByTheCfg()   // prints §4.2
  private fun cfgElements(file: PsiFile): Set<PsiElement>   // union over ownersFor(file)
  private fun valueNodeElements(file: PsiFile): List<Pair<PsiElement, String>>  // element to node class
  ```

#### 2.2.1 How the harness reaches the graph — measured, not assumed

**Verified visibility (this decided the mechanism, so it is written down):**

| symbol | modifier | reachable from `src/test/kotlin`? |
| :-- | :-- | :-- |
| `LuaTypeGraph.nodes: List<TypeNode>` | public, insertion-ordered (`LuaTypeGraph.kt:38-41`) | yes |
| `TypeNode.element`, `ValueNode.write`, `VariableNode.upSet`/`downSet` | public (`LuaTypeNodes.kt:11-80`) | yes |
| `LuaTypesVisitor.buildSnapshot(file)` (companion, `LuaTypesVisitor.kt:1533`), `LuaTypesVisitor.KEY` (`:1500`) | `internal` | **yes** — the Kotlin test compilation is associated with main. Proven by **two** existing call sites, both re-checked at `99b45f92`: `TypeElevenGenerationSignalTest.kt:172` calls `LuaTypesSnapshot.dependenciesFor`, which is `internal fun` at `LuaTypes.kt:281`; `TypeElevenGenerationSignalTest.kt:239` reads `LuaTypesVisitor.KEY`, `internal val` at `LuaTypesVisitor.kt:1500`. **An earlier revision claimed a third, and it was false**: `LuaTypeSourceRecorderTest.kt:36` touches `LuaTypeSourceRecorder.snapshotFrames`, a **public** `val` (`LuaTypeSourceRecorder.kt:98`) on a **public** `object` (`:28`), so it proves nothing about `internal` access. Two sites are enough; the point is that the association exists, not how many use it |
| `LuaTypesSnapshot.graph`, `LuaTypesSnapshot.elementNodes` | **`private`** (`LuaTypes.kt:55-64`) | **no** |
| `LuaTypesVisitor.graph`, `LuaTypesVisitor.elementNodes` | **`private`** (`:22-23`) | **no** |

So the node list is public and the *handle to the graph* is not. The harness bridges that with
**exactly one reflective field read**, isolated in one helper:

```kotlin
/** The only reflective access in this spike. Self-describing on BOTH failure modes — see below. */
private fun graphOf(types: LuaTypes): LuaTypeGraph {
    // (1) The receiver. `LuaTypes.forFile` is declared to return the INTERFACE (`LuaTypes.kt:237`),
    // and the field below is declared on the class, so a non-snapshot receiver would otherwise die
    // inside `Field.get` with a bare IllegalArgumentException naming neither type.
    val snapshot =
        types as? LuaTypesSnapshot
            ?: error(
                "LuaTypes.forFile returned ${types.javaClass.name}, not LuaTypesSnapshot — " +
                    "a second LuaTypes implementation exists and §2.2.1's reflective read no " +
                    "longer applies; re-derive the graph handle before trusting any census",
            )
    // (2) The field name. Expected to be `graph` for a `private val` constructor property, but this
    // design does not assert that from reading. Print the real names so the first run fixes itself.
    val field =
        runCatching { LuaTypesSnapshot::class.java.getDeclaredField("graph") }
            .getOrElse { failure ->
                error(
                    "no field 'graph' on LuaTypesSnapshot; declared fields = " +
                        LuaTypesSnapshot::class.java.declaredFields.joinToString { it.name } +
                        " (cause: $failure)",
                )
            }
    return field.apply { isAccessible = true }.get(snapshot) as LuaTypeGraph
}
```

Everything downstream of that is ordinary public/`internal` API. **Two claims here are taken from
convention rather than from a run, and each gets its own self-describing failure** rather than a bare
`getDeclaredField(...).get(...)`:

| claim | why it is not asserted from reading | what fires if it is wrong |
| :-- | :-- | :-- |
| the backing field is named `graph` | Kotlin's backing-field naming for a `private val` constructor property is a compiler convention | branch (2) — prints every declared field name |
| the value is a `LuaTypesSnapshot` | `forFile` is typed to the interface `LuaTypes` (`LuaTypes.kt:20`, `:237`); `LuaTypesSnapshot` is its only implementation **today**, and "only implementation today" is not a guarantee | branch (1) — names the actual class |

**The pattern behind "only implementation" is stated because the obvious one finds nothing.** The
declaration is ktlint-wrapped — `class LuaTypesSnapshot(` on `LuaTypes.kt:55`, the supertype clause
nine lines later on `:64` — so `grep -rnE "(class|object) [A-Za-z]+.*: LuaTypes\b" src/main/kotlin/`
returns **no match at all**. What was actually run is
`grep -rn ") : LuaTypes {" src/main/kotlin/` → one line, `LuaTypes.kt:64:) : LuaTypes {`, plus the
same pattern over `src/test/kotlin/` → none. Note that the loose `grep -rn ": LuaTypes {"` returns
**three** lines and is the wrong count: `:237` and `LuaTypesVisitor.kt:1533` are *return* types
(`fun forFile(...): LuaTypes {`, `fun buildSnapshot(...): LuaTypes {`), not supertype clauses.

**Why reflection and not a temporary `internal` test seam.** The repo has the seam convention —
`LuaTypeGraph.compatMemoSize()` is `@TestOnly internal fun` added purely so a test can observe
(`LuaTypeGraph.kt:851-853`) — and copying it here would be *reusing a convention on precedent*. It is
the wrong choice for a **throwaway** probe for one asymmetric reason: a widened production modifier
that is not reverted **ships silently**, while a deleted test file cannot leave anything behind. The
spike's whole contract is that it vanishes (§2), so the mechanism that fails safe under an
incomplete revert wins. If a permanent seam is ever wanted, that is a decision for §5, made with the
direction known.

### 2.3 `net.internetisalie.lunar.analysis.AnalysisSevenJoinSpikeTest`

- **Base class**: `AnalysisSevenFixtureBase` (§2.0.1). Same two files, same order and same guards
  as §2.2 — `corpusFile("penlight", "lua", "pl/stringx.lua")` then
  `corpusFile("luarocks", "src", "luarocks/fs/lua.lua")`.
- **Responsibility**: the join census that sizes directions **B** and **C** — can a `VariableNode`
  be reached from a CFG instruction, and vice versa, keyed on PSI identity? Feeds DR-07, DR-09.
- **Threading / graph access**: read action; obtains the graph through §2.2.1's `graphOf`.
- **Key API**:

  ```kotlin
  @Test fun testEveryVariableNodeJoinsToAnInstruction()   // prints §4.3
  @Test fun testInstructionOrderAgainstNodeCreationOrder() // prints §4.4

  /** §3.5. Length of the longest common subsequence, PSI identity as equality. */
  private fun lcsLength(left: List<PsiElement>, right: List<PsiElement>): Int
  ```

### 2.4 `net.internetisalie.lunar.analysis.AnalysisSevenCostSpikeTest`

- **Responsibility**: NFR-1 and NFR-2 numbers — the cost of building every owner's CFG for a file
  against the cost of `LuaTypesSnapshot.forFile` on the same file, **medians of five cold samples**.
  Feeds DR-08, DR-13.
- **Medians of five, not one shot**: `COMP-09-00-DR-08` recorded −60 % run-to-run spread and one
  flipped verdict from single-shot timing. A cold sample means a fresh `configureByText` of a copy
  of the file under a distinct name, so no `CachedValuesManager` entry is warm.
- **Key API**:

  ```kotlin
  @Test fun testCfgBuildCostAgainstSnapshotCost()  // prints §4.5
  private fun medianMs(samples: Int = 5, block: () -> Unit): Long
  ```

### 2.5 `net.internetisalie.lunar.analysis.AnalysisSevenDescopeSpikeTest`

- **Base class**: `AnalysisSevenFixtureBase` (§2.0.1) — **its DR-11 half reads corpus files, so
  §2.0 is mandatory**; its DR-10 half does not (BUG-435's fixture is `configureByText`, not corpus).
  A single class carrying both is still correct: §2.0's setup is inert for a test that never calls
  `corpusFile`, beyond one `VfsRootAccess.allowRootAccess` call in `setUp`.
- **Responsibility**: the two premise checks that can delete requirements. Feeds DR-10, DR-11.
- **DR-10 (`-03`)**: on [[BUG-435]]'s fixture, print the `LuaGraphType` of the node
  `injectNarrowedBinding` installs (`LuaTypesVisitor.kt:475-478`) **and** the type of the binding it
  displaced (`scope.lookup(guard.variableName)` at `:468`). BUG-435 says explicitly: *"confirm that
  by reading the node the guard installs, not by assuming this paragraph."*
- **DR-11 (`-04`)**: re-run BUG-428's two residual sites and print, per site, the anchor element,
  the message, and the `upSet` membership of the reported variable. **Both files come from
  `corpusFile("penlight", "lua", …)`** — `pl/config.lua` and `pl/stringx.lua`, one call each, the
  second reusing the already-copied `lua` root. The two sites, **read from the fetched corpus at
  `99b45f92` rather than from BUG-428's prose**:

  | site | line 131 | line 132 |
  | :-- | :-- | :-- |
  | `penlight/lua/pl/config.lua` | `    local function check_cnfg (var,def)` ← **the anchor** | `        local val = cnfg[var]` |
  | `penlight/lua/pl/stringx.lua` | *(`:231`)* `local function _find_all(s,sub,first,last,allow_overlap)` ← **the anchor** | — |

  Both anchors are therefore **function-declaration lines**, which is what makes BUG-428's
  description of the anchoring path checkable. `requirements.md` TC-4a once glossed
  `config.lua:131` as `local val = cnfg[var]`, which is `:132`; the corrected TC-4a and
  `risks-and-gaps.md` DR-11 now agree with the table above. The harness prints the anchor's own
  `element.text` so a future corpus re-pin shows up as a changed dump rather than as a silently
  different measurement.
- **Key API**:

  ```kotlin
  @Test fun testWhatTheGuardInstalls()          // prints §4.6
  @Test fun testWhatTheBugFourTwoEightResidualIs() // prints §4.7
  ```

### 2.6 Corpus instrumentation — a temporary patch, not a new class

DR-02 and DR-12 need a **per-site dump** (`file:line`, tool id, message, source line) over the four
corpora. This is done by temporarily editing `CorpusSweep.accumulateHits`
(`src/test/kotlin/net/internetisalie/lunar/corpus/CorpusSweep.kt:263-287`), which is exactly the
method BUG-428's 2026-08-20 re-measurement used (*"`LuaCorpusSweepTest` instrumented to print
`file:line`, message and source line"*).

**Why a patch and not a new `AnalysisSevenCorpusDumpTest`** — and this is a convention that is
*chosen*, not inherited:

- `build.gradle.kts:272-283` excludes `*Corpus*` from the routine loop, so a new class named
  `…Corpus…` would be swept into the `--tests '*Corpus*'` filter. `LuaCorpusSweepTest`'s own KDoc
  (`:27-31`) and `LuaInspectionParityTest`'s (`:32-36`) both record that **anything inside that
  filter shares the sweeps' JVM and shifts their counts** — measured at +12 on luacheck's
  `LuaTypeAssignability`. A dump class inside the filter would move the very baseline it is dumping.
- A class *outside* the filter would need its own corpus fetch, `VfsRootAccess` grant, module-root
  application and language-level pin — a second copy of `LuaCorpusSweepTest.setUp`
  (`:37-65`) and `CorpusSweep.run` (`:75-…`) that can drift from the real one. `LuaInspectionParityTest`
  is the standing example of the cost: it maintains its own `setUp` (`:45-52`) and needed an
  explicit `assertAnchored` (`:93-100`, KDoc `:85-92`) precisely because *"a probe that did not
  reproduce the sweep's setup reported `undeclaredAlone=0 withTypes=0` and read as perfect parity."*

  **§2.0 is this design's answer to that cost, not a waiver of it.** The three corpus-reading
  harnesses are outside the filter and therefore *do* carry all four things — §2.0 specifies them
  once, in one abstract base, reading every value from `CorpusManifest` rather than restating it,
  and adds §2.0.3's two guards so a drifted setup fails loudly instead of reporting a number. What
  §2.6 still refuses is a **fourth full sweep**: a class that re-implements `CorpusSweep.run`'s
  per-file walk, oracle and hit attribution. Reading two named files is not that.

The patch is added, run, its output pasted into this document, and **reverted before anything else
happens**. `git diff src/test/kotlin/net/internetisalie/lunar/corpus/` must be empty afterwards —
and that path check is **necessary, not sufficient**: T0.9 also patches production source
(`LuaTypeGraph.kt:352-353`), so the phase's real end-state is §1.3's whole-tree `git status --short`
**and** `git diff` both empty.

### 2.7 The contract `-02` must satisfy — fixed now, sited later

`-02`'s **behaviour** is direction-independent and is fixed here so §5 cannot quietly redefine it.
Its **home** (package, class, whether it is a service, an index, or a method on `LuaTypeGraph`) is an
output of §3.7 and is deliberately unnamed.

Whatever ships must replace this expression, and only this expression:

```kotlin
// LuaTypeGraph.kt:352-353, today
val unknownProvenance =
    currentUpSet.any { it is ValueNode && isUnknown(it.write) }
```

with a computation whose value is `true` **iff at least one definition that actually reaches this
use is unknown**, where:

- *definition* means a `ValueNode` in `node.upSet` (unchanged);
- *unknown* means `LuaTypeGraph.isUnknown` (`:252-255`), unchanged;
- *reaches* is §3.1's relation, evaluated per (variable, use) pair rather than per variable.

Three properties are non-negotiable, each already paid for by an earlier bug:

1. **It may only ever change a tier, never skip work.** BUG-416's rule — suppression must not
   *enable* anything — and F13: `reportIncompatible` must still run and still wire member edges
   (`LuaTypeGraph.kt:257-285`). A `return` in place of a tier change fails.
2. **It must not consult the merged union.** BUG-441 RC-2, measured across three failed attempts:
   `checkTypes` checks *each reaching definition against the demand on its own*, so any design
   aimed at `resolveWrite`/`simplify`/`Union.create` is aimed at an object the diagnostic path never
   looks at.
3. **It must be computable without a hard reference to `Project`, `Editor`, `PsiFile` or
   `VirtualFile`** held in a long-lived field (engineering contract §4). `LuaTypeGraph` is per-file
   and short-lived; anything cached across files uses `CachedValuesManager` keyed on PSI, as
   `ControlFlowCache` already does (F8).

## 3. Algorithms

### 3.1 Backward reaching definitions over `LuaControlFlow`

- **Input**: `flow: LuaControlFlow`; `target: LuaReadWriteInstruction` with
  `accessType == AccessType.READ`; `sameBinding: (write, read) -> Boolean`.
- **Output**: `List<LuaReadWriteInstruction>`, every WRITE that reaches `target` without being
  overwritten on that path. Order is `iteratePrev`'s pop order; callers must treat it as a **set**
  and compare set-wise.

**Steps**:

1. `val instructions: Array<Instruction> = flow.instructions` (`ControlFlow.getInstructions()`).
2. `val start = target.num()`.
3. **Assert the index identity rather than assuming it**: `require(instructions[start] === target)`.
   F16 argues `num()` is the array index, but the argument depends on there being no
   `TransparentInstruction` (F6) — assert it in code so the algorithm is self-checking if that ever
   changes.
4. `val reached = LinkedHashSet<LuaReadWriteInstruction>()`.
5. Call `ControlFlowUtil.iteratePrev(start, instructions) { instruction -> … }` with this closure,
   evaluated in exactly this order:
   1. `if (instruction === target) return@iteratePrev Operation.NEXT` — the read itself is not a
      definition; keep walking to its predecessors.
   2. `if (instruction !is LuaReadWriteInstruction) return@iteratePrev Operation.NEXT` — entry, exit,
      statement and condition nodes are transparent to this analysis.
   3. `if (instruction.accessType != AccessType.WRITE) return@iteratePrev Operation.NEXT` — a READ
      neither generates nor kills.
   4. `if (!sameBinding(instruction, target)) return@iteratePrev Operation.NEXT` — a write to a
      different variable is transparent.
   5. `reached.add(instruction); return@iteratePrev Operation.CONTINUE`.
6. Return `reached.toList()`.

**Step 5.5 is the kill rule, and `CONTINUE` is the whole of it.** The platform documents
`Operation.CONTINUE` as *"ignore previous/next elements processing for the node, however it doesn't
stop the iteration process"* (`ControlFlowUtil.java:131-135`); mechanically, `iterate` skips the
`for (Instruction pred : nextToProcess)` push loop for that node — `:109-111` `continue`s past `:118-124`. So a
matching write is recorded and its own predecessors are not explored **along that path** — which is
exactly "a definition kills earlier definitions of the same binding". `BREAK` would abandon the
whole walk and is never correct here; `NEXT` at step 5.5 would make the query "all definitions",
which is what `upSet` already is.

**Step 7 — the shared-`visited[]` question, which must be checked and not argued.**
`ControlFlowUtil.iterate` marks visitation in **one** `boolean[] visited` for the whole walk
(`:99-101, :119-123`), not per path. The design's position is that this is sound *for this closure*,
because (a) `CONTINUE` prevents a killed node's predecessors from ever being pushed, so they are
never marked through a killed path, and (b) the closure's verdict for a node depends only on that
node, so processing it once loses nothing. **That is an argument, not a measurement.** `P5-diamond`,
`P6-loop` and `P7-goto` exist to check it by execution, and `testDiamondAndLoopSoundness` asserts
their expected sets outright:

All four are evaluated **under `matchesByBinding`** (§3.2) and compared as `defSiteOf` sets
(§3.1a):

| fixture | expected reaching set for the `count(...)` read |
| :-- | :-- |
| `P3-shadowed` | `{local x = 1}` — the inner `do local x = "s" end` is a **different binding** and must not appear |
| `P5-diamond` | `{d = "s", d = "t"}` — the `local d = 1` write is killed on both arms |
| `P6-loop` | `{local d = 1, d = "s"}` — the back edge carries the loop-body write |
| `P7-goto` | `{local d = 1}` — the `goto` skips `d = "s"` |

If any of the four disagrees, **the disagreement is DR-03's finding**, it is recorded verbatim in
§4.1, and D1 cannot fire (§3.7).

- **Complexity**: O(V + E) per query, V = instruction count. `iteratePrev` visits each instruction at
  most once.
- **Cancellation**: `iterate` already calls `ProgressManager.checkCanceled()` on every pop
  (`ControlFlowUtil.java:104`), which is why using it satisfies NFR-2 where a hand-rolled walk would
  not (F7).

### 3.1a Comparing the CFG's answer with the type graph's — the two are not the same objects

§3.1 returns **instructions**; the `upSet` holds **nodes**; and they are anchored on different PSI.
A naive element comparison silently reports "disagrees" everywhere, so the comparison is specified.

**Grounded anchor facts** (this is why the step is needed at all):

| declaration | type-engine anchor | CFG anchor | identical? |
| :-- | :-- | :-- | :-- |
| `local d = expr` | the `LuaNameRef` — `graph.variable(nameRef)` (`LuaTypesVisitor.kt:748`) | the same `LuaNameRef` — `LuaControlFlowBuilder.kt:309` | **yes** |
| the *value* `expr` | the **RHS expression** — `collectRhsNodes` → `graph.value(expr, …)` (`LuaTypesVisitor.kt:225-256`) | there is **no instruction** for it (F11) | n/a |
| `local function f` | the **`LuaLocalFuncDecl`** — `graph.variable(o)` (`LuaTypesVisitor.kt:767`) | the `LuaNameRef` — `LuaControlFlowBuilder.kt:342` | **no** — the decl is an *ancestor* of the nameRef |

The third row is exactly why DR-07 reports an **ancestor** join beside the exact-identity one
(§3.5): a real, already-present asymmetry, not a hypothetical.

**`defSiteOf` — the common key.** Both sides are projected to the innermost enclosing `LuaStatement`,
which is the granularity "which assignment reaches here" actually means:

```kotlin
private fun defSiteOf(element: PsiElement): LuaStatement? =
    PsiTreeUtil.getParentOfType(element, LuaStatement::class.java, /* strict = */ false)
```

This is `LuaUnreachableCodeInspection.statementReachability`'s own attribution rule verbatim
(`:100`) — reused rather than reinvented, and for the same reason: an instruction belongs to the
statement it is part of.

- `defSitesFromCfg` = `reachingWrites(...).mapNotNull { defSiteOf(it.element) }`, as an identity set.
- `defSitesFromUpSet` = the target variable's `upSet`, filtered to `ValueNode`, mapped through
  `defSiteOf(node.element)`, as an identity set. For `local d = wx.thing` the `ValueNode`'s element
  is the RHS expression and the CFG write's element is the name — **both project to the same
  `LuaLocalVarDecl`**, which is what makes the two comparable at all.

**Finding the target variable's `VariableNode`** — **four** steps, in order, first hit wins:

1. `val identifier: PsiElement? = declarationOf(target)` (§3.2). Phase-1 resolution lands on the
   IDENTIFIER **leaf**, and that is true of **every** assignment in the resolver, not of a sample.
   The count below is from `grep -n "result =" LuaScopeProcessor.kt` — **no trailing space**, which
   matters: `:88` is a ktlint-wrapped assignment (`result =` alone on its line, the value on
   `:89-90`), so `grep -c "result = "` **with** the trailing space returns `9` and undercounts by
   one. The pattern as written returns **ten** sites — `:45, 54, 63, 73, 82, 88, 102,
   112, 121, 131` — across **nine** `when` branches (`LuaFuncDecl` carries two: the function name at
   `:82` and implicit `self` at `:88-90`). All ten assign an `identifier`:
   `attName.nameRef.identifier` (`:45, 63`), `element.nameRef.identifier` (`:54`),
   `element.nameRef?.identifier` (`:73`), `element.funcName.nameRef.identifier` (`:82`),
   `element.funcName.funcNameMethod!!.nameRef.identifier` (`:88-90`), `nameRef.identifier`
   (`:102, 121, 131`) and `element.identifier` (`:112`). An earlier revision listed five of the ten
   and read as though it had listed all of them.

   **Nine of the ten resolve to an identifier whose parent is a `LuaNameRef`. One does not.**
   `:112` is `result = element.identifier` on a `LuaNumericForStatement`, whose `getIdentifier()` is
   `findNotNullChildByType(IDENTIFIER)` on the statement itself
   (`src/main/gen/net/internetisalie/lunar/lang/psi/impl/LuaNumericForStatementImpl.java:44-46`;
   the grammar is `numericForStatement ::= FOR IDENTIFIER '=' expr ',' expr [',' expr] DO block END`,
   `lua.bnf:152`, with **no** `nameRef` wrapper). Its `.parent` is the `LuaNumericForStatement`, not
   a `LuaNameRef`. An earlier revision claimed *"there is no branch that returns a declaration
   element instead, so step 2 may take `.parent` unconditionally"*; that is false for `for i = 1, n`.
2. **The declaring anchor**, which is `.parent` *except* for the numeric-for control variable:

   ```kotlin
   /** §3.1a step 2. The element both graphs key this declaration on, or null if step 1 missed. */
   private fun declaringAnchorOf(identifier: PsiElement?): PsiElement? =
       when {
           identifier == null -> null
           identifier.parent is LuaNumericForStatement -> identifier
           else -> identifier.parent
       }

   // at the call site:
   val declaringAnchor = declaringAnchorOf(identifier)   // null falls through to step 4
   ```

   For the nine `LuaNameRef` cases the anchor is that `LuaNameRef` (`LuaNameRefElementImpl.getName`
   reads its `IDENTIFIER` child, `LuaBaseElements.kt:81`). For the numeric-for case the anchor is the
   **IDENTIFIER leaf itself**, which is also what the CFG keys on: `visitNumericForStatement` emits
   `LuaReadWriteInstruction(builder, id, id.text, AccessType.WRITE)` over
   `numericForStatement.getIdentifier()` (`LuaControlFlowBuilder.kt:219-220`) — unlike
   `visitGenericForStatement`, which uses the `nameRef` (`:245-246`). Taking `.parent` here would
   compare a `LuaNumericForStatement` against an IDENTIFIER leaf and report a spurious disagreement.

   **Latent for Phase 0, load-bearing for §5.** None of §2.1's eight fixtures targets a numeric
   `for`, so this branch is unreachable in the spike; §5 is instructed to reuse §3.1a, and a
   real-world query will hit it (`for i = 1, #t do … t[i] … end` is ubiquitous in the corpus).
3. With a non-null `declaringAnchor`:
   `graph.nodes.filterIsInstance<VariableNode>().firstOrNull { it.element === declaringAnchor }`
   **else** `…firstOrNull { PsiTreeUtil.isAncestor(it.element, declaringAnchor, false) }` — the
   fallback that row 3 of the anchor table above requires.
4. If `declaringAnchor` is null, or both lookups miss, print `varNode=NOT_FOUND` and set the
   fixture's `VERDICT` to `DISAGREES` with the reason. **Do not silently skip the fixture** — a probe that quietly drops its hard cases is the
   vacuous-measurement failure `LuaInspectionParityTest.assertAnchored` exists to prevent.

### 3.2 The two match predicates, and why both are measured

`sameBinding` is the parameter that decides whether the shipped CFG can answer `-02` at all (F4).

**`matchesByName`** — what the CFG supports today:

```
matchesByName(write, read) = write.variableName == read.variableName
```

**`matchesByBinding`** — the oracle, using the production resolver rather than a new one:

1. `fun declarationOf(instruction: LuaReadWriteInstruction): PsiElement?`
   1. `val element = instruction.element ?: return null`.
   2. `val reference = element.reference ?: return null`. For a `LuaNameRef` this is a
      `LuaNameReference` (`LuaBaseElements.kt:98-105` → `LuaNameReference.kt:28`).
   3. `return reference.resolve()`. Phase-1 (local) resolution returns the declaring **IDENTIFIER
      leaf**; Phase-2 (cross-file, stub-index) returns the declaration element —
      `LuaNameReference.isReferenceTo` (`:233-243`) documents both and normalizes with
      `declarationIdentifier`. The spike prints which phase answered by recording whether the result
      is in the same `containingFile`.
2. `matchesByBinding(write, read)`:
   1. `val w = declarationOf(write)`, `val r = declarationOf(read)`.
   2. `if (w != null && r != null) return w === r` — PSI identity, not equality.
   3. `if (w == null && r == null) return write.variableName == read.variableName` — both
      unresolved (an implicit global is the normal case); fall back to the name and **count this
      pair in the `unresolved=` field of §4.1** so the fallback's share is visible.

      **This field has a threshold and a consequence; it is not reported for interest.** Split it
      into the two populations §4.1 prints as `unresolved = <targetPairs>/<allPairs>`:
      - **`<allPairs>`, the denominator, has no threshold and is diagnostic only** — it counts
        every (write, read) pair the fixture evaluated, including the deliberately-undeclared
        globals `cond`, `c`, `e` and `wx` that §2.1's fixture table introduces precisely so they are
        *not* modelled. A non-zero denominator is the expected state and says nothing.
      - **`<targetPairs>`, the numerator, must be `0` for every one of the eight fixtures.** Each
        fixture's target (`d`, `x`, `p`) is a **declared local**, and §3.2's own edge note records
        why `declarationOf` is total on those: *"`local d = 1`'s `d` resolves to itself (its own
        IDENTIFIER)"*. A non-zero numerator therefore means `declarationOf` failed where it cannot,
        `matchesByBinding` silently degraded into `matchesByName`, and DR-03's `RD_OK` and DR-04's
        `NAME_DELTA` are both measuring the name predicate twice. Their sum over the eight fixtures
        is the input `UNRESOLVED_TARGET`, and **`UNRESOLVED_TARGET > 0` fires §3.7's DX** — probe
        defect, fix and re-run §2.1, no branch selected.
   4. `return false` — one resolved and one not is a genuine mismatch.

- **Edge — a declaration's own name.** `local d = 1`'s `d` resolves to *itself* (its own IDENTIFIER),
  so `declarationOf` is total on both writes and reads of a local. `isReferenceTo` explicitly
  excludes this from Find Usages (`:234-237`) but `resolve()` still returns it, which is what makes
  it usable as an identity key.
- **Edge — `null` reference.** `LuaNameRefBaseImpl.getReference` returns `null` when `getName()` is
  null (`LuaBaseElements.kt:99-104`). Treated as unresolved (step 2.3).
- **Edge — poly-variant.** `resolve()` returns `null` when `multiResolve` yields ≠ 1 result
  (`LuaNameReference.kt:228-231`). Also unresolved; MAINT-29-03 records the same trap for
  unused-locals. The spike prints the multi-resolve arity for every such case rather than hiding it.

**What the spike reports** is the **delta between the two predicates** on `P3-shadowed`: if
`matchesByName` returns the inner `"s"` write and `matchesByBinding` does not, F4's consequence is
confirmed with a fixture, and any `-02` must carry a binding step. Incidence is already known and is
**not re-derived**: `inspection.LuaShadowingVariable` sums to **320 sites over 419 corpus files**
(luacheck 10, luarocks 26, penlight 65, zerobrane 219 — read from the committed baselines). Note the
field: the `symbol.LuaShadowingVariable.*` rows are a **top-ten sample**, not a total
(`SYMBOLS_PER_INSPECTION = 10`, `CorpusSweep.kt:29`), and summing them understates zerobrane by
roughly half. 320 is itself a lower bound — the inspection counts *declarations*, while a by-name
query also conflates every **read** of either binding.

### 3.3 Owner enumeration and the closure gap

- **Input**: `file: PsiFile`. **Output**: `List<ScopeOwner>`.
- **Steps** — reuse `LuaUnreachableCodeInspection.scopeOwners` verbatim (`:64-70`), which is the
  shipped, tested answer to "which owners does `ControlFlowCache` accept":
  1. `add(file)`
  2. `addAll(PsiTreeUtil.findChildrenOfType(file, LuaFuncDecl::class.java))`
  3. `addAll(PsiTreeUtil.findChildrenOfType(file, LuaLocalFuncDecl::class.java))`
  4. `addAll(PsiTreeUtil.findChildrenOfType(file, LuaFuncDef::class.java))`
- **The closure probe** (`P4-closure`, DR-05) runs §3.1 **twice**: once against the CFG of the owner
  lexically containing the read, and once against the union of every owner's CFG in the file. It
  prints both sets. F5 predicts the first misses the write inside `f`; the union has no single
  instruction array, so a union query is **not** a `ControlFlowUtil` call and the harness prints
  `union=UNSUPPORTED` with the per-owner sets rather than inventing a cross-graph walk. **Inventing
  one would be the third analysis `ANALYSIS-07-05` forbids** — if a union is needed, that is a
  finding that selects D2, not something the spike papers over.

### 3.4 Value-node coverage census (sizes direction A)

- **Input**: one corpus file. **Output**: a percentage and a miss histogram.
- **Steps**:
  1. Build the type graph for the file: `val types = LuaTypesSnapshot.forFile(file)`, then
     `val graph = graphOf(types)` (§2.2.1). Using `forFile` rather than a hand-rolled
     `LuaTypesVisitor()` is deliberate — it is the path every production consumer takes, including
     the ambient-global seeding and `checkTypes()` run that `buildSnapshot` performs
     (`LuaTypesVisitor.kt:1533-1545`), so the census measures the graph users actually get.
  2. `valueElements` = `graph.nodes.filterIsInstance<ValueNode>().map { it.element }`, de-duplicated
     into an **identity-backed** ordered collection (one element commonly carries several nodes).
  3. `cfgElements` = the same identity set built from
     `ownersFor(file).flatMap { ControlFlowCache.getControlFlow(it).instructions.mapNotNull { i -> i.element } }`.
  4. `covered = valueElements.count { it in cfgElements }` — membership by identity, per step 2/3.
  5. `coverage = covered * 100.0 / valueElements.size`, printed to one decimal.
  6. Histogram the misses by `element.javaClass.simpleName`, descending by count, ties broken by
     class name ascending (so the output is stable across runs).
- **Choice of file, pinned so the number is comparable** — both paths and sizes confirmed present
  in the fetched corpus at `99b45f92`:
  - `test/corpus/penlight/lua/pl/stringx.lua` — 26 156 bytes, 917 lines. It is the file BUG-428's
    residual lives in, and penlight carries the LDoc constructs the type engine exercises most
    (`LuaCorpusSweepTest.kt:76-80`).
  - `test/corpus/luarocks/src/luarocks/fs/lua.lua` — 40 093 bytes, 1 310 lines. A second data point
    with no annotations at all, so the census is not a measurement of one file's LDoc density.

  **Two files, both named**, because one file's shape is not a corpus. Both are inside their
  corpus's declared `roots` (`tooling/corpus/corpus.json`: penlight `["lua","spec"]`, luarocks
  `["src","spec"]`), so they are files the sweep already indexes.
- **Prediction, which the run must confirm or refute**: F11 says the CFG has no
  `visitBinOpExpr`/`visitFuncCall`/`visitIndexExpr`/`visitTableConstructor`/`visitTerminalExpr`/
  `visitUnOpExpr`, so literals, calls, index expressions and table constructors should be the
  dominant misses. **This is a prediction from reading and is exactly what the census is for.**

### 3.5 Join census and order comparison (sizes directions B and C)

- **Join (DR-07)**: for every `VariableNode` in the file's graph, ask whether some CFG instruction in
  `ownersFor(file)` has `element === node.element`, or has an element that is an ancestor-or-self of
  it (`PsiTreeUtil.isAncestor(instructionElement, nodeElement, false)`). Report both numbers
  separately — exact-identity join and ancestor join — because C needs the first and B can live with
  the second.
- **Order (DR-09)** — fully specified, because "compare the orders" is exactly the kind of step a
  weak implementer would invent three different answers to:

  1. **Build `nodeOrder`.** Walk `graph.nodes` in creation order (`LuaTypeGraph.nodes` preserves it,
     `:38-41`). Map each node to `node.element`. **De-duplicate keeping the first occurrence** — one
     element commonly carries several nodes (a `VariableNode` plus a `ValueNode`), and keeping all of
     them would inflate the LCS with self-matches.
  2. **Build `cfgOrder`.** Concatenate, over `ownersFor(file)` in the order §3.3 produces them, each
     `flow.instructions` array in index order; map each instruction to `instruction.element`, drop
     nulls (entry/exit nodes have none — `Instruction.getElement()` is `@Nullable`); de-duplicate
     keeping the first occurrence.
  3. **Restrict each to the intersection.** Build `shared` as an **identity set** —
     `Collections.newSetFromMap(IdentityHashMap<PsiElement, Boolean>())` — filled from `nodeOrder`
     and then intersected against `cfgOrder`; filter both lists by membership. An identity set is
     specified rather than `HashSet`: whether a given `PsiElement` implementation inherits
     `Object.equals` is not something this design asserts from reading, and `IdentityHashMap` makes
     the question moot. After this step both lists are permutations of the same element set, which
     is what makes an LCS meaningful.
  4. **`lcsLength(left, right)`** — the standard O(n·m) dynamic program, stated so there is nothing
     to invent:
     - `dp` is an `(n+1) × (m+1)` `IntArray` grid, `dp[0][*] = dp[*][0] = 0`.
     - For `i in 1..n`, `j in 1..m`: `dp[i][j] = if (left[i-1] === right[j-1]) dp[i-1][j-1] + 1 else maxOf(dp[i-1][j], dp[i][j-1])`.
     - **Equality is PSI identity (`===`), never `equals`.**
     - Return `dp[n][m]`.
     - Guard: if `n.toLong() * m > 4_000_000`, print `lcs=SKIPPED size=<n>x<m>` and return `-1`
       rather than allocating — the two pinned files are ~1 000 nodes each, so this never fires, and
       it stops a larger file from turning a probe into a hang.
  5. **`lcsPct` = `lcs * 100.0 / minOf(nodeOrder.size, cfgOrder.size)`**, one decimal. After step 3
     the two sizes are equal, so `min` is a defensive spelling, not a semantic choice.
  6. **Divergences**: walk both restricted lists in parallel by index and report the first five `i`
     where `nodeOrder[i] !== cfgOrder[i]`, printing both elements' class and start offset.

  A high `lcsPct` means the CFG's instruction order is a legal construction order for the type graph
  (direction B is an increment); a low one means it is not (B is a rewrite). The threshold is 90 %
  and it is applied at §3.7's D3/D4 boundary.

### 3.6 Per-site corpus dump (DR-02, DR-12)

Temporary patch to `CorpusSweep.accumulateHits` (§2.6). Inside the existing
`fixture.doHighlighting(HighlightSeverity.WEAK_WARNING).forEach { info -> … }` loop
(`CorpusSweep.kt:271`), for every `info` whose `inspectionToolId` is non-null, print one line in the
format pinned at §4.8. Line number is computed once per file from the document:

```kotlin
val document = com.intellij.psi.PsiDocumentManager.getInstance(fixture.project)
    .getDocument(fixture.psiManager.findFile(entry.file) ?: return@forEach)
val line = document?.getLineNumber(info.startOffset)?.plus(1) ?: -1
```

DR-12 runs it once on `99b45f92` to record the **pre-change** site list (expected: 11 type-inspection
sites + 5 `LuaUnreachableCode` sites, per requirements' headroom table). DR-02 runs it a second time
with a **throwaway** `unknownProvenance` computed by §3.1 rather than by `upSet.any`, and diffs the
two site lists. **The diff is `-02`'s measured payoff** — the count of sites restored (and any
newly-suppressed) is the number branch D0 tests.

### 3.7 The decision procedure

Evaluated **in order; the first rule that fires wins.** Every threshold is stated; none is left to
judgement. `§4.n` names the output block each input comes from.

**Inputs**

| symbol | meaning | source |
| :-- | :-- | :-- |
| `FN` | does `P1-killed-unknown` show a false negative today (no ERROR where one is warranted)? | DR-01, §4.1 |
| `RESTORED` | corpus sites the §3.1 query **restores** vs BUG-441's gate — lines in the DR-02 dump and not in the DR-12 dump, restricted to `LuaTypeAssignability`/`LuaReturnTypeMismatch` | DR-02, §4.8 |
| `NEW_SUPPRESSED` | corpus sites the §3.1 query **silences** that BUG-441's gate did not — lines in the DR-12 dump and not in the DR-02 dump, same restriction. **The opposite direction of the same diff** | DR-02, §4.8 |
| `UNRESOLVED_TARGET` | over all eight §2.1 fixtures, the number of (write, read) pairs that fell through to §3.2's name fallback **while the target variable is a declared local** — i.e. `declarationOf` failed where it must not | DR-04, §4.1's `unresolved=` field |
| `RD_OK` | do `P3`, `P5`, `P6`, `P7` **all** match §3.1 step 7's expected sets **under `matchesByBinding`**? | DR-03, §4.1 |
| `NAME_DELTA` | does `matchesByName` differ from `matchesByBinding` on `P3-shadowed`? | DR-04, §4.1 |
| `CLOSURE_LOST` | does the lexically-enclosing owner's CFG miss `P4-closure`'s write? | DR-05, §4.1 |
| `COVERAGE` | `ValueNode`s with a CFG instruction, % (mean of the two pinned files) | DR-06, §4.2 |
| `JOIN` | `VariableNode`s joining a CFG instruction by exact PSI identity, % (mean of the two files) | DR-07, §4.3 |
| `COST` | added ms for all owners' CFGs as % of median `forFile`, medians of 5 — a **design obligation**, not a branch term (see below) | DR-08, §4.5 |
| `LCS` | longest-common-subsequence of `cfgOrder` and `nodeOrder`, % of the shorter | DR-09, §4.4 |

**Rules — ordered, first match wins, and exhaustive by construction**

- **DX — the probe is invalid; nothing is selected and Phase 0 does not exit.** If
  `NEW_SUPPRESSED > 0` **or** `UNRESOLVED_TARGET > 0`:
  the measurement, not the CFG, is what failed, and no branch below may be read.
  - `NEW_SUPPRESSED > 0` means T0.9's throwaway `unknownProvenance` **suppressed a diagnostic
    BUG-441's gate emitted**. A reaching-definitions answer is strictly more precise than
    "any unknown anywhere in the `upSet`", so it can only ever *restore* sites; a suppression in the
    other direction means the probe violates §2.7 property 1 (*may only ever change a tier, never
    skip work*) or mis-implements §3.1. It is a defect **in the probe**, not a finding about the CFG.
  - `UNRESOLVED_TARGET > 0` means `declarationOf` (§3.2) returned `null` on both sides for a
    variable that **is** a declared local, so `matchesByBinding` silently degraded to
    `matchesByName` on the very fixtures that exist to tell the two apart. `RD_OK` and `NAME_DELTA`
    would then be measuring the name predicate twice.
  - **Destination**: record the offending lines (for `NEW_SUPPRESSED`, the `[a07:site]` lines
    verbatim; for `UNRESOLVED_TARGET`, the fixture ids and the `unresolved=` field), fix the probe,
    and **re-run only the affected harness** — DR-02's corpus pair for the first, `§2.1`'s
    `AnalysisSevenReachingDefsSpikeTest` for the second. `COVERAGE`, `JOIN` and `LCS` come from
    §2.2/§2.3, which neither condition touches, so those numbers **carry forward unchanged** and are
    not re-measured. DX is a **gate, not an outcome**: it is not a value `ANALYSIS-07-01` may be
    closed with, and `implementation-plan.md` T0.11 may not run while it fires.
  - This rule is evaluated **first**, ahead of D0, because `RESTORED` is the other half of the same
    diff: a probe that mis-suppresses cannot be trusted to have restored correctly either.
- **D0 — re-scope.** If `FN == false` **and** `RESTORED == 0`:
  `-02` has neither a reproducible defect nor a measured payoff. Set `ANALYSIS-07-02` to
  `cancelled` with DR-01's and DR-02's output quoted as the reason; the feature reduces to whatever
  DR-10 and DR-11 found, plus `-05` as a standing constraint. **File the finding as a BUG report and
  a roadmap row — do not leave the requirement sitting at `Not Implemented`.**
- **D1 — direction C.** Else if `RD_OK` **and** `JOIN ≥ 99 %` **and** `CLOSURE_LOST == false`
  **and** `NEW_SUPPRESSED == 0`:
  the shipped CFG answers `-02` as-is. §5 designs a query service in direction C; A and B stay
  unbuilt.

  The `NEW_SUPPRESSED == 0` conjunct is **redundant under the ordering** — DX already fired — and it
  is written anyway, for a reason this document has now been caught by once: §4.8 stated that a
  newly-suppressed line *"blocks D1 regardless of `RESTORED`"* while D1's rule mentioned no such
  term, there was no input symbol for it, and no rule said where a blocked run landed. Under
  `FN=true, RESTORED=3, RD_OK=true, JOIN=100 %, CLOSURE_LOST=false` plus one newly-suppressed line,
  §3.7 selected D1 and §4.8 forbade it. **A prohibition stated in a prose paragraph and absent from
  the rule it constrains is not a decision procedure.** The term is here so D1 is readable standalone
  and so §4.8's sentence has something to point at.
- **D2 — direction A.** Else if `COVERAGE ≥ 80 %`:
  C is blocked, and by this point only three things can have blocked it — a `P3`/`P5`/`P6`/`P7`
  disagreement (`RD_OK == false`), an incomplete join (`JOIN < 99 %`), or F5's lost closure write
  (`CLOSURE_LOST == true`). D1's fourth term cannot be the reason: `NEW_SUPPRESSED > 0` fires DX and
  never reaches here. Every one of the three **is** a deficiency **internal to the CFG**. Its
  value coverage is close enough that growing a domain on it is an increment rather than a rebuild.
  §5 designs A, whose **first** increment is repairing the specific defects DR-03/-04/-05/-07 named,
  after which C's query becomes correct as a by-product. **A subsumes C; C is not also built.**
- **D3 — direction B.** Else if `LCS ≥ 90 %` (with `COVERAGE < 80 %` implied by falling through D2):
  the CFG would have to grow an instruction for most of the type engine's value domain (F11's 8
  type-only visit methods), but its instruction order **is** a legal construction order for that
  domain. §5 designs B — the type engine consumes the CFG rather than the CFG growing a value domain.
- **D4 — TYPE-08 §9 confirmed.** Else (`COVERAGE < 80 %` **and** `LCS < 90 %`):
  neither analysis can absorb the other cheaply, and *"a much larger refactor"* is **measured, not
  inherited**. Record the numbers in §4.9. `-02` then ships as direction C **with its imprecision
  documented against the failing DR**, or the feature is deferred to a wave with room for the
  refactor — that choice is a wave-planning call, and it is the only place in this procedure where
  one is admitted, because by construction D4 means no cheap answer exists.

**Exhaustiveness and exclusivity — executed, not asserted.** The rules are evaluated in order, so
exclusivity is automatic. Totality is **brute-forced over the whole input space**, re-run after
`NEW_SUPPRESSED` and `UNRESOLVED_TARGET` were added, with each numeric input sampled on both sides of
every threshold it appears in (`RESTORED`/`NEW_SUPPRESSED`/`UNRESOLVED_TARGET` at `0` and two
non-zero values; `COVERAGE` at `0 / 79.9 / 80.0 / 100.0`; `JOIN` at `0 / 98.9 / 99.0 / 100.0`;
`LCS` at `0 / 89.9 / 90.0 / 100.0`; `COST` at `0 / 10.0 / 10.1 / 250.0`), so a boundary written
`>` where `≥` was meant would show as a changed count:

```
combinations evaluated:             110592
branch hits:                        {'D0': 2048, 'D1': 1280, 'D2': 4480, 'D3': 2240, 'D4': 2240, 'DX': 98304}
branches never fired:               []
input combos landing nowhere:       0
verdict varies with NAME_DELTA/COST: False

valid-probe subspace (NEW_SUPPRESSED=0, UNRESOLVED_TARGET=0):
  combinations evaluated:           12288
  branch hits:                      {'D0': 2048, 'D1': 1280, 'D2': 4480, 'D3': 2240, 'D4': 2240}
  input combos landing nowhere:     0
```

Three things this run establishes, none of which a reading would have:

1. **Nothing lands nowhere** — `0` unreached, in the full space and in the valid-probe subspace
   alike — and **every** rule including DX is reachable, so no rule is dead.
2. **`NAME_DELTA` and `COST` never move the verdict** (`verdict varies … : False`), which is the
   claim the obligations table below makes and the reason they are not branch terms.
3. **DX's share (98 304 of 110 592) is an artefact of the sampling, not a prediction.** Two of the
   three sampled values for each of `NEW_SUPPRESSED` and `UNRESOLVED_TARGET` are non-zero, so
   `1 − (1/3)²  = 8/9` of the space trips the gate. The distribution that describes a *valid* run is
   the subspace block, and the counts there are identical to the pre-DX procedure's — DX **adds a
   gate and changes no selector**.

The review's counterexample is the direct check: `FN=true`, `RESTORED=3`, `RD_OK=true`,
`JOIN=100 %`, `CLOSURE_LOST=false`, `NEW_SUPPRESSED=1` selected **D1** under the old rules while §4.8
forbade D1, and selects **DX** under these.

An earlier draft also made D2 conditional on `RD_OK == false || CLOSURE_LOST == true` and left a
different hole — `RD_OK` true, `CLOSURE_LOST` false, `JOIN < 99 %`, `COVERAGE ≥ 80 %` fired
**nothing**. D2 is therefore an unconditional `COVERAGE` test and the specific reason C was blocked
is *recorded*, not *re-tested*. Both holes were found the same way: by running the procedure over its
own input space rather than reading it.

**Two inputs shape the design instead of selecting a branch, and neither is decorative:**

| input | what it obliges |
| :-- | :-- |
| `NAME_DELTA` | `true` means the CFG's `variableName` key is demonstrably insufficient (F4), so **§5 must carry §3.2's binding step**. A §5 that keys on `variableName` is a defect regardless of which branch fired. The case where name-keying is *unrepairable from outside* is separately covered — `RD_OK` fails if `P3` is wrong even under `matchesByBinding`, which routes to D2. |
| `COST` | `≤ 10 %` → §5 may compute the query on demand. `> 10 %` → **§5 must cache it per file** through `CachedValuesManager`, keyed on the PSI file, mirroring `ControlFlowCache.kt:6-12`, and must re-measure against NFR-1 before landing. It is deliberately **not** a D1 term: a per-file cache is the standard remedy and the repo already ships one for exactly this graph, so a high figure changes the implementation rather than the architecture. |

**A DR whose outcome cannot change the plan is not a DR.** Of the **eleven** inputs, **nine** appear
in a rule's condition — `NEW_SUPPRESSED` and `UNRESOLVED_TARGET` in DX (and `NEW_SUPPRESSED` again in
D1), then `FN`, `RESTORED`, `RD_OK`, `JOIN`, `CLOSURE_LOST`, `COVERAGE`, `LCS`. The remaining
**two**, `NAME_DELTA` and `COST`, carry the design obligations tabulated above and are proven not to
move the verdict by the brute force below (`verdict varies with NAME_DELTA/COST: False`). DR-10 and
DR-11 can each delete a requirement outright. DR-12 and DR-13 are gates rather than selectors and are
labelled as such in the DR table.

## 4. External Data & Parsing — the probe output formats

These blocks are the feature's real external data: a human and a follow-on DR both read them. Each
format is pinned so a second run is diffable against the first.

### 4.1 RD dump (DR-01, DR-03, DR-04, DR-05) — `AnalysisSevenReachingDefsSpikeTest`

One block per fixture, fields in this exact order:

```
[a07:rd] id=P1-killed-unknown read=d@<startOffset>
[a07:rd]   upSet        = [Any@wx.thing, String@"s"]
[a07:rd]   byName       = [WRITE d@<offset> "s"]
[a07:rd]   byBinding    = [WRITE d@<offset> "s"]
[a07:rd]   defSites cfg = [LuaAssignmentStatement@<offset>]
[a07:rd]   defSites up  = [LuaLocalVarDecl@<offset>, LuaAssignmentStatement@<offset>]
[a07:rd]   varNode      = LuaNameRef@<offset> | NOT_FOUND
[a07:rd]   unresolved   = 0/2          # <targetPairs>/<allPairs>; targetPairs MUST be 0 (§3.2)
[a07:rd]   diagnostics  = []          # ERROR-tier messages containing "not assignable"
[a07:rd]   VERDICT      = FALSE_NEGATIVE | CORRECT | DISAGREES
```

- `upSet` renders `<LuaGraphType.displayName()>@<element.text truncated to 20 chars>`, in `upSet`
  iteration order (`OrderedSet` is insertion-ordered, `LuaTypeNodes.kt:233-243`).
- `byName` / `byBinding` render `WRITE|READ <variableName>@<startOffset> <element.text ≤ 20 chars>`,
  sorted by `startOffset` so the two are comparable set-wise regardless of pop order.
- `VERDICT` is computed, not typed, over §3.1a's `defSiteOf` projection — never over raw elements:
  - `FALSE_NEGATIVE` when `defSitesFromCfg` is a **strict subset** of `defSitesFromUpSet`
    **and** `diagnostics` is empty. Strict-subset means the CFG proved a definition unreachable that
    `upSet` still counts, and no diagnostic was emitted — i.e. BUG-441's gate suppressed on a
    definition that cannot reach the use.
  - `DISAGREES` when `byName != byBinding` (set-wise, after `defSiteOf`), or when the target's
    `VariableNode` was `NOT_FOUND` (§3.1a step 4).
  - `CORRECT` otherwise.
  Set comparison uses identity membership (`IdentityHashMap`-backed), for the reason given in §3.5
  step 3.
- `FN` (§3.7) is `true` iff `P1-killed-unknown`'s verdict is `FALSE_NEGATIVE`.
- `NAME_DELTA` is `true` iff `P3-shadowed`'s `byName != byBinding`.

### 4.2 Coverage census (DR-06)

```
[a07:cov] file=penlight/lua/pl/stringx.lua valueNodes=<n> covered=<m> coverage=<pct>%
[a07:cov]   miss LuaTerminalExpr=<n>
[a07:cov]   miss LuaFuncCall=<n>
...
```

Misses descending by count, ties by class name ascending.

### 4.3 Join census (DR-07)

```
[a07:join] file=<path> variableNodes=<n> exactIdentity=<m> (<pct>%) ancestor=<k> (<pct>%)
[a07:join]   unjoined <PsiClass>@<offset> <text ≤ 20 chars>
```

Up to 20 `unjoined` lines, then `… +<n> more` — the cap `CorpusSweep` already uses for oracle sites
(`private const val ORACLE_SITES_CAP = 20`, `CorpusSweep.kt:67`, applied at `:126`). It is declared
in `CorpusSweep`, not in `LuaCorpusSweepTest`, which an earlier revision of this line said.

### 4.4 Order comparison (DR-09)

```
[a07:order] file=<path> cfgOrder=<n> nodeOrder=<m> lcs=<k> lcsPct=<pct>%
[a07:order]   diverge#1 cfg=<PsiClass>@<offset> node=<PsiClass>@<offset>
```

First five divergences only.

### 4.5 Cost (DR-08, DR-13)

```
[a07:cost] file=<path> owners=<n> instructions=<total>
[a07:cost]   forFile   median=<ms> samples=[<5 values>]
[a07:cost]   allCfgs   median=<ms> samples=[<5 values>]
[a07:cost]   overhead=<pct>%
[a07:cost]   worstOwnerBuild=<ms> at <PsiClass>@<offset>
```

`COST` (§3.7) is the `overhead=` field. `worstOwnerBuild` is DR-13's, and its threshold is
**exactly `> 50 ms`** — strictly greater, so `50.0` passes and `50.1` does not, with the figure
reported to one decimal as elsewhere in §4. **The number is derived, not judged**: `forFile` is on
the completion path, where [[COMP-09]]'s NFR-1 budgets **100 ms** to first result
(`docs/features/completion/09-member-enumeration/requirements.md:143`, *"**< 100 ms**"*), and a CFG
build that cannot be cancelled (F7 — the package contains no `checkCanceled`) spends that budget
with no way for the user to interrupt it. 50 ms is **half** of it: the point at which one
un-cancellable build is as expensive as everything else completion is allowed to do. Above it,
adding `ProgressManager.checkCanceled()` to `LuaControlFlowBuilder.visitBlock`'s statement loop
(`:96`) is a prerequisite for every direction; at or below it, the figure is recorded and left.
An earlier revision wrote `> ~50 ms`, which was the only threshold in this document admitting
judgement while §3.7 claimed none did.

### 4.6 Guard node dump (DR-10)

```
[a07:guard] fixture=BUG-435
[a07:guard]   original  write=<displayName> members=[<sorted names>]
[a07:guard]   narrowed  write=<displayName> members=[<sorted names>]
[a07:guard]   completion inside guard = [<sorted offered strings>]
[a07:guard]   VERDICT = NODE_REPLACED_BY_MEMBERLESS_TABLE | OTHER:<one line>
```

`NODE_REPLACED_BY_MEMBERLESS_TABLE` means BUG-435's hypothesis is confirmed and `-03` is a one-site
fix, not CFG work.

### 4.7 BUG-428 residual dump (DR-11)

```
[a07:428] site=penlight/lua/pl/config.lua:131 tool=LuaTypeAssignability
[a07:428]   message=<verbatim>
[a07:428]   anchor=<PsiClass>@<offset> text=<≤ 40 chars>
[a07:428]   upSet=[<rendered as §4.1>]
[a07:428]   VERDICT = CALL_SITE_UNION | OTHER:<one line>
```

### 4.8 Per-site corpus dump (DR-02, DR-12) — the temporary patch's format

```
[a07:site] <corpus>/<path>:<line> <toolId> | <message> | <source line trimmed>
```

- One line per highlight with a non-null `inspectionToolId`; ordering follows the sweep's own
  `swept.sortedBy { it.path }` (`CorpusSweep.kt:87`), so two runs diff cleanly with `diff -u`.
- `<line>` is 1-based (`document.getLineNumber(startOffset) + 1`); `-1` when the document is
  unavailable.
- `|` is the field separator; a message containing `|` is escaped to `\|` before printing, because
  the two dumps are compared with `diff` and a stray separator would silently shift a field. (This
  is the BUG-407 lesson applied: a positional text format read by two parsers is how the corpus
  manifest broke.)
- **Reading it** — both directions of one `diff -u`, each with a named symbol in §3.7's inputs
  table, so neither is a rule stated only in prose:
  - **`RESTORED`** = lines present in the DR-02 (after) dump and absent from the DR-12 (before)
    dump, restricted to `LuaTypeAssignability` and `LuaReturnTypeMismatch`. In `diff -u` terms:
    `+` lines.
  - **`NEW_SUPPRESSED`** = lines present in the DR-12 dump and absent from the DR-02 dump, same
    restriction — the `-` lines. **`NEW_SUPPRESSED > 0` fires §3.7's DX**: the probe is invalid, the
    offending lines are recorded verbatim, the probe is fixed and DR-02 is re-run. It does **not**
    select a direction, and it does not fall through to D2/D3/D4 — a defective measurement is not
    evidence about the CFG. It also blocks D1 by an explicit conjunct in D1's own rule, which is
    redundant under the ordering and is written for the reason given there.
  - Lines whose tool id is neither of those two (e.g. `LuaUnreachableCode`) are **counted and
    reported but feed no symbol**: they are the explicit non-goal (`§1.2`). A **rise** in their count
    is a regression under NFR-3's ratchet and a **fall** is an `IMPROVED` line (§4.8a below); neither
    is an input to this procedure.
- **What the ratchet does to a run carrying the DR-02 probe** — stated here because an implementer
  who does not know it will read T0.9's red as a broken probe (§4.8a has the mechanism):
  - `RESTORED > 0` ⇒ the two gated inspection keys **rise** ⇒ `assertRatchet` **fails**. T0.9's
    corpus run is therefore **expected to go red**, and that red is the finding. The `[a07:site]`
    lines are printed from inside `CorpusSweep.accumulateHits`, which runs during
    `CorpusSweep.run` (`LuaCorpusSweepTest.kt:93`) — **before** `assertRatchet` (`:101`) — so the
    dump is complete regardless of the verdict, and each of the four corpora is its own `@Test`
    member, so one red does not stop the others.
  - `NEW_SUPPRESSED > 0` ⇒ the keys **fall** ⇒ the run is **green** with `IMPROVED` lines. A green
    T0.9 is therefore *not* evidence that the probe is valid — it is the exact shape of the DX
    defect. Read the diff, never the verdict.

#### 4.8a The ratchet's direction, and why a working `-02` cannot be "corpus green"

**Read the direction off the comparator, not off the printer.** `CorpusGuards.assertRatchet` shows
only *that* improvements are printed and regressions asserted (`CorpusGuards.kt:49-55`). **Which
direction is which** is decided one file over, in `CorpusBaseline.compare`:

```kotlin
// src/test/kotlin/net/internetisalie/lunar/corpus/CorpusMetrics.kt:283-284
regressions  = gated.filter { it.third > it.second }.map(::describe),
improvements = gated.filter { it.third < it.second }.map(::describe),
```

The tuple is `Triple(key, baseline, observed)` — `Triple("parseErrors", baseline.parseErrors,
observed.parseErrors)` at `:259`, and the identical `(key, baseline[…], observed[…])` shape for every
crash key at `:271` and every inspection key at `:276-280`. So **`it.second` is the baseline and
`it.third` is the observed count**, and:

| observed vs baseline | classified as | consequence |
| :-- | :-- | :-- |
| **more** hits | `regressions` | **hard failure** — `assertTrue("Corpus regression:…", regressions.isEmpty())`, `CorpusGuards.kt:52-55` |
| **fewer** hits | `improvements` | `println("[corpus] IMPROVED …")` and nothing else, `CorpusGuards.kt:49-51` |

Each line reads `"<key>: baseline <n> → observed <m>"` (`CorpusMetrics.kt:288-289`). Per-inspection
keys are gated only while `highlightFailures` is `0` on both sides (`CorpusMetrics.kt:250-253`); the
committed baselines carry no `inspection.highlightFailures` row, so today they **are** gated.

**The consequence for this feature, written out because three sites in these artifacts previously had
it backwards.** `-02`'s entire measurable payoff is `RESTORED > 0` — diagnostics BUG-441's gate
suppressed, coming back. Restored sites **raise** `inspection.LuaTypeAssignability` (baseline `5` in
`penlight.baseline:9` and `5` in `luarocks.baseline:9`) and possibly
`inspection.LuaReturnTypeMismatch` (`penlight.baseline:6` = `1`). More hits is a **regression**. A
working `-02` therefore **hard-fails `assertRatchet` by construction, and can never produce an
`IMPROVED` line.**

So the operational requirement for `-02` is **not** "corpus green". It is a **deliberate re-record**:

1. Run `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache -PwithCorpus"` and let
   it go **red**. Capture every `Corpus regression:` line — e.g.
   `inspection.LuaTypeAssignability: baseline 5 → observed 7`, one set per corpus.
2. Diff the §4.8 per-site dumps and attribute **every** restored `file:line` in writing — one line
   of prose per site, naming which reaching definition became accountable. A restored site that
   cannot be attributed is a defect in `-02`, not a baseline to be re-recorded.
3. Only then re-record, deliberately and in one step:
   `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache -PwithCorpus -PrecordCorpusBaseline"`
   (`build.gradle.kts:286-288` sets `lunar.corpus.record=true`; `LuaCorpusSweepTest.kt:98-99` then
   calls `recordBaseline` **instead of** `assertRatchet`).
4. Commit the rewritten `src/test/resources/corpus/*.baseline` **in the same commit** as the change
   that moved them, with the per-site attribution in the message. A baseline re-recorded in a
   separate commit is indistinguishable from one re-recorded to hide a regression.

The **inverse** case — a gated count going **down** — is `-03`/`-04`'s shape, and *that* is what an
`IMPROVED` line describes. `implementation-plan.md` Phase 3's exit criterion (penlight's
`LuaTypeAssignability` moving `5 → 3`) is written that way and is correct; it is the model the two
`-02` criteria are now stated against.

### 4.9 The TYPE-08 §9 verdict block

Whichever rule fires, §4.9 is filled with one of:

```
[a07:type08] "a much larger refactor" — REFUTED. coverage=<pct>% join=<pct>% cost=<pct>% lcs=<pct>%
             direction=<A|B|C> landed as <one sentence>.
```

or

```
[a07:type08] "a much larger refactor" — CONFIRMED. coverage=<pct>% lcs=<pct>%
             The blocking measurement is <DR-NN>: <one sentence>.
```

and the same two lines are appended to `docs/features/type/08-flow-sensitive/design.md` §9 as a
cross-reference (requirements TC-1b).

## 5. `ANALYSIS-07-02` — DEFERRED, gated on §3.7

**Gate**: `ANALYSIS-07-00-DR-01`, `-02`, `-03`, `-04`, `-05`, `-06`, `-07`, `-08`, `-09` complete and
§3.7 fired. **Do not implement `-02` before this section exists.**

Fixed now (§2.7): the contract, the single call site (`LuaTypeGraph.kt:352-353`), the three
non-negotiable properties, and the acceptance cases (requirements TC-2a…TC-2e).

Deferred to §3.7's output: which component computes it, which package it lives in, whether it is a
light `@Service`, a `CachedValuesManager`-backed helper, or a method on `LuaTypeGraph`; whether the
CFG is repaired first; and what `plugin.xml`, if anything, gains (§7).

**When §5 is written it must contain**, or it has not cleared the bar:
FQCN and key signatures for every new class; the threading context of each; the exact
`CachedValuesManager` key and dependency list for anything cached; the algorithm by which a
`(variable, use)` pair is turned into a `target` instruction for §3.1; the behaviour when the use
has **no** CFG instruction (F5's closure case — fail open to today's `upSet.any`, or fail closed);
and the `plugin.xml` delta or an explicit "none".

## 6. `ANALYSIS-07-03` / `-04` — DEFERRED, gated on DR-10 / DR-11

- **`-03`** gate: `ANALYSIS-07-00-DR-10` (§4.6). If the verdict is
  `NODE_REPLACED_BY_MEMBERLESS_TABLE`, `-03` is **not** CFG work: it is a fix at
  `LuaTypesVisitor.injectNarrowedBinding` (`:464-478`) — most plausibly intersecting
  `originalNode.write` with `guard.narrowedType` instead of installing a bare
  `graph.value(guard.anchor, narrowedType)` — and the requirement is `cancelled` here and refiled as
  a bug. If the verdict is `OTHER`, §6 is written against what the dump actually showed.
- **`-04`** gate: `ANALYSIS-07-00-DR-11` (§4.7). `CALL_SITE_UNION` keeps it a `Could`;
  anything else `cancel`s it and refiles.

## 7. Integration Points

### 7.1 Phase 0 registers nothing

**No `plugin.xml` change.** `src/main/resources/META-INF/plugin.xml` is untouched — Phase 0 registers
nothing, adds no service, listener, action or extension, and removes none. This is stated rather than
omitted because an absent registration section is one of the recorded ways a plan misses the bar.

**Production sources are patched and reverted, not left alone.** Phase 0 adds six test files and two
temporary patches, one of which — T0.9's throwaway `unknownProvenance` at `LuaTypeGraph.kt:352-353` —
is in `src/main/`. The invariant is §1.3's: **no production change survives the phase**, enforced by
T0.10 plus `temporary-edits` and checked by an empty `git status --short` / `git diff`. `plugin.xml`
is untouched at every point, patched or not.

### 7.2 What the deferred phases may and may not register

`ANALYSIS-07` adds **no** `<localInspection>` (requirements → Out of Scope), so the registered
surface is unchanged; `-02` changes the *tier* an existing inspection emits at (F13), which needs no
registration.

**The registered surface is SIXTEEN `<localInspection>` elements, not ten** —
`grep -c "<localInspection" src/main/resources/META-INF/plugin.xml` → `16`, at lines
`194, 202, 210, 219, 227, 232, 241, 250, 259, 268, 277, 286, 297, 308, 318, 337`, **all**
`language="Lua"`:

| plugin.xml line | `shortName` (or implementation, where none is declared) |
| --: | :-- |
| 194 | *(none declared)* `net.internetisalie.lunar.analysis.LuaTypeAssignabilityInspection` |
| 202 | *(none declared)* `net.internetisalie.lunar.analysis.LuaReturnTypeMismatchInspection` |
| 210 | `LuaUndeclaredVariable` |
| 219 | `LuaGlobalCreation` |
| 227 | `LuaUnusedLocal` |
| 232 | `LuaShadowingVariable` |
| 241 | `LuaDeprecatedApi` |
| 250 | `LuaSuspiciousConcatenation` |
| 259 | `LuaUnreachableCode` |
| 268 | `LuaLanguageLevel` |
| 277 | `LuaValkeyPortability` |
| 286 | `LuaRedisCommand` |
| 297 | `LuaRedisSandbox` |
| 308 | `LuaRedisFunctionKeys` |
| 318 | `LuaJsonSchemaCompliance` |
| 337 | `LuaCheck` |

**"Ten" is a different quantity and an earlier revision conflated them.** Ten is the count of
*language-only* inspections `LuaCorpusSweepTest.setUp` enables for the sweep
(`LuaCorpusSweepTest.kt:53-64`) — the six omitted there (`LuaValkeyPortability`, `LuaRedisCommand`,
`LuaRedisSandbox`, `LuaRedisFunctionKeys`, `LuaJsonSchemaCompliance`, `LuaCheck`) are excluded
because they *"would measure the environment (absent binary, absent Redis, absent schema mapping)
rather than the plugin"* (`:50-52`), **not** because they are unregistered. The distinction matters
to this feature in one concrete place: the corpus dumps of §4.8 are read *"restricted to
`LuaTypeAssignability` and `LuaReturnTypeMismatch`"*, and those two are exactly the registrations at
`:194` and `:202` that declare **no `shortName`**. What `info.inspectionToolId`
(`CorpusSweep.kt:272`) actually reports for them is **not inferred from the platform's derivation
rule** — it is read off recorded output: the committed baselines carry the keys
`inspection.LuaTypeAssignability` (`src/test/resources/corpus/penlight.baseline:9`,
`luarocks.baseline:9`) and `inspection.LuaReturnTypeMismatch` (`penlight.baseline:6`). So the strings
§4.8 filters on are the strings a previous run produced.

If §5 selects a light service, it registers as:

```xml
<!-- src/main/resources/META-INF/plugin.xml, inside <extensions defaultExtensionNs="com.intellij"> -->
<!-- ONLY IF §5 selects a service; the FQCN is §5's output, not this document's -->
<!--
<applicationService serviceImplementation="net.internetisalie.lunar.analysis.controlflow.…"/>
-->
```

and **must not** hold a `Project`/`PsiFile` field (engineering contract §4); a per-file cache goes
through `CachedValuesManager` keyed on the PSI file, mirroring `ControlFlowCache.kt:6-12`.

### 7.3 Build and test wiring

- Phase 0's **five** harnesses (§2.1–§2.5 — `AnalysisSevenReachingDefsSpikeTest`,
  `…CoverageSpikeTest`, `…JoinSpikeTest`, `…CostSpikeTest`, `…DescopeSpikeTest`) are **excluded from
  nothing** and run in the routine loop while they exist; they are deleted before the phase closes,
  so no permanent cost is added. **Three of them read the corpus** (§2.2, §2.3, §2.5) and therefore
  carry §2.0's fixture setup and need `tooling/corpus/fetch-corpus.py` to have run — but they are
  still outside the `*Corpus*` filter, which is exactly the trade §2.0 and §2.6 weigh.
- `AnalysisSeven*` deliberately contains no `Corpus` substring, so `build.gradle.kts:272-283`'s
  `excludeTestsMatching("*Corpus*")` does not capture them and they cannot perturb the sweeps'
  JVM (§2.6).
- The corpus runs use the sanctioned invocation and no other:
  `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache -PwithCorpus"`.
  `--rerun` is mandatory — without it `:test` is served `FROM-CACHE` and reports a pass having
  executed nothing.
- **`LuaInspectionParityTest` is run separately, by name, in the same session** (NFR-4, and §8's
  NFR-4 row points at this bullet):
  `tooling/gce-builder/gce-builder.sh run "test --rerun --tests '*LuaInspectionParityTest*'"`.
  It must be by name and **not** folded into the `-PwithCorpus` run: `build.gradle.kts:280` excludes
  `*InspectionParityTest` from the routine loop separately from `*Corpus*`, and its own KDoc records
  why running it inside the sweeps' JVM is wrong — *"inside the filter it moved luacheck's
  `LuaTypeAssignability` by +12"* (`LuaInspectionParityTest.kt:32-36`). What is recorded from it is
  `filesAtExactParity` and the `LuaUndeclaredVariable` total, as DR-12's before-figure.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| ANALYSIS-07-01 | M | §2.0 (the shared corpus fixture), §2.1–§2.6 (the five harnesses + the patch), §3.1–§3.6 (what they compute), **§3.7 (the decision, incl. the DX gate)**, §4.1–§4.9 (the recorded output) |
| ANALYSIS-07-02 | M | Contract fixed at **§2.7**; acceptance at requirements TC-2a…TC-2e; measured by §3.1/§3.6; **implementation §5, deferred with a named gate** |
| ANALYSIS-07-03 | S | **§6**, gated on DR-10 (§2.5, §4.6) — may be `cancelled` by it |
| ANALYSIS-07-04 | C | **§6**, gated on DR-11 (§2.5, §4.7) — may be `cancelled` by it |
| ANALYSIS-07-05 | M | §1.2 (nothing duplicated), §3.3's refusal to invent a cross-graph walk, §3.7's D2 "A subsumes C; C is not also built", and requirements TC-5a's structural assertion |
| ANALYSIS-07-NFR-1 | M | §2.4, §3.7's `COST ≤ 10 %` term, §4.5 |
| ANALYSIS-07-NFR-2 | M | §3.1's use of `ControlFlowUtil.iteratePrev` (which calls `checkCanceled` per pop, `ControlFlowUtil.java:104`), and §4.5's `worstOwnerBuild` against its exact `> 50 ms` threshold (DR-13) |
| ANALYSIS-07-NFR-3 | M | §3.6 (the dump), §4.8's `RESTORED`/`NEW_SUPPRESSED` reading, **§4.8a (the ratchet's direction and the deliberate-re-record procedure `-02` needs)**, §7.3's invocation |
| ANALYSIS-07-NFR-4 | M | **§7.3's third bullet** — `LuaInspectionParityTest` run **by name**, `test --rerun --tests '*LuaInspectionParityTest*'`, never folded into the `-PwithCorpus` run; `filesAtExactParity` recorded as DR-12's before-figure |

## 9. Alternatives Considered

1. **Write §5 now, choosing direction C because it is cheapest.** Rejected. C's viability rests
   entirely on `RD_OK`, `JOIN` and `CLOSURE_LOST`, none of which is known; F4 alone (the CFG
   resolves no bindings) is enough to make a by-name C wrong on at least 320 measured corpus sites. A §5 written today
   would be a guess dressed as a design — the exact failure BUG-441 recorded when its own mechanism
   did not survive a probe.
2. **Inherit TYPE-08 §9 and skip the direction question entirely**, shipping only a narrow C.
   Rejected: the estimate is unmeasured, and `ANALYSIS-07-01` is a `Must` precisely because the repo
   has been carrying it as fact since TYPE-08.
3. **Contradict TYPE-08 §9 and go straight to B.** Rejected for the mirror-image reason. §3.7's D3
   and D4 are the only two ways this document permits an opinion about §9, and both require numbers.
4. **Select the direction from a corpus sweep.** Rejected on measurement already in hand: BUG-441
   left **11** type-inspection sites across 419 files (requirements → headroom). The corpus can
   confirm that nothing broke; it cannot distinguish three architectures. It stays the guard
   (NFR-3), and §3.1's targeted probes are the selector.
5. **A new `AnalysisSevenCorpusDumpTest` instead of instrumenting `CorpusSweep`.** Rejected, with
   the reasons written down rather than assumed (§2.6): the `*Corpus*` filter would make the probe
   perturb the baseline it measures (+12 measured on luacheck), and a standalone class re-implements
   `LuaCorpusSweepTest.setUp` — the drift `LuaInspectionParityTest.assertAnchored` exists to catch.
6. **Hand-roll the backward walk instead of using `ControlFlowUtil.iteratePrev`.** Rejected twice
   over: it would be a third flow traversal (`ANALYSIS-07-05`), and it would lose the
   `ProgressManager.checkCanceled()` the platform utility performs on every pop — the one thing the
   CFG package currently lacks (F7) and NFR-2 requires.
7. **Reintroduce `LuaBranchInstruction` / adopt `ConditionalInstruction` up front.** Rejected as
   premature: F6 records that it was deleted by MAINT-31 as unused and that MAINT-29 shipped without
   it. A condition-carrying instruction is part of direction **A**'s design if A is selected, and
   pre-committing to it would presuppose the answer.

## 10. Open Questions

_None._ Every unresolved decision is a numbered de-risking task in [risks-and-gaps.md](risks-and-gaps.md) — each with a question, an exact probe, and the §3.7 branch its outcome selects — and §5/§6 are DEFERRED against those gates rather than guessed.
