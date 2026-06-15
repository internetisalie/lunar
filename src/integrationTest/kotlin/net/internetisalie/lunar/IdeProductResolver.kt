package net.internetisalie.lunar

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.IdeInfo
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * IDE product resolver that reads from gradle.properties
 * Maps platformType to IdeProductProvider instances and reads testVersion
 */
object IdeProductResolver {
    
    private const val GRADLE_PROPERTIES_PATH = "gradle.properties"
    private const val PLATFORM_TYPE_KEY = "platformType"
    private const val TEST_VERSION_KEY = "testVersion"
    private const val LICENSE_ENV = "LUNAR_LICENSE_KEY"

    /**
     * Apply a JetBrains license to a commercial integration-test IDE (e.g. GoLand) so its
     * License / Activation modal doesn't block the headless ide-starter run (which otherwise times
     * out "due to a dialog being shown"). The key file is located via the LUNAR_LICENSE_KEY env var
     * — docker-helper.sh mounts the host key in and sets it — falling back to the standard host
     * config location for local `./gradlew integrationTest` runs. No-op with a warning when no key
     * is found (the run will then hit the activation dialog).
     *
     * The key is the raw `<ide>.key` file; [IDETestContext.setLicense] base64-encodes it and writes
     * it into the per-test config dir. Licenses are account-bound and never committed.
     */
    fun applyLicense(context: IDETestContext): IDETestContext {
        val keyPath = resolveLicenseKeyPath(context.ide.productCode)
        if (keyPath == null) {
            println("⚠ No license key found (set $LICENSE_ENV=/path/to/<ide>.key). A commercial IDE will block on the License dialog.")
            return context
        }
        println("✓ Applying integration-test license from: $keyPath")
        return context.setLicense(keyPath)
    }

    /**
     * Resolve the license key file: LUNAR_LICENSE_KEY env var first, then the newest matching
     * `~/.config/JetBrains/<Product><version>/<product>.key` on the host. Null if none found.
     */
    private fun resolveLicenseKeyPath(productCode: String): Path? {
        System.getenv(LICENSE_ENV)?.takeIf { it.isNotBlank() }?.let { env ->
            val explicit = Paths.get(env)
            if (Files.isRegularFile(explicit)) return explicit
            println("⚠ $LICENSE_ENV=$env does not point to a readable file; ignoring")
        }
        val (configPrefix, keyFileName) = hostKeyCoordinates(productCode) ?: return null
        val jetbrainsDir = Paths.get(System.getProperty("user.home"), ".config", "JetBrains")
        if (!Files.isDirectory(jetbrainsDir)) return null
        return Files.list(jetbrainsDir).use { stream ->
            stream
                .filter { Files.isDirectory(it) && it.fileName.toString().startsWith(configPrefix) }
                .map { it.resolve(keyFileName) }
                .filter { Files.isRegularFile(it) }
                .sorted(Comparator.reverseOrder())
                .findFirst()
                .orElse(null)
        }
    }

    /** Host config-dir prefix and key file name for a product code (only commercial IDEs). */
    private fun hostKeyCoordinates(productCode: String): Pair<String, String>? = when (productCode) {
        "GO" -> "GoLand" to "goland.key"
        "IU" -> "IntelliJIdea" to "idea.key"
        "RM" -> "RubyMine" to "rubymine.key"
        "WS" -> "WebStorm" to "webstorm.key"
        "PS" -> "PhpStorm" to "phpstorm.key"
        "PY" -> "PyCharm" to "pycharm.key"
        "DB" -> "DataGrip" to "datagrip.key"
        "CL" -> "CLion" to "clion.key"
        "RD" -> "Rider" to "rider.key"
        else -> null
    }

    /**
     * Get IDE info from gradle.properties platformType
     * @return IdeInfo for the configured platform type, defaults to IC if not found
     */
    fun getConfiguredIdeProduct(): IdeInfo {
        val platformType = readPlatformType()
        return getIdeProductByType(platformType)
    }
    
