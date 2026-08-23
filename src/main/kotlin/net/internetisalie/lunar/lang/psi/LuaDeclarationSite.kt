package net.internetisalie.lunar.lang.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

/**
 * What a Lua declaration site declares, and how widely its name is visible.
 *
 * [usageViewType] strings are the ones
 * [net.internetisalie.lunar.lang.insight.LuaFindUsagesProvider] has always reported.
 * [isFileLocal] is what a search scope is narrowed by: a Lua global is `_ENV.x` and therefore
 * visible in every file.
 */
enum class LuaDeclarationKind(
    val usageViewType: String,
    val isFileLocal: Boolean,
) {
    LOCAL_VARIABLE("local variable", true),
    PARAMETER("parameter", true),
    NUMERIC_FOR_VARIABLE("local variable", true),
    GENERIC_FOR_VARIABLE("local variable", true),
    LOCAL_FUNCTION("local function", true),
    GLOBAL_VARIABLE("global variable", false),
    GLOBAL_FUNCTION("global function", false),
    DOTTED_FUNCTION("global function", false),
    METHOD_FUNCTION("global function", false),
    LABEL("label", true),
}

/**
 * The single classifier and normaliser for Lua declaration sites (REFACT-01, design §2.1/§3.5).
 *
 * Lunar models no declaration PSI: apart from `::labels::` every declared name is a [LuaNameRef]
 * in a particular parent container, so "is this a declaration?" is a question about shape.
 * Find Usages, Safe Delete, reference search and rename all have to answer it identically, and
 * before this object each kept its own copy of the rule.
 *
 * All members are pure PSI reads; callers hold read access. No state, no cached `Project`.
 */
object LuaDeclarationSite {
    /** The kind of declaration [element] names, or null when it is not a declaration site. */
    fun kindOf(element: PsiElement): LuaDeclarationKind? {
        if (element is LuaLabelName) return LuaDeclarationKind.LABEL
        if (element.node?.elementType != LuaElementTypes.IDENTIFIER) return null
        val parent = element.parent ?: return null
        kindFromLeafParent(parent)?.let { return it }
        if (parent !is LuaNameRef) return null
        return kindFromNameRefGrandParent(parent.parent ?: return null)
    }

    /**
     * The IDENTIFIER leaf that names the declaration [element] is part of, or null when [element]
     * names no declaration. Total over both directions: a leaf maps to itself, a declaration node
     * or a [LuaNameRef] composite maps down to its leaf.
     */
    fun identifierLeafOf(element: PsiElement): PsiElement? =
        when {
            element is LuaLabelName -> element.identifier
            kindOf(element) != null -> element
            element is LuaNameRef -> element.identifier.takeIf { kindOf(it) != null }
            element is LuaAttName -> element.nameRef.identifier
            element is LuaLocalVarDecl ->
                element.attNameList
                    .firstOrNull()
                    ?.nameRef
                    ?.identifier
            element is LuaGlobalVarDecl ->
                element.attNameList
                    .firstOrNull()
                    ?.nameRef
                    ?.identifier
            element is LuaLocalFuncDecl -> element.nameRef.identifier
            element is LuaGlobalFuncDecl -> element.nameRef?.identifier
            element is LuaFuncDecl -> functionNameLeafOf(element.funcName)
            element is LuaAssignmentStatement ->
                element.varList.varList
                    .singleOrNull()
                    ?.nameRef
                    ?.identifier
            element is LuaVar -> element.nameRef?.identifier
            else -> null
        }

    /**
     * The whole-declaration node a caret leaf belongs to — what Safe Delete removes, so that a
     * declaration goes away as a statement rather than as a bare token. Falls back to [element]
     * for the kinds that have no statement-level container (parameters, `for` variables, labels)
     * and for anything that is not a declaration at all.
     */
    fun declarationNodeOf(element: PsiElement): PsiElement {
        val parent = element.parent
        if (parent !is LuaNameRef) return element
        return when (val grandParent = parent.parent) {
            is LuaAttName -> attNameDeclarationNode(grandParent)
            is LuaLocalFuncDecl -> grandParent
            is LuaGlobalFuncDecl -> grandParent
            is LuaFuncName -> grandParent.parent as? LuaFuncDecl ?: grandParent
            is LuaFuncNameProperty -> grandParent.parent?.parent as? LuaFuncDecl ?: grandParent
            is LuaFuncNameMethod -> grandParent.parent?.parent as? LuaFuncDecl ?: grandParent
            is LuaVar -> assignmentDeclarationNode(grandParent) ?: element
            else -> element
        }
    }

    /**
     * The IDENTIFIER leaf that names the function whose name chain is [funcName] — its LAST
     * segment: the `funcNameMethod` if present, else the last `funcNameProperty`, else the bare
     * `nameRef`. One rule, three callers: [identifierLeafOf], the rename processor's
     * receiver-segment guard, and
     * [net.internetisalie.lunar.lang.LuaNameReference]'s declaration normalisation.
     */
    fun functionNameLeafOf(funcName: LuaFuncName): PsiElement =
        funcName.funcNameMethod?.nameRef?.identifier
            ?: funcName.funcNamePropertyList
                .lastOrNull()
                ?.nameRef
                ?.identifier
            ?: funcName.nameRef.identifier

