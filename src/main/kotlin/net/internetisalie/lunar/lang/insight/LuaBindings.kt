package net.internetisalie.lunar.lang.insight

import com.intellij.psi.PsiElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
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

data class DelayedBinding(val offset: Int, val name: String)

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
        return """
            <html><body><pre>
            binding:
              kind: ${binding?.kind}
              global: ${binding?.global}
              platform: ${binding?.platform}
              shadowed: ${binding?.shadowed}
              param: ${binding?.param}
              element:
                textOffset: ${binding?.element?.textOffset}
                file: ${binding?.element?.containingFile?.name}
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

class LuaBindingsVisitor(private val imports : LuaImports?) : LuaRecursiveVisitor() {
    private val references = mutableMapOf<Int, Reference>()
    private val delayed = mutableSetOf<DelayedBinding>()
    private val global = Scope(null) // non-local variables
    private var file = Scope(global) // local variables at file scope
    private val labels = Scope(null) // labels
    private val requires = mutableListOf<String>()
    private var scope = file

    init {
        global.global = true
    }

    private fun inEnclosedScope(fn: () -> Unit) {
        val parentScope = scope
        scope = Scope(parentScope)
        fn()
        scope = parentScope
    }

    private fun newOrExistingName(target: Scope, name: String, binding: Binding): Binding {
        // Check for existing declaration
        val decl = target.lookupReference(name)
        if (decl.defined) {
            return decl.binding!!
        }
        if (binding.global) {
            // New global name declaration
            global.declarations[name] = binding
        } else {
            // New local name declaration
            target.declarations[name] = binding
        }
        return binding
    }

    override fun visitLocalVarDecl(o: LuaLocalVarDecl) {
        super.visitLocalVarDecl(o)
        o.attNameList.forEach {
            val identifier = it.nameRef.identifier
            scope.declarations[identifier.text] = scope.createBinding(identifier, Kind.Variable)
        }
    }

    override fun visitLocalFuncDecl(o: LuaLocalFuncDecl) {
        inEnclosedScope {
            super.visitLocalFuncDecl(o)
        }
        val identifier = o.nameRef.identifier
        val binding = scope.createBinding(identifier, Kind.Function)
        scope.declarations[identifier.text] = binding
        references[identifier.textOffset] = Reference(binding)
    }

    private fun visitFuncNameElements(elements: DottedElements) {
        var funcScope = global
        var isGlobal = true
        val (iter, tail) = elements.splitIter()
        val names = mutableListOf<String>()

        if (iter.hasNext()) {
            val firstElement = iter.next()
            val firstName = firstElement.text
            names.add(firstName)

            val containingScope = scope.lookupContainingScope(firstName)
            if (containingScope == null) {
                val reference = imports?.lookupReference(names) ?: Reference(null)
                reference.global = true
                reference.name = names.subList(0, 1).toList()
                references[firstElement.textOffset] = reference

                funcScope = global.table(firstName)
            } else {
                // Defined identifier
                val reference = containingScope.lookupReference(firstName)
                reference.name = names.subList(0, 1).toList()
                references[firstElement.textOffset] = reference

                isGlobal = containingScope.global
                funcScope = containingScope.table(firstName)
            }
            
            while (iter.hasNext()) {
                val element = iter.next()
                val name =  element.text
                names.add(name)

                var reference = funcScope.lookupReference(name)
                if (!reference.defined && isGlobal && imports != null) {
                    reference = imports.lookupReference(names)
                }
                
                reference.global = isGlobal
                reference.name = names.subList(0, names.size).toList()
                references[element.textOffset] = reference

                funcScope = funcScope.table(name)
            }
        }

        // Bind the name
        val name = tail.text
        names.add(name)
        val binding = funcScope.createBinding(tail, Kind.Function)
        funcScope.declarations[name] = binding
        val reference = Reference(binding)
        reference.global = isGlobal // set visibility
        reference.name = names
        references[tail.textOffset] = reference
    }

    override fun visitFuncDecl(o: LuaFuncDecl) {
        inEnclosedScope {
            if (o.funcName.funcNameMethod != null) {
                val identifier = o.funcName.funcNameMethod!!.nameRef.identifier
                scope.declarations["self"] = Binding(identifier, Kind.Variable)
            }

            super.visitFuncDecl(o)

            val elements = getFuncNameElements(o.funcName)
            visitFuncNameElements(elements)
        }
    }

    override fun visitParList(o: LuaParList) {
        val nameList = o.nameList
        if (nameList != null) {
            val nameList = nameList
            nameList.nameRefList.forEach {
                val identifier = it.identifier
                val binding = Binding(identifier, Kind.Variable) // not shadowing
                binding.param = true
                scope.declarations[identifier.text] = binding
                references[identifier.textOffset] = Reference(binding)
            }
        }
        super.visitParList(o)
    }

    private fun visitVarElements(varElements: DottedElements, assignment: Boolean) {
        var varScope : Scope? = null
        val names = mutableListOf<String>()
        val (iter, tail) = varElements.splitIter()
        var isGlobal : Boolean? = null
        while (iter.hasNext()) {
            val identifier = iter.next()
            val name = identifier.text
            names.add(name)

            if (varScope == null) {
                varScope = scope.lookupContainingScope(name) ?: global
            }

            isGlobal = isGlobal ?: varScope.global

            var reference = varScope.lookupReference(name)

            if (isGlobal && !reference.defined && imports != null) {
                reference = imports.lookupReference(names)
            }

            reference.global = isGlobal
            reference.name = names.subList(0, names.size).toList()
            references[identifier.textOffset] = reference

            varScope = varScope.table(name)
        }

        val name = tail.text
        names.add(name)

        if (varScope == null) {
            varScope = scope.lookupContainingScope(name) ?: global
        }
        isGlobal = isGlobal ?: varScope.global

        var reference : Reference?
        if (assignment) {
            val binding = newOrExistingName(
                varScope,
                name,
                varScope.createBinding(tail, Kind.Variable),
            )
            varScope.declarations[tail.text] = binding
            reference = Reference(binding)
        } else {
            reference = varScope.lookupReference(name)

            if (isGlobal && !reference.defined && imports != null) {
                reference = imports.lookupReference(names)
            }
        }


        reference.global = isGlobal
        reference.name = names
        references[tail.textOffset] = reference
    }

    override fun visitVar(o: LuaVar) {
        val varElements = getVarElements(o)
        if (varElements != null) {
            visitVarElements(varElements, o.parent is LuaVarList)
        }

        super.visitVar(o)
    }

    override fun visitLabel(o: LuaLabel) {
        val identifier = o.labelName.identifier
        val binding = Binding(identifier, Kind.Label)
        labels.declarations[identifier.text] = binding
        references[identifier.textOffset] = Reference(binding)
        super.visitLabel(o)
    }

    override fun visitLabelRef(o: LuaLabelRef) {
        val identifier = o.identifier
        delayed.add(DelayedBinding(identifier.textOffset, identifier.text))
    }

    override fun visitGenericForStatement(o: LuaGenericForStatement) {
        inEnclosedScope {
            o.nameList.nameRefList.forEach {
                val identifier = it.identifier
                scope.declarations[identifier.text] = scope.createBinding(identifier, Kind.Variable)
            }
            super.visitGenericForStatement(o)
        }
    }

    override fun visitNumericForStatement(o: LuaNumericForStatement) {
        inEnclosedScope {
            val identifier = o.identifier
            scope.declarations[identifier.text] = scope.createBinding(identifier, Kind.Variable)
            super.visitNumericForStatement(o)
        }
    }

    override fun visitDoStatement(o: LuaDoStatement) {
        inEnclosedScope {
            super.visitDoStatement(o)
        }
    }

    override fun visitRepeatStatement(o: LuaRepeatStatement) {
        inEnclosedScope {
            super.visitRepeatStatement(o)
        }
    }

    override fun visitIfStatement(o: LuaIfStatement) {
        o.exprList.forEach {
            super.visitExpr(it)
        }
        o.getBlockList().forEach {
            inEnclosedScope {
                super.visitBlock(it)
            }
        }
    }

    private fun visitRequire(o: LuaFuncCall) {
        val varVal = o.varOrExp.`var` ?: return
        val varElements = getVarElements(varVal) ?: return
        if (varElements.size != 1) return
        if (varElements.first.text != "require") return
        if (scope.lookupContainingScope(varElements.first.text)?.platform == false) return
        if (o.nameAndArgsList.size != 1) return
        val nameAndArgs = o.nameAndArgsList[0]
        if (nameAndArgs.methodExpr != null) return
        var exprString: PsiElement? = nameAndArgs.args.string
        if (exprString == null) {
            val exprList = nameAndArgs.args.exprList?.exprList ?: return
            if (exprList.size != 1) return
            exprString = (exprList.first() as? LuaTerminalExpr ?: return).string ?: return
        }
        val packageName = extractLuaString(exprString.text)
        if (imports != null) {
            references[exprString.textOffset] = imports.lookupPackage(packageName)
        }
        requires.add(packageName)
    }

    override fun visitFuncCall(o: LuaFuncCall) {
        visitRequire(o)
        super.visitFuncCall(o)
    }

    fun resolveDelayedReferences() {
        delayed.forEach { delayedReference ->
            val labelReference = labels.lookupReference(delayedReference.name)
            if (!labelReference.defined) {
                labelReference.kind = Kind.Label
                labelReference.name = listOf(delayedReference.name)
            }
            references[delayedReference.offset] = labelReference
        }
    }

    companion object {
        private val bindingsKey = Key<FileUserData<LuaBindings>>("LuaBindingsAnnotator.KEY_BINDINGS")
        private val bindingsFullKey = Key<FileUserData<LuaBindings>>("LuaBindingsAnnotator.KEY_BINDINGS_FULL")

        fun getBindings(element: PsiElement): LuaBindings {
            return bindingsKey.cacheFileUserData(element) { psiFile -> visit(psiFile, null) }
        }

        fun getBindingsWithImports(element : PsiElement) : LuaBindings {
            return bindingsFullKey.cacheFileUserData(element) { psiFile ->
                val bindings = getBindings(element)
                val imports = LuaImports.create(element.project, bindings)
                visit(psiFile, imports)
            }
        }

        private fun visit(psiFile : PsiFile, imports : LuaImports?) : LuaBindings {
            val visitor = LuaBindingsVisitor(imports)
            psiFile.accept(visitor)
            visitor.resolveDelayedReferences()
            return LuaBindings(visitor.references, visitor.global, visitor.requires.toList())
        }
    }
}
