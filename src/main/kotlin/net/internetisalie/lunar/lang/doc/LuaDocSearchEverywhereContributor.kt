package net.internetisalie.lunar.lang.doc

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManager
import com.intellij.ide.util.NavigationItemListCellRenderer
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.intellij.util.indexing.FileBasedIndex
import net.internetisalie.lunar.lang.indexing.LuaDescriptionIndex
import net.internetisalie.lunar.lang.psi.LuaCommentOwner
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.syntax.collectDescriptionText
import javax.swing.ListCellRenderer

class LuaDocSearchEverywhereContributor(
    private val project: Project
) : SearchEverywhereContributor<LuaDocSearchItem> {

    override fun getSearchProviderId(): String = this::class.java.simpleName

    override fun getGroupName(): String = "Lua Documentation"

    override fun getSortWeight(): Int = 600

    override fun showInFindResults(): Boolean = true

    override fun isShownInSeparateTab(): Boolean = true

    // MAINT-03: ProgressIndicatorUtils.yieldToPendingWriteActions() /
    // runInReadActionWithWriteActionPriority() are deprecated, but the only modern replacement
    // is coroutine readAction/smartReadAction — a threading-semantics change out of scope for
    // this behavior-preserving cleanup. Suppress rather than force-migrate.
    @Suppress("DEPRECATION")
    override fun fetchElements(
        pattern: String,
        progressIndicator: ProgressIndicator,
        consumer: Processor<in LuaDocSearchItem>
    ) {
        if (project.isDisposed || DumbService.isDumb(project) || pattern.isBlank()) return

        val tokens = pattern.trim().lowercase().split(Regex("[^a-zA-Z0-9_]+")).filter { it.length >= 2 }
        if (tokens.isEmpty()) return

        val task = Runnable {
            if (DumbService.isDumb(project)) return@Runnable
            val scope = try {
                if (SearchEverywhereManager.getInstance(project).isEverywhere) {
                    GlobalSearchScope.allScope(project)
                } else {
                    GlobalSearchScope.projectScope(project)
                }
            } catch (_: IllegalStateException) {
                GlobalSearchScope.projectScope(project)
            }

            val index = FileBasedIndex.getInstance()
            val firstToken = tokens[0]

            val matchingKeys = mutableListOf<String>()
            index.processAllKeys(
                LuaDescriptionIndex.KEY,
                Processor { key ->
                    ProgressManager.checkCanceled()
                    if (key.contains(firstToken)) {
                        matchingKeys.add(key)
                    }
                    true
                },
                scope,
                null
            )

            val seen = hashSetOf<String>()
            for (key in matchingKeys) {
                for (value in index.getValues(LuaDescriptionIndex.KEY, key, scope)) {
                    for (record in value.split('|')) {
                        ProgressManager.checkCanceled()
                        val parts = record.split('\t')
                        if (parts.size != 3) continue
                        val name = parts[0]
                        val fileUrl = parts[1]
                        val offsetStr = parts[2]
                        val offset = offsetStr.toIntOrNull() ?: continue
                        val dedupKey = "$name:$fileUrl"
                        if (!seen.add(dedupKey)) continue

                        if (tokens.size > 1 && !descriptionContainsAllTokens(fileUrl, offset, tokens)) {
                            continue
                        }

                        if (!consumer.process(LuaDocSearchItem(project, name, fileUrl, offset))) {
                            return@Runnable
                        }
                    }
                }
            }
        }

        val app = ApplicationManager.getApplication()
        if (app.isUnitTestMode) {
            runReadActionBlocking { task.run() }
        } else {
            app.assertIsNonDispatchThread()
            ProgressIndicatorUtils.yieldToPendingWriteActions()
            ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(task, progressIndicator)
        }
    }

    private fun descriptionContainsAllTokens(
        fileUrl: String,
        declOffset: Int,
        tokens: List<String>
    ): Boolean {
        val vFile = VirtualFileManager.getInstance().findFileByUrl(fileUrl) ?: return false
        return runReadActionBlocking {
            val psiFile = PsiManager.getInstance(project).findFile(vFile) as? LuaFile ?: return@runReadActionBlocking false
            val element = psiFile.findElementAt(declOffset) ?: return@runReadActionBlocking false
            val owner = PsiTreeUtil.getParentOfType(element, LuaCommentOwner::class.java)
                ?: return@runReadActionBlocking false
            val comment = owner.catsComment ?: return@runReadActionBlocking false
            val fullText = collectDescriptionText(comment)
            if (fullText.isBlank()) return@runReadActionBlocking false
            val lowerText = fullText.lowercase()
            tokens.all { token -> lowerText.contains(token) }
        }
    }

    override fun processSelectedItem(
        selected: LuaDocSearchItem,
        modifiers: Int,
        searchText: String
    ): Boolean {
        selected.navigate(true)
        return true
    }

    override fun getElementsRenderer(): ListCellRenderer<in LuaDocSearchItem> {
        return NavigationItemListCellRenderer()
    }

    override fun dispose() {}

    class Factory : SearchEverywhereContributorFactory<LuaDocSearchItem> {
        override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<LuaDocSearchItem> {
            val project = requireNotNull(initEvent.project) {
                "LuaDocSearchEverywhereContributor requires a project context"
            }
            return LuaDocSearchEverywhereContributor(project)
        }
    }
}
