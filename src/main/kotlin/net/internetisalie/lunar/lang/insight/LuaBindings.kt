package net.internetisalie.lunar.lang.insight

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.ProjectScope
import net.internetisalie.lunar.lang.indexing.*
import net.internetisalie.lunar.lang.path.PathConfiguration
import net.internetisalie.lunar.lang.psi.*
import net.internetisalie.lunar.lang.syntax.extractLuaString
import net.internetisalie.lunar.project.PlatformLibraryIndex

enum class Kind(val index: Int) {
    Package(0),
    Function(1),
    Variable(2),
    Label(3),
}

data class Binding(
    val element: PsiElement,
    val kind: Kind,
    // TODO: scope?
) {
    var global: Boolean = false
    var platform: Boolean = false
    var shadowed: Boolean = false
    var param: Boolean = false
}

data class DelayedBinding(val offset: Int, val name: String, val scope: Scope)

data class DottedElements(val parts: List<PsiElement>) {
    fun split(): Pair<DottedElements?, PsiElement> {
        if (parts.size > 1) {
            return Pair(
                DottedElements(parts.subList(0, parts.size - 1)),
                parts[parts.size - 1]
            )
        }
        return Pair(null, parts[0])
    }

    fun splitIter(): Pair<Iterator<PsiElement>, PsiElement> {
        val (head, tail) = split()
        if (head != null) {
            return Pair(head.iterator(), tail)
        }
        return Pair(emptyList<PsiElement>().iterator(), parts[0])
    }

    fun iterator(): Iterator<PsiElement> {
        return parts.iterator()
    }

    val size: Int
        get() = parts.size

    val first: PsiElement
        get() = parts[0]

    override fun toString(): String {
        return parts.joinToString(".") { it.text }
    }
}

private fun getFuncNameElements(funcName: LuaFuncName): DottedElements {
    val result = mutableListOf<PsiElement>(funcName.nameRef.identifier)
    funcName.funcNamePropertyList.forEach {
        result.add(it.nameRef.identifier)
    }
    val funcNameMethod = funcName.funcNameMethod
    if (funcNameMethod != null) {
        result.add(funcNameMethod.nameRef.identifier)
    }
    return DottedElements(result)
}

// Return the leading identifiers
private fun getVarElements(v: LuaVar): DottedElements? {
    if (v.expr != null) return null
    val varName = v.nameRef ?: return null
    val result = mutableListOf(varName.identifier)
    for (it in v.varSuffixList) {
        if (it.nameAndArgsList.size > 0) break
        val identifier = it.indexExpr.nameRef?.identifier ?: break
        result.add(identifier)
    }

    if (result.size == 0) {
        return null
    }

    return DottedElements(result)
}

data class Reference(val binding: Binding?) {
    var upValue: Boolean = false
    var tailCall: Boolean = false
    var self: Boolean = false
    var global: Boolean = false
    var kind: Kind? = binding?.kind
    var name: List<String> = emptyList()

    val defined : Boolean
        get() =  binding != null

    override fun toString(): String {
        val bindingElement = binding?.element
        val isValid = bindingElement?.isValid == true

        return """
            <html><body><pre>
            binding:
              kind: ${binding?.kind}
              global: ${binding?.global}
              platform: ${binding?.platform}
              shadowed: ${binding?.shadowed}
              param: ${binding?.param}
              element:
                textOffset: ${if (isValid) bindingElement?.textOffset else "invalid"}
                file: ${if (isValid) bindingElement?.containingFile?.name else "invalid"}
            external:
                global: $global
                name: ${name.joinToString(".")}
            upValue: $upValue
            tailCall: $tailCall
            self: $self
            kind: $kind
            </pre></body></html>""".trimIndent()
    }
}

class Scope(private val enclosure: Scope?) {
    val declarations = mutableMapOf<String, Binding>()
    val tables = mutableMapOf<String, Scope>()
    var global: Boolean = false
    var platform: Boolean = false

    fun lookupReference(name: String): Reference {
        if (declarations.containsKey(name)) {
            val reference = Reference(declarations[name])
            reference.global = global
            return reference
        }
        if (enclosure != null) {
            val reference = enclosure.lookupReference(name)
            reference.upValue = true
            return reference
        }
        return Reference(null)
    }

    fun lookupContainingScope(name: String): Scope? {
        if (declarations.containsKey(name)) {
            return this
        }
        if (enclosure != null) {
            return enclosure.lookupContainingScope(name)
        }
        return null
    }

    fun table(name: String): Scope {
        if (!tables.containsKey(name)) {
            tables[name] = Scope(null)
        }
        return tables[name]!!
    }

    fun createBinding(element : PsiElement, kind: Kind) : Binding {
        val binding = Binding(element, kind)
        binding.global = global
        // TODO: re-declaration in same scope?
        binding.shadowed = lookupReference(element.text).defined
        return binding
    }
}

class LuaBindings(
    val references: Map<Int, Reference>,
    val global : Scope,
    val requires: List<String>,
) {
    fun lookup(source : PsiElement) :Reference? {
        return references[source.textOffset]
    }
}

data class LuaImports(
    val project :Project,
    val platform : List<FilesQueryResult>,
    val requires : List<FilesQueryResult>
) {
    val packages : List<PackageFile> = listOf(
        platform.map { it.packageFile },
        requires.map { it.packageFile },
    ).flatten()
    val loaded : Set<String> = packages.map { it.name }.toSet()

    fun lookupReference(names: MutableList<String>): Reference {
        val name = names.joinToString(".")
        requires.forEach {
            it.bindings.forEach { export ->
                if (export.name == name) {
                    return reference(it.packageFile.virtualFile, export, it.packageFile.platform)
                }
            }
        }
        platform.forEach {
            it.bindings.forEach { export ->
                if (export.name == name) {
                    return reference(it.packageFile.virtualFile, export, it.packageFile.platform)
                }
            }
        }
        return Reference(null)
    }

    private fun reference(virtualFile : VirtualFile, export : LuaBinding, platform : Boolean) : Reference {
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return Reference(null)
        val element = psiFile.findElementAt(export.textOffset) ?: return Reference(null)
        var kind = Kind.entries[export.kind]
        if (kind == Kind.Variable && loaded.contains(export.name)) {
            kind = Kind.Package
        }

        val binding = Binding(element, kind)
        binding.platform = platform
        return Reference(binding)
    }

    fun lookupPackage(packageName: String): Reference {
        val packageFile = packages.firstOrNull { it.name == packageName } ?: return Reference(null)
        val psiFile = PsiManager.getInstance(project).findFile(packageFile.virtualFile) ?: return Reference(null)
        val binding = Binding(psiFile, Kind.Package)
        val reference = Reference(binding)
        reference.name = packageName.split('.')
        return reference
    }

    companion object {
        fun create(project : Project, bindings : LuaBindings) : LuaImports {
            val platformQuery = VirtualFilesQuery(
                ProjectScope.getLibrariesScope(project),
                PlatformLibraryIndex.getPackageFiles(project),
            )

            val requiresQuery = RequiredFilesQuery(
                ProjectScope.getProjectScope(project),
                PathConfiguration.getProjectSourcePathPatterns(project),
                bindings.requires,
            )

            val importedResults = queryFiles(platformQuery, requiresQuery)

            return LuaImports(
                project,
                importedResults.first { it.query == platformQuery }.results,
                importedResults.first { it.query == requiresQuery }.results,
            )
        }
    }
}
