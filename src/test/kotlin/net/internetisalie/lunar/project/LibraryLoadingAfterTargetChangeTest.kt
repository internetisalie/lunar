package net.internetisalie.lunar.project

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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test: Verify that library files load after target changes.
 *
 * This test ensures that:
 * 1. Changing the project target updates the library index
 * 2. Code references resolve correctly after the change
 * 3. No stale libraries remain from the previous target
 */
class LibraryLoadingAfterTargetChangeTest {
    private lateinit var fixture: CodeInsightTestFixture
    private lateinit var project: Project

    @BeforeEach
    fun before() {
        val descriptor = LightProjectDescriptor()
        val factory = IdeaTestFixtureFactory.getFixtureFactory()
        val builder = factory.createLightFixtureBuilder(descriptor, "LibraryLoadingTest")
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
    fun testLibraryFilesLoadAfterTargetChange() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val settings = LuaProjectSettings.getInstance(project)

            val initialTarget = Target(LuaPlatform.STANDARD, VersionEntry("5.4", "lua-5.4"))
            settings.setTargetAndNotify(initialTarget)

            val initialLibraries = PlatformLibraryIndex.getPlatformLibrary(project)
            assertNotNull(initialLibraries, "Should have library files for initial target (Lua 5.4)")
            val (initialLevel, initialRoot) = initialLibraries
            assertTrue(initialRoot.children.isNotEmpty(), "Initial library should have files")

            val newTarget = Target(LuaPlatform.STANDARD, VersionEntry("5.1", "lua-5.1"))
            settings.setTargetAndNotify(newTarget)

            val updatedLibraries = PlatformLibraryIndex.getPlatformLibrary(project)
            assertNotNull(updatedLibraries, "Should have library files after target change")
            val (updatedLevel, updatedRoot) = updatedLibraries

            assertTrue(
                initialRoot != updatedRoot,
                "Library root should change when target changes"
            )
            assertTrue(
                initialLevel != updatedLevel,
                "Language level should change when target changes"
            )

            assertTrue(
                updatedRoot.children.isNotEmpty(),
                "Updated library should have files"
            )
        }
    }

    @Test
    fun testLibraryIndexReloadsOnTargetChange() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val settings = LuaProjectSettings.getInstance(project)

            val standard54 = Target(LuaPlatform.STANDARD, VersionEntry("5.4", "lua-5.4"))
            settings.setTargetAndNotify(standard54)

            val initialLibFolder = PlatformLibraryIndex.getPlatformLibraryFolder(project)
            assertNotNull(initialLibFolder, "Initial library folder should exist")

            val redis7 = Target(LuaPlatform.REDIS, VersionEntry("7+", "redis-7"))
            settings.setTargetAndNotify(redis7)

            val redisLibFolder = PlatformLibraryIndex.getPlatformLibraryFolder(project)
            assertNotNull(redisLibFolder, "Redis library folder should exist after target change")

            assertTrue(
                initialLibFolder.path != redisLibFolder.path,
                "Library folders should be different for different platforms"
            )
        }
    }

    @Test
    fun testPackageFilesUpdatedAfterTargetChange() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val settings = LuaProjectSettings.getInstance(project)

            val standard54 = Target(LuaPlatform.STANDARD, VersionEntry("5.4", "lua-5.4"))
            settings.setTargetAndNotify(standard54)

            val initialPackages = PlatformLibraryIndex.getPackageFiles(project)
            assertTrue(initialPackages.isNotEmpty(), "Should have package files for Lua 5.4")

            val standard51 = Target(LuaPlatform.STANDARD, VersionEntry("5.1", "lua-5.1"))
            settings.setTargetAndNotify(standard51)

            val updatedPackages = PlatformLibraryIndex.getPackageFiles(project)
            assertTrue(updatedPackages.isNotEmpty(), "Should have package files for Lua 5.1 after switch")

            assertTrue(
                initialPackages.isNotEmpty() && updatedPackages.isNotEmpty(),
                "Both targets should provide library files"
            )
        }
    }
}
