package net.internetisalie.lunar.lang.types

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaVar
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.project.PlatformLibraryIndex
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * MAINT-30-02 (§2.3/§3.4): the `LuaTypesSnapshot.forFile` snapshot cache, re-keyed onto
 * `CachedValuesManager`, invalidates on a text-free target switch (TC-04) and on a same-length
 * reparse (TC-05, the old FileUserData text-hash-collision staleness path), and stays reentrancy-safe
 * during a build (TC-06).
 */
@RunWith(JUnit4::class)
class LuaTypesSnapshotCacheTest : IndexedBasePlatformTestCase() {

    override fun tearDown() {
        try {
            setTarget(LuaPlatform.STANDARD, "5.4")
        } finally {
            super.tearDown()
        }
    }

    private fun setTarget(platform: LuaPlatform, label: String) {
        val version = requireNotNull(PlatformVersionRegistry.findVersion(platform, label))
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            LuaProjectSettings.getInstance(project).state.setTarget(Target(platform, version))
            PlatformLibraryIndex.reload()
        }
    }

    private inline fun <reified T : PsiElement> exprByText(text: String): T? =
        PsiTreeUtil.collectElementsOfType(myFixture.file, T::class.java).firstOrNull { it.text == text }

    @Test
    fun testTargetSwitchInvalidatesSnapshot() {
        // TC-04: `local x = KEYS[1]` infers `string` under REDIS (KEYS seeded) but not under STANDARD.
        // Switching the target with NO text edit must recompute the snapshot (cache depends on the
        // project's targetModificationTracker), not serve the stale REDIS-seeded type.
        setTarget(LuaPlatform.REDIS, "7+")
        myFixture.configureByText("test.lua", "local x = KEYS[1]")
        runReadAction {
            val keysAccess = requireNotNull(exprByText<LuaVar>("KEYS[1]")) { "KEYS[1] must exist" }
            assertEquals(
                "Under REDIS, KEYS[1] infers string",
                LuaGraphType.String,
                LuaTypesSnapshot.forFile(myFixture.file).getValueType(keysAccess),
            )
        }

        setTarget(LuaPlatform.STANDARD, "5.4")
        runReadAction {
            val keysAccess = requireNotNull(exprByText<LuaVar>("KEYS[1]")) { "KEYS[1] must exist" }
            assertNotSame(
                "After a text-free target switch, KEYS[1] must no longer infer the REDIS string type",
                LuaGraphType.String,
                LuaTypesSnapshot.forFile(myFixture.file).getValueType(keysAccess),
            )
        }
    }

    @Test
    fun testSameLengthEditInvalidatesSnapshot() {
        // TC-05: `local a = 1` → edit to `local a = "s"` — reparse must recompute (no stale-hash serve).
        val file = myFixture.configureByText("edit.lua", "local a = 1  ")
        runReadAction {
            val a = requireNotNull(exprByText<LuaNameRef>("a")) { "a must exist" }
            assertEquals(LuaGraphType.Number, LuaTypesSnapshot.forFile(file).getValueType(a))
        }

        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val document = myFixture.editor.document
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                document.setText("local a = \"s\"")
                com.intellij.psi.PsiDocumentManager.getInstance(project).commitDocument(document)
            }
        }

        runReadAction {
            val a = requireNotNull(exprByText<LuaNameRef>("a")) { "a must exist after edit" }
            assertEquals(
                "Same-length reparse must reflect the new type (no stale-hash serve)",
                LuaGraphType.String,
                LuaTypesSnapshot.forFile(myFixture.file).getValueType(a),
            )
        }
    }

    @Test
    fun testReentrantForFileDoesNotRecurse() {
        // TC-06: a self-referential callee resolved during visitFuncCall re-enters forFile while the
        // snapshot is under construction. The inProgressSnapshot guard short-circuits before
        // CachedValuesManager, so the build completes without recursion.
        val file = myFixture.configureByText(
            "reentrant.lua",
            """
            local function f()
                return f()
            end
            local y = f()
            """.trimIndent(),
        )
        runReadAction {
            val snapshot = LuaTypesSnapshot.forFile(file)
            assertNotNull("Reentrant build must complete without recursion", snapshot)
        }
    }
}
