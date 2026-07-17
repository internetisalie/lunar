package net.internetisalie.lunar.rocks

import net.internetisalie.lunar.rocks.deps.ConflictType
import net.internetisalie.lunar.rocks.deps.DependencyNode
import net.internetisalie.lunar.rocks.deps.LuaRocksVersion
import net.internetisalie.lunar.rocks.deps.VersionConstraint
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/** Covers TC-ROCKS-03-02 (unsatisfiable + violated) and TC-ROCKS-03-05 (missing dependency). */
class VersionConflictEngineTest {
    private fun node(name: String, version: String?): DependencyNode =
        DependencyNode(name, isTransitive = true, resolvedVersion = version?.let { LuaRocksVersion.parse(it) })

    private fun constraint(piece: String) = VersionConstraint.parse(piece)!!

    @Test
    fun missingDependencyIsFlagged() {
        val root = DependencyNode("project", isTransitive = false)
        val ghost = node("ghost", null).apply { requiredConstraints += constraint(">= 1.0") }
        root.children += ghost
        VersionConflictEngine.annotate(root)
        assertTrue(ghost.conflicts.any { it.type == ConflictType.MISSING_DEPENDENCY })
    }

    @Test
    fun installedVersionViolatingConstraintIsFlagged() {
        val root = DependencyNode("project", isTransitive = false)
        val lib = node("lib", "1.0").apply { requiredConstraints += constraint(">= 2.0") }
        root.children += lib
        VersionConflictEngine.annotate(root)
        assertTrue(lib.conflicts.any { it.type == ConflictType.VERSION_MISMATCH })
    }

    @Test
    fun unsatisfiableConstraintSetIsFlaggedEvenWhenMissing() {
        val root = DependencyNode("project", isTransitive = false)
        val lib = node("lib", null).apply {
            requiredConstraints += constraint(">= 2.0")
            requiredConstraints += constraint("< 1.5")
        }
        root.children += lib
        VersionConflictEngine.annotate(root)
        assertTrue(lib.conflicts.any { it.type == ConflictType.MISSING_DEPENDENCY })
        assertTrue(lib.conflicts.any { it.type == ConflictType.VERSION_MISMATCH })
    }

    @Test
    fun satisfiedInstalledVersionHasNoConflict() {
        val root = DependencyNode("project", isTransitive = false)
        val lib = node("lib", "2.5").apply { requiredConstraints += constraint(">= 2.0") }
        root.children += lib
        VersionConflictEngine.annotate(root)
        assertTrue(lib.conflicts.isEmpty())
    }

    // BUG-383: equal-version exclusive bounds must be flagged unsatisfiable

    @Test
    fun equalVersionWithExclusiveUpperIsFlaggedUnsatisfiable() {
        val root = DependencyNode("project", isTransitive = false)
        val lib = node("lib", null).apply {
            requiredConstraints += constraint(">= 2.0")
            requiredConstraints += constraint("< 2.0")
        }
        root.children += lib
        VersionConflictEngine.annotate(root)
        assertTrue(
            lib.conflicts.any { it.type == ConflictType.VERSION_MISMATCH },
            ">= 2.0 + < 2.0 is unsatisfiable and must be flagged",
        )
    }

    @Test
    fun equalVersionBothInclusiveIsSatisfiable() {
        val root = DependencyNode("project", isTransitive = false)
        val lib = node("lib", "2.0").apply {
            requiredConstraints += constraint(">= 2.0")
            requiredConstraints += constraint("<= 2.0")
        }
        root.children += lib
        VersionConflictEngine.annotate(root)
        assertTrue(
            lib.conflicts.none { it.type == ConflictType.VERSION_MISMATCH },
            ">= 2.0 + <= 2.0 is satisfiable by exactly 2.0 — must not be flagged as a conflict",
        )
    }
}
