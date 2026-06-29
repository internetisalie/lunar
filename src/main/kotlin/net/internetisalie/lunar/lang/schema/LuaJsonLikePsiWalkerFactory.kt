package net.internetisalie.lunar.lang.schema

import com.intellij.psi.PsiElement
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalkerFactory
import net.internetisalie.lunar.lang.LuaLanguage

class LuaJsonLikePsiWalkerFactory : JsonLikePsiWalkerFactory {
    override fun handles(element: PsiElement): Boolean = element.language === LuaLanguage.INSTANCE

    override fun create(element: PsiElement?): JsonLikePsiWalker = LuaJsonLikePsiWalker.INSTANCE
}
