package net.internetisalie.lunar.luacats.lang.psi

import com.intellij.psi.util.PsiTreeUtil

/**
 * The single reader of a LuaCATS tag into the `(name, type-string)` pairs the type engine consumes.
 *
 * A LuaCATS tag is read on two paths — `*StubElementType.createStub` at index time and
 * `LuaTypeManagerImpl` from live PSI — and which one runs depends only on whether the containing
 * file's AST happens to be loaded. Those two were copy-paste siblings rather than one function with
 * two callers, and they drifted three times: BUG-400 (a third copy had to be written to close it),
 * BUG-401 (the stub kept an optional field's `?` in the member key, making it unreachable) and
 * BUG-402 (the stub split a parameterized parent in half).
 *
 * The stub/AST fork itself cannot be removed — a stub is a serialized snapshot with no PSI to read —
 * but with the extraction living here it collapses to a *choice of data source*. The two arms can
 * still differ in provenance; they can no longer disagree about meaning.
 *
 * MAINT-34-05's `LuaCatsStubAstParityTest` is what keeps that true, since the drift is invisible to
 * the ordinary fixture idiom (`configureByText` reaches the AST branch, `addFileToProject` the stub
 * branch).
 */
object LuaCatsDeclarations {
    /**
     * A `@field`'s member name, the type string the engine should give it, and the tag it came from.
     *
     * [tag] is non-null on every PSI-read path and null only when the pair is rebuilt from a stub,
     * which has no PSI to point at. Callers turn that into `member.tag ?: host`, which reproduces
     * the asymmetry `LuaTypeMember.sourceElement` has always had — and that asymmetry is
     * load-bearing: `LuaOverrideLineMarkerProvider` uses `sourceElement` as gutter navigation
     * targets, so collapsing the AST path onto the host declaration would silently regress override
     * navigation.
     */
    data class FieldMember(
        val name: String,
        val typeName: String,
        val tag: LuaCatsFieldTag?,
    )

    /** Every `@field` declared on [comment], in declaration order. */
    fun fieldMembers(comment: LuaCatsComment): List<FieldMember> = comment.fieldTagList.map { fieldMember(it) }

    /**
     * The member [tag] declares.
     *
     * Two grammar facts shape this (`luacats.bnf:109-113`). A named field surfaces as `argName` with
     * the optional marker *inside its text* (`fieldNameDescriptor ::= NAME '?'?`), while a keyed
     * field surfaces as `argType` (`fieldKeyDescriptor ::= '[' type ']'`), so the fallback between
     * them is both necessary and sufficient. `fieldScope` (`private`/`protected`/`public`) is a
     * separate `argKeyword` child, so it is excluded by construction rather than by stripping.
     */
    fun fieldMember(tag: LuaCatsFieldTag): FieldMember {
        val declared = declaredDescriptor(tag)
        val declaredType = tag.argType.text
        // A key describes entries a table may hold, so no table has to supply it (BUG-556).
        val isKeyed = tag.fieldDescriptor.argName == null
        val isOptional = declared.endsWith("?") || isKeyed || hasOptionalMark(tag.description)
        if (!isOptional) return FieldMember(declared, declaredType, tag)
        // An optional field is not merely named without the marker — its type admits nil (BUG-401).
        return FieldMember(declared.removeSuffix("?"), "($declaredType) | nil", tag)
    }

    /** A tag's top-level type stops before `T?`, so the `?` opens its description (BUG-555). */
    fun hasOptionalMark(description: LuaCatsDescription?): Boolean = description?.text?.startsWith("?") == true

    /**
     * The field name as **written**, marker included — what quick-doc should render, matching LuaLS.
     *
     * This deliberately differs from [fieldMember]'s name, and lives here so that the difference is
     * an adjacent, visible choice rather than a fourth private copy free to drift (MAINT-34-07).
     */
    fun fieldDisplayName(tag: LuaCatsFieldTag): String = declaredDescriptor(tag)

    /** The descriptor text as written: a name (`beta?`) or a key (`[string]`). */
    private fun declaredDescriptor(tag: LuaCatsFieldTag): String =
        tag.fieldDescriptor.argName?.text ?: tag.fieldDescriptor.argType?.text ?: ""

