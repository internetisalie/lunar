package net.internetisalie.lunar.lang.schema

import com.intellij.json.pointer.JsonPointerPosition
import com.intellij.psi.PsiElement
import com.intellij.util.ThreeState
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter

class LuaJsonLikePsiWalker : JsonLikePsiWalker {
    companion object {
        val INSTANCE: LuaJsonLikePsiWalker = LuaJsonLikePsiWalker()
    }

    override fun isName(element: PsiElement): ThreeState = ThreeState.NO

    override fun isPropertyWithValue(element: PsiElement): Boolean = false

    override fun isTopJsonElement(element: PsiElement): Boolean = false

    override fun requiresNameQuotes(): Boolean = false

    override fun isQuotedString(element: PsiElement): Boolean = false

    override fun findElementToCheck(element: PsiElement): PsiElement? = null

    override fun createValueAdapter(element: PsiElement): JsonValueAdapter? = null

    override fun hasMissingCommaAfter(element: PsiElement): Boolean = false

    override fun getParentPropertyAdapter(element: PsiElement): JsonPropertyAdapter? = null

    override fun getPropertyNamesOfParentObject(
        originalPosition: PsiElement,
        computedPosition: PsiElement?
    ): Set<String> = emptySet()

    override fun findPosition(element: PsiElement, forceLastTransition: Boolean): JsonPointerPosition? = null

    override fun requiresValueQuotes(): Boolean = false

    override fun allowsSingleQuotes(): Boolean = true

    override fun acceptsEmptyRoot(): Boolean = true

    override fun getDefaultObjectValue(): String = "{}"

    override fun getDefaultArrayValue(): String = "{}"
}
