package net.internetisalie.lunar.platform.target

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.project.PlatformLibraryIndex
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * REDIS-03 Phase 2 — Valkey stub resources (TC-STUB-1..5).
 *
 * These tests drive the *real* reference-resolution path (`multiResolve`) against the
 * **bundled** `runtime/valkey/valkey-8/` stubs under a real `Target(VALKEY, "8")`, so a green
 * assertion proves what a user actually gets: `server.*` / `redis.*` member access resolves and
 * `SERVER_*` globals are present. This is the same mechanism by which `redis.call` resolves
 * (`function redis.call` indexed in `LuaGlobalDeclarationIndex`), applied to the Valkey target.
 *
 * Note (design deviation, see risks-and-gaps §Gap 2.3): `server.*` resolves through concrete
 * `function server.<m>` declarations in `server.lua` (design §9 fallback), NOT through
 * `---@class server : redis` inheritance — the reference resolver does not consult `@class`
 * inheritance for dotted member access, and a global `server = {}` assignment is never indexed
 * into `LuaClassNameIndex` (only `local`-declared classes are). The class tag is retained for
 * hover / type-hierarchy fidelity.
 */
@RunWith(JUnit4::class)
class ValkeyStubResourceTest : IndexedBasePlatformTestCase() {

    /**
     * Set the project target to `Target(VALKEY, "8")` and force the platform-library roots +
     * stub index to reload, so the bundled `runtime/valkey/valkey-8/` stubs are indexed and
     * visible to resolution deterministically (independent of test ordering).
     */
    private fun setValkey8Target() {
        val valkey8 = requireNotNull(PlatformVersionRegistry.findVersion(LuaPlatform.VALKEY, "8"))
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            LuaProjectSettings.getInstance(project).setTargetAndNotify(Target(LuaPlatform.VALKEY, valkey8))
            PlatformLibraryIndex.reload()
        }
    }

    /** Resolve the member leaf of a `receiver.member(...)` call in [source] against the bundled stubs. */
    private fun resolveCountForMember(source: String, member: String): Int {
        setValkey8Target()
        myFixture.configureByText("test.lua", source)
        return runReadAction {
            val leaf = myFixture.file.findElementAt(myFixture.file.text.indexOf(member))
            val nameRef = leaf?.parent as? LuaNameRef ?: return@runReadAction 0
            val ref = nameRef.reference as? PsiPolyVariantReference ?: return@runReadAction 0
            ref.multiResolve(false).size
        }
    }

    // -------------------------------------------------------------------------
    // TC-STUB-1: server.call resolves under a real Valkey target via the bundled
    //            server.lua stub.
    // -------------------------------------------------------------------------
    @Test
    fun testServerCallResolvesThroughRedisInheritance() {
        val count = resolveCountForMember("server.call(\"PING\")", "call")
        assertTrue("server.call must resolve against the bundled valkey-8 server.lua stub", count > 0)
    }

    // -------------------------------------------------------------------------
    // TC-STUB-2: SERVER_VERSION_NUM is declared with @type number in
    //            server_global.lua; the Valkey-8 library root contains the file.
    // -------------------------------------------------------------------------
    @Test
    fun testServerGlobalStubPresentInValkeyLibraryRoot() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val valkey8 = requireNotNull(PlatformVersionRegistry.findVersion(LuaPlatform.VALKEY, "8"))
            val target = Target(LuaPlatform.VALKEY, valkey8)
            val files = RuntimeLibraryProvider(project).getLibraryFiles(target)
            val serverGlobal = files.firstOrNull { it.name == "server_global.lua" }
            assertNotNull("server_global.lua must be present in valkey-8 library root", serverGlobal)
            val content = serverGlobal?.contentsToByteArray()?.toString(Charsets.UTF_8) ?: return@runInEdtAndWait
            assertTrue("SERVER_VERSION_NUM must be declared", content.contains("SERVER_VERSION_NUM"))
            assertTrue("SERVER_VERSION_NUM must be typed number", content.contains("@type number"))
        }
    }

    // -------------------------------------------------------------------------
    // TC-STUB-3: server.error_reply resolves under a real Valkey target — mirroring
    //            redis.error_reply through the bundled server.lua stub.
    // -------------------------------------------------------------------------
    @Test
    fun testServerErrorReplyInheritedFromRedisNoLocalDecl() {
        val count = resolveCountForMember("server.error_reply(\"x\")", "error_reply")
        assertTrue("server.error_reply must resolve against the bundled valkey-8 server.lua stub", count > 0)
    }

    // -------------------------------------------------------------------------
    // TC-STUB-4: redis.*/KEYS/ARGV resolve under the Valkey target.
    //            redis.lua and global.lua are present in both valkey dirs.
    // -------------------------------------------------------------------------
    @Test
    fun testRedisCompatFilesInBothValkeyLibraryRoots() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            for (label in listOf("7.2", "8")) {
                val version = requireNotNull(PlatformVersionRegistry.findVersion(LuaPlatform.VALKEY, label))
                val target = Target(LuaPlatform.VALKEY, version)
                val files = RuntimeLibraryProvider(project).getLibraryFiles(target)
                val names = files.map { it.name }.toSet()
                assertTrue("redis.lua missing from valkey-$label", names.contains("redis.lua"))
                assertTrue("global.lua missing from valkey-$label", names.contains("global.lua"))
                assertTrue("server.lua missing from valkey-$label", names.contains("server.lua"))
            }
        }
    }

    // -------------------------------------------------------------------------
    // TC-STUB-5: redis.call resolves under the Valkey target via the compat
    //            redis.lua namespace (bundled valkey-8 copy of the redis base).
    // -------------------------------------------------------------------------
    @Test
    fun testRedisCallResolvesThroughCompatNamespace() {
        val count = resolveCountForMember("redis.call(\"GET\")", "call")
        assertTrue("redis.call must resolve against the bundled valkey-8 redis.lua compat stub", count > 0)
    }
}
