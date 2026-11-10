package net.internetisalie.lunar.corpus.audit

import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.corpus.CorpusDefinitionLibraries
import net.internetisalie.lunar.corpus.CorpusManifest
import net.internetisalie.lunar.corpus.CorpusSourcePath
import net.internetisalie.lunar.definitions.LuaDefinitionLibraryFetcher
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

/**
 * MAINT-56: MAINT-54's audit over the representative type corpus, `tooling/corpus/type-corpus.json`, each
 * member with its target pinned and its definition libraries enabled (design §5.2). Writes
 * `build/type-corpus/audit/<member>.json` and `build/type-corpus/extras/<member>.json`.
 *
 * Opt-in only, with `-PwithTypeCorpus`, never with `-PwithCorpus` or `-PwithTypeAudit` (BUG-418):
 * `test -PwithTypeCorpus --rerun --no-build-cache --tests '*RepresentativeTypeAudit*'`. Needs
 * `tooling/corpus/fetch-corpus.py --manifest tooling/corpus/type-corpus.json` first. One method per
 * manifest entry; `RepresentativeMemberSetTest` keeps the two in step.
 */
@RunWith(JUnit4::class)
class LuaRepresentativeTypeAuditTest : BasePlatformTestCase() {
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
        VfsRootAccess.allowRootAccess(testRootDisposable, LuaDefinitionLibraryFetcher.defaultCacheRoot().toString())
        LuaProjectSettings.getInstance(project).state.enabledDefinitionLibraries = mutableListOf()
        savedSourcePath = CorpusSourcePath.snapshot(project)
        runner = AuditMemberRun(myFixture, AuditRunMode.REPRESENTATIVE)
    }

    override fun tearDown() {
        try {
            if (::runner.isInitialized) runner.restore(savedSourcePath)
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun testTelescopeMember() = runner.run("telescope")

    @Test
    fun testGitsignsMember() = runner.run("gitsigns")

    @Test
    fun testBlinkcmpMember() = runner.run("blinkcmp")

    @Test
    fun testDifferMember() = runner.run("differ")

    @Test
    fun testWeakaurasMember() = runner.run("weakauras")

    @Test
    fun testOufMember() = runner.run("ouf")

    @Test
    fun testOmniinvMember() = runner.run("omniinv")

    @Test
    fun testKristalMember() = runner.run("kristal")

    @Test
    fun testPrismMember() = runner.run("prism")

    @Test
    fun testOpensmceMember() = runner.run("opensmce")

    @Test
    fun testFactoryplannerMember() = runner.run("factoryplanner")

    @Test
    fun testRampantMember() = runner.run("rampant")

    @Test
    fun testFaccMember() = runner.run("facc")

    @Test
    fun testAwesomeMember() = runner.run("awesome")

    @Test
    fun testLainMember() = runner.run("lain")

    @Test
    fun testSomewmMember() = runner.run("somewm")

    @Test
    fun testBullmqMember() = runner.run("bullmq")

    @Test
    fun testInngestMember() = runner.run("inngest")

    @Test
    fun testBeequeueMember() = runner.run("beequeue")

    @Test
    fun testKongMember() = runner.run("kong")

    @Test
    fun testApisixMember() = runner.run("apisix")

    @Test
    fun testRestyhttpMember() = runner.run("restyhttp")

    @Test
    fun testTntmetricsMember() = runner.run("tntmetrics")

    @Test
    fun testTntcrudMember() = runner.run("tntcrud")

    @Test
    fun testTntqueueMember() = runner.run("tntqueue")

    @Test
    fun testPokisampleMember() = runner.run("pokisample")

    @Test
    fun testDruidMember() = runner.run("druid")

    @Test
    fun testMonarchMember() = runner.run("monarch")

    @Test
    fun testLualsMember() = runner.run("luals")

    @Test
    fun testLdocMember() = runner.run("ldoc")

    @Test
    fun testLnkoMember() = runner.run("lnko")

    @Test
    fun testFalloMember() = runner.run("fallo")

    @Test
    fun testLdeMember() = runner.run("lde")

    @Test
    fun testSnabbMember() = runner.run("snabb")

    @Test
    fun testLjtkMember() = runner.run("ljtk")
}
