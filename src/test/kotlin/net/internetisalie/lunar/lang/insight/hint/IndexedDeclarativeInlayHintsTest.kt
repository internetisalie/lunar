package net.internetisalie.lunar.lang.insight.hint

import com.intellij.openapi.application.WriteAction
import com.intellij.psi.stubs.StubIndex
import com.intellij.testFramework.EdtTestUtil

/**
 * Base class for inlay hints tests that require indexed platform files.
 *
 * Wraps [LuaInlayHintsTestCase] and ensures stub indices are rebuilt
 * for type resolution and documentation lookups.
 */
abstract class IndexedDeclarativeInlayHintsTest : LuaInlayHintsTestCase() {
    override fun setUp() {
        super.setUp()
        // Force index rebuild to ensure platform files are indexed
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            WriteAction.run<RuntimeException> {
                StubIndex.getInstance().forceRebuild(Throwable("Indexed inlay hints test setup: forcing stub index rebuild"))
            }
        }
    }
}
