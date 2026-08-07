package net.internetisalie.lunar.lang

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.psi.LuaTerminalExpr
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Slash-style module names (`require("ui/uimanager")`) resolve only through `package.path`, never
 * through the filename-index fallback: [net.internetisalie.lunar.lang.path.resolveModuleCandidates]
 * asks `FilenameIndex` for a *bare* file name, and `"ui/uimanager"` is not one.
 *
 * This is the mechanism the corpus manifest's `moduleRoot` column depends on, so it is pinned here
 * rather than left implicit in the sweep. Note the source path must point at real on-disk files:
 * `findByPath` consults `LocalFileSystem` only, so a `temp://` fixture path resolves nothing.
 */
class LuaSourcePathModuleResolutionTest : BaseDocumentTest() {
    @TempDir
    lateinit var moduleRoot: Path

    private fun resolveRequire(moduleName: String): Boolean {
        myFixture.configureByText(LuaFileType, "local m = require(\"$moduleName\")\n")
        return runReadAction {
            PsiTreeUtil
                .findChildrenOfType(myFixture.file, LuaTerminalExpr::class.java)
                .flatMap { it.references.toList() }
                .filterIsInstance<LuaRequireReference>()
                .any { it.resolve() != null }
        }
    }

    private fun declareModuleRoot() {
        val base = moduleRoot.toFile().canonicalPath
        LuaProjectSettings.getInstance(myFixture.project).state.sourcePath =
            "$base/?.lua;$base/?/init.lua"
    }

    @Test
    fun testSlashModuleResolvesUnderDeclaredRoot() {
        moduleRoot.resolve("ui").createDirectories()
        moduleRoot.resolve("ui/uimanager.lua").writeText("return {}\n")
        declareModuleRoot()
        Assertions.assertTrue(
            resolveRequire("ui/uimanager"),
            "require(\"ui/uimanager\") should resolve against the declared module root",
        )
    }

    @Test
    fun testInitLuaResolvesUnderDeclaredRoot() {
        moduleRoot.resolve("ui/widget").createDirectories()
        moduleRoot.resolve("ui/widget/init.lua").writeText("return {}\n")
        declareModuleRoot()
        Assertions.assertTrue(
            resolveRequire("ui/widget"),
            "require(\"ui/widget\") should resolve to the directory's init.lua",
        )
    }

    /** Without the declared root the same require is unresolved — which is the 76% KOReader read. */
    @Test
    fun testSlashModuleIsUnresolvedWithoutDeclaredRoot() {
        moduleRoot.resolve("ui").createDirectories()
        moduleRoot.resolve("ui/uimanager.lua").writeText("return {}\n")
        Assertions.assertFalse(
            resolveRequire("ui/uimanager"),
            "Without a module root on package.path, a slash-style require has nothing to resolve to",
        )
    }
}
