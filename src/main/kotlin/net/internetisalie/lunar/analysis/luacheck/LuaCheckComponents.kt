/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.internetisalie.lunar.analysis.luacheck

import com.intellij.find.FindModel
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.CommonActionsManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.AutoScrollToSourceHandler
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.SimpleTree
import com.intellij.usageView.UsageInfo
import com.intellij.usages.impl.UsagePreviewPanel
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.ui.tree.TreeModelAdapter
import com.intellij.util.ui.tree.TreeUtil
import net.internetisalie.lunar.LuaBundle
import net.internetisalie.lunar.LuaPluginDisposable
import java.awt.GridLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.TreeModelEvent
import javax.swing.tree.DefaultMutableTreeNode

/**
 * ToolWindowFactory
 * Created by tangzx on 2017/7/11.
 */
class LuaCheckComponents : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        DumbService.getInstance(project).runWhenSmart {
            val checkView = project.getService(LuaCheckView::class.java)
            checkView.init(toolWindow)
        }
    }
}

/**
 * LuaCheckView
 * Created by tangzx on 2017/7/12.
 */
@Service(Service.Level.PROJECT)
class LuaCheckView(val project: Project) {
    val panel: LuaCheckPanel by lazy {
        LuaCheckPanel(project, LuaPluginDisposable.getInstance(project))
    }

    fun init(toolWindow: ToolWindow) {
        panel.init()
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

/**
 * LuaCheckPanel
 * Created by tangzx on 2017/7/12.
 */
class LuaCheckPanel(val project: Project, private val disposable: Disposable) : SimpleToolWindowPanel(false),
    DataProvider, Disposable {
    private val myTreeBuilder: ProblemTreeBuilder = ProblemTreeBuilder(project, this)
    private val myTree: SimpleTree = SimpleTree(myTreeBuilder.asyncTreeModel)
    private val treeExpander = DefaultTreeExpander(myTree)
    private var myUsagePreviewPanel: UsagePreviewPanel? = null

    val builder: ProblemTreeBuilder
        get() = myTreeBuilder

    inner class MyAutoScrollToSourceHandler : AutoScrollToSourceHandler() {
        override fun isAutoScrollMode() = myTreeBuilder.treeStructure.isAutoScrollMode

        override fun setAutoScrollMode(value: Boolean) {
            myTreeBuilder.treeStructure.isAutoScrollMode = value
        }
    }

    fun init() {
        myTree.cellRenderer = NodeRenderer()
        myTree.isRootVisible = false
        myTree.selectionModel.addTreeSelectionListener {
            ApplicationManager.getApplication().invokeLater { updatePreview() }
        }

        PopupHandler.installPopupMenu(myTree, createTreePopupActions(), "LuaCheckProblemPopup");

        // double click handler
        EditSourceOnDoubleClickHandler.install(myTree)

        // single click handler
        val autoScrollToSourceHandler = MyAutoScrollToSourceHandler()
        autoScrollToSourceHandler.install(myTree)

        // toolbar
        val group = DefaultActionGroup()
        group.add(CommonActionsManager.getInstance().createExpandAllAction(treeExpander, this))
        group.add(CommonActionsManager.getInstance().createCollapseAllAction(treeExpander, this))
        group.add(autoScrollToSourceHandler.createToggleAction())
        group.add(Separator.getInstance())
        group.add(createToggleUsagePreviewAction())

        val toolBar: ActionToolbar =
            ActionManager.getInstance().createActionToolbar("LuaCheckProblemToolbar", group, false)
        toolBar.targetComponent = myTree

        val toolBarPanel = JPanel(GridLayout())
        setToolbar(toolBarPanel)
        toolBarPanel.add(toolBar.component)

        // preview
        val usagePreviewPanel = UsagePreviewPanel(project, FindInProjectUtil.setupViewPresentation(false, FindModel()))
        Disposer.register(this, usagePreviewPanel)

        myUsagePreviewPanel = usagePreviewPanel
        myUsagePreviewPanel!!.isVisible = myTreeBuilder.treeStructure.showPreview

        setContent(createCenterComponent())

        // Disposal
        Disposer.register(disposable, this)

        myTreeBuilder.asyncTreeModel.addTreeModelListener(MyExpandListener(myTreeBuilder))
    }

    private fun createToggleUsagePreviewAction(): AnAction {
        return object : AnAction(
            LuaBundle.message("problems.action.toggleUsagePreview"),
            null,
            AllIcons.Actions.Preview,
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                myTreeBuilder.treeStructure.showPreview = !myTreeBuilder.treeStructure.showPreview
                myUsagePreviewPanel?.isVisible = myTreeBuilder.treeStructure.showPreview
            }
        }
    }

    private fun createTreePopupActions(): ActionGroup {
        val group = DefaultActionGroup()
        return group
    }

    override fun dispose() {}

    private fun createCenterComponent(): JComponent {
        val splitter = OnePixelSplitter()
        val jbScrollPane = JBScrollPane(myTree)
        jbScrollPane.border = null
        splitter.firstComponent = jbScrollPane//ScrollPaneFactory.createScrollPane(tree, false)
        splitter.secondComponent = myUsagePreviewPanel
        return splitter
    }

    private fun updatePreview() {
        val treePath = myTree.selectionPath
        treePath ?: return

        val list: MutableList<UsageInfo> = mutableListOf()
        val node = treePath.lastPathComponent as DefaultMutableTreeNode
        val userdata = node.userObject
        if (userdata is NodeDescriptor<*>) {
            val data = userdata.element
            if (data is ProblemNode) {
                val psiFile = data.value.psiFile
                if (psiFile != null) {
                    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                    document ?: return

                    val startOffset = document.getLineStartOffset(data.value.lineStart) + data.value.columnStart
                    val endOffset = document.getLineStartOffset(data.value.lineEnd) + data.value.columnEnd + 1
                    list.add(UsageInfo(psiFile, startOffset, endOffset))
                }
            }
        }

        myUsagePreviewPanel?.updateLayout(if (list.isEmpty()) null else list)
    }

    override fun getData(dataId: String): Any? {
        if (CommonDataKeys.NAVIGATABLE.`is`(dataId)) {
            val path = myTree.selectionPath
            if (path != null && !myTreeBuilder.treeStructure.showPreview) {
                val node = path.lastPathComponent as DefaultMutableTreeNode
                val userObject = node.userObject
                if (userObject is NodeDescriptor<*>) {
                    val element = userObject.element
                    if (element is ProblemNode) {
                        return element.getNavigator()
                    }
                }
            }
        }
        return super.getData(dataId)
    }

    private inner class MyExpandListener(private val myBuilder: ProblemTreeBuilder) : TreeModelAdapter() {
        override fun treeNodesInserted(e: TreeModelEvent) {
            val parentPath = e.treePath
            if (parentPath == null || parentPath.pathCount > 2) return
            val children = e.children
            for (o in children) {
                val descriptor = TreeUtil.getUserObject(
                    NodeDescriptor::class.java, o
                )
                if (descriptor != null && myBuilder.treeStructure.isAutoExpandNode(descriptor)) {
                    ApplicationManager.getApplication().invokeLater({
                        if (myTree.isVisible(parentPath) && myTree.isExpanded(parentPath)) {
                            myTree.expandPath(parentPath.pathByAddingChild(o))
                        }
                    }, project.disposed)
                }
            }
        }
    }

}
