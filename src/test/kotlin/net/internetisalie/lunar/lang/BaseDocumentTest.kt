package net.internetisalie.lunar.lang

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import org.junit.jupiter.api.TestInfo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

open class BaseDocumentTest {
    lateinit var myFixture: CodeInsightTestFixture
    lateinit var myProjectDescriptor: LightProjectDescriptor

    @BeforeTest
    fun before(testInfo: TestInfo) {
        myProjectDescriptor = LightProjectDescriptor()

        val factory = IdeaTestFixtureFactory.getFixtureFactory()

        val lightFixtureBuilder = factory.createLightFixtureBuilder(
            myProjectDescriptor, testInfo.displayName
        )

        myFixture = factory.createCodeInsightFixture(
            lightFixtureBuilder.getFixture(),
            LightTempDirTestFixtureImpl(true),
        )
        myFixture.setUp()
    }

    @AfterTest
    fun after() {
        myFixture.tearDown()
    }

}