package net.internetisalie.lunar

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import net.internetisalie.lunar.lang.LuaFileType
import org.junit.jupiter.api.TestInfo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/**
 * Base class for Lua document-based tests with standard fixture setup.
 *
 * Provides:
 * - Fresh [CodeInsightTestFixture] for each test
 * - PSI tree access via [configureByText]
 * - Template testing support
 *
 * **Does NOT force stub index rebuild** to keep tests fast.
 * If your tests require indexed content (type resolution, documentation lookups),
 * extend [IndexedDocumentTest] instead.
 */
open class BaseDocumentTest {
    lateinit var myFixture: CodeInsightTestFixture
    lateinit var myProjectDescriptor: LightProjectDescriptor
    protected val luaFileType = LuaFileType
    protected val luaExtension = "lua"
    protected val luaFileName = "test.lua"

    @BeforeTest
    open fun before(testInfo: TestInfo) {
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
