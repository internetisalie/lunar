package net.internetisalie.lunar.toolchain.provision

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * BUG-370: provision-dialog tool checkboxes must show human-readable display names rather than raw
 * kind ids. [kindDisplayName] is the pure helper that resolves via [LuaToolKindRegistry]; the dialog
 * uses it to label each checkbox.
 */
class LuaProvisionDialogLabelTest {

    @Test
    fun testKnownKindsResolveToDisplayName_BUG370() {
        assertEquals("StyLua", kindDisplayName("stylua"))
        assertEquals("Busted", kindDisplayName("busted"))
        assertEquals("LuaCov", kindDisplayName("luacov"))
        assertEquals("luacheck", kindDisplayName("luacheck"))
        assertEquals("Lua Language Server", kindDisplayName("lua-language-server"))
    }

    @Test
    fun testUnknownKindFallsBackToRawId_BUG370() {
        assertEquals("unknown-tool", kindDisplayName("unknown-tool"))
    }

    @Test
    fun testAllToolKindsHaveDisplayNames_BUG370() {
        for (kindId in LuaToolCatalog.TOOL_KINDS) {
            val label = kindDisplayName(kindId)
            // After BUG-373 all TOOL_KINDS are in the registry, so no label should equal a raw id
            // with hyphens (that would indicate a registry miss)
            assert(!label.contains('-')) {
                "kindDisplayName('$kindId') returned raw id '$label' — registry entry missing"
            }
        }
    }
}
