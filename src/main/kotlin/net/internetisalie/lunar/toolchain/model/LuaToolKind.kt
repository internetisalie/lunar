package net.internetisalie.lunar.toolchain.model

import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaPlatform

data class LuaToolKind(
    val id: String,
    val displayName: String,
    val binaryNames: List<String>,
    val probe: ProbeSpec,
    val capabilities: Set<Capability>,
    val minVersion: SemanticVersion? = null,
    val provisioning: List<ProvisioningSpec> = emptyList()
) {
    val isRuntime: Boolean get() = Capability.RUNTIME in capabilities
}

enum class Capability {
    RUNTIME,
    PACKAGE_MANAGER,
    LINTER,
    FORMATTER,
    TEST_RUNNER,
    COVERAGE
}

data class ProbeSpec(
    val args: List<String>,
    val versionRegex: Regex,
    val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    val luaVersionRegex: Regex? = null,
    val runtime: RuntimeProbeSpec? = null
) {
    companion object {
        const val DEFAULT_TIMEOUT_MS: Int = 10_000
    }
}

data class RuntimeProbeSpec(
    val productToken: String,
    val platform: LuaPlatform,
    val languageLevel: LanguageLevelRule
)

sealed interface LanguageLevelRule {
    data class Fixed(val level: LuaLanguageLevel) : LanguageLevelRule
    data class ByVersionPrefix(
        val prefixes: List<Pair<String, LuaLanguageLevel>>,
        val fallback: LuaLanguageLevel
    ) : LanguageLevelRule
}

sealed interface ProvisioningSpec {
    data class ReleaseBinary(val urlTemplate: String, val checksumUrlTemplate: String?) : ProvisioningSpec
    data class SourceBuild(val sourceUrlTemplate: String) : ProvisioningSpec
    data class LuaRocksInstall(val rockName: String) : ProvisioningSpec
}
