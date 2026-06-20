package net.internetisalie.lunar.lang.doc

import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaCommentOwner
import javax.swing.Icon

class LuaDocSearchItem(
    private val project: Project,
    val symbolName: String,
    private val fileUrl: String,
    private val declarationOffset: Int
) : NavigationItem {
    override fun getName(): String = symbolName

    override fun getPresentation(): ItemPresentation? {
        val vFile = VirtualFileManager.getInstance().findFileByUrl(fileUrl) ?: return null
        val relativePath = ProjectFileIndex.getInstance(project)
            .getContentRootForFile(vFile)?.let { VfsUtilCore.getRelativePath(vFile, it) }
            ?: vFile.name
        return object : ItemPresentation {
            override fun getPresentableText(): String = symbolName
            override fun getLocationString(): String = relativePath
            override fun getIcon(unused: Boolean): Icon? {
                return runReadAction {
                    val element = PsiManager.getInstance(project).findFile(vFile)
                        ?.findElementAt(declarationOffset) ?: return@runReadAction null
                    val owner = PsiTreeUtil.getParentOfType(element, LuaCommentOwner::class.java)
                    owner?.getIcon(0)
                }
            }
        }
    }

    override fun navigate(requestFocus: Boolean) {
        val vFile = VirtualFileManager.getInstance().findFileByUrl(fileUrl) ?: return
        val owner = runReadAction {
            val psiFile = PsiManager.getInstance(project).findFile(vFile)
                ?: return@runReadAction null
            val element = psiFile.findElementAt(declarationOffset)
                ?: return@runReadAction null
            PsiTreeUtil.getParentOfType(element, LuaCommentOwner::class.java)
        }
        (owner as? Navigatable)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = true
    override fun canNavigateToSource(): Boolean = true
}
