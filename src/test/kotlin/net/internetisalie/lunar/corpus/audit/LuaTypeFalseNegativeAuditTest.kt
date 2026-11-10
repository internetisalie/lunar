package net.internetisalie.lunar.corpus.audit

import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.corpus.CorpusDefinitionLibraries
import net.internetisalie.lunar.corpus.CorpusManifest
import net.internetisalie.lunar.corpus.CorpusSourcePath
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

/**
 * MAINT-54: injects known type errors into copies of each corpus member's declared-demand sites and
 * writes what the checker reported to `build/type-audit/<member>.json` (design §3.5).
 *
 * Opt-in only, with `-PwithTypeAudit` (never `-PwithCorpus`, BUG-418):
 * `test -PwithTypeAudit --rerun --no-build-cache --tests '*FalseNegativeAudit*'`. No definition
 * library is enabled, so library calls count as unresolved. The per-member body is [AuditMemberRun]'s.
 */
@RunWith(JUnit4::class)
class LuaTypeFalseNegativeAuditTest : BasePlatformTestCase() {
    private var savedSourcePath: String = ""
    private lateinit var runner: AuditMemberRun

    override fun getTestDataPath(): String = System.getProperty("user.dir")

    override fun setUp() {
        super.setUp()
        CorpusDefinitionLibraries.requireSettingsSubscriber(project)
        VfsRootAccess.allowRootAccess(
            testRootDisposable,
            File(System.getProperty("user.dir"), CorpusManifest.CORPUS_DIR).canonicalPath,
        )
        LuaProjectSettings.getInstance(project).state.enabledDefinitionLibraries = mutableListOf()
        savedSourcePath = CorpusSourcePath.snapshot(project)
        runner = AuditMemberRun(myFixture, AuditRunMode.FALSE_NEGATIVE)
    }

    override fun tearDown() {
        try {
            if (::runner.isInitialized) runner.restore(savedSourcePath)
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun testLuacheckMember() = runner.run("luacheck")

    @Test
    fun testLuarocksMember() = runner.run("luarocks")

    @Test
    fun testZerobraneMember() = runner.run("zerobrane")

    @Test
    fun testPenlightMember() = runner.run("penlight")

    @Test
    fun testLazynvimMember() = runner.run("lazynvim")
}
