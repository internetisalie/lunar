package net.internetisalie.lunar.lang.indexing

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import org.jetbrains.annotations.NonNls
import java.io.DataInput
import java.io.DataOutput

private val LuaGlobalAssignmentIndexId: @NonNls ID<String, String> = ID.create("lunar.global.assignment")

/**
 * File-based index of **bare** global assignments (BUG-391): a top-level `name = value` whose target
 * has no dotted suffix declares the global `name`.
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
    override fun getVersion(): Int = 1

    override fun dependsOnFileContent(): Boolean = true
    override fun indexDirectories(): Boolean = false

    override fun getInputFilter(): FileBasedIndex.InputFilter = InputFilter()

    private class InputFilter : FileBasedIndex.InputFilter {
        override fun acceptInput(file: VirtualFile): Boolean = file.extension == "lua"
    }

    private class StringDataExternalizer : DataExternalizer<String> {
        override fun save(output: DataOutput, value: String) = output.writeUTF(value)
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
            return result
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
                    is LuaLocalVarDecl -> stmt.attNameList.forEach { attName ->
                        boundName(attName)?.let { names += it }
                    }
                    is LuaLocalFuncDecl -> boundName(stmt)?.let { names += it }
                    else -> Unit
                }
            }
            return names
        }

        private fun boundName(declaration: PsiElement): String? =
            declaration.node.findChildByType(LuaElementTypes.NAME_REF)?.psi?.text
    }

    companion object {
        val KEY: ID<String, String> = LuaGlobalAssignmentIndexId
    }
}
