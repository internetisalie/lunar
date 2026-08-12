package net.internetisalie.lunar.type

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.ParameterizedCachedValue
import com.intellij.testFramework.PlatformTestUtil
import net.internetisalie.lunar.definitions.LuaDefinitionLibraryEnabler
import net.internetisalie.lunar.lang.psi.types.LuaTypes
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.LuaTypesVisitor
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.platform.target.VersionEntry
import net.internetisalie.lunar.project.LuaSettingsChangeListener
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * TYPE-11-02 — **every generation signal invalidates a pinned snapshot**, in the three parts design
 * §1.11 concluded it takes, plus the target axis and one cheap non-gate.
 *
 * The split is not tidiness. The obvious form of this requirement — "tick roots, assert the snapshot
 * rebuilt" — **cannot discriminate this build from `main`**: `makeRootsChange` fires
 * `propertyChanged(PROP_ROOTS)` and `PsiModificationTrackerImpl.canAffectPsi` admits it, so every
 * roots tick is also a `MODIFICATION_COUNT` tick (§1.11, traced into platform source). Two earlier
 * forms of this case died exactly there, and holding PSI still is impossible because the roots tick
 * *is* the PSI event.
 *
 * So the requirement is asserted as an ingredient (TC-2a), a signal (TC-2b) and a wiring (TC-2c),
 * and the wiring case is the one that catches a pinnable branch which computes the right churn
 * object and then forgets to pass it to `Result.create`.
 */
class TypeElevenGenerationSignalTest : TypeElevenDefinitionLibraryTestCase() {
    private fun libraryText(): String = "---@meta\n\n---@class wx\nwx = {}\n\n---@type number\nwx.value = nil\n"

    /**
     * TC-2a — the **ingredient**. A pinned file's churn dependency **is**
     * `ProjectRootModificationTracker.getInstance(project)`, by identity.
     *
     * Mutations, both red: `LuaLibraryProvenance.generationTracker()` → `NEVER_CHANGED`, and §3.3
     * step 9 → always `MODIFICATION_COUNT`. The `isPinnable` assertion is what stops this from being
     * a claim about a branch the fixture never takes; TC-2b in this class is what stops it passing
     * vacuously in a fixture where `getInstance` falls back to a shared `NEVER_CHANGED`.
     */
    fun testAPinnedLibraryFileDependsOnTheProjectRootModificationTracker() {
        val libraryRoot = installDefinitionLibrary("luassert", mapOf("wx.lua" to libraryText()))
        myFixture.configureByText("consumer.lua", "local pad = 1\n")
        val libraryFile = checkNotNull(libraryRoot.findChild("wx.lua"))

        val recordedFrame = frameOf(snapshotOf(libraryFile))
        val churnDependency =
            runReadAction {
                val libraryPsi = psiFileOf(libraryFile)
                assertTrue(
                    "the file must be pinnable, or step 9's other branch is under test",
                    LuaTypesSnapshot.isPinnable(libraryPsi, recordedFrame),
                )
                LuaTypesSnapshot.churnDependencyFor(libraryPsi, recordedFrame)
            }

        assertSame(
            "a pinned snapshot's churn dependency is the project's generation signal and nothing else",
            ProjectRootModificationTracker.getInstance(project),
            churnDependency,
        )
    }

