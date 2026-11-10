package net.internetisalie.lunar.analysis.conformance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

/** MAINT-55 TC-16: LuaLS's copyright and MIT notice ship with the imported cases (design §5.4). */
@RunWith(JUnit4::class)
class ConformanceAttributionTest {
    private val header =
        Regex(
            """^-- LuaCATS conformance cases, adapted from LuaLS/lua-language-server """ +
                """test/diagnostics/([a-z-]+)\.lua @ [0-9a-f]{8} \(MIT\); see LICENSE-LuaLS$""",
        )

    @Test
    fun testLicenseIsLuaLsMit_TC_16() {
        val license = File(ConformancePaths.root(), "LICENSE-LuaLS").readText(Charsets.UTF_8)
        assertTrue(license.contains("Copyright (c) 2018 最萌小汐"))
        assertTrue(license.contains("Permission is hereby granted"))
    }

    @Test
    fun testEveryCaseFileNamesItsSource_TC_16() {
        val files =
            ConformancePaths
                .cases()
                .listFiles { file ->
                    file.extension == "lua"
                }.orEmpty()
                .sortedBy { it.name }
        assertTrue("no case files under ${ConformancePaths.cases()}", files.isNotEmpty())
        files.forEach { file ->
            val firstLine = file.readLines(Charsets.UTF_8).firstOrNull().orEmpty()
            val match = header.matchEntire(firstLine)
            assertTrue("${file.name} line 1 was: $firstLine", match != null)
            assertEquals(file.nameWithoutExtension, match?.groupValues?.get(1))
        }
    }
}
