/*
* Copyright 2000-2009 JetBrains s.r.o.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package net.internetisalie.lunar.luadoc.lang.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.jetbrains.annotations.NonNls

/**
 * @author ilyas
 */
class LuaDocParser : PsiParser {
    private fun parseDataItem(builder: PsiBuilder): Boolean {
        if (timeToEnd(builder)) return false

        val tokenType = builder.tokenType
        if (LuaDocElementTypes.LDOC_TAG_NAME === tokenType) {
            parseTag(builder)
        } else {
            builder.advanceLexer()
        }

        return true
    }

    private fun parseTag(builder: PsiBuilder): Boolean {
        val marker = builder.mark()

        assert(builder.tokenType === LuaDocElementTypes.LDOC_TAG_NAME)
        val tagName = builder.tokenText
        builder.advanceLexer()

        if (SEE_TAG == tagName) {
            parseSeeOrLinkTagReference(builder)
        } else if (PARAM_TAG == tagName) {
            parseParamTagReference(builder)
        } else if (FIELD_TAG == tagName) {
            parseFieldReference(builder)
        } else if (NAME_TAG == tagName) {
            parseNameReference(builder)
        } else if (builder.tokenType is LuaDocTagValueTokenType) {
            parseTagValue(builder)
        }

        var lastdata = builder.mark()
        val start = builder.currentOffset
        while (!timeToEnd(builder)) {
            if (LuaDocElementTypes.LDOC_TAG_NAME === builder.tokenType && builder.currentOffset != start) {
                lastdata.rollbackTo()
                marker.done(LuaDocElementTypes.LDOC_TAG)
                return true
            } else if (LuaDocElementTypes.LDOC_COMMENT_DATA === builder.tokenType) {
                lastdata.drop()
                builder.advanceLexer()
                lastdata = builder.mark()
            } else {
                builder.advanceLexer()
            }
        }
        lastdata.drop()
        marker.done(LuaDocElementTypes.LDOC_TAG)

        return true
    }

    private fun parseTagValue(builder: PsiBuilder) : Boolean {
        val marker = builder.mark()
        builder.advanceLexer()
        marker.done(LuaDocElementTypes.LDOC_TAG_VALUE)
        return true
    }

    private fun parseNameReference(builder: PsiBuilder): Boolean {
        val marker = builder.mark()
        if (LuaDocElementTypes.LDOC_TAG_VALUE === builder.tokenType) {
            parseTagValue(builder)
            marker.done(LuaDocElementTypes.LDOC_REFERENCE_ELEMENT)
            return true
        }
        marker.drop()
        return false
    }

    private fun parseParamTagReference(builder: PsiBuilder): Boolean {
        val marker = builder.mark()
        if (LuaDocElementTypes.LDOC_TAG_VALUE === builder.tokenType) {
            parseTagValue(builder)
            marker.done(LuaDocElementTypes.LDOC_PARAM_REF)
            return true
        }
        marker.drop()
        return false
    }

    private fun parseSeeOrLinkTagReference(builder: PsiBuilder): Boolean {
//        IElementType type = builder.getTokenType();
//        if (!REFERENCE_BEGIN.contains(type)) return false;
//        PsiBuilder.Marker marker = builder.mark();
//        if (LDOC_TAG_VALUE == type) {
//            builder.advanceLexer();
//        }
        val marker = builder.mark()
        if (LuaDocElementTypes.LDOC_TAG_VALUE === builder.tokenType) {
            parseTagValue(builder)
            marker.done(LuaDocElementTypes.LDOC_REFERENCE_ELEMENT)
            return true
        }

        marker.drop()
        return true
    }


    private fun parseFieldReference(builder: PsiBuilder): Boolean {
        val marker = builder.mark()
        if (LuaDocElementTypes.LDOC_TAG_VALUE === builder.tokenType) {
            parseTagValue(builder)
            marker.done(LuaDocElementTypes.LDOC_FIELD_REF)
            return true
        }
        marker.drop()
        return false
    }

    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val rootMarker = builder.mark()
        while (parseDataItem(builder)) {
            /* empty */
        }
        rootMarker.done(root)
        return builder.treeBuilt
    }

    companion object {
        private const val SEE_TAG: @NonNls String = "@see"
        private const val PARAM_TAG: @NonNls String = "@param"
        private const val FIELD_TAG: @NonNls String = "@field"
        private const val NAME_TAG: @NonNls String = "@name"
        private const val CLASS_TAG: @NonNls String = "@class"
        private const val RETVAL_TAG: @NonNls String = "@retval"

        private val REFERENCE_BEGIN = TokenSet.create(LuaDocElementTypes.LDOC_TAG_VALUE)

        private fun timeToEnd(builder: PsiBuilder): Boolean {
            return builder.eof()
        }
    }
}
