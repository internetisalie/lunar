package net.internetisalie.lunar.platform.target

import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import net.internetisalie.lunar.platform.LuaPlatform
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.AfterTest
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RuntimeLibraryProviderTest {
    private lateinit var fixture: CodeInsightTestFixture

    @BeforeEach
    fun before() {
        val descriptor = LightProjectDescriptor()
        val factory = IdeaTestFixtureFactory.getFixtureFactory()
        val builder = factory.createLightFixtureBuilder(descriptor, "RuntimeLibraryProviderTest")
        fixture = factory.createCodeInsightFixture(
            builder.fixture,
            LightTempDirTestFixtureImpl(false)
        )
        fixture.setUp()
    }

    @AfterTest
    fun after() {
        fixture.tearDown()
    }

    @Test
    fun testGetLibraryRootForStandardLua51() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val provider = RuntimeLibraryProvider(fixture.project)
            val target = Target(LuaPlatform.STANDARD, VersionEntry("5.1", "lua-5.1"))
            val root = provider.getLibraryRoot(target)
            assertNotNull(root, "Library root should exist for Standard Lua 5.1")
            assertTrue(root.isDirectory, "Library root should be a directory")
        }
    }

    @Test
    fun testGetLibraryRootForStandardLua54() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val provider = RuntimeLibraryProvider(fixture.project)
            val target = Target(LuaPlatform.STANDARD, VersionEntry("5.4", "lua-5.4"))
            val root = provider.getLibraryRoot(target)
            assertNotNull(root, "Library root should exist for Standard Lua 5.4")
            assertTrue(root.isDirectory, "Library root should be a directory")
        }
    }

    @Test
    fun testGetLibraryRootForRedis7() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val provider = RuntimeLibraryProvider(fixture.project)
            val target = Target(LuaPlatform.REDIS, VersionEntry("7+", "redis-7"))
            val root = provider.getLibraryRoot(target)
            assertNotNull(root, "Library root should exist for Redis 7")
            assertTrue(root.isDirectory, "Library root should be a directory")
        }
    }

    @Test
    fun testGetLibraryFilesForStandardLua54() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val provider = RuntimeLibraryProvider(fixture.project)
            val target = Target(LuaPlatform.STANDARD, VersionEntry("5.4", "lua-5.4"))
            val files = provider.getLibraryFiles(target)
            assertTrue(files.isNotEmpty(), "Should have library files for Standard Lua 5.4")
            assertTrue(files.all { it.extension == "lua" }, "All library files should be .lua files")
        }
    }

    @Test
    fun testGetLibraryFilesForRedis7() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val provider = RuntimeLibraryProvider(fixture.project)
            val target = Target(LuaPlatform.REDIS, VersionEntry("7+", "redis-7"))
            val files = provider.getLibraryFiles(target)
            assertTrue(files.isNotEmpty(), "Should have library files for Redis 7")
            assertTrue(files.all { it.extension == "lua" }, "All library files should be .lua files")
        }
    }

    @Test
    fun testGetLibraryFilesForFuturePlatformReturnsEmpty() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val provider = RuntimeLibraryProvider(fixture.project)
            val target = Target(LuaPlatform.LUAJIT, VersionEntry("2.1", "luajit-2.1"))
            val files = provider.getLibraryFiles(target)
            assertTrue(files.isEmpty(), "Future platforms should return empty list when not yet bundled")
        }
    }

    @Test
    fun testGetLibraryRootReturnsCorrectPath() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val provider = RuntimeLibraryProvider(fixture.project)
            val target = Target(LuaPlatform.STANDARD, VersionEntry("5.4", "lua-5.4"))
            val root = provider.getLibraryRoot(target)
            assertNotNull(root)
            val expectedPath = "runtime/standard/lua-5.4"
            assertTrue(
                root.path.endsWith(expectedPath),
                "Library root path should end with $expectedPath, got ${root.path}"
            )
        }
    }
}