    /**
     * TC-2b — the **signal**, closing `risks-and-gaps.md` Gap 2.3: the plugin's own enable path
     * reaches `ProjectRootModificationTracker`.
     *
     * Three fixture facts, each of which would make this red against a **correct** implementation:
     * the applied list must **differ** from the stored one, because
     * `setEnabledDefinitionLibrariesAndNotify` early-returns on equality; both trees must already be
     * on disk, because the enabler's second notify route only runs when a fetch happened; and
     * `LuaSettingsChangeListener` must be instantiated explicitly, because it subscribes in its
     * `init` and its post-startup activity does not run under `BasePlatformTestCase`.
     */
    fun testTheProductionEnablePathTicksTheGenerationTracker() {
        seedDefinitionLibrary("luassert", mapOf("wx.lua" to libraryText()))
        seedDefinitionLibrary("love2d", mapOf("love.lua" to "---@meta\n\nlove = {}\n"))
        LuaProjectSettings.getInstance(project).state.enabledDefinitionLibraries = mutableListOf("luassert")
        LuaSettingsChangeListener.getInstance(project)
        val rootsTracker = ProjectRootModificationTracker.getInstance(project)
        val before = rootsTracker.modificationCount

        LuaDefinitionLibraryEnabler(project).apply(listOf("luassert", "love2d"))
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertTrue(
            "enabling a definition library must reach the generation tracker the pin depends on " +
                "($before -> ${rootsTracker.modificationCount})",
            rootsTracker.modificationCount > before,
        )
    }

    /**
     * TC-2c — the **wiring**, and the case that must not be dropped again.
     *
     * `A !== B` alone is green on `main` (§1.11). `B === C` alone is green on a build that pins
     * everything forever. Together they say "rebuilds on roots, does not rebuild on PSI", which is
     * the requirement stated as behaviour.
     *
     * ⚠ `design.md` §1.11 claims this is the only assertion that `forFile` passes the churn object
     * into `Result.create` at all. **Measured false**: under exactly that mutation this case stays
     * green, because a roots change moves the library `PsiFile`'s own `modificationStamp` and the
     * file is a dependency in both branches. [testAPinnedSnapshotIsWiredToAllThreeOfItsDependencies]
     * is what catches it; this case remains the behavioural statement of the requirement.
     */
    fun testAPinnedSnapshotRebuildsOnARootsTickAndNotOnAProjectEdit() {
        val libraryRoot = installDefinitionLibrary("luassert", mapOf("wx.lua" to libraryText()))
        val projectFile = myFixture.addFileToProject("unrelated.lua", "local before = 1\n")
        val libraryFile = checkNotNull(libraryRoot.findChild("wx.lua"))

        val beforeRootsTick = snapshotOf(libraryFile)
        assertTrue(
            "the file must be pinnable, or neither half of this case is about the pin",
            runReadAction { LuaTypesSnapshot.isPinnable(psiFileOf(libraryFile), frameOf(beforeRootsTick)) },
        )
        announceRootsChange()
        val afterRootsTick = snapshotOf(libraryFile)
        rewriteAssertingRootsAreStill(projectFile, "local after = 2\n")
        val afterProjectEdit = snapshotOf(libraryFile)

        assertNotSame(
            "a roots change is a generation signal: the pinned snapshot must be rebuilt",
            beforeRootsTick,
            afterRootsTick,
        )
        assertSame(
            "a project keystroke is not a generation signal: the pinned snapshot must survive it",
            afterRootsTick,
            afterProjectEdit,
        )
    }

