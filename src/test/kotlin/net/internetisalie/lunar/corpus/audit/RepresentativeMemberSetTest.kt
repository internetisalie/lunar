package net.internetisalie.lunar.corpus.audit

import net.internetisalie.lunar.corpus.CorpusManifest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

/**
 * MAINT-56 TC-6: `LuaRepresentativeTypeAuditTest` has one `test<Name>Member` per `type-corpus.json` entry.
 * Routine and fixture-free, as `BaselineRatchetTest`, so a member added to one and not the other fails the
 * routine suite rather than the opt-in run. Loading through [CorpusManifest] also refuses an unknown target.
 */
@RunWith(JUnit4::class)
class RepresentativeMemberSetTest {
    @Test
    fun auditMethodsMatchTheTypeManifest() {
        val repoRoot = File(System.getProperty("user.dir"))
        val expected =
            CorpusManifest
                .load(repoRoot, CorpusManifest.TYPE_MANIFEST_PATH)
                .map { "test" + it.name.replaceFirstChar(Char::uppercaseChar) + "Member" }
                .sorted()
        val declared =
            LuaRepresentativeTypeAuditTest::class.java.declaredMethods
                .filter { it.isAnnotationPresent(Test::class.java) }
                .map { it.name }
                .sorted()
        assertEquals(expected, declared)
    }
}
