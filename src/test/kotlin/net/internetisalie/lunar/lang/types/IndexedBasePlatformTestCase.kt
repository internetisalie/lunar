package net.internetisalie.lunar.lang.types

import com.intellij.openapi.application.WriteAction
import com.intellij.psi.stubs.StubIndex
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Base class for type checking tests that require indexed platform files.
 *
 * Wraps [BasePlatformTestCase] and ensures stub indices are rebuilt
 * for type inference, global declaration resolution, and related features.
 */
abstract class IndexedBasePlatformTestCase : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        // Force index rebuild to ensure platform files are indexed
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            WriteAction.run<RuntimeException> {
                StubIndex.getInstance().forceRebuild(Throwable("Indexed platform test setup: forcing stub index rebuild"))
            }
        }
    }
}

