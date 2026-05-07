package net.internetisalie.lunar

import com.intellij.openapi.application.WriteAction
import com.intellij.psi.stubs.StubIndex
import com.intellij.testFramework.EdtTestUtil
import org.junit.jupiter.api.TestInfo
import kotlin.test.BeforeTest

/**
 * Test base class for tests that require indexed platform files.
 *
 * Extends [BaseDocumentTest] and adds [StubIndex.forceRebuild()] to ensure
 * platform files (stdlib definitions) are indexed for type resolution,
 * documentation lookups, etc.
 *
 * **Performance Note**: The stub rebuild adds ~1-3 seconds per test.
 * Only use this base class if your tests actually require indexed content.
 *
 * Tests that fail without this:
 * - [LuaTypeInlayHintsTest] - requires indexed type information
 * - [TestLuaTypeCheckPhase4] - requires indexed global declarations
 */
open class IndexedDocumentTest : BaseDocumentTest() {
    @BeforeTest
    override fun before(testInfo: TestInfo) {
        super.before(testInfo)

        // Force index rebuild to ensure platform files are indexed
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            WriteAction.run<RuntimeException> {
                StubIndex.getInstance().forceRebuild(Throwable("Indexed test setup: forcing stub index rebuild"))
            }
        }
    }
}
