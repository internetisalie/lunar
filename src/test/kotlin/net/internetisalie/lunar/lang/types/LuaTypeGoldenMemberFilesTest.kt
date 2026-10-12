package net.internetisalie.lunar.lang.types

import net.internetisalie.lunar.BaseDocumentTest
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/** TYPE-12-03/-04 over real pinned-corpus files; needs the out-of-repo test/ tree, so not run in CI. */
class LuaTypeGoldenMemberFilesTest : BaseDocumentTest() {
    @Test
    fun everyMemberFileMatchesItsGolden() {
        val failures = TypeGoldenFixtures.memberFiles().mapNotNull { TypeGolden.mismatch(myFixture, it) }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
