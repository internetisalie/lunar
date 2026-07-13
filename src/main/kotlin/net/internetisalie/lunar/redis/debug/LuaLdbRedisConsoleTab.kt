package net.internetisalie.lunar.redis.debug

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBTextField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.internetisalie.lunar.redis.console.RespReplyTreeConsole
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The mid-pause "Redis" console tab (design §2.7, AC-6).
 *
 * A command input field over the REUSED REDIS-01 [RespReplyTreeConsole] (no reply-tree duplication).
 * A submitted line is tokenized and run through [LuaLdbController.redisCommand] on [scope] (off-EDT),
 * then the reply is rendered into the tree on the EDT (as REDIS-01 §2.6 marshals tree mutations).
 * [Disposable]: the reply console is registered as a child so it tears down with the tab. Holds no
 * hard `Editor`/`PsiFile`/`VirtualFile` field — only the project-scoped console and the controller.
 */
class LuaLdbRedisConsoleTab(
    project: Project,
    private val controller: LuaLdbController,
    private val scope: CoroutineScope,
) : Disposable {

    private val replyConsole = RespReplyTreeConsole(project)
    private val input = JBTextField()

    val component: JComponent = JPanel(BorderLayout()).apply {
        add(input, BorderLayout.NORTH)
        add(replyConsole.component, BorderLayout.CENTER)
    }

    init {
        Disposer.register(this, replyConsole)
        input.addActionListener { onSubmit() }
    }

    private fun onSubmit() {
        val line = input.text?.trim().orEmpty()
        if (line.isEmpty()) return
        input.text = ""
        submit(line)
    }

    /** Tokenizes [commandLine] on whitespace, runs it in the paused session, and renders the reply. */
    fun submit(commandLine: String) {
        val args = commandLine.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        if (args.isEmpty()) return
        scope.launch {
            val reply = controller.redisCommand(args)
            withContext(Dispatchers.EDT) { replyConsole.showReply(reply) }
        }
    }

    override fun dispose() {
        // replyConsole is disposed via Disposer registration.
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
