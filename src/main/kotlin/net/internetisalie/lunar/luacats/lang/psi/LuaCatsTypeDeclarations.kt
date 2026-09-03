package net.internetisalie.lunar.luacats.lang.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.FileBasedIndex
import net.internetisalie.lunar.lang.indexing.LuaCatsTypeNameIndex
import net.internetisalie.lunar.lang.psi.LuaFile

/**
 * The single reader of a LuaCATS type-name **slot** — which leaf spells a type name, and is that
 * spelling a declaration or a use (`REFACT-08` design.md §2.1, §3.1-§3.4, §3.11).
 *
 * Disjoint from [LuaCatsDeclarations], which answers *what a tag means* and returns strings for the
 * type engine's stub/AST readers. This answers *which PSI leaf spells a name*, because a rename must
 * write one. Neither reads the other.
 *
 * A type name is spelled in five grammar slots (`luacats.bnf`): two declarations — a `@class` tag's
 * [LuaCatsArgType] (`classTag ::= '@class' … <<ArgType typeName>> …`, `:91`) and an `@alias` tag's
 * [LuaCatsArgName] (`aliasTag ::= '@alias' <<ArgName NAME>> …`, `:82`) — and three uses, each
 * `::= NAME`: [LuaCatsNamedType] (`:207`), [LuaCatsTypeParam] (`:203`) and [LuaCatsGenericType]
 * (`:202`).
 *
 * [LuaCatsTypeParam] is reached from **two** grammar rules and is a declaration in one and a use in
 * the other, and the NAME leaf's immediate parent cannot tell them apart — both are `TYPE_PARAM`.
 * It is the *grandparent* that separates a parameterized class head's own parameter
 * (`parameterizedName ::= genericType '<' typeParam … '>'`, `:201`, reached when the enclosing
 * `parameterizedName` is itself a `@class` tag's `ArgType`) from a `@generic` declaration
 * (`genericTypeParam ::= <<ArgName typeParam>> …`, `:117`) — both function/class-local declarations,
 * neither a project type. [isDeclarationSlotHolder] is that discrimination, executed against both
 * shapes plus every use control in `REFACT-08-00-DR-04` P-B.
 */
object LuaCatsTypeDeclarations {
    /** The `builtinType` alternative of `luacats.bnf:206`, verbatim. */
    val BUILTIN_KEYWORDS: Set<String> =
        setOf(
            "nil",
            "any",
            "boolean",
            "string",
            "number",
            "integer",
            "function",
            "table",
            "thread",
            "userdata",
            "lightuserdata",
        )

    /**
     * The NAME leaf a `@class` tag's own name is spelled by, or null when the head is
     * parameterized (`---@class Box<T>`).
     *
     * `typeName ::= parameterizedName | NAME` (`:94`) gives the tag's [LuaCatsArgType] exactly one
     * child. **The element-type test on that child is the whole exclusion** — do not add a separate
     * `LuaCatsParameterizedName` guard in front of it; it would be dead code (`REFACT-08-00-DR-02`
     * mutation G / G3). Excluding a parameterized head matters because [LuaCatsTypeNameIndex] keys
     * the tag under the whole text (`Box<T>`), so admitting the head as a declaration leaf would
     * make [declarationLeaves] of the bare name find nothing.
     */
    fun classDeclarationLeaf(tag: LuaCatsClassTag): PsiElement? {
        val argType = PsiTreeUtil.getChildOfType(tag, LuaCatsArgType::class.java) ?: return null
        val first = argType.node.firstChildNode ?: return null
        return first.psi.takeIf { first.elementType == LuaCatsElementTypes.NAME }
    }

    /** The NAME leaf an `@alias` tag's own name is spelled by. `ArgName ::= <<child>>` (`:43`). */
    fun aliasDeclarationLeaf(tag: LuaCatsAliasTag): PsiElement? {
        val argName = PsiTreeUtil.getChildOfType(tag, LuaCatsArgName::class.java) ?: return null
        val first = argName.node.firstChildNode ?: return null
        return first.psi.takeIf { first.elementType == LuaCatsElementTypes.NAME }
    }

    /**
     * True for the NAME leaf of a `@class` or `@alias` tag's own name, written as a round trip
     * against [classDeclarationLeaf] / [aliasDeclarationLeaf] rather than as a shape enumeration —
     * so it cannot fall behind them. False for every other NAME leaf, including every residue class
     * `REFACT-08-00-DR-01` measured: a `@param` name and a `@cast` name (both under a
     * [LuaCatsArgName], but under the wrong tag), a `@field` name (under a
     * [LuaCatsFieldNameDescriptor], not an `ArgName` at all) and a `@generic` parameter (under a
     * [LuaCatsTypeParam], not an `ArgType`/`ArgName` slot).
     */
    fun isDeclarationLeaf(element: PsiElement): Boolean {
        if (element.node?.elementType != LuaCatsElementTypes.NAME) return false
        val slot = element.parent ?: return false
        val tag = slot.parent ?: return false
        return when {
            slot is LuaCatsArgType && tag is LuaCatsClassTag -> classDeclarationLeaf(tag) === element
            slot is LuaCatsArgName && tag is LuaCatsAliasTag -> aliasDeclarationLeaf(tag) === element
            else -> false
        }
    }

