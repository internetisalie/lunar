package net.internetisalie.lunar

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import net.internetisalie.lunar.lang.LuaFileType
import org.junit.jupiter.api.TestInfo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

open class BaseDocumentTest {
    lateinit var myFixture: CodeInsightTestFixture
    lateinit var myProjectDescriptor: LightProjectDescriptor
    protected val luaFileType = LuaFileType
    protected val luaExtension = "lua"
    protected val luaFileName = "test.lua"

    @BeforeTest
    fun before(testInfo: TestInfo) {
        myProjectDescriptor = LightProjectDescriptor()

        val factory = IdeaTestFixtureFactory.getFixtureFactory()

        val lightFixtureBuilder = factory.createLightFixtureBuilder(
            myProjectDescriptor, testInfo.displayName
        )

        myFixture = factory.createCodeInsightFixture(
            lightFixtureBuilder.getFixture(),
            LightTempDirTestFixtureImpl(false),
        )
        myFixture.setUp()
        TemplateManagerImpl.setTemplateTesting(myFixture.testRootDisposable)
    }

    @AfterTest
    fun after() {
        myFixture.tearDown()
    }

    fun configureByText(text: String) : PsiFile {
        return myFixture.configureByText(luaFileName, text)
    }

}
