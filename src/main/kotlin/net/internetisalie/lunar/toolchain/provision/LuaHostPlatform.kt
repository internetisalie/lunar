package net.internetisalie.lunar.toolchain.provision

import com.intellij.openapi.util.SystemInfo

/** Operating-system family used for strategy selection and feed asset matching (design §2.3). */
enum class LuaOs { LINUX, MACOS, WINDOWS }

/** CPU architecture family used for feed asset matching (design §2.3). */
enum class LuaArch { X86_64, AARCH64 }

/**
 * The current host OS/arch pair. Drives strategy selection and feed asset matching.
 * See design §2.3.
 */
data class LuaHostPlatform(val os: LuaOs, val arch: LuaArch) {
    companion object {
        fun current(): LuaHostPlatform = LuaHostPlatform(currentOs(), currentArch())

        private fun currentOs(): LuaOs =
            when {
                SystemInfo.isWindows -> LuaOs.WINDOWS
                SystemInfo.isMac -> LuaOs.MACOS
                SystemInfo.isLinux -> LuaOs.LINUX
                else -> throw LuaProvisionException(
                    "Toolchain provisioning is not supported on this operating system: ${SystemInfo.OS_NAME}",
                )
            }

        private fun currentArch(): LuaArch {
            val rawArch = System.getProperty("os.arch").orEmpty()
            return when (rawArch) {
                "amd64", "x86_64" -> LuaArch.X86_64
                "aarch64", "arm64" -> LuaArch.AARCH64
                else -> throw LuaProvisionException(
                    "Toolchain provisioning is not supported on this CPU architecture: '$rawArch'",
                )
            }
        }
    }
}
