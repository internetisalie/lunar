package net.internetisalie.lunar.toolchain.health

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TC-TOOLING-07-10 — pure VFS match-predicate tests (design §3.2). No IDE fixture: the predicate is
 * a pure function over path strings, so it is exercised directly.
 */
class LuaHealthWatchSetTest {

    private val watchSet = LuaHealthWatchSet(
        exactPaths = setOf("/opt/t/bin/luacheck"),
        envRoots = setOf("/opt/env"),
        binDirs = setOf("/opt/env/bin")
    )

    @Test
    fun testDeleteEnvRoot_matches() {
        assertTrue(matchesDeleteOrMove("/opt/env", watchSet))
    }

    @Test
    fun testDeleteAncestorOfEnvRoot_matches() {
        assertTrue(matchesDeleteOrMove("/opt", watchSet))
    }

    @Test
    fun testDeleteExactBinary_matches() {
        assertTrue(matchesDeleteOrMove("/opt/t/bin/luacheck", watchSet))
    }

    @Test
    fun testDeleteBinDir_matches() {
        assertTrue(matchesDeleteOrMove("/opt/env/bin", watchSet))
    }

    @Test
    fun testDeleteUnrelated_noMatch() {
        assertFalse(matchesDeleteOrMove("/unrelated", watchSet))
    }

    @Test
    fun testDeleteSiblingPrefix_noMatch() {
        assertFalse(matchesDeleteOrMove("/opt/envx", watchSet))
    }

    @Test
    fun testContentChangeInsideBinDir_matches() {
        assertTrue(matchesContentChange("/opt/env/bin/lua", watchSet))
    }

    @Test
    fun testContentChangeExactBinary_matches() {
        assertTrue(matchesContentChange("/opt/t/bin/luacheck", watchSet))
    }

    @Test
    fun testContentChangeEnvReadme_noMatch() {
        assertFalse(matchesContentChange("/opt/env/README", watchSet))
    }

    @Test
    fun testContentChangeNestedUnderBinDir_noMatch() {
        assertFalse(matchesContentChange("/opt/env/bin/sub/lua", watchSet))
    }

    @Test
    fun testContentChangeEnvRootItself_noMatch() {
        assertFalse(matchesContentChange("/opt/env", watchSet))
    }
}
