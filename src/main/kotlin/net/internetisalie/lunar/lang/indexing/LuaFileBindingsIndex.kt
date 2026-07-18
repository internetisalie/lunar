package net.internetisalie.lunar.lang.indexing

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.EnumeratorIntegerDescriptor
import com.intellij.util.io.KeyDescriptor
import net.internetisalie.lunar.lang.path.SourcePathPattern
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFuncCall
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaGlobalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaGlobalVarDecl
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaTerminalExpr
import net.internetisalie.lunar.lang.syntax.extractLuaString
import org.jetbrains.annotations.NonNls
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

public val LuaFileBindingsIndexName: @NonNls ID<Int, LuaFileBindingsRecord> =
    ID.create("$ExternalIdPrefix.FileBindings")

class LuaFileBindingsIndex : FileBasedIndexExtension<Int, LuaFileBindingsRecord>() {
    private val myExternalizer: com.intellij.util.io.DataExternalizer<LuaFileBindingsRecord> = DataExternalizer()
    private val myIndexer: ForwardIndexer<LuaFileBindingsRecord> = LuaFileBindingsIndexer()

    private val myInputFilter: FileBasedIndex.InputFilter = InputFilter()

    private class DataExternalizer : com.intellij.util.io.DataExternalizer<LuaFileBindingsRecord> {

        @Throws(IOException::class)
        override fun save(dataOutput: DataOutput, fileBindings: LuaFileBindingsRecord) {
            dataOutput.writeInt(fileBindings.bindings.size)
            for ((name, textOffset, kind) in fileBindings.bindings) {
                dataOutput.writeUTF(name)
                dataOutput.writeInt(textOffset)
                dataOutput.writeInt(kind)
            }
            dataOutput.writeInt(fileBindings.requires.size)
            for (require in fileBindings.requires) {
                dataOutput.writeUTF(require)
            }
        }

        @Throws(IOException::class)
        override fun read(dataInput: DataInput): LuaFileBindingsRecord {
            val bindingsLength = dataInput.readInt()
            val bindings = ArrayList<LuaBinding>()
            for (i in 0..<bindingsLength) {
                val name = dataInput.readUTF()
                val textOffset = dataInput.readInt()
                val kind = dataInput.readInt()
                bindings.add(LuaBinding(name, textOffset, kind))
            }

            val requiresLength = dataInput.readInt()
            val requires = ArrayList<String>()
            for (i in 0..<requiresLength) {
                val packageName = dataInput.readUTF()
                requires.add(packageName)
            }

            return LuaFileBindingsRecord(bindings, requires)
        }
    }

    private class InputFilter : FileBasedIndex.InputFilter {

        private val luaFileInputFilter = LuaFileInputFilter()
        override fun acceptInput(file: VirtualFile): Boolean {
            if (!luaFileInputFilter.acceptInput(file)) return false
            // make sure it's the right file type
            val extension = file.path.substringAfterLast('.', "")
            return extension == "lua"
        }
    }

    override fun getIndexer(): ForwardIndexer<LuaFileBindingsRecord> {
        return myIndexer
    }

    override fun getValueExternalizer(): com.intellij.util.io.DataExternalizer<LuaFileBindingsRecord> {
        return myExternalizer
    }

    override fun getVersion(): Int {
        return VERSION
    }

    override fun getName(): ID<Int, LuaFileBindingsRecord> {
        return LuaFileBindingsIndexName
    }

    override fun getKeyDescriptor(): KeyDescriptor<Int> {
        return EnumeratorIntegerDescriptor.INSTANCE
    }

    override fun dependsOnFileContent(): Boolean {
        return true
    }

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return myInputFilter
    }

    companion object {
        // MAINT-30-01 (§3.2): 2 → 3 forces a one-time full rebuild so the shrunk declaration-only
        // binding set (usages no longer recorded) replaces the stale usage-inclusive index.
        const val VERSION: Int = 3
    }
}

interface FilesQuery {
    val scope: GlobalSearchScope
    fun results(processor: FilesQueryProcessor): List<FilesQueryResult>
}

data class FilesQueryResults(
    val query: FilesQuery,
    val results: List<FilesQueryResult>,
)

data class FilesQueryResult(
    val packageFile: PackageFile,
    val bindings: List<LuaBinding>,
    val requires: List<String>,
)

data class PackageFile(
    val name: String,
    val platform: Boolean,
    val virtualFile: VirtualFile,
)

data class LuaBinding(
    val name: String,
    val textOffset: Int,
    val kind: Int,
)


