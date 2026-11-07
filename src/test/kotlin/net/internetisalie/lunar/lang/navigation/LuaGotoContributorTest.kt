package net.internetisalie.lunar.lang.navigation

import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider
import com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider.SEEqualElementsActionType
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.util.Disposer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.util.CommonProcessors
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
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
        val processor =
            Processor<NavigationItem> {
                items.add(it)
                true
            }
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
     * A *bare* `@class` comment — no following `local`, a pure type-level declaration — is found by
     * Go to Class. Same latent gap as bare `@alias`, fixed by the shared file-based type index.
     */
    @Test
    fun testGotoClassFindsBareClass() {
        myFixture.addFileToProject("bare.lua", "--- @class Bob\n--- @field name string\n")
        val contributor = LuaGotoClassContributor()

        assertTrue("processNames should enumerate the bare class name 'Bob'", namesOf(contributor).contains("Bob"))

        val items = itemsOf(contributor, "Bob")
        assertEquals("Expected one Go-to-Class item for the bare class 'Bob'", 1, items.size)
        assertEquals("Bare class item name must be the index key", "Bob", items.first().name)
        assertEquals(
            "Bare class item should locate to bare.lua",
            "bare.lua",
            items.first().presentation?.locationString,
        )
        assertTrue("Bare class item should be navigable", items.first().canNavigate())
    }

    /**
     * TC-NAV-03-04: a *bare* `@alias` comment — no following `local`, the normal LuaCATS form — is
     * found by Go to Class. This is the realistic case the stub-only index missed.
     */
    @Test
    fun testGotoClassFindsBareAlias() {
        myFixture.addFileToProject("aliases.lua", "--- @alias DeviceSide table<string,Bob>\n")
        val contributor = LuaGotoClassContributor()

        assertTrue(
            "processNames should enumerate the bare alias name 'DeviceSide'",
            namesOf(contributor).contains("DeviceSide"),
        )

        val items = itemsOf(contributor, "DeviceSide")
        assertEquals("Expected one Go-to-Class item for the alias 'DeviceSide'", 1, items.size)
        assertEquals("Alias item name must be the index key", "DeviceSide", items.first().name)
        assertEquals(
            "Alias item should locate to aliases.lua",
            "aliases.lua",
            items.first().presentation?.locationString,
        )
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

        assertTrue(
            "processNames should enumerate the global function 'Helper'",
            namesOf(contributor).contains("Helper"),
        )

        val items = itemsOf(contributor, "Helper")
        assertEquals("Expected one Go-to-Symbol item for 'Helper'", 1, items.size)
        val item = items.first()
        assertEquals("Item name must be the index key (function name)", "Helper", item.name)
        assertEquals("Item should locate to helper.lua", "helper.lua", item.presentation?.locationString)
        assertTrue("Go-to-Symbol item for a declaration should be navigable", item.canNavigate())
    }

    /**
     * Case 27 (TYPE-14-08): a generic class is found by its head. The key set is asserted only not to
     * hold the raw `Box<T>`: a bare `Box` key can come from the light project's library roots, so its
     * presence would prove nothing (DR-03).
     */
    @Test
    fun testGotoClassFindsAGenericClassByItsHead() {
        myFixture.addFileToProject("genericbox.lua", "---@class Box<T>\nlocal Box = {}")
        val keys = CommonProcessors.CollectProcessor<String>()
        LuaCatsTypeNavigation.processNames(keys, GlobalSearchScope.allScope(project), null)
        assertFalse("the raw head must not be a key: ${keys.results}", "Box<T>" in keys.results)

        val items = mutableListOf<NavigationItem>()
        LuaCatsTypeNavigation.processElements("Box", project, GlobalSearchScope.allScope(project)) { items.add(it) }
        assertEquals("exactly one item for Box", 1, items.size)
        assertEquals("Box", items.first().name)
        assertEquals("genericbox.lua", items.first().presentation?.locationString)
        assertTrue("the item lands on the tag", items.first().canNavigate())
    }

    /** Go to Symbol also surfaces classes (any top-level named symbol is reachable). */
    @Test
    fun testGotoSymbolAlsoFindsClass() {
        myFixture.addFileToProject("symclass.lua", "---@class SymClass\nlocal SymClass = {}")
        val contributor = LuaGotoSymbolContributor()

        assertTrue("Go to Symbol should also enumerate class names", namesOf(contributor).contains("SymClass"))
        assertEquals("Go to Symbol should yield the class item", 1, itemsOf(contributor, "SymClass").size)
    }

    /**
     * BUG-35: another language's Go to Class contributor that also enumerates `Box` (GoLand's
     * `GoTypeContributor` does, twice) makes the platform ask every contributor for `Box` once per
     * enumeration. Search Everywhere then collapses the repeats through its results-equality
     * providers, so the same class must come back as equal items, or the list shows it once per
     * enumeration.
     */
    @Test
    fun testGotoClassListsAClassOnceWhenAnotherContributorEnumeratesItsName() {
        myFixture.addFileToProject("s31.lua", "---@class Box<T>\n---@field value T\nlocal Box = {}\n")
        ExtensionTestUtil.addExtensions(
            ChooseByNameContributor.CLASS_EP_NAME,
            listOf(NameOnlyContributor("Box"), NameOnlyContributor("Box")),
            testRootDisposable,
        )

        val raw = myFixture.getGotoClassResults("Box", false, null).filterIsInstance<NavigationItem>()
        assertTrue("the platform asks for Box once per enumeration: $raw", raw.count { it.name == "Box" } > 1)

        val shownBoxes = afterSearchEverywhereEquality(raw).filter { it.name == "Box" }
        assertEquals("Box is listed once: ${shownBoxes.map { it.presentation?.locationString }}", 1, shownBoxes.size)
    }

    /** Applies the platform's registered results-equality providers the way Search Everywhere does. */
    private fun afterSearchEverywhereEquality(items: List<NavigationItem>): List<NavigationItem> {
        val equality = SEResultsEqualityProvider.composite(SEResultsEqualityProvider.EP_NAME.extensionList)
        val event =
            AnActionEvent.createFromDataContext(
                ActionPlaces.UNKNOWN,
                null,
                SimpleDataContext.getProjectContext(project),
            )
        val classes = ClassSearchEverywhereContributor(event).also { Disposer.register(testRootDisposable, it) }
        val shown = mutableListOf<SearchEverywhereFoundElementInfo>()
        for (item in items) {
            val info = SearchEverywhereFoundElementInfo(item, 0, classes)
            when (val action = equality.compareItemsCollection(info, shown)) {
                is SEEqualElementsActionType.Skip -> Unit
                is SEEqualElementsActionType.Replace -> {
                    shown.removeAll(action.toBeReplaced)
                    shown.add(info)
                }
                else -> shown.add(info)
            }
        }
        return shown.map { it.element as NavigationItem }
    }

    /** A foreign contributor that enumerates [name] but owns no element for it. */
    private class NameOnlyContributor(
        private val name: String,
    ) : ChooseByNameContributorEx {
        override fun processNames(
            processor: Processor<in String>,
            scope: GlobalSearchScope,
            filter: IdFilter?,
        ) {
            processor.process(name)
        }

        override fun processElementsWithName(
            name: String,
            processor: Processor<in NavigationItem>,
            parameters: FindSymbolParameters,
        ) = Unit
    }
}
