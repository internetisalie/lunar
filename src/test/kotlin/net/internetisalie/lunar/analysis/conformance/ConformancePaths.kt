package net.internetisalie.lunar.analysis.conformance

import java.io.File

/** Where the suite's committed data and its run output live (MAINT-55 design §4). */
object ConformancePaths {
    private fun repo(): File = File(System.getProperty("user.dir"))

    fun root(): File = File(repo(), "src/test/resources/conformance/luacats")

    fun cases(): File = File(root(), "cases")

    fun deviations(): File = File(root(), "deviations.json")

    fun results(): File = File(root(), "results.json")

    fun importRecord(): File = File(root(), "import.json")

    fun bugs(): File = File(root(), "bugs.json")

    fun actual(): File = File(repo(), "build/conformance/results.json")

    fun recorded(): File = File(repo(), "build/conformance/recorded/results.json")
}
