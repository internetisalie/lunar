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

package net.internetisalie.lunar.luadoc.lang.parser;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.*;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import net.internetisalie.lunar.lang.LuaLanguage;
import net.internetisalie.lunar.luadoc.lang.lexer.LuaDocElementType;
import net.internetisalie.lunar.luadoc.lang.lexer.LuaDocLexer;
import net.internetisalie.lunar.luadoc.lang.lexer.LuaDocTokenTypes;
import net.internetisalie.lunar.luadoc.lang.psi.impl.*;
import org.jetbrains.annotations.NotNull;

import static net.internetisalie.lunar.luadoc.lang.parser.LuaDocTagValueTokenType.TagValueTokenType.REFERENCE_ELEMENT;


/**
 * @author ilyas
 */
public interface LuaDocElementTypes extends LuaDocTokenTypes {

    /**
     * LuaDoc comment
     */
    ILazyParseableElementType LUADOC_COMMENT = new ILazyParseableElementType("LuaDocComment") {
        @NotNull
        public Language getLanguage() {
            return LuaLanguage.INSTANCE;
        }

        public ASTNode parseContents(ASTNode chameleon) {
            final PsiElement parentElement = chameleon.getTreeParent().getPsi();
            assert parentElement != null;

            final Project project = parentElement.getProject();
            final PsiParser parser = new LuaDocParser();
            final Lexer lexer = new LuaDocLexer();

            final PsiBuilder builder = PsiBuilderFactory.getInstance().createBuilder(project, chameleon, lexer, getLanguage(), chameleon.getText());
            return parser.parse(this, builder).getFirstChildNode();
        }

        @Override
        public ASTNode createNode(CharSequence text) {
            return new LuaDocCommentImpl(text);
        }
    };

    LuaDocElementType LDOC_TAG = new LuaDocElementType("LuaDocTag");

    LuaDocElementType LDOC_REFERENCE_ELEMENT = new LuaDocElementType("LuaDocReferenceElement");
    LuaDocElementType LDOC_PARAM_REF = new LuaDocElementType("LuaDocParameterReference");
    LuaDocElementType LDOC_FIELD_REF = new LuaDocElementType("LuaDocFieldReference");


    class Factory {
        public static PsiElement createElement(ASTNode node) {
            IElementType type = node.getElementType();

            if (type instanceof LuaDocTagValueTokenType) {
                LuaDocTagValueTokenType value = (LuaDocTagValueTokenType) type;
                LuaDocTagValueTokenType.TagValueTokenType valueType = value.getValueType(node);
                if (valueType == REFERENCE_ELEMENT) return new LuaDocSymbolReferenceElementImpl(node);

                return new LuaDocTagValueImpl(node);
            }

            if (type == LDOC_TAG) return new LuaDocTagImpl(node);
            if (type == LDOC_FIELD_REF) return new LuaDocFieldReferenceImpl(node);
            if (type == LDOC_PARAM_REF) return new LuaDocParameterReferenceImpl(node);
            if (type == LDOC_REFERENCE_ELEMENT) return new LuaDocSymbolReferenceElementImpl(node);

            return new ASTWrapperPsiElement(node);
        }
    }
}