    /**
     * True when [holder] is a declaration of a function- or class-local type **parameter**, not of
     * a project type — the `T` of `---@generic T`, or anything inside the parameterized name a
     * `@class` tag itself declares (both the `Box` head and every `T` of `---@class Box<T>`).
     *
     * Both clauses are grandparent-deep, matching the discrimination [LuaCatsTypeParam]'s two
     * grammar rules force (class KDoc). Clause 2 stops at the class tag's own `ArgType`
     * deliberately: a generic head in a **parent-type** position (`---@class Panel : Box<string>`)
     * sits under `ParentTypes`, not directly under `ArgType`'s tag, so it is a use and this returns
     * false for it (`REFACT-08-00-DR-03` G3's control).
     */
    fun isDeclarationSlotHolder(holder: PsiElement): Boolean {
        val parent = holder.parent ?: return false
        if (holder is LuaCatsTypeParam && parent is LuaCatsArgName) return true
        val grandparent = parent.parent
        val greatGrandparent = grandparent?.parent
        return parent is LuaCatsParameterizedName &&
            grandparent is LuaCatsArgType &&
            greatGrandparent is LuaCatsClassTag
    }

    /**
     * Every type-parameter name [comment] declares — both a `@generic` parameter and a
     * parameterized class tag's own parameters — so that a use of the same spelling elsewhere in
     * the same comment can be recognised as bound to the parameter rather than to a project type
     * sharing the spelling (`REFACT-08-17`).
     *
     * [LuaCatsComment] is the scope because a `@generic` tag or a parameterized `@class` tag and the
     * tags they govern are siblings under one comment; a same-spelled parameter in an unrelated
     * comment must not shadow anything (`REFACT-08-00-DR-04` P-E's control).
     */
    fun shadowedTypeParameterNames(comment: LuaCatsComment): Set<String> =
        PsiTreeUtil
            .findChildrenOfType(comment, LuaCatsTypeParam::class.java)
            .filter(::isDeclarationSlotHolder)
            .mapTo(mutableSetOf()) { it.text }

    /**
     * The use holder above [element] — one of the three NAME leaf's parents of the class KDoc — or
     * null when [element] is not a NAME leaf, its holder is a declaration slot
     * ([isDeclarationSlotHolder]), or its text names a type parameter [shadowedTypeParameterNames]
     * declares for the same comment.
     */
    fun useHolderOf(element: PsiElement): PsiElement? {
        if (element.node?.elementType != LuaCatsElementTypes.NAME) return null
        val holder = element.parent ?: return null
        return holder.takeIf(::isUnshadowedUseHolder)
    }

    /**
     * The inverse of [useHolderOf]: the NAME leaf under [holder], or null when [holder] is not one
     * of the three use holders, is a declaration slot, or is shadowed — the same three exclusions,
     * asked from the holder side so [LuaCatsTypeReferenceContributor] (design §2.4) has exactly one
     * clause to call rather than a second copy of the guard.
     */
    fun useLeafOf(holder: PsiElement): PsiElement? {
        if (!isUnshadowedUseHolder(holder)) return null
        val first = holder.node?.firstChildNode ?: return null
        return first.psi.takeIf { first.elementType == LuaCatsElementTypes.NAME }
    }

    private fun isUnshadowedUseHolder(holder: PsiElement): Boolean {
        if (holder !is LuaCatsNamedType && holder !is LuaCatsTypeParam && holder !is LuaCatsGenericType) return false
        if (isDeclarationSlotHolder(holder)) return false
        val comment = PsiTreeUtil.getParentOfType(holder, LuaCatsComment::class.java) ?: return true
        return holder.text !in shadowedTypeParameterNames(comment)
    }

    /**
     * Every declaration leaf spelling [name] within [scope] — a **list**, not a single element,
     * because LuaCATS allows a class to be re-opened. Reuses [LuaCatsTypeNameIndex] (already keyed
     * "name → declaration file" for Go-to-Class and quick-doc) rather than building a second map;
     * this is the only extra step, walking each candidate file's tags to the leaf a rename can
     * write.
     */
    fun declarationLeaves(
        name: String,
        project: Project,
        scope: GlobalSearchScope,
    ): List<PsiElement> {
        val psiManager = PsiManager.getInstance(project)
        val index = FileBasedIndex.getInstance()
        val result = mutableListOf<PsiElement>()
        for (virtualFile in index.getContainingFiles(LuaCatsTypeNameIndex.KEY, name, scope)) {
            val luaFile = psiManager.findFile(virtualFile) as? LuaFile ?: continue
            PsiTreeUtil.findChildrenOfType(luaFile, LuaCatsClassTag::class.java).forEach { tag ->
                val leaf = classDeclarationLeaf(tag)
                if (leaf != null && leaf.text == name) result += leaf
            }
            PsiTreeUtil.findChildrenOfType(luaFile, LuaCatsAliasTag::class.java).forEach { tag ->
                val leaf = aliasDeclarationLeaf(tag)
                if (leaf != null && leaf.text == name) result += leaf
            }
        }
        return result
    }

    /**
     * The presentable path of every file outside [project]'s own [GlobalSearchScope.projectScope]
     * that also declares [name] — a bundled runtime stub, a rock, a fetched definitions tree.
     *
     * Resolution reads [GlobalSearchScope.allScope] because [declarationLeaves] must agree with
     * `LuaTypeManagerImpl`'s own resolution scope; the **rewrite** may write only `projectScope`
     * (design §3.11). A non-empty result here is what `substituteElementToRename` refuses on,
     * before any write.
     */
    fun outOfProjectDeclarationFiles(
        name: String,
        project: Project,
    ): List<String> {
        val projectScope = GlobalSearchScope.projectScope(project)
        return declarationLeaves(name, project, GlobalSearchScope.allScope(project))
            .mapNotNull { it.containingFile?.virtualFile }
            .filterNot { projectScope.contains(it) }
            .map { it.presentableUrl }
            .distinct()
    }
}
