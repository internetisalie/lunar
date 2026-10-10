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
 * BUG-497 build-time guard. `build.gradle.kts` keeps the slow sweeps out of the routine suite with
 * `excludeTestsMatching("*Corpus*")`, and Gradle applies that pattern to test METHOD names as well
 * as class names. A routine test that merely mentions the word in a method name is therefore
 * silently dropped from the routine suite and runs inside the sweeps' JVM instead, where it can
 * shift their recorded baselines (BUG-418). Only the sweep classes may use the word.
 *
 * Like [UncollectedTestMethodGuardTest] it reads the compiled test classes, so it sees classes the
 * Gradle filters skip at run time.
 */
class SweepNamingGuardTest {
    @Test
    fun `no routine test class or method carries the sweep filter word`() {
        val scanned = compiledTestClasses()
        assertTrue(scanned.size > MIN_PLAUSIBLE_CLASS_COUNT, "scan found only ${scanned.size} classes; wrong root?")
        val offenders = scanned.flatMap { filteredNames(it.name, testMethodNames(it)) }
        assertTrue(
            offenders.isEmpty(),
            "these names match the routine suite's `*$SWEEP_WORD*` exclusion, so they never run there " +
                "(rename them; only the sweep classes may use the word):\n" +
                offenders.sorted().joinToString("\n"),
        )
    }

    @Test
    fun `guard flags a method name and a class name that the filter would match`() {
        assertEquals(
            listOf("a.b.RoutineTest.absent${SWEEP_WORD}Fails"),
            filteredNames("a.b.RoutineTest", listOf("fine", "absent${SWEEP_WORD}Fails")),
        )
        assertEquals(
            listOf("a.b.Routine${SWEEP_WORD}Test"),
            filteredNames("a.b.Routine${SWEEP_WORD}Test", listOf("fine")),
        )
    }

    @Test
    fun `guard exempts the sweep classes and ignores lowercase package segments`() {
        assertEquals(emptyList(), filteredNames("a.corpus.Lua${SWEEP_WORD}SweepTest", listOf("test${SWEEP_WORD}Foo")))
        assertEquals(emptyList(), filteredNames("a.corpus.RoutineTest", listOf("fine")))
        assertEquals(emptyList(), filteredNames("a.corpus.RoutineTest", emptyList()))
    }

    private companion object {
        private const val MIN_PLAUSIBLE_CLASS_COUNT = 500
        private const val SWEEP_WORD = "Corpus"

        private val sweepClassSimpleNames = setOf("Lua${SWEEP_WORD}SweepTest", "LuaTorture${SWEEP_WORD}Test")

        private val collectingAnnotations =
            setOf(
                "org.junit.Test",
                "org.junit.jupiter.api.Test",
                "org.junit.jupiter.api.TestFactory",
                "org.junit.jupiter.api.RepeatedTest",
                "org.junit.jupiter.params.ParameterizedTest",
            )

        /** The names Gradle's filter would test for [className]; empty for a class that holds no tests. */
        fun filteredNames(
            className: String,
            testMethodNames: List<String>,
        ): List<String> {
            if (testMethodNames.isEmpty() ||
                className.substringAfterLast('.') in sweepClassSimpleNames
            ) {
                return emptyList()
            }
            val classOffender = if (SWEEP_WORD in className) listOf(className) else emptyList()
            return classOffender + testMethodNames.filter { SWEEP_WORD in it }.map { "$className.$it" }
        }

        fun testMethodNames(testClass: Class<*>): List<String> {
            val byName = TestCase::class.java.isAssignableFrom(testClass)
            return testClass.declaredMethods
                .filter { isCollectable(it, byName) }
                .map { it.name }
        }

        private fun isCollectable(
            method: Method,
            collectedByName: Boolean,
        ): Boolean {
            if (method.parameterCount != 0 || !Modifier.isPublic(method.modifiers) || method.isSynthetic) return false
            val annotated = method.annotations.any { it.annotationClass.java.name in collectingAnnotations }
            return annotated || (collectedByName && method.name.startsWith("test"))
        }

        fun compiledTestClasses(): List<Class<*>> {
            val loader = SweepNamingGuardTest::class.java.classLoader
            val selfResource = SweepNamingGuardTest::class.java.name.replace('.', '/') + ".class"
            val selfPath = Paths.get(checkNotNull(loader.getResource(selfResource)).toURI())
            val root = selfPath.parents(selfResource.count { it == '/' } + 1)
            return classNamesUnder(root).map { name -> Class.forName(name, false, loader) }
        }

        private fun Path.parents(count: Int): Path = generateSequence(this) { it.parent }.drop(count).first()

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
