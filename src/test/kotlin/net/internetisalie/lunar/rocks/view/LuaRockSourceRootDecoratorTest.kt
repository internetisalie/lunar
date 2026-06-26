package net.internetisalie.lunar.rocks.view

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.openapi.project.Project
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.openapi.vfs.VirtualFile
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class LuaRockSourceRootDecoratorTest {
    private lateinit var fixture: CodeInsightTestFixture
    private lateinit var project: Project

    @BeforeEach
    fun before() {
        val descriptor = LightProjectDescriptor()
        val factory = IdeaTestFixtureFactory.getFixtureFactory()
        val builder = factory.createLightFixtureBuilder(descriptor, "LuaRockSourceRootDecoratorTest")
        fixture = factory.createCodeInsightFixture(
            builder.fixture,
            TempDirTestFixtureImpl()
        )
        fixture.setUp()
        project = fixture.project
    }

    @AfterEach
    fun after() {
        fixture.tearDown()
    }

    @Test
    fun testDecoratorMarksSourceRoot() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val base = java.io.File(project.basePath!!)
            val srcIo = java.io.File(base, "src")
            srcIo.mkdirs()
            
            val thirdpartyIo = java.io.File(base, "thirdparty")
            thirdpartyIo.mkdirs()
            
            com.intellij.openapi.vfs.VfsUtil.markDirtyAndRefresh(false, true, true, base)
            
            val srcDir = com.intellij.openapi.vfs.VfsUtil.findFileByIoFile(srcIo, true)!!
            val thirdpartyDir = com.intellij.openapi.vfs.VfsUtil.findFileByIoFile(thirdpartyIo, true)!!

            val settings = LuaProjectSettings.getInstance(project)
            settings.state.sourcePath = "\$PROJECT_DIR$/src/?.lua"
            
            val decorator = LuaRockSourceRootDecorator()
            
            val srcNode = DummyNode(project, srcDir)
            val srcData = PresentationData()
            decorator.decorate(srcNode, srcData)
            
            val srcText = srcData.coloredText
            assertTrue(srcText.any { it.text == " rock source root" }, "src folder should be badged")
            
            val thirdpartyNode = DummyNode(project, thirdpartyDir)
            val thirdpartyData = PresentationData()
            decorator.decorate(thirdpartyNode, thirdpartyData)
            
            val thirdpartyText = thirdpartyData.coloredText
            assertFalse(thirdpartyText.any { it.text == " rock source root" }, "thirdparty folder should not be badged")
        }
    }

    class DummyNode(project: Project, val dir: VirtualFile) : ProjectViewNode<VirtualFile>(project, dir, ViewSettings.DEFAULT) {
        override fun contains(file: VirtualFile) = false
        override fun getVirtualFile() = dir
        override fun getChildren() = emptyList<ProjectViewNode<*>>()
        override fun update(presentation: PresentationData) {}
    }
}
