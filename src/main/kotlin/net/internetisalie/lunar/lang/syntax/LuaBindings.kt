package net.internetisalie.lunar.lang.syntax

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiManager
import net.internetisalie.lunar.lang.psi.*
import net.internetisalie.lunar.project.PlatformLibraryIndex

enum class Kind {
    Package,
    Function,
    Variable,
    Label,
}

data class Binding(
    val element: PsiElement,
    val kind: Kind,
    // TODO: scope?
) {
    var global: Boolean = false
    var platform: Boolean = false
    var shadowed: Boolean = false
}

data class DelayedBinding(val offset: Int, val name: String)

data class Reference(val binding: Binding?) {
    var upValue: Boolean = false
    var tailCall: Boolean = false
    var kind: Kind? = binding?.kind

    fun defined(): Boolean {
        return binding != null
    }

    fun isElement(element: PsiElement): Boolean {
        return element == binding?.element
    }
}

private fun createGlobalBinding(element: PsiElement, kind: Kind): Binding {
    val binding = Binding(element, kind)
    binding.global = true
    return binding
}

private fun createPlatformBinding(element: PsiElement, kind: Kind): Binding {
    val binding = Binding(element, kind)
    binding.platform = true
    return binding
}

private class Scope(val parent: Scope?) {
    val declarations = mutableMapOf<String, Binding>()

    fun lookup(name: String): Reference {
        if (declarations.containsKey(name)) {
            return Reference(declarations[name])
        }
        if (parent != null) {
            val reference = parent.lookup(name)
            reference.upValue = true
            return reference
        }
        return Reference(null)
    }

    fun createLocalBinding(element: PsiElement, kind: Kind): Binding {
        val binding = Binding(element, kind)
        // TODO: re-declaration in same scope?
        binding.shadowed = lookup(element.text).defined()
        return binding
    }
}

private fun createPlatformScope(project: Project, index: PlatformLibraryIndex): Scope {
    val scope = Scope(null)
    val psiManager = PsiManager.getInstance(project)

    index.getGlobalNames().forEach { entry ->
        val psiFile = psiManager.findFile(entry.value) ?: return@forEach
        scope.declarations[entry.key] = createPlatformBinding(psiFile, Kind.Function)
    }

    index.getPackageNames().forEach { entry ->
        val psiFile = psiManager.findFile(entry.value) ?: return@forEach
        scope.declarations[entry.key] = createPlatformBinding(psiFile, Kind.Package)
    }

    return scope
}

class LuaBindingsVisitor(project: Project, platformLibraryIndex: PlatformLibraryIndex) : LuaRecursiveVisitor() {
    private val references = mutableMapOf<Int, Reference>()
    private val delayed = mutableSetOf<DelayedBinding>()
    private val platform = createPlatformScope(project, platformLibraryIndex)
    private val global = Scope(platform) // non-local variables
    private val labels = Scope(null) // labels
    private var scope = global

    fun inChildScope(fn: () -> Unit) {
        val parentScope = scope
        scope = Scope(parentScope)
        fn()
        scope = parentScope
    }

    fun newOrExistingName(name: String, binding: Binding): Binding {
        // Check for existing global declaration
        val decl = scope.lookup(name)
        if (decl.defined()) {
            return decl.binding!!
        }
        if (binding.global) {
            // New global name declaration
            global.declarations[name] = binding
        } else {
            // New local name declaration
            scope.declarations[name] = binding
        }
        return binding
    }

    override fun visitAssignmentStatement(o: LuaAssignmentStatement) {
        super.visitAssignmentStatement(o)
        o.varList.varList.forEach {
            val varName = it.varName ?: return@forEach
            val identifier = varName.identifier
            references[identifier.textOffset] = Reference(
                newOrExistingName(
                    identifier.text,
                    createGlobalBinding(identifier, Kind.Variable)
                )
            )
        }
    }

    override fun visitLocalVarDecl(o: LuaLocalVarDecl) {
        super.visitLocalVarDecl(o)
        o.nameList.nameDeclList.forEach {
            val identifier = it.identifier
            scope.declarations[identifier.text] = scope.createLocalBinding(identifier, Kind.Variable)
        }
    }

