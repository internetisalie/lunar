package net.internetisalie.lunar.lang.library

import com.intellij.openapi.project.Project
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.platform.target.VersionEntry
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LibraryProviderTest {
    private lateinit var fixture: CodeInsightTestFixture
    private lateinit var project: Project

    @BeforeEach
    fun before() {
        val descriptor = LightProjectDescriptor()
        val factory = IdeaTestFixtureFactory.getFixtureFactory()
        val builder = factory.createLightFixtureBuilder(descriptor, "LibraryProviderTest")
        fixture = factory.createCodeInsightFixture(
            builder.fixture,
            LightTempDirTestFixtureImpl(false)
        )
        fixture.setUp()
        project = fixture.project
    }

    @AfterTest
    fun after() {
        fixture.tearDown()
    }

    @Test
    fun testGetAdditionalProjectLibraries() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val provider = LuaLibraryProvider()
            val settings = LuaProjectSettings.getInstance(project)

            // Set to 5.4
            settings.setTargetAndNotify(Target(LuaPlatform.STANDARD, VersionEntry("5.4", "lua-5.4")))
            val libraries54 = provider.getAdditionalProjectLibraries(project)
            assertEquals(1, libraries54.size)
            val library54 = libraries54.first() as LuaLibraryProvider.LuaLibrary
            val roots54 = library54.sourceRoots
            assertEquals(1, roots54.size)
            assertTrue(roots54.first().path.endsWith("runtime/standard/lua-5.4"))

            // Verify presentation properties
            assertEquals("Lua External API Stubs", library54.presentableText)
            assertEquals("Lua Stubs", library54.locationString)
            assertNotNull(library54.getIcon(false))

            // Set to 5.1
            settings.setTargetAndNotify(Target(LuaPlatform.STANDARD, VersionEntry("5.1", "lua-5.1")))
            val libraries51 = provider.getAdditionalProjectLibraries(project)
            assertEquals(1, libraries51.size)
            val library51 = libraries51.first() as LuaLibraryProvider.LuaLibrary
            val roots51 = library51.sourceRoots
            assertEquals(1, roots51.size)
            assertTrue(roots51.first().path.endsWith("runtime/standard/lua-5.1"))

            // Equality checks
            assertEquals(library54, library54)
            assertTrue(library54 != library51)
        }
    }

    @Test
    fun testGetRootsToWatch() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val provider = LuaLibraryProvider()
            val settings = LuaProjectSettings.getInstance(project)

            // Set to 5.4
            settings.setTargetAndNotify(Target(LuaPlatform.STANDARD, VersionEntry("5.4", "lua-5.4")))
            val watchRoots54 = provider.getRootsToWatch(project)
            assertEquals(1, watchRoots54.size)
            assertTrue(watchRoots54.first().path.endsWith("runtime/standard/lua-5.4"))

            // Set to 5.1
            settings.setTargetAndNotify(Target(LuaPlatform.STANDARD, VersionEntry("5.1", "lua-5.1")))
            val watchRoots51 = provider.getRootsToWatch(project)
            assertEquals(1, watchRoots51.size)
            assertTrue(watchRoots51.first().path.endsWith("runtime/standard/lua-5.1"))
        }
    }
}
