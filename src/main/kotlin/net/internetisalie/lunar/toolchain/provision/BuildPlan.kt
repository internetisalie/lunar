package net.internetisalie.lunar.toolchain.provision

import java.nio.file.Path

/**
 * One command in a source-build plan (design §2.9). The working directory and environment
 * ride on the [command] because every command executes through the TOOLING-03 execution
 * service, which takes a fully-configured `GeneralCommandLine` (design §2.9, Behavior Rules).
 */
data class BuildStep(
    val command: List<String>,
    val workDir: Path,
    val env: Map<String, String> = emptyMap(),
)

/**
 * A pure, unit-testable source-build plan (design §2.9): the ordered [steps] to run, the
 * `source → dest` [installCopies] performed after the build, and the [executables] whose exec
 * bit is restored and which become registration candidates.
 */
data class BuildPlan(
    val steps: List<BuildStep>,
    val installCopies: List<Pair<Path, Path>>,
    val executables: List<Path>,
)