    /**
     * TC-2d — the wiring, **as an assertion that can actually fail**.
     *
     * `design.md` §1.11 and the TC-2c row both state that TC-2c is the only case able to catch a
     * pinnable branch which computes the right churn object and then never passes it to
     * `Result.create`. Measured on this build, under exactly that mutation, **TC-2c stayed green**:
     * a roots change moves the library `PsiFile`'s own `modificationStamp` — probed `0 -> 1` on the
     * *same* `PsiFile` instance — and `forFile` depends on `psiFile` in both branches, so the
     * snapshot is rebuilt whether or not the churn object was ever handed over. §1.11 eliminated
     * `MODIFICATION_COUNT` as the confound and left `psiFile`, which is the same mechanism §1.6
     * recorded for the dumb-mode exit.
     *
     * Every route to a `ProjectRootModificationTracker` tick runs through `makeRootsChange`, which
     * is what moves that stamp, so no behavioural fixture can separate the two. The dependency set
     * is therefore asserted directly — the §1.9 B5 remedy, applied to the wiring instead of the
     * decision.
     *
     * ⚠ This used to add that `forFile` spreads exactly this array, so "omitted from
     * `Result.create`" and "omitted from `dependenciesFor`" are the same edit. **Measured false**
     * (Phase 3 review F2): inlining the three arguments into the provider leaves this case green.
     * [testTheProviderHandsEveryDependencyToTheResultItCreates] is the case that catches it.
     *
     * Mutations, each red here and green under TC-2c: the churn object dropped from the pinnable
     * branch; `targetTracker` dropped from the pinnable branch (TC-3's mutation, which this catches
     * directly rather than through an invalidation).
     */
    fun testAPinnedSnapshotIsWiredToAllThreeOfItsDependencies() {
        val libraryRoot = installDefinitionLibrary("luassert", mapOf("wx.lua" to libraryText()))
        val libraryFile = checkNotNull(libraryRoot.findChild("wx.lua"))
        val recordedFrame = frameOf(snapshotOf(libraryFile))
        val targetTracker = LuaProjectSettings.getInstance(project).state.targetModificationTracker

        val libraryPsi = psiFileOf(libraryFile)
        val dependencies = runReadAction { LuaTypesSnapshot.dependenciesFor(libraryPsi, recordedFrame).toList() }

        assertTrue(
            "a pinned snapshot must depend on the generation tracker, or it is stale for the whole " +
                "session on any roots change (dependencies=$dependencies)",
            dependencies.any { it === ProjectRootModificationTracker.getInstance(project) },
        )
        assertTrue(
            "a pinned snapshot must keep the target axis as its own dependency (dependencies=$dependencies)",
            dependencies.any { it === targetTracker },
        )
        assertTrue(
            "the file itself stays a dependency in both branches (dependencies=$dependencies)",
            dependencies.any { it === libraryPsi },
        )
        assertEquals("exactly three dependencies, each with one job", 3, dependencies.size)
    }

    /**
     * TC-2e — the **hand-over**, closing the hole TC-2d cannot: what `forFile`'s provider passes to
     * `Result.create`, read off the `CachedValue` the platform stored.
     *
     * TC-2d asserts `dependenciesFor`'s contents, and design §2.3 used to claim that "omitted from
     * `Result.create`" and "omitted from `dependenciesFor`" were therefore *the same edit*. Phase
     * 3's review (F2) refuted that: inlining `Result.create(builtTypes, psiFile, targetTracker)` and
     * leaving `dependenciesFor` intact is green under TC-1, TC-2c, TC-2d and TC-3, because the
     * spread is a convention, not an invariant a type can enforce.
     *
     * The gap closes by asking the stored cache entry instead of the helper. The `PsiElement`
     * overload of `getCachedValue` stores a `ParameterizedCachedValue` under [LuaTypesVisitor.KEY]
     * (measured: `PsiParameterizedCachedValue$Soft`) wrapping the production lambda in
     * `CachedValuesManager$NonPhysicalPsiHandlerProvider`, and its `valueProvider` is public API, so
     * recomputing through it yields the real `Result` — dependency items and all. The key is erased
     * to `Key<Any>` for the read because `ParameterizedCachedValue` does **not** implement
     * `CachedValue`, so `getUserData(KEY)`'s inserted checkcast would fail on the platform's own
     * heap-polluting store.
     *
     * Mutation: inline `Result.create(builtTypes, psiFile, targetTracker)` into the provider,
     * `dependenciesFor` untouched → **red here, green in TC-2d**.
     */
    fun testTheProviderHandsEveryDependencyToTheResultItCreates() {
        val libraryRoot = installDefinitionLibrary("luassert", mapOf("wx.lua" to libraryText()))
        val libraryFile = checkNotNull(libraryRoot.findChild("wx.lua"))
        val libraryPsi = psiFileOf(libraryFile)
        snapshotOf(libraryFile)

        val dependencies = dependenciesTheProviderPasses(libraryPsi)

        assertTrue(
            "the pinned branch's churn object must reach `Result.create`, not merely be computed " +
                "(dependencies=$dependencies)",
            dependencies.any { it === ProjectRootModificationTracker.getInstance(project) },
        )
        assertTrue(
            "and so must the target axis (dependencies=$dependencies)",
            dependencies.any { it === LuaProjectSettings.getInstance(project).state.targetModificationTracker },
        )
        assertTrue("and the file itself (dependencies=$dependencies)", dependencies.any { it === libraryPsi })
        assertEquals("exactly the three `dependenciesFor` returns", 3, dependencies.size)
    }