    override fun visitLocalFuncDecl(o: LuaLocalFuncDecl) {
        inChildScope {
            val identifier = o.localFuncName.identifier
            scope.declarations[identifier.text] = scope.createLocalBinding(identifier, Kind.Function)
            super.visitLocalFuncDecl(o)
        }
    }

    override fun visitFuncDecl(o: LuaFuncDecl) {
        inChildScope {
            // TODO: function and method properties
            if (o.funcName.funcNamePropertyList.size == 0 && o.funcName.funcNameMethod == null) {
                val identifier = o.funcName.identifier
                global.declarations[identifier.text] = createGlobalBinding(identifier, Kind.Function)
            } else if (o.funcName.funcNameMethod != null) {
                val identifier = o.funcName.funcNameMethod!!.identifier
                scope.declarations["self"] = Binding(identifier, Kind.Variable)
            }
            super.visitFuncDecl(o)
        }
    }

    override fun visitFuncBody(o: LuaFuncBody) {
        if (o.parList?.nameList != null) {
            val nameList = o.parList!!.nameList!!
            nameList.nameDeclList.forEach {
                val identifier = it.identifier
                scope.declarations[identifier.text] = Binding(identifier, Kind.Variable) // not shadowing
            }
        }
        super.visitFuncBody(o)
    }

    override fun visitVarOrExp(o: LuaVarOrExp) {
        val identifier = o.`var`?.varName?.identifier ?: return
        references[identifier.textOffset] = scope.lookup(identifier.text)
        super.visitVarOrExp(o)
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
        inChildScope {
            o.nameList.nameDeclList.forEach {
                val identifier = it.identifier
                scope.declarations[identifier.text] = scope.createLocalBinding(identifier, Kind.Variable)
            }
            super.visitGenericForStatement(o)
        }
    }

    override fun visitNumericForStatement(o: LuaNumericForStatement) {
        inChildScope {
            val identifier = o.identifier
            scope.declarations[identifier.text] = scope.createLocalBinding(identifier, Kind.Variable)
            super.visitNumericForStatement(o)
        }
    }

    override fun visitDoStatement(o: LuaDoStatement) {
        inChildScope {
            super.visitDoStatement(o)
        }
    }

    override fun visitRepeatStatement(o: LuaRepeatStatement) {
        inChildScope {
            super.visitRepeatStatement(o)
        }
    }

    override fun visitIfStatement(o: LuaIfStatement) {
        o.exprList.forEach {
            super.visitExpr(it)
        }
        o.blockList.forEach {
            inChildScope {
                super.visitBlock(it)
            }
        }
    }

    fun resolveDelayedReferences() {
        delayed.forEach { delayedReference ->
            val labelReference = labels.lookup(delayedReference.name)
            if (!labelReference.defined()) {
                labelReference.kind = Kind.Label
            }
            references[delayedReference.offset] = labelReference
        }
    }

    companion object {
        data class DocumentReferences(val hash: Int, val references: Map<Int, Reference>)

        private val referencesKey = Key<DocumentReferences>("LuaBindingsAnnotator.KEY_BINDINGS")
        private val logger = Logger.getInstance(LuaBindingsVisitor::class.java)

        fun generateReferences(psiFile: PsiFile): Map<Int, Reference> {
            logger.warn("indexing references in ${psiFile.name}")

            val visitor = LuaBindingsVisitor(psiFile.project, PlatformLibraryIndex.instance)
            psiFile.accept(visitor)
            visitor.resolveDelayedReferences()
            return visitor.references
        }

        fun getReferences(element: PsiElement): Map<Int, Reference> {
            val psiFile = element.containingFile
            val documentHash = getDocumentHash(psiFile)

            val existing = psiFile.getUserData(referencesKey)
            if (existing != null && documentHash == existing.hash) {
                return existing.references
            }

            val fresh = generateReferences(psiFile)
            psiFile.putUserData(referencesKey, DocumentReferences(documentHash, fresh))
            return fresh
        }

        private fun getDocumentHash(psiFile: PsiFile): Int {
            return psiFile.fileDocument.text.hashCode()
        }
    }
}
