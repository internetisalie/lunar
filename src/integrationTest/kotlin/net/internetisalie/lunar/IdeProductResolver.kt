package net.internetisalie.lunar

import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.IdeInfo
import java.io.File

/**
 * IDE product resolver that reads from gradle.properties
 * Maps platformType to IdeProductProvider instances and reads testVersion
 */
object IdeProductResolver {
    
    private const val GRADLE_PROPERTIES_PATH = "gradle.properties"
    private const val PLATFORM_TYPE_KEY = "platformType"
    private const val TEST_VERSION_KEY = "testVersion"
    
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