data class VirtualFilesQuery(
    override val scope: GlobalSearchScope,
    val packageFiles: List<PackageFile>,
) : FilesQuery {
    override fun results(processor: FilesQueryProcessor): List<FilesQueryResult> {
        return processor.packageFiles(packageFiles).map {
            FilesQueryResult(
                it.first,
                it.second.bindings,
                it.second.requires,
            )
        }
    }
}

data class RequiredFilesQuery(
    override val scope: GlobalSearchScope,
    val sourcePathPatterns: List<SourcePathPattern>,
    val requires: List<String>,
) : FilesQuery {

    override fun results(processor: FilesQueryProcessor): List<FilesQueryResult> {
        return processor.requiredFiles(sourcePathPatterns, requires).map {
            FilesQueryResult(
                it.first,
                it.second.bindings,
                it.second.requires,
            )
        }
    }
}

fun queryFiles(vararg queries: FilesQuery): List<FilesQueryResults> {
    val scope = if (queries.size > 1)
        GlobalSearchScope.union(queries.map { it.scope })
    else
        queries.first().scope

    val processor = FilesQueryProcessor()
    FileBasedIndex.getInstance().processValues(LuaFileBindingsIndexName, ForwardIndexer.KEY, null, processor, scope)

    return queries.map { FilesQueryResults(it, it.results(processor)) }
}

class FilesQueryProcessor() : FileBasedIndex.ValueProcessor<LuaFileBindingsRecord> {
    private val entries = mutableMapOf<VirtualFile, LuaFileBindingsRecord>()

    private val files = mutableMapOf<String, VirtualFile>()

    override fun process(file: VirtualFile, value: LuaFileBindingsRecord?): Boolean {
        if (value != null) {
            entries[file] = value
            files[file.path] = file

        }
        return true
    }

    fun requiredFiles(
        sourcePathPatterns: List<SourcePathPattern>,
        requires: List<String>
    ): List<Pair<PackageFile, LuaFileBindingsRecord>> {
        val visited = mutableSetOf<String>()
        val results = mutableListOf<Pair<PackageFile, LuaFileBindingsRecord>>()
        requires.forEach {
            require(sourcePathPatterns, it, false, visited, results)
        }
        return results
    }

    fun packageFiles(
        packageFiles: List<PackageFile>
    ): List<Pair<PackageFile, LuaFileBindingsRecord>> {
        val results = mutableListOf<Pair<PackageFile, LuaFileBindingsRecord>>()
        packageFiles.forEach { packageFile ->
            val entry = entries[packageFile.virtualFile] ?: return@forEach
            results.add(Pair(packageFile, entry))
        }
        return results
    }

    private fun require(
        sourcePathPatterns: List<SourcePathPattern>,
        packageName: String,
        platform: Boolean,
        visited: MutableSet<String>,
        results: MutableList<Pair<PackageFile, LuaFileBindingsRecord>>,
    ) {
        // don't visit a package more than once
        if (visited.contains(packageName)) return
        visited.add(packageName)

        // resolve the package name to a file and its bindings
        val pair = findSourceFile(sourcePathPatterns, packageName) ?: return

        // add the file to the results
        results.add(Pair(PackageFile(packageName, platform, pair.first), pair.second))

        // require recursively
        val entry = pair.second
        entry.requires.forEach {
            require(sourcePathPatterns, it, platform, visited, results)
        }
    }

    private fun findSourceFile(
        sourcePathPatterns: List<SourcePathPattern>,
        packageName: String,
    ): Pair<VirtualFile, LuaFileBindingsRecord>? {
        sourcePathPatterns.forEach {
            val path = it.interpolate(packageName)
            val virtualFile = files[path] ?: return@forEach
            val entry = entries[virtualFile] ?: return@forEach
            return Pair(virtualFile, entry)
        }
        return null
    }
}

class LuaFileBindingsIndexer : ForwardIndexer<LuaFileBindingsRecord>() {

    private val logger = Logger.getInstance(LuaFileBindingsIndexer::class.java)

    override fun computeValue(inputData: FileContent): LuaFileBindingsRecord {
        val psiFile = inputData.psiFile
        val fileBindings = mutableListOf<LuaBinding>()
        val requires = mutableListOf<String>()

        // Extract requires by walking statements
        extractRequires(psiFile, requires)
        
        // Extract bindings - for now, keep old approach but can be refactored
        // TODO: Replace with processDeclarations() for consistency
        if (psiFile is LuaFile) {
            extractBindings(psiFile, fileBindings)
        }

        return LuaFileBindingsRecord(fileBindings.sortedBy { it.name }, requires)
    }