    /**
     * True when [target] is an undotted target of a file-scope assignment — the shape half of a
     * bare global declaration, with no name set consulted. O(1), so an indexer can call it once
     * per assignment target.
     *
     * The file-scope test is the O(1) restatement of "the statement is one of
     * `containingFile.blockList.flatMap { it.statementList }`": both `LuaFile.getBlockList` and
     * `LuaBlock.getStatementList` enumerate DIRECT children only.
     */
    fun isBareAssignmentTarget(target: LuaVar): Boolean {
        if (target.varSuffixList.isNotEmpty()) return false
        val statement = (target.parent as? LuaVarList)?.parent as? LuaAssignmentStatement ?: return false
        val block = statement.parent as? LuaBlock ?: return false
        return block.parent is LuaFile
    }

    /**
     * True when [target] declares a global: [isBareAssignmentTarget], and the name is not also
     * bound by a file-scope `local`, which would make the assignment a local write instead.
     */
    fun isGlobalAssignmentTarget(target: LuaVar): Boolean {
        if (!isBareAssignmentTarget(target)) return false
        val name = target.nameRef?.text ?: return false
        val luaFile = target.containingFile as? LuaFile ?: return false
        return name !in fileScopeLocalNames(luaFile)
    }

    /**
     * Names bound by a file-scope `local` / `local function`, cached per file because
     * [isGlobalAssignmentTarget] is reached on the EDT once per classification.
     */
    internal fun fileScopeLocalNames(file: LuaFile): Set<String> =
        CachedValuesManager.getCachedValue(file) {
            CachedValueProvider.Result.create(computeFileScopeLocalNames(file), file)
        }

    /**
     * The uncached body of [fileScopeLocalNames]. Published because
     * [net.internetisalie.lunar.lang.indexing.LuaGlobalAssignmentIndex]'s indexer must call it
     * instead: it runs on the indexing thread over a non-physical `FileContent` PSI copy that is
     * discarded after the run, so a `CachedValuesManager` round trip buys nothing there.
     */
    internal fun computeFileScopeLocalNames(file: LuaFile): Set<String> {
        val names = mutableSetOf<String>()
        file.getBlockList().flatMap { it.statementList }.forEach { statement ->
            when (statement) {
                is LuaLocalVarDecl ->
                    statement.attNameList.forEach { attName ->
                        boundName(attName)?.let { names += it }
                    }
                is LuaLocalFuncDecl -> boundName(statement)?.let { names += it }
                else -> Unit
            }
        }
        return names
    }

    /**
     * The bound name of [declaration], read through the AST node rather than the generated getter.
     *
     * SYNTAX-18: `LuaLocalFuncDecl.getNameRef()` is declared `@NotNull` but returns null for a
     * partially parsed decl — `local function repeat(...)`, where a keyword sits in the name slot
     * — and the platform *logs an error* rather than returning null, which surfaces as a
     * `TestLoggerAssertionError` in any test that indexes such a file.
     */
    internal fun boundName(declaration: PsiElement): String? =
        declaration.node
            .findChildByType(LuaElementTypes.NAME_REF)
            ?.psi
            ?.text

    private fun kindFromLeafParent(parent: PsiElement): LuaDeclarationKind? =
        if (parent is LuaNumericForStatement) LuaDeclarationKind.NUMERIC_FOR_VARIABLE else null

    private fun kindFromNameRefGrandParent(grandParent: PsiElement): LuaDeclarationKind? =
        when {
            grandParent is LuaAttName && grandParent.parent is LuaGlobalVarDecl -> LuaDeclarationKind.GLOBAL_VARIABLE
            grandParent is LuaAttName -> LuaDeclarationKind.LOCAL_VARIABLE
            grandParent is LuaGlobalFuncDecl -> LuaDeclarationKind.GLOBAL_FUNCTION
            grandParent is LuaLocalFuncDecl -> LuaDeclarationKind.LOCAL_FUNCTION
            grandParent is LuaFuncName -> LuaDeclarationKind.GLOBAL_FUNCTION
            grandParent is LuaFuncNameProperty -> LuaDeclarationKind.DOTTED_FUNCTION
            grandParent is LuaFuncNameMethod -> LuaDeclarationKind.METHOD_FUNCTION
            grandParent is LuaNameList && grandParent.parent is LuaParList -> LuaDeclarationKind.PARAMETER
            grandParent is LuaNameList &&
                grandParent.parent is LuaGenericForStatement -> LuaDeclarationKind.GENERIC_FOR_VARIABLE
            grandParent is LuaVar -> kindFromAssignmentTarget(grandParent)
            else -> null
        }

    private fun kindFromAssignmentTarget(grandParent: LuaVar): LuaDeclarationKind? =
        if (isGlobalAssignmentTarget(grandParent)) LuaDeclarationKind.GLOBAL_VARIABLE else null

    private fun attNameDeclarationNode(attName: LuaAttName): PsiElement =
        when (val owner = attName.parent) {
            is LuaLocalVarDecl -> if (owner.attNameList.size == 1) owner else attName
            is LuaGlobalVarDecl -> if (owner.attNameList.size == 1) owner else attName
            else -> attName
        }

    private fun assignmentDeclarationNode(target: LuaVar): PsiElement? {
        if (!isGlobalAssignmentTarget(target)) return null
        val statement = (target.parent as? LuaVarList)?.parent as? LuaAssignmentStatement ?: return target
        return if (statement.varList.varList.size == 1) statement else target
    }
}
