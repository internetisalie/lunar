package net.internetisalie.lunar

import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.CompletionType
import com.intellij.tools.ide.performanceTesting.commands.assertCompletionCommandContains
import com.intellij.tools.ide.performanceTesting.commands.closeLookup
import com.intellij.tools.ide.performanceTesting.commands.doComplete
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import com.intellij.tools.ide.performanceTesting.commands.goto
import com.intellij.tools.ide.performanceTesting.commands.openFile
import com.intellij.tools.ide.performanceTesting.commands.waitForSmartMode
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

/**
 * Full-IDE integration test for COMP-03 recursive cross-file completion.
 *
 * This is verified here rather than as a unit test because the behaviour depends on real
 * `require()` resolution (which needs the real [com.intellij.openapi.vfs.LocalFileSystem]) and a
 * fully indexed project — neither of which the light `LightTempDirTestFixture` provides. See the
 * disabled unit test `LuaCompletionTest.COMP-03 Verify recursive cross-file completion`.
 *
 * Project shape (transitive require graph):
 *   main.lua     -> require("module_b")
 *   module_b.lua -> require("module_a"); function helper_from_b() end
 *   module_a.lua -> function helper_from_a() end
 *
 * Completing `helper_` in main.lua must offer both `helper_from_b` (direct require) and
 * `helper_from_a` (reached transitively through module_b's require of module_a).
 */
class LuaCrossFileCompletionIntegrationTest {

    @Test
    fun `recursive cross-file completion offers transitively required globals`() {
        val projectDir = createTransitiveRequireProject()

        val context = Starter.newContext(
            testName = "RecursiveCrossFileCompletion",
            testCase = TestCase(
                ideInfo = IdeProductResolver.getConfiguredIdeProduct(),
                projectInfo = LocalProjectInfo(projectDir),
            ).withVersion(IdeProductResolver.getTestVersion()),
        )
        IdeProductResolver.applyLicense(context)

        // assertCompletionCommandContains reads dumped completion items from the directory named by
        // the `completion.command.report.dir` system property; without it the IDE-side command
        // throws "Completion items dump dir not set". Point it at a writable path in the launched
        // IDE (the per-test system dir, which the CompletionCommand mkdirs into).
        context.applyVMOptionsPatch {
            addSystemProperty("completion.command.report.dir", context.paths.systemDir.resolve("completion-report"))
        }

        val pathToPlugin = System.getProperty("path.to.build.plugin")
        require(pathToPlugin != null) { "path.to.build.plugin system property not set" }
        PluginConfigurator(context).installPluginFromPath(File(pathToPlugin).toPath())

        // Caret is placed just after `helper_` on line 2 of main.lua (1-based line/column).
        val commands = CommandChain()
            .waitForSmartMode()
            .openFile("main.lua")
            .goto(2, 8)
            .doComplete(CompletionType.BASIC)
            .assertCompletionCommandContains(listOf("helper_from_a", "helper_from_b"))
            // Dismiss the lookup and quit, else the open completion popup keeps the IDE alive until
            // the runTimeout (the script-chain runIDE has no implicit exit like runIdeWithDriver).
            .closeLookup()
            .exitApp()

        context.runIDE(commands = commands, runTimeout = 5.minutes)
    }

    private fun createTransitiveRequireProject(): Path {
        val projectDir = Path.of("build/test-projects/lua-recursive-cross-file")
        projectDir.createDirectories()
        projectDir.resolve(".idea").createDirectories()

        projectDir.resolve("module_a.lua").writeText(
            """
            function helper_from_a() end
            return {}
            """.trimIndent() + "\n",
        )
        projectDir.resolve("module_b.lua").writeText(
            """
            require("module_a")
            function helper_from_b() end
            return {}
            """.trimIndent() + "\n",
        )
        projectDir.resolve("main.lua").writeText(
            """
            require("module_b")
            helper_
            """.trimIndent() + "\n",
        )
        return projectDir
    }
}
