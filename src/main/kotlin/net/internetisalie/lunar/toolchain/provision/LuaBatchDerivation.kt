package net.internetisalie.lunar.toolchain.provision

/** One matrix row: a runtime kind + version to provision (design §2.13, §3.10). */
data class LuaBatchRow(val kindId: String, val versionSpec: String)

/**
 * Pure batch-request derivation (design §3.10), extracted from the Swing layer so it is
 * unit-testable. Each row yields one [LuaProvisionRequest] under `{baseDir}/{kind}-{ver}` with
 * items `[(kind, ver), (luarocks, latest)]`.
 */
object LuaBatchDerivation {
    fun toRequests(baseDir: String, rows: List<LuaBatchRow>): List<LuaProvisionRequest> {
        val trimmedBase = baseDir.trim().trimEnd('/')
        return rows.map { row ->
            val label = "${row.kindId}-${row.versionSpec}"
            LuaProvisionRequest(
                environmentName = label,
                rootDir = "$trimmedBase/$label",
                items = listOf(
                    LuaProvisionItem(row.kindId, row.versionSpec),
                    LuaProvisionItem("luarocks", "latest"),
                ),
            )
        }
    }
}
