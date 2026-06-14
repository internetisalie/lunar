package net.internetisalie.lunar.lang.navigation

import com.intellij.navigation.NavigationItem
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.CommonProcessors
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * NAV-03 Go to Class / Symbol contributor tests.
 *
 * The ChooseByName UI is awkward to drive in a fixture test, so the contributors are exercised
 * directly: [com.intellij.navigation.ChooseByNameContributorEx.processNames] (assert the index key
 * is enumerated) and [com.intellij.navigation.ChooseByNameContributorEx.processElementsWithName]
 * (assert a [NavigationItem] is produced whose name/presentation match the index key and whose
 * navigation lands in the right file). [IndexedBasePlatformTestCase] forces a stub-index rebuild so
 * the class/alias/global indexes are populated.
 */
@RunWith(JUnit4::class)
class LuaGotoContributorTest : IndexedBasePlatformTestCase() {

    /** All names the contributor enumerates over the whole project scope. */
    private fun namesOf(contributor: com.intellij.navigation.ChooseByNameContributorEx): Set<String> {
        val collector = CommonProcessors.CollectProcessor<String>()
        contributor.processNames(collector, GlobalSearchScope.allScope(project), null)
        return collector.results.toSet()
    }

    /** All navigation items the contributor produces for [name]. */
    private fun itemsOf(
        contributor: com.intellij.navigation.ChooseByNameContributorEx,
        name: String,
    ): List<NavigationItem> {
        val items = mutableListOf<NavigationItem>()
        val processor = Processor<NavigationItem> { items.add(it); true }
        contributor.processElementsWithName(name, processor, FindSymbolParameters.wrap(name, project, false))
        return items
    }

    /** TC-NAV-03-01: a `@class MyClass` is found by Go to Class under the name "MyClass". */
    @Test
    fun testGotoClassFindsClassByCatsName() {
        myFixture.addFileToProject("klass.lua", "---@class MyClass\nlocal MyClass = {}")
        val contributor = LuaGotoClassContributor()

        assertTrue("processNames should enumerate the class name 'MyClass'", namesOf(contributor).contains("MyClass"))

        val items = itemsOf(contributor, "MyClass")
        assertEquals("Expected one Go-to-Class item for 'MyClass'", 1, items.size)
        val item = items.first()
        assertEquals("Item name must be the index key (class name)", "MyClass", item.name)
        assertEquals("Presentable text must be the index key", "MyClass", item.presentation?.presentableText)
        assertEquals("Item should locate to klass.lua", "klass.lua", item.presentation?.locationString)
    }

    /**
     * TC-NAV-03-04: a *bare* `@alias` comment — no following `local`, the normal LuaCATS form — is
     * found by Go to Class. This is the realistic case the stub-only index missed.
     */
    @Test
    fun testGotoClassFindsBareAlias() {
        myFixture.addFileToProject("aliases.lua", "--- @alias DeviceSide table<string,Bob>\n")
        val contributor = LuaGotoClassContributor()

        assertTrue("processNames should enumerate the bare alias name 'DeviceSide'", namesOf(contributor).contains("DeviceSide"))

        val items = itemsOf(contributor, "DeviceSide")
        assertEquals("Expected one Go-to-Class item for the alias 'DeviceSide'", 1, items.size)
        assertEquals("Alias item name must be the index key", "DeviceSide", items.first().name)
        assertEquals("Alias item should locate to aliases.lua", "aliases.lua", items.first().presentation?.locationString)
        assertTrue("Alias item should be navigable", items.first().canNavigate())
    }

    /** Go to Symbol also surfaces a bare `@alias` declaration. */
    @Test
    fun testGotoSymbolFindsBareAlias() {
        myFixture.addFileToProject("symalias.lua", "--- @alias Side \"'left'\" | \"'right'\"\n")
        val contributor = LuaGotoSymbolContributor()

        assertTrue("Go to Symbol should enumerate the bare alias name 'Side'", namesOf(contributor).contains("Side"))
        assertEquals("Go to Symbol should yield the alias item", 1, itemsOf(contributor, "Side").size)
    }

    /** TC-NAV-03-02: a global function `Helper` is found by Go to Symbol. */
    @Test
    fun testGotoSymbolFindsGlobalFunction() {
        myFixture.addFileToProject("helper.lua", "function Helper() end")
        val contributor = LuaGotoSymbolContributor()

        assertTrue("processNames should enumerate the global function 'Helper'", namesOf(contributor).contains("Helper"))

        val items = itemsOf(contributor, "Helper")
        assertEquals("Expected one Go-to-Symbol item for 'Helper'", 1, items.size)
        val item = items.first()
        assertEquals("Item name must be the index key (function name)", "Helper", item.name)
        assertEquals("Item should locate to helper.lua", "helper.lua", item.presentation?.locationString)
        assertTrue("Go-to-Symbol item for a declaration should be navigable", item.canNavigate())
    }

    /** Go to Symbol also surfaces classes (any top-level named symbol is reachable). */
    @Test
    fun testGotoSymbolAlsoFindsClass() {
        myFixture.addFileToProject("symclass.lua", "---@class SymClass\nlocal SymClass = {}")
        val contributor = LuaGotoSymbolContributor()

        assertTrue("Go to Symbol should also enumerate class names", namesOf(contributor).contains("SymClass"))
        assertEquals("Go to Symbol should yield the class item", 1, itemsOf(contributor, "SymClass").size)
    }
}
