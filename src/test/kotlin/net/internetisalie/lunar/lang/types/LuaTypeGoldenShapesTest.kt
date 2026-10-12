package net.internetisalie.lunar.lang.types

import net.internetisalie.lunar.BaseDocumentTest
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/** TYPE-12-03/-04: today's engine, recorded per shape; and no shape shares a type name with a bundled stub. */
class LuaTypeGoldenShapesTest : BaseDocumentTest() {
    @Test
    fun everyShapeMatchesItsGolden() {
        val failures = TypeGoldenFixtures.shapes().mapNotNull { TypeGolden.mismatch(myFixture, it) }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun noShapeDeclaresAStubTypeName() {
        val stubNames = TypeGolden.stubTypeNames()
        val collisions =
            TypeGoldenFixtures.shapes().flatMap { source ->
                TypeGolden.declaredTypeNames(source.text).filter { it in stubNames }.map { "${source.goldenPath}: $it" }
            }
        assertTrue(stubNames.isNotEmpty(), "no stub type names found under src/main/resources/runtime")
        assertTrue(collisions.isEmpty(), collisions.joinToString("\n"))
    }
}
