package net.internetisalie.lunar.lang.indexing

import com.intellij.psi.PsiElement
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaFuncName
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import org.jetbrains.annotations.NonNls
import java.io.DataInput
import java.io.DataOutput

private val LuaGlobalAssignmentIndexId: @NonNls ID<String, String> = ID.create("lunar.global.assignment")

/**
 * File-based index of **bare** global declarations: a top-level `name = value` whose target has no
 * dotted suffix (BUG-391), and a top-level `function name() end` with no dotted or method name
 * (BUG-427). Both declare the global `name`; only the first was indexed until 2026-08-07.
 *
 * Sibling of [LuaMemberFieldIndex], which owns the dotted `receiver.field = value` case. Neither is
 * stubbed (only `LuaFuncDecl`/`LuaLocalVarDecl`/`LuaLocalFuncDecl` are), so both read straight from
 * the PSI.
 *
 * Why this is needed at all: [net.internetisalie.lunar.lang.LuaNameReference] resolves external names
 * through platform library files and files reachable by `require` from the *current* file. A Lua
 * global is visible everywhere once assigned, regardless of `require`, so a project that shares an
 * application object through a global (`ide = {...}` in one file, `ide.foo` in fifty others) had
 * every use reported undeclared.
 */
class LuaGlobalAssignmentIndex : FileBasedIndexExtension<String, String>() {
    private val externalizer: DataExternalizer<String> = StringDataExternalizer()
    private val indexer: DataIndexer<String, String, FileContent> = Indexer()

    override fun getName(): ID<String, String> = LuaGlobalAssignmentIndexId

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<String> = externalizer

    override fun getIndexer(): DataIndexer<String, String, FileContent> = indexer

    // 2: BUG-427 added bare global `function f() end` declarations.
    // 2 -> 3 (BUG-436): the filter widened, so the index CONTENT changed. Without the bump a
    // persisted index keeps its `.lua`-only entries and the fix is invisible on any machine
    // that has indexed before it.
    override fun getVersion(): Int = 3

    override fun dependsOnFileContent(): Boolean = true

    override fun indexDirectories(): Boolean = false

    /**
     * BUG-436: derived from the file type, never re-stated as an extension. `plugin.xml:99-100`
     * registers `LuaFileType` for `extensions="lua;rockspec"` **and** `fileNames=".luacheckrc;.busted"`;
     * this filter used to read `file.extension == "lua"`, so three of the four registrations were
     * silently unindexed — absent, not stale, and absent in the direction no gate here looks.
     *
     * **Instantiated, never subclassed.** `RequiredIndexesEvaluator.toHint` turns this into a real
     * file-type predicate only when `filter.javaClass == DefaultFileTypeSpecificInputFilter::class.java`
     * — a subclass silently loses the hint and is evaluated per file instead. `LuaReceiverMemberIndex`
     * (fixed first, in `fcce5966`) is the worked example.
     */
    override fun getInputFilter(): FileBasedIndex.InputFilter = DefaultFileTypeSpecificInputFilter(LuaFileType)

    private class StringDataExternalizer : DataExternalizer<String> {
        override fun save(
            output: DataOutput,
            value: String,
        ) = output.writeUTF(value)

        override fun read(input: DataInput): String = input.readUTF()
    }

    private class Indexer : DataIndexer<String, String, FileContent> {
        override fun map(inputData: FileContent): Map<String, String> {
            val psiFile = inputData.psiFile
            if (psiFile !is LuaFile) return emptyMap()
            // Only file-scope statements: a bare assignment nested inside a function may well be
            // writing to an enclosing local, and an indexer must not attempt scope resolution.
            val topLevel = psiFile.getBlockList().flatMap { it.statementList }
            val fileLocals = fileScopeLocalNames(topLevel)
            val result = mutableMapOf<String, String>()
            topLevel.filterIsInstance<LuaAssignmentStatement>().forEach { stmt ->
                stmt.varList.varList.forEach { target ->
                    val name = target.nameRef?.text
                    // A dotted target belongs to LuaMemberFieldIndex; a name also declared local at
                    // file scope is a local write, not a global declaration.
                    if (name != null && target.varSuffixList.isEmpty() && name !in fileLocals) {
                        result[name] = ""
                    }
                }
            }
            topLevel.filterIsInstance<LuaFuncDecl>().forEach { decl ->
                declaredGlobalName(decl)?.takeIf { it !in fileLocals }?.let { result[it] = "" }
            }
            return result
        }

        /**
         * BUG-427: `function count() end` declares the global `count` exactly as `count = function`
         * does, and only the assignment form was indexed — so a bare global API was unresolvable
         * from any other file, taking hover, hints, parameter info and assignability with it.
         *
         * Null for a dotted or method name (`function Lib.f()`, `function C:m()`): those write a
         * *member*, which is [LuaMemberFieldIndex]'s business, and their base name is declared by
         * whatever created the receiver.
         */
        private fun declaredGlobalName(decl: LuaFuncDecl): String? {
            val funcName = decl.node.findChildByType(LuaElementTypes.FUNC_NAME)?.psi as? LuaFuncName ?: return null
            if (funcName.funcNamePropertyList.isNotEmpty() || funcName.funcNameMethod != null) return null
            return boundName(funcName)
        }

        /**
         * SYNTAX-18: read the bound name through the AST node, not the generated getter.
         * `LuaLocalFuncDecl.getNameRef()` is declared `@NotNull` but returns null for a partially
         * parsed decl — `local function repeat(...)`, where a keyword sits in the name slot — and the
         * platform *logs an error* rather than returning null, which surfaces as a
         * `TestLoggerAssertionError` in any test that indexes such a file.
         */
        private fun fileScopeLocalNames(topLevel: List<Any?>): Set<String> {
            val names = mutableSetOf<String>()
            topLevel.forEach { stmt ->
                when (stmt) {
                    is LuaLocalVarDecl ->
                        stmt.attNameList.forEach { attName ->
                            boundName(attName)?.let { names += it }
                        }
                    is LuaLocalFuncDecl -> boundName(stmt)?.let { names += it }
                    else -> Unit
                }
            }
            return names
        }

        private fun boundName(declaration: PsiElement): String? =
            declaration.node
                .findChildByType(LuaElementTypes.NAME_REF)
                ?.psi
                ?.text
    }

    companion object {
        val KEY: ID<String, String> = LuaGlobalAssignmentIndexId
    }
}
