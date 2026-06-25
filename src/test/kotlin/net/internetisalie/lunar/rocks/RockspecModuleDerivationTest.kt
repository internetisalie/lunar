package net.internetisalie.lunar.rocks

import net.internetisalie.lunar.lang.path.SourcePathPattern
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RockspecModuleDerivationTest {

    @Test
    fun `TC4 derive standard module`() {
        val dir = "/proj"
        val modules = mapOf("foo.bar" to "src/foo/bar.lua")
        val patterns = RockspecModuleDerivation.derive(dir, modules)
        
        assertEquals(2, patterns.size)
        assertEquals(SourcePathPattern("/proj/src/?.lua"), patterns[0])
        assertEquals(SourcePathPattern("/proj/src/?/init.lua"), patterns[1])
    }

    @Test
    fun `TC5 derive init module`() {
        val dir = "/proj"
        val modules = mapOf("mymod" to "lua/mymod/init.lua")
        val patterns = RockspecModuleDerivation.derive(dir, modules)
        
        assertEquals(2, patterns.size)
        assertEquals(SourcePathPattern("/proj/lua/?.lua"), patterns[0])
        assertEquals(SourcePathPattern("/proj/lua/?/init.lua"), patterns[1])
    }

    @Test
    fun `derive from current dir module`() {
        val dir = "/proj/rocks/foo"
        val modules = mapOf("foo" to "foo.lua")
        val patterns = RockspecModuleDerivation.derive(dir, modules)
        
        assertEquals(2, patterns.size)
        assertEquals(SourcePathPattern("/proj/rocks/foo/?.lua"), patterns[0])
        assertEquals(SourcePathPattern("/proj/rocks/foo/?/init.lua"), patterns[1])
    }
    
    @Test
    fun `fallback to source dir when module path doesn't match`() {
        val dir = "/proj"
        val modules = mapOf("foo.bar" to "src/flattened_bar.lua")
        val patterns = RockspecModuleDerivation.derive(dir, modules)
        
        assertEquals(2, patterns.size)
        assertEquals(SourcePathPattern("/proj/src/?.lua"), patterns[0])
        assertEquals(SourcePathPattern("/proj/src/?/init.lua"), patterns[1])
    }
    
    @Test
    fun `multiple modules with same root are deduplicated`() {
        val dir = "/proj"
        val modules = mapOf(
            "foo.bar" to "src/foo/bar.lua",
            "foo.baz" to "src/foo/baz.lua",
            "other" to "src/other.lua"
        )
        val patterns = RockspecModuleDerivation.derive(dir, modules)
        
        assertEquals(2, patterns.size)
        assertEquals(SourcePathPattern("/proj/src/?.lua"), patterns[0])
        assertEquals(SourcePathPattern("/proj/src/?/init.lua"), patterns[1])
    }

    @Test
    fun `multiple distinct roots are derived sorted`() {
        val dir = "/proj"
        val modules = mapOf(
            "foo.bar" to "src/foo/bar.lua",
            "bin" to "lib/bin.lua"
        )
        val patterns = RockspecModuleDerivation.derive(dir, modules)
        
        assertEquals(4, patterns.size)
        // lib comes before src
        assertEquals(SourcePathPattern("/proj/lib/?.lua"), patterns[0])
        assertEquals(SourcePathPattern("/proj/lib/?/init.lua"), patterns[1])
        assertEquals(SourcePathPattern("/proj/src/?.lua"), patterns[2])
        assertEquals(SourcePathPattern("/proj/src/?/init.lua"), patterns[3])
    }

    @Test
    fun `empty modules returns empty list`() {
        val dir = "/proj"
        val modules = emptyMap<String, String>()
        val patterns = RockspecModuleDerivation.derive(dir, modules)
        
        assertEquals(0, patterns.size)
    }
}