    /**
     * The parent type names [tag] declares, one entry per parent — never a joined string.
     *
     * `parentTypes ::= <<ArgType parentType>> { ',' <<ArgType parentType>> }*` (`luacats.bnf:93`),
     * so the grammar has already separated them and the comma never needs re-interpreting. That is
     * the whole of BUG-402: the stub used to flatten this to `parentTypes.text` and the type manager
     * re-split it on `','`, cutting `Base<string, number>` into `Base<string` and `number>`.
     *
     * The fragments' joined `toString` reads back as the original string, so the defect is invisible
     * unless the list is counted — which is why the parity harness compares per-element renderings.
     */
    fun parentTypeNames(tag: LuaCatsClassTag): List<String> =
        tag.parentTypes
            ?.argTypeList
            .orEmpty()
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }

    /** One `@param`: its name, its type string, and whether `name?` marked it optional (BUG-554). */
    data class ParamDecl(
        val name: String,
        val typeName: String,
        val isOptional: Boolean,
    )

    /** The type string a `@type` declares. */
    fun typeName(tag: LuaCatsTypeTag): String {
        val declared = tag.argType.text.trim()
        return if (hasOptionalMark(tag.description)) "($declared) | nil" else declared
    }

    /** `@param p? T` and `@param p T?` both let a call omit `p`. */
    fun isOptionalParam(tag: LuaCatsParamTag): Boolean = tag.argSymbol?.text == "?" || hasOptionalMark(tag.description)

    /**
     * Every `@param` on [comment], in declaration order. A repeated name keeps its first position
     * and its last tag, which is the order and the winner the former name → type map had.
     */
    fun params(comment: LuaCatsComment): List<ParamDecl> =
        comment.paramTagList
            .map { ParamDecl(it.argName?.text ?: "", it.argType.text, isOptionalParam(it)) }
            .associateBy { it.name }
            .values
            .toList()

    /**
     * The first declared `@return`'s type string, or null.
     *
     * Only the first: the nominal layer models a single return (`LuaFunctionType.returnType`).
     * Resolving `---@return self` to the receiver class stays with the caller, since that needs the
     * class name — a type-engine concern, not a tag-reading one.
     */
    fun returnTypeName(comment: LuaCatsComment): String? =
        comment.returnTagList
            .flatMap { it.returnTypeDescriptorList }
            .firstOrNull()
            ?.argType
            ?.text

    /** One `@generic` parameter: its name and the text after `:` of `T : Base`, or null. */
    data class GenericParameter(
        val name: String,
        val boundText: String?,
    )

    /** Every `@generic` parameter on [comment], in declaration order; a later duplicate name overwrites. */
    fun genericParameters(comment: LuaCatsComment): List<GenericParameter> =
        comment.genericTagList
            .flatMap { it.genericTypeParams?.genericTypeParamList.orEmpty() }
            .map { GenericParameter(it.argName.text, it.argType?.text?.trim()) }
            .associateBy { it.name }
            .values
            .toList()

    /**
     * The class name the indexes key on: `Box` for `---@class Box<T>`, the trimmed head text
     * otherwise, and empty for a partial tag with no name. `typeName ::= parameterizedName | NAME`.
     */
    fun classHeadName(tag: LuaCatsClassTag): String {
        val argType = PsiTreeUtil.getChildOfType(tag, LuaCatsArgType::class.java) ?: return ""
        val parameterized = PsiTreeUtil.getChildOfType(argType, LuaCatsParameterizedName::class.java)
        return (parameterized?.genericType?.text ?: argType.text).trim()
    }

    /**
     * The head's own `<T, U>` names, in order; empty for a plain head. A nested type in a head
     * (`---@class Box<string[]>`) is not a parameter and is ignored.
     */
    fun classTypeParameterNames(tag: LuaCatsClassTag): List<String> {
        val argType = PsiTreeUtil.getChildOfType(tag, LuaCatsArgType::class.java) ?: return emptyList()
        val parameterized = PsiTreeUtil.getChildOfType(argType, LuaCatsParameterizedName::class.java)
        return parameterized?.typeParamList.orEmpty().map { it.text.trim() }
    }

    /** The first `@alias`'s target type string, or null. */
    fun aliasTarget(comment: LuaCatsComment): String? =
        comment.aliasTagList
            .firstOrNull()
            ?.argType
            ?.text
}
