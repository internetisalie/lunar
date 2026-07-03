package net.internetisalie.lunar.rocks.env

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/** Phase 2: locator resolution (design §3.1) — TC-1/2/3. */
class HererocksLocatorTest : BasePlatformTestCase() {

    fun testHererocksOnPath() {
        val fake = File("/opt/bin/hererocks")
        val prefix = HererocksLocator.resolvePrefix(
            findInPath = { name -> if (name == "hererocks") fake else null },
            probeImport = { false },
        )
        assertEquals(listOf(fake.absolutePath), prefix)
    }

    fun testPython3ModuleFallback() {
        val python = File("/usr/bin/python3")
        val prefix = HererocksLocator.resolvePrefix(
            findInPath = { name -> if (name == "python3") python else null },
            probeImport = { path -> path == python.absolutePath },
        )
        assertEquals(listOf(python.absolutePath, "-m", "hererocks"), prefix)
    }

    fun testNoneAvailable() {
        val prefix = HererocksLocator.resolvePrefix(
            findInPath = { null },
            probeImport = { false },
        )
        assertNull(prefix)
    }
}
