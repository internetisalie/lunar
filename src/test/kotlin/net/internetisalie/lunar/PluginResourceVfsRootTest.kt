package net.internetisalie.lunar

import com.intellij.openapi.util.io.FileUtil
import net.internetisalie.lunar.lang.schema.providers.RockspecSchemaProvider
import java.io.File
import java.net.URI
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * BUG-539: tests load the plugin's own resources from its jar in the test sandbox. That location must
 * be an allowed VFS root, or the first test to load one fails with `VfsRootAccessNotAllowedError`
 * wherever the checkout is outside `$HOME`, as on CI.
 */
class PluginResourceVfsRootTest {
    @Test
    fun `the plugin's resource location is an allowed VFS root BUG539`() {
        val resource = assertNotNull(RockspecSchemaProvider::class.java.getResource(SCHEMA_RESOURCE))
        val location = FileUtil.toSystemIndependentName(localPath(resource).path)
        val allowedRoots = additionalAllowedRoots()

        assertTrue(
            allowedRoots.any { FileUtil.isAncestor(it, location, false) },
            "Expected $location under one of vfs.additional-allowed-roots: $allowedRoots",
        )
    }

    private fun localPath(resource: URL): File =
        if (resource.protocol == "jar") File(URI(resource.path.substringBefore("!/"))) else File(resource.toURI())

    private fun additionalAllowedRoots(): List<String> =
        System
            .getProperty("vfs.additional-allowed-roots")
            .orEmpty()
            .split(File.pathSeparator)
            .filter { it.isNotBlank() }
            .map(FileUtil::toSystemIndependentName)

    private companion object {
        const val SCHEMA_RESOURCE = "/jsonschema/rockspec-schema-v30.json"
    }
}
