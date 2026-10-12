package net.internetisalie.lunar.lang.types

import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import java.io.File
import java.security.MessageDigest

/**
 * TYPE-12 golden gate: renders a file's type snapshot (errors, mapped element types, declared
 * globals, file return type) and compares it with a recording under `src/test/resources/types-golden`.
 * With `-PrecordTypeGolden` the rendering is written to `build/type-golden/recorded/` instead.
 */
internal object TypeGolden {
    private val TYPE_TAG = Regex("""---\s*@(?:class|alias)\s+(?:\([^)]*\)\s*)?([A-Za-z_][A-Za-z0-9_.]*)""")

    /** Null when [source] matches its golden (or was just recorded); else a one-line description. */
    fun mismatch(
        fixture: CodeInsightTestFixture,
        source: GoldenSource,
    ): String? {
        val actual = renderFor(fixture, source)
        val actualFile = File(System.getProperty("user.dir"), "build/type-golden/actual/${source.goldenPath}")
        actualFile.parentFile.mkdirs()
        actualFile.writeText(actual)
        val golden = File(System.getProperty("user.dir"), "src/test/resources/types-golden/${source.goldenPath}")
        if (System.getProperty("lunar.typegolden.record") == "true") {
            val recorded = File(System.getProperty("user.dir"), "build/type-golden/recorded/${source.goldenPath}")
            recorded.parentFile.mkdirs()
            recorded.writeText(actual)
            println("[type-golden] recorded ${source.goldenPath}")
            return null
        }
        if (!golden.isFile) return "no golden ${source.goldenPath}; record with -PrecordTypeGolden"
        val expected = golden.readText()
        if (expected == actual) return null
        return "golden ${source.goldenPath}: " + firstDifference(expected, actual)
    }

    /** Every name a `---@class` or `---@alias` tag in [text] declares (§5.6). */
    internal fun declaredTypeNames(text: String): Set<String> = TYPE_TAG.findAll(text).map { it.groupValues[1] }.toSet()

    /** [declaredTypeNames] over every `.lua` file under `src/main/resources/runtime` (§5.6). */
    internal fun stubTypeNames(): Set<String> =
        File(System.getProperty("user.dir"), "src/main/resources/runtime")
            .walkTopDown()
            .filter { it.isFile && it.extension == "lua" }
            .flatMap { declaredTypeNames(it.readText()).asSequence() }
            .toSet()

    internal fun render(
        snapshot: LuaTypesSnapshot,
        sourceSha256: String,
    ): String {
        val fileReturn = snapshot.getFileReturnType()
        val lines =
            listOf("# lunar type golden v1", "# source-sha256 $sourceSha256", "[errors]") +
                errorLines(snapshot) + "[elements]" + elementLines(snapshot) + "[globals]" + globalLines(snapshot) +
                "[fileReturn]" + "${escape(fileReturn.displayName())}|${hash16(renderType(fileReturn, 2))}"
        return lines.joinToString("\n") + "\n"
    }

    private fun renderFor(
        fixture: CodeInsightTestFixture,
        source: GoldenSource,
    ): String {
        var rendered: String? = null
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val file = fixture.configureByText(source.fileName, source.text)
            val snapshot = LuaTypesSnapshot.forFile(file) as LuaTypesSnapshot
            rendered = render(snapshot, sha256Hex(source.text))
        }
        return checkNotNull(rendered)
    }

    private fun errorLines(snapshot: LuaTypesSnapshot): List<String> =
        snapshot
            .getErrors()
            .map { error ->
                val range = error.element.textRange
                "${error.severity}|${escape(error.message)}|(${range.startOffset},${range.endOffset})|" +
                    error.element.javaClass.simpleName
            }.sorted()

    private fun elementLines(snapshot: LuaTypesSnapshot): List<String> =
        snapshot
            .mappedElements()
            .map { element ->
                val range = element.textRange
                val valueType = snapshot.getValueType(element)
                val display = escape(valueType.displayName())
                "(${range.startOffset},${range.endOffset})|${element.javaClass.simpleName}|$display|" +
                    hash16(renderType(valueType, 2) + " :: " + renderLua(snapshot, valueType))
            }.sorted()

    private fun globalLines(snapshot: LuaTypesSnapshot): List<String> =
        snapshot
            .declaredGlobalNames()
            .map { name ->
                val globalType = snapshot.getGlobalType(name)
                "$name|${escape(globalType.displayName())}|${hash16(renderType(globalType, 2))}"
            }.sorted()

    private fun renderType(
        type: LuaGraphType,
        depth: Int,
    ): String {
        if (depth <= 0) return type.displayName()
        return when (type) {
            is LuaGraphType.Table ->
                "${type.className ?: "{}"}[" +
                    type.getMembers().entries.joinToString(",") { (k, v) ->
                        "$k:w=${renderType(v.write, depth - 1)}/r=${renderType(v.read, depth - 1)}"
                    } + "]^" + type.superTypes.joinToString(",") { it.displayName() } + "#" + type.metamethods
            is LuaGraphType.Function ->
                "fun(" +
                    type.params.joinToString(",") {
                        "${it.name}:${renderType(it.node.write, depth - 1)}/${renderType(it.node.read, depth - 1)}"
                    } + ")->" + type.returns.joinToString(",") { renderType(it.write, depth - 1) } +
                    "!${type.declaredSignature}"
            is LuaGraphType.Union -> type.types.joinToString("|") { renderType(it, depth - 1) }
            is LuaGraphType.Array -> "${renderType(type.elementType, depth - 1)}[]"
            else -> type.displayName()
        }
    }

    private fun renderLua(
        snapshot: LuaTypesSnapshot,
        type: LuaGraphType,
    ): String =
        try {
            val luaType = snapshot.graphTypeToLuaType(type)
            val members =
                luaType.getMembers().entries.joinToString(",") { (k, m) ->
                    "$k:${m.type.name}@${m.sourceElement?.textRange}"
                }
            luaType.name + "{" + members + "}"
        } catch (e: RuntimeException) {
            "EXC:" + e.javaClass.simpleName
        }

    private fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun hash16(text: String): String = sha256Hex(text).take(16)

    private fun escape(text: String): String = text.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r")

    private fun firstDifference(
        expected: String,
        actual: String,
    ): String {
        val expectedLines = expected.split("\n")
        val actualLines = actual.split("\n")
        val lineCount = maxOf(expectedLines.size, actualLines.size)
        val index = (0 until lineCount).first { expectedLines.getOrNull(it) != actualLines.getOrNull(it) }
        return "line ${index + 1}: expected '${expectedLines.getOrNull(index) ?: "<eof>"}' " +
            "actual '${actualLines.getOrNull(index) ?: "<eof>"}'"
    }
}
