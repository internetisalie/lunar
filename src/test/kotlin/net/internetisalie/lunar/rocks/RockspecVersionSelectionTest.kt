package net.internetisalie.lunar.rocks

import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals

/** BUG-53: several rockspecs of one package are versions of one rock. */
class RockspecVersionSelectionTest {
    private fun rock(
        path: String,
        packageName: String? = null,
    ) = DiscoveredRockspec(Path.of(path), packageName)

    @Test
    fun `a development rockspec wins over a release archive`() {
        val dev = rock("/p/luacheck/luacheck-dev-1.rockspec", "luacheck")
        val discovered =
            listOf(
                rock("/p/luacheck/rockspecs/luacheck-0.1.0-1.rockspec", "luacheck"),
                dev,
                rock("/p/luacheck/rockspecs/luacheck-1.2.0-1.rockspec", "luacheck"),
            )

        assertEquals(listOf(dev), RockspecVersionSelection.onePerPackage(discovered))
    }

    @Test
    fun `without a development rockspec the highest release wins, compared as versions`() {
        val newer = rock("/p/rockspecs/penlight-1.10.0-1.rockspec")
        val discovered = listOf(rock("/p/rockspecs/penlight-1.9.2-1.rockspec"), newer)

        assertEquals(listOf(newer), RockspecVersionSelection.onePerPackage(discovered))
    }

    @Test
    fun `different packages and unnamed rockspecs are all kept, in order`() {
        val discovered =
            listOf(
                rock("/p/rocks/a/a-1.0-1.rockspec", "a"),
                rock("/p/odd.rockspec"),
                rock("/p/rocks/b/b-1.0-1.rockspec", "b"),
            )

        assertEquals(discovered, RockspecVersionSelection.onePerPackage(discovered))
    }
}