    /**
     * The `CachedValueProvider.Result` [LuaTypesSnapshot.forFile]'s own lambda produces for
     * [libraryPsi], recomputed through the provider the platform kept — see TC-2e.
     */
    private fun dependenciesTheProviderPasses(libraryPsi: PsiFile): List<Any> {
        @Suppress("UNCHECKED_CAST")
        val erasedKey = LuaTypesVisitor.KEY as Key<Any>
        val stored =
            checkNotNull(libraryPsi.getUserData(erasedKey)) {
                "nothing is cached under LuaTypesVisitor.KEY, so this asserts nothing"
            }

        @Suppress("UNCHECKED_CAST")
        val parameterized = stored as ParameterizedCachedValue<LuaTypes, PsiElement>
        val recomputed = runReadAction { parameterized.valueProvider.compute(libraryPsi) }
        return checkNotNull(recomputed) { "the provider returned no Result" }.dependencyItems.toList()
    }

    /**
     * TC-3 — the target axis, which is **not** composited into the generation tracker.
     *
     * A definition-library root is target-independent, so nothing but `targetModificationTracker`
     * can invalidate this file when the target moves. Mutation: drop `targetTracker` from the
     * pinnable branch of §3.3 step 9 → red.
     *
     * ⚠ `state.setTarget`, **not** `setTargetAndNotify`: the latter also publishes
     * `LuaSettingsChangedListener.TOPIC` → `PlatformLibraryIndex.reload()` → `makeRootsChange`,
     * which would invalidate through the roots tracker regardless and leave the mutation unable to
     * fire.
     */
    fun testATargetSwitchInvalidatesAPinnedDefinitionLibrarySnapshot() {
        val libraryRoot = installDefinitionLibrary("luassert", mapOf("wx.lua" to libraryText()))
        val libraryFile = checkNotNull(libraryRoot.findChild("wx.lua"))

        val beforeTargetSwitch = snapshotOf(libraryFile)
        assertTrue(
            "the file must be pinnable, or this asserts the unpinned branch's dependencies",
            runReadAction { LuaTypesSnapshot.isPinnable(psiFileOf(libraryFile), frameOf(beforeTargetSwitch)) },
        )
        LuaProjectSettings
            .getInstance(project)
            .state
            .setTarget(Target(LuaPlatform.STANDARD, VersionEntry("5.1", "lua-5.1")))
        val afterTargetSwitch = snapshotOf(libraryFile)

        assertNotSame(
            "a target switch changes which stubs are provisioned, so a pinned snapshot must be rebuilt",
            beforeTargetSwitch,
            afterTargetSwitch,
        )
    }

    /**
     * TC-4 — a project edit does not tick roots.
     *
     * **Explicitly not a gate**: it is a platform fact no TYPE-11 defect can change, and
     * `rewriteAssertingRootsAreStill` already asserts it on every edit in every fixture here. Kept
     * as a cheap regression check so no reader mistakes it for TYPE-11-02's evidence — TC-2a, TC-2b
     * and TC-2c are that.
     */
    fun testAProjectEditIsNotAGenerationSignal() {
        installDefinitionLibrary("luassert", mapOf("wx.lua" to libraryText()))
        val projectFile = myFixture.addFileToProject("unrelated.lua", "local before = 1\n")
        val rootsTracker = ProjectRootModificationTracker.getInstance(project)
        val before = rootsTracker.modificationCount

        rewriteAssertingRootsAreStill(projectFile, "local after = 2\n")

        assertEquals(
            "editing a project file must not announce a roots change",
            before,
            rootsTracker.modificationCount,
        )
    }
}