    private fun extractRequires(psiFile: PsiFile, requires: MutableList<String>) {
        if (psiFile !is LuaFile) return
        
        // Walk all statements in all blocks to find require() calls
        psiFile.getBlockList().forEach { block ->
            block.statementList.forEach { stmt ->
                extractRequiresFromStatement(stmt, requires)
            }
        }
    }

    private fun extractRequiresFromStatement(stmt: PsiElement?, requires: MutableList<String>) {
        if (stmt == null) return
        
        // Recursively walk to find require() calls
        stmt.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is LuaFuncCall) {
                    // Try to extract require() call
                    val varOrExp = element.varOrExp ?: return@visitElement
                    val luaVar = varOrExp.`var` ?: return@visitElement
                    
                    // Check if function name is "require"
                    val nameAndArgsList = element.nameAndArgsList
                    if (nameAndArgsList.isEmpty()) return@visitElement
                    
                    if (luaVar.nameRef?.identifier?.text != "require") return@visitElement
                    
                    // Extract string argument
                    val nameAndArgs = nameAndArgsList[0]
                    val args = nameAndArgs.args ?: return@visitElement
                    
                    // Try to get string from args
                    var stringElem = args.string
                    if (stringElem == null) {
                        // Try to get from exprList
                        val exprList = args.exprList?.exprList ?: return@visitElement
                        if (exprList.size == 1) {
                            val expr = exprList[0]
                            if (expr is LuaTerminalExpr) {
                                stringElem = expr.string
                            }
                        }
                    }
                    
                    stringElem?.let {
                        val str = extractLuaString(it.text)
                        if (str != null && !requires.contains(str)) {
                            requires.add(str)
                        }
                    }
                }
                super.visitElement(element)
            }
        })
    }

    /**
     * MAINT-30-01 (§3.1): record only **file-scope declaration** names, never a bare-name usage.
     * Lua has no declaration PSI (a declared name is a `LuaNameRef` inside a declaration container),
     * so this matches the top-level container kinds that introduce file-scope names. A usage
     * (`print(foo)`) is not one of these containers, so it is never recorded (#20 fix).
     */
    private fun extractBindings(file: LuaFile, fileBindings: MutableList<LuaBinding>) {
        val visited = mutableSetOf<String>()
        file.getBlockList().forEach { block ->
            block.statementList.forEach { stmt ->
                declaredNameLeaves(stmt).forEach { leaf -> addBinding(fileBindings, visited, leaf) }
            }
        }
    }

    private fun addBinding(fileBindings: MutableList<LuaBinding>, visited: MutableSet<String>, leaf: PsiElement) {
        val name = leaf.text ?: return
        if (visited.add(name)) fileBindings.add(LuaBinding(name, leaf.textOffset, 0))
    }

    /** The declared-name IDENTIFIER leaves introduced by [stmt] at file scope, or empty for a usage. */
    private fun declaredNameLeaves(stmt: PsiElement): List<PsiElement> = when (stmt) {
        is LuaLocalVarDecl -> stmt.attNameList.map { it.nameRef.identifier }
        is LuaGlobalVarDecl -> stmt.attNameList.map { it.nameRef.identifier }
        is LuaLocalFuncDecl -> listOf(stmt.nameRef.identifier)
        is LuaGlobalFuncDecl -> listOfNotNull(stmt.nameRef?.identifier)
        is LuaFuncDecl -> funcDeclNameLeaves(stmt)
        is LuaAssignmentStatement -> assignmentNameLeaves(stmt)
        else -> emptyList()
    }

    /** `function foo` → `foo`; `function recv:m` / `recv.m` → the method-qualified leaf as well. */
    private fun funcDeclNameLeaves(decl: LuaFuncDecl): List<PsiElement> {
        val plain = decl.funcName.nameRef.identifier
        val method = decl.funcName.funcNameMethod?.nameRef?.identifier
        return listOfNotNull(plain, method)
    }

    /** Bare global assignment `x = …` declares `x`; a dotted `t.f = …` is owned by the member-field index. */
    private fun assignmentNameLeaves(stmt: LuaAssignmentStatement): List<PsiElement> =
        stmt.varList.varList.mapNotNull { luaVar ->
            if (luaVar.varSuffixList.isEmpty()) luaVar.nameRef?.identifier else null
        }
}

data class LuaFileBindingsRecord(
    val bindings: List<LuaBinding>,
    val requires: List<String>,
)
