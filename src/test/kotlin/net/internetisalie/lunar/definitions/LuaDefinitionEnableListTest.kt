package net.internetisalie.lunar.definitions

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.settings.LuaSettingsChangedListener
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * TARGET-08-02 / TC 3: the enabled-library ids round-trip through the project settings state that
 * `lunar.xml` serializes, and changing them notifies so the roots refresh (TARGET-08-05).
 */
@RunWith(JUnit4::class)
class LuaDefinitionEnableListTest : BasePlatformTestCase() {

    private val settings get() = LuaProjectSettings.getInstance(project)

    /**
     * The light project is cached across test *classes*, not just methods, so this service's state
     * outlives both. Resetting on the way in stops method order deciding the result; resetting on
     * the way out stops this class leaving `["busted", "love2d"]` behind for whatever runs next —
     * inert today, but TARGET-08-04's provider will read exactly this field.
     */
    override fun setUp() {
        super.setUp()
        settings.state.enabledDefinitionLibraries = mutableListOf()
    }

    override fun tearDown() {
        try {
            settings.state.enabledDefinitionLibraries = mutableListOf()
        } finally {
            super.tearDown()
        }
    }

    /**
     * The default belongs to the persisted [LuaProjectSettings.State] itself, so assert it on a
     * fresh instance — asserting it through the shared fixture would only re-check [setUp].
     */
    @Test
    fun testStateDefaultsToEmpty() {
        assertEmpty(LuaProjectSettings.State().enabledDefinitionLibraries)
    }

    @Test
    fun testRoundTripsThroughPersistedState() {
        settings.setEnabledDefinitionLibrariesAndNotify(listOf("busted", "luassert"))
        assertEquals(listOf("busted", "luassert"), settings.enabledDefinitionLibraries)
        // The persisted State object is what lunar.xml serializes, so assert on it directly.
        assertEquals(listOf("busted", "luassert"), settings.state.enabledDefinitionLibraries.toList())
    }

    @Test
    fun testDeduplicates() {
        settings.setEnabledDefinitionLibrariesAndNotify(listOf("busted", "busted", "luassert"))
        assertEquals(listOf("busted", "luassert"), settings.enabledDefinitionLibraries)
    }

    /**
     * A no-op write must not fire a reload — `PlatformLibraryIndex.reload()` rebuilds the stub
     * index across every open project (Risk 1.3).
     *
     * The refresh is marshalled with `invokeLater`, so the queue is pumped before counting;
     * without that this would assert nothing at all. `PlatformTestUtil.dispatchAllEventsInIdeEventQueue`
     * rather than `UIUtil.dispatchAllInvocationEvents`: the latter passed in a filtered run and
     * failed in the full suite, because it does not reliably drain a runnable queued under the
     * modality state an earlier test class left behind.
     */
    @Test
    fun testUnchangedListDoesNotNotify() {
        settings.setEnabledDefinitionLibrariesAndNotify(listOf("busted"))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        var notifications = 0
        project.messageBus.connect(testRootDisposable)
            .subscribe(
                LuaSettingsChangedListener.TOPIC,
                object : LuaSettingsChangedListener {
                    override fun onSettingsChanged() {
                        notifications++
                    }
                },
            )

        settings.setEnabledDefinitionLibrariesAndNotify(listOf("busted"))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals("An unchanged list must not trigger a global index reload", 0, notifications)

        settings.setEnabledDefinitionLibrariesAndNotify(listOf("busted", "love2d"))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals("A changed list must refresh the roots", 1, notifications)
    }

    /** The state write is synchronous even though the refresh is not — callers read back at once. */
    @Test
    fun testStateVisibleBeforeRefreshRuns() {
        settings.setEnabledDefinitionLibrariesAndNotify(listOf("busted"))
        assertEquals(listOf("busted"), settings.enabledDefinitionLibraries)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }
}
