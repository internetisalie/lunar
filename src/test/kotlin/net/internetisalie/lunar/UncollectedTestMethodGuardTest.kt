package net.internetisalie.lunar

import junit.framework.TestCase
import org.junit.Test
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * BUG-461 build-time guard. Under JUnit 3 (`TestCase`) a `test*` method is collected by name; in
 * any other class it is collected only by an annotation, so a bare `fun testFoo()` there is never
 * run and never reported as skipped. This test fails the build if one exists.
 *
 * It reads the compiled test classes (the code source of this very class), so it also covers
 * classes the Gradle filters skip at run time (`*Corpus*`, `-PexcludeExternalFixtureTests`).
 */
class UncollectedTestMethodGuardTest {
    @Test
    fun `no test method is silently uncollected`() {
        val scanned = compiledTestClasses()
        assertTrue(scanned.size > MIN_PLAUSIBLE_CLASS_COUNT, "scan found only ${scanned.size} classes; wrong root?")
        val offenders = scanned.flatMap { uncollectedTestMethods(it) }
        assertTrue(
            offenders.isEmpty(),
            "test* methods in non-JUnit3 classes need @Test (or @Ignore + @Test) to be collected:\n" +
                offenders.sorted().joinToString("\n"),
        )
    }

    @Test
    fun `guard flags a bare test method in a plain class`() {
        assertEquals(
            listOf("${PlainWithBare::class.java.name}.testBare"),
            uncollectedTestMethods(PlainWithBare::class.java),
        )
    }

    @Test
    fun `guard accepts annotated methods, JUnit3 classes and helpers that take arguments`() {
        assertEquals(emptyList(), uncollectedTestMethods(PlainAnnotated::class.java))
        assertEquals(emptyList(), uncollectedTestMethods(Junit3Style::class.java))
        assertEquals(emptyList(), uncollectedTestMethods(PlainHelperWithArgument::class.java))
    }

    private class PlainWithBare {
        fun testBare() = Unit
    }

    private class PlainAnnotated {
        @Test
        fun testAnnotated() = Unit
    }

    private class Junit3Style : TestCase() {
        fun testByName() = Unit
    }

    private class PlainHelperWithArgument {
        fun testHelper(text: String) = text
    }

    private companion object {
        private const val MIN_PLAUSIBLE_CLASS_COUNT = 500

        private val collectingAnnotations =
            setOf(
                "org.junit.Test",
                "org.junit.jupiter.api.Test",
                "org.junit.jupiter.api.TestFactory",
                "org.junit.jupiter.api.RepeatedTest",
                "org.junit.jupiter.params.ParameterizedTest",
            )

        fun uncollectedTestMethods(testClass: Class<*>): List<String> {
            if (TestCase::class.java.isAssignableFrom(testClass)) return emptyList()
            return testClass.declaredMethods
                .filter(::looksLikeTestMethod)
                .filter { method -> method.annotations.none { it.annotationClass.java.name in collectingAnnotations } }
                .map { "${testClass.name}.${it.name}" }
        }

        private fun looksLikeTestMethod(method: Method): Boolean =
            method.name.startsWith("test") &&
                method.parameterCount == 0 &&
                Modifier.isPublic(method.modifiers) &&
                !method.isSynthetic &&
                '$' !in method.name

        fun compiledTestClasses(): List<Class<*>> {
            val loader = UncollectedTestMethodGuardTest::class.java.classLoader
            val selfResource = UncollectedTestMethodGuardTest::class.java.name.replace('.', '/') + ".class"
            val selfPath = Paths.get(checkNotNull(loader.getResource(selfResource)).toURI())
            val root =
                selfPath.parent.let { dir ->
                    generateSequence(dir) { it.parent }
                        .drop(
                            selfResource.count { c ->
                                c ==
                                    '/'
                            },
                        ).first()
                }
            return classNamesUnder(root).map { name -> Class.forName(name, false, loader) }
        }

        private fun classNamesUnder(root: Path): List<String> =
            Files.walk(root).use { paths ->
                paths
                    .filter { it.toString().endsWith(".class") }
                    .map {
                        root
                            .relativize(it)
                            .toString()
                            .removeSuffix(".class")
                            .replace('/', '.')
                    }.filter { '$' !in it }
                    .toList()
            }
    }
}