    /**
     * Get test IDE version from gradle.properties testVersion
     * @return version string or "2024.3.1" if not found
     */
    fun getTestVersion(): String {
        return readTestVersion()
    }
    
    /**
     * Get IDE info by platform type code
     * @param platformType Platform type (GO, IU, IC, AI, WS, PS, etc.)
     * @return IdeInfo instance or IC as fallback
     */
    fun getIdeProductByType(platformType: String): IdeInfo {
        return when (platformType.uppercase()) {
            "GO" -> IdeProductProvider.GO
            "IU" -> IdeProductProvider.IU
            "IC" -> IdeProductProvider.IC
            "AI" -> IdeProductProvider.AI
            "WS" -> IdeProductProvider.WS
            "PS" -> IdeProductProvider.PS
            "DB" -> IdeProductProvider.DB
            "RM" -> IdeProductProvider.RM
            "PY" -> IdeProductProvider.PY
            "CL" -> IdeProductProvider.CL
            "DS" -> IdeProductProvider.DS
            "PC" -> IdeProductProvider.PC
            "QA" -> IdeProductProvider.QA
            "RR" -> IdeProductProvider.RR
            "RD" -> IdeProductProvider.RD
            "GW" -> IdeProductProvider.GW
            else -> {
                println("⚠ Unknown platform type: $platformType, using IC (IntelliJ Community)")
                IdeProductProvider.IC
            }
        }
    }
    
    /**
     * Read platformType from gradle.properties
     * @return platform type string or "IC" if not found
     */
    private fun readPlatformType(): String {
        return try {
            val gradleProps = File(GRADLE_PROPERTIES_PATH)
            if (!gradleProps.exists()) {
                println("⚠ gradle.properties not found at: ${gradleProps.absolutePath}")
                return "IC"
            }
            
            gradleProps.useLines { lines ->
                lines
                    .filter { it.contains(PLATFORM_TYPE_KEY) && !it.trim().startsWith("#") }
                    .firstNotNullOfOrNull { line ->
                        val parts = line.split("=")
                        if (parts.size == 2) parts[1].trim() else null
                    }
            } ?: "IC"
        } catch (e: Exception) {
            println("⚠ Error reading platformType: ${e.message}")
            "IC"
        }
    }
    
    /**
     * Read testVersion from gradle.properties
     * @return version string or "2024.3.1" if not found
     */
    private fun readTestVersion(): String {
        return try {
            val gradleProps = File(GRADLE_PROPERTIES_PATH)
            if (!gradleProps.exists()) {
                println("⚠ gradle.properties not found at: ${gradleProps.absolutePath}")
                return "2024.3.1"
            }
            
            gradleProps.useLines { lines ->
                lines
                    .filter { it.contains(TEST_VERSION_KEY) && !it.trim().startsWith("#") }
                    .firstNotNullOfOrNull { line ->
                        val parts = line.split("=")
                        if (parts.size == 2) parts[1].trim() else null
                    }
            } ?: "2024.3.1"
        } catch (e: Exception) {
            println("⚠ Error reading testVersion: ${e.message}")
            "2024.3.1"
        }
    }
    
    /**
     * Get product name for display
     */
    fun getProductName(ideInfo: IdeInfo): String {
        return when (ideInfo.productCode) {
            "GO" -> "GoLand"
            "IU" -> "IntelliJ IDEA Ultimate"
            "IC" -> "IntelliJ IDEA Community"
            "AI" -> "Android Studio"
            "WS" -> "WebStorm"
            "PS" -> "PhpStorm"
            "DB" -> "DataGrip"
            "RM" -> "RubyMine"
            "PY" -> "PyCharm Pro"
            "CL" -> "CLion"
            "DS" -> "DataSpell"
            "PC" -> "PyCharm Community"
            "QA" -> "Aqua"
            "RR" -> "RustRover"
            "RD" -> "Rider"
            "GW" -> "JetBrains Gateway"
            else -> ideInfo.fullName
        }
    }
}
