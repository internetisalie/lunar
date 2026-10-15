package net.internetisalie.lunar.lang.indexing

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileBasedIndex

/**
 * BUG-503: one description-index value holds every record for one token in one file, so a token
 * shared by enough documented declarations produces a value over the 65535-byte limit of
 * `DataOutput.writeUTF`. Kept out of [net.internetisalie.lunar.lang.LuaDescriptionIndexTest]
 * because CI skips that class under `-PexcludeExternalFixtureTests`.
 */
class LuaDescriptionIndexValueSizeTest : BasePlatformTestCase() {
    fun testAValueOver64KbRoundTrips() {
        val declarationCount = 3000
        val text = (1..declarationCount).joinToString("") { "--- Returns the value.\nfunction vim.fn.f$it() end\n" }
        myFixture.addFileToProject("big.lua", text)

        val records = recordsInBigFile("returns")

        assertEquals(declarationCount, records.size)
        assertEquals((1..declarationCount).map { "vim.fn.f$it" }, records.map { it.ownerName })
    }

    private fun recordsInBigFile(token: String): List<DescriptionRecord> =
        runReadAction {
            FileBasedIndex
                .getInstance()
                .getValues(LuaDescriptionIndex.KEY, token, GlobalSearchScope.allScope(project))
                .flatMap { DescriptionRecord.parseAll(it) }
                .filter { it.fileUrl.endsWith("/big.lua") }
        }
}
