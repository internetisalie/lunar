package net.internetisalie.lunar.toolchain.provision

import com.intellij.openapi.project.Project
import java.nio.file.Path

/**
 * The *Re-register* action of [LuaEnvRedetectionStartup]: registers an orphaned tree's recorded
 * tools + environment, then makes the manifest carry the id the registration kept. Registration
 * merges by root, so a tree whose root is already registered under another id keeps that id; without
 * rewriting the manifest the tree would be reported as an orphan again on every reopen (BUG-533).
 * Blocking file I/O — call off the EDT.
 */
object LuaEnvReRegistration {
    fun reRegister(
        project: Project,
        rootDir: Path,
        manifest: LuaEnvManifest,
    ) {
        val sink = RegistryProvisionResultSink()
        sink.register(project, LuaEnvRedetection.toResult(rootDir, manifest))
        val registeredId = sink.registeredEnvironmentId(project, rootDir.toString()) ?: return
        if (registeredId != manifest.environmentId) {
            LuaEnvManifest.write(rootDir, manifest.copy(environmentId = registeredId))
        }
    }
}
