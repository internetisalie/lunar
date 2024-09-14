package net.internetisalie.lunar.analysis.luacheck

import com.intellij.codeInsight.navigation.fileLocation
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.pom.Navigatable
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.SimpleNode
import com.intellij.ui.treeStructure.SimpleTreeStructure
import net.internetisalie.lunar.LuaBundle
import net.internetisalie.lunar.lang.LuaIcons
import java.util.*
import kotlin.io.path.Path

abstract class BaseNode<Value : Any>(
    project: Project,
    public val value: Value,
    protected val builder: ProblemTreeBuilder,
    protected val parent : BaseNode<*>?,
) : SimpleNode(project, parent) {
    val id : UUID = UUID.randomUUID()
    abstract val filter: ProblemFilter

    override fun getEqualityObjects(): Array<Any> {
        return arrayOf(id)
    }
}

class RootNode(project: Project, value: Any, builder: ProblemTreeBuilder) :
    BaseNode<Any>(project, value, builder, null) {

    override fun getChildren(): Array<SimpleNode> {
        return listOf<SimpleNode>(SummaryNode(project, builder, this)).toTypedArray()
    }

    override fun doUpdate(presentation: PresentationData) {}

    override val filter: ProblemFilter
        get() = ProblemFilter()

}

class SummaryNode(project: Project, builder: ProblemTreeBuilder, parent: BaseNode<*>) :
    BaseNode<ProblemSummary>(project, ProblemSummary(), builder, parent) {

    override fun doUpdate(presentation: PresentationData) {
        val problemCount: Int = builder.problems.size
        val fileCount: Int = builder.files.size
        presentation.presentableText = LuaBundle.message("problems.node.summary", problemCount, fileCount)
        presentation.setIcon(AllIcons.Nodes.Folder)
    }

    override fun getChildren(): Array<SimpleNode> =
        builder.files.map { FileNode(project, it, builder, this) }.toTypedArray()

    override val filter: ProblemFilter
        get() = parent?.filter ?: ProblemFilter()
}

class FileNode(project: Project, value: ProblemFile, builder: ProblemTreeBuilder, parent: BaseNode<*>) :
    BaseNode<ProblemFile>(project, value, builder, parent) {
    override fun doUpdate(presentation: PresentationData) {
        presentation.presentableText = value.file
        presentation.setIcon(LuaIcons.FILE)
    }

    override fun getChildren(): Array<SimpleNode> =
        filter.filter(builder.problems).map { ProblemNode(project, it, builder, this) }.toTypedArray()

    override val filter: ProblemFilter
        get() {
            val filter = parent?.filter ?: ProblemFilter()
            filter.file = value.file
            return filter
        }
}

class ProblemNode(project: Project, value: Problem, builder: ProblemTreeBuilder, parent: BaseNode<*>) :
    BaseNode<Problem>(project, value, builder, parent), Navigatable {
    override fun doUpdate(presentation: PresentationData) {
        presentation.presentableText = value.message
        presentation.locationString = locationString
        presentation.setIcon(AllIcons.General.BalloonWarning)
    }

    override fun getChildren():Array<SimpleNode> = emptySet<SimpleNode>().toTypedArray()

    private val locationString : String
        get() = (
                if (parent?.filter?.file != null) ":${value.lineStart}:${value.columnStart} - :${value.lineEnd}:${value.columnEnd}"
                else "${value.file}:${value.lineStart}:${value.columnStart}")


    override val filter: ProblemFilter
        get() = ProblemFilter(
            file = value.file,
            code = value.code,
        )

    override fun canNavigate() = true

    override fun navigate(requestFocus: Boolean) {
        getNavigator()?.navigate(requestFocus)
    }

    fun getNavigator(): OpenFileDescriptor? {
        val vFile = virtualFile // value.vFile
        return if (vFile != null)
            OpenFileDescriptor(project!!, vFile, value.lineStart, value.columnStart)
        else
            null
    }

    private val virtualFile
        get() = LocalFileSystem.getInstance().findFileByNioFile(Path(value.absFile!!))
}

interface ProblemTreeSettings {
    val showPreview: Boolean
    val isAutoScrollMode: Boolean
}

class ProblemTreeStructure(private val myProject : Project) : SimpleTreeStructure(), ProblemTreeSettings {
    private var myRootElement: SimpleNode? = null

    override var isAutoScrollMode: Boolean = true
    override var showPreview: Boolean = false
    // TODO: grouping

    fun setBuilder(source: ProblemTreeBuilder) {
        myRootElement = RootNode(myProject, Any(), source)
    }

    override fun getRootElement(): Any {
        return myRootElement!!
    }

    override fun getChildElements(element: Any): Array<Any> {
        return super.getChildElements(element) as Array<Any>
    }

    fun isAutoExpandNode(descriptor: NodeDescriptor<*>): Boolean {
        return descriptor !is ProblemNode
    }
}

class ProblemTreeBuilder(project: Project, disposable: Disposable) {
    private val myProblems: MutableList<Problem> = arrayListOf()
    private val myTreeStructure: ProblemTreeStructure = ProblemTreeStructure(project)
    private val myStructureTreeModel: StructureTreeModel<ProblemTreeStructure> =
        StructureTreeModel(myTreeStructure, disposable)
    private val myAsyncTreeModel: AsyncTreeModel

    init {
        myTreeStructure.setBuilder(this)
        myAsyncTreeModel = AsyncTreeModel(myStructureTreeModel, disposable)
    }

    val treeStructure: ProblemTreeStructure
        get() = myTreeStructure

    val asyncTreeModel: AsyncTreeModel
        get() = myAsyncTreeModel

    val problems: MutableCollection<Problem>
        get() = myProblems

    val files: Collection<ProblemFile>
        get() = myProblems.distinctBy { it.file }.map { it.problemFile }

    fun updateAsync() {
        myStructureTreeModel.invalidateAsync()
    }
}
