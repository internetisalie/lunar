/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package net.internetisalie.lunar.luacats.lang.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import static net.internetisalie.lunar.luacats.lang.lexer.LuaCatsTokenTypes.*;

%%

%class _LuaCatsLexer
%implements FlexLexer

%unicode
%public

%function advance
%type IElementType

NL=\r?\n
WHITESPACE=[ \t\f]
NOT_WHITESPACE=[^ \t\f\r\n]

UNDERSCORE="_"
PERIOD="."
COLON=":"
PIPE="|"
COMMA=","
ELLIPSIS="..."
MINUS="-"
ASTERISK="*"
DASHES=---+
QUESTION="?"
LT="<"
GT=">"
LPAREN="("
RPAREN=")"
PLUS="+"
POUND="#"
LBRACE="{"
RBRACE="}"
LBRACKET="["
RBRACKET="]"
BACKTICK="`"
APOS="'"
QUOTE=\"
ARRAY="[]"

DIGIT=[0-9]
INT={DIGIT}+
FLOAT={DIGIT}+"."{DIGIT}*|{DIGIT}*"."{DIGIT}+
NUMBER={INT}|{FLOAT}
ALPHA_LOWER=[a-z]
ALPHA_UPPER=[A-Z]
UNICODE_LETTER=[:letter:]

NAME_LEADING={ALPHA_LOWER}|{ALPHA_UPPER}|{UNDERSCORE}|{UNICODE_LETTER}
NAME_TRAILING={ALPHA_LOWER}|{ALPHA_UPPER}|{DIGIT}|{UNDERSCORE}|{UNICODE_LETTER}|{PERIOD}|{MINUS}|{ASTERISK}
CODE={BACKTICK}[^`\r\n]+{BACKTICK}
NAME=({NAME_LEADING}{NAME_TRAILING}*)|({DIGIT}+{NAME_TRAILING}+)
TYPES={LT}|{GT}|{ARRAY}|{QUESTION}|{PIPE}|{LBRACE}|{RBRACE}|{LBRACKET}|{RBRACKET}|{LPAREN}|{RPAREN}|{COLON}|{COMMA}
DIAGNOSTIC={ALPHA_LOWER}+(-({ALPHA_LOWER}|{DIGIT})+)*
STRINGS={APOS}{NAME}{APOS}
STRINGD={QUOTE}[^\"\r\n]*{QUOTE}
STRINGV=("5.1"|"5.2"|"5.3"|"5.4"|"JIT")

%state COMMENT_START, COMMENT_DATA
%state TAG_CAST, TAG_CLASS, TAG_DIAGNOSTIC, TAG_ENUM, TAG_FIELD, TAG_META, TAG_MODULE, TAG_OPERATOR
%state TAG_PARAM, TAG_RETURN, TAG_SEE, TAG_SOURCE, TAG_TYPE, TAG_VERSION
%state OPTION

%%

{WHITESPACE}+               { return LCATS_WHITESPACE; }
{NL}                        { yybegin(YYINITIAL); return LCATS_WHITESPACE; }

<YYINITIAL> {
    {DASHES}                { yybegin(COMMENT_START); return LCATS_DASHES; }
    .                       { return LCATS_BAD_CHARACTER; }
}

<COMMENT_START> {
    "@alias"                { yybegin(TAG_TYPE); return LCATS_TAG; }
    "@async"                { yybegin(COMMENT_DATA); return LCATS_TAG; }
    "@cast"                 { yybegin(TAG_CAST); return LCATS_TAG; }
    "@class"                { yybegin(TAG_CLASS); return LCATS_TAG; }
    "@deprecated"           { yybegin(COMMENT_DATA); return LCATS_TAG; }
    "@diagnostic"           { yybegin(TAG_DIAGNOSTIC); return LCATS_TAG; }
    "@enum"                 { yybegin(TAG_ENUM); return LCATS_TAG; }
    "@field"                { yybegin(TAG_FIELD); return LCATS_TAG; }
    "@generic"              { yybegin(TAG_TYPE); return LCATS_TAG; }
    "@meta"                 { yybegin(TAG_META); return LCATS_TAG; }
    "@module"               { yybegin(TAG_MODULE); return LCATS_TAG; }
    "@nodiscard"            { yybegin(COMMENT_DATA); return LCATS_TAG; }
    "@operator"             { yybegin(TAG_OPERATOR); return LCATS_TAG; }
    "@overload"             { yybegin(TAG_TYPE); return LCATS_TAG; }
    "@package"              { yybegin(COMMENT_DATA); return LCATS_TAG; }
    "@param"                { yybegin(TAG_PARAM); return LCATS_TAG; }
    "@private"              { yybegin(COMMENT_DATA); return LCATS_TAG; }
    "@protected"            { yybegin(COMMENT_DATA); return LCATS_TAG; }
    "@return"               { yybegin(TAG_RETURN); return LCATS_TAG; }
    "@see"                  { yybegin(TAG_SEE); return LCATS_TAG; }
    "@source"               { yybegin(TAG_SOURCE); return LCATS_TAG; }
    "@type"                 { yybegin(TAG_TYPE); return LCATS_TAG; }
    "@vararg"               { yybegin(TAG_TYPE); return LCATS_TAG; }
    "@version"              { yybegin(TAG_VERSION); return LCATS_TAG; }
    "|"                     { yybegin(OPTION); return LCATS_SYMBOL; }
    .                       { yybegin(COMMENT_DATA); return LCATS_TEXT; }
}

<COMMENT_DATA> {
    .+                      { return LCATS_TEXT; }
}

<TAG_CAST> {
    {NAME}                  { return LCATS_NAME; }
    {PLUS}|{MINUS}          { return LCATS_SYMBOL; }
    {TYPES}                 { return LCATS_SYMBOL; }
    .                       { yybegin(COMMENT_DATA); return LCATS_TEXT; }
}

<TAG_CLASS> {
    "(exact)"               { return LCATS_KEYWORD; }
    {NAME}                  { return LCATS_NAME; }
    {COLON}                 { return LCATS_SYMBOL; }
    {COMMA}                 { return LCATS_SYMBOL; }
    {TYPES}                 { return LCATS_SYMBOL; }
    .                       { yybegin(COMMENT_DATA); return LCATS_TEXT; }
}

<TAG_DIAGNOSTIC> {
    "disable-next-line"     { return LCATS_KEYWORD; }
    "disable-line"          { return LCATS_KEYWORD; }
    "disable"               { return LCATS_KEYWORD; }
    "enable"                { return LCATS_KEYWORD; }
    {DIAGNOSTIC}            { return LCATS_NAME; }
    {COLON}                 { return LCATS_SYMBOL; }
    {COMMA}                 { return LCATS_SYMBOL; }
    .                       { yybegin(COMMENT_DATA); return LCATS_TEXT; }
}

<TAG_ENUM> {
    "(key)"                 { return LCATS_KEYWORD; }
    {NAME}                  { return LCATS_NAME; }
    .                       { yybegin(COMMENT_DATA); return LCATS_TEXT; }
}

<TAG_FIELD> {
    "private"               { return LCATS_KEYWORD; }
    "protected"             { return LCATS_KEYWORD; }
    "public"                { return LCATS_KEYWORD; }
    {NAME}                  { return LCATS_NAME; }
    {TYPES}                 { return LCATS_SYMBOL; }
    .                       { yybegin(COMMENT_DATA); return LCATS_TEXT; }
}

<TAG_META> {
    {NAME}                  { return LCATS_NAME; }
    .                       { yybegin(COMMENT_DATA); return LCATS_TEXT; }
}

<TAG_MODULE> {
    {STRINGS}               { return LCATS_STRING; }
    {STRINGD}               { return LCATS_STRING; }
    .                       { yybegin(COMMENT_DATA); return LCATS_TEXT; }
}

<TAG_OPERATOR> {
    {NAME}                  { return LCATS_NAME; }
    {TYPES}                 { return LCATS_SYMBOL; }
    .                       { yybegin(COMMENT_DATA); return LCATS_TEXT; }
}

<TAG_PARAM> {
    {NAME}                  { return LCATS_NAME; }
    {ELLIPSIS}              { return LCATS_SYMBOL; }
    {CODE}                  { return LCATS_CODE; }
    {TYPES}                 { return LCATS_SYMBOL; }
    .                       { yybegin(COMMENT_DATA); return LCATS_TEXT; }
}

<TAG_RETURN> {
    {NAME}                  { return LCATS_NAME; }
    {ELLIPSIS}              { return LCATS_SYMBOL; }
    {TYPES}                 { return LCATS_SYMBOL; }
    {POUND}                 { yybegin(COMMENT_DATA); return LCATS_SYMBOL; }
    .                       { yybegin(COMMENT_DATA); return LCATS_TEXT; }
}

<TAG_SEE> {
    {NAME}                  { return LCATS_NAME; }
    {PERIOD}                { return LCATS_SYMBOL; }
    {COLON}                 { return LCATS_SYMBOL; }
    .                       { yybegin(COMMENT_DATA); return LCATS_TEXT; }
}

<TAG_SOURCE> {
    {NOT_WHITESPACE}+       { return LCATS_STRING; }
    .                       { yybegin(COMMENT_DATA); return LCATS_TEXT; }
}

<TAG_TYPE> {
    // Needs support for other lua language literals
    "fun"                   { return LCATS_KEYWORD; }
    {NUMBER}                { return LCATS_NUMBER; }
    {NAME}                  { return LCATS_NAME; }
    {STRINGD}               { return LCATS_STRING; }
    {STRINGS}               { return LCATS_STRING; }
    {TYPES}                 { return LCATS_SYMBOL; }
    .                       { yybegin(COMMENT_DATA); return LCATS_TEXT; }
}

<TAG_VERSION> {
    {GT}                    { return LCATS_SYMBOL; }
    {LT}                    { return LCATS_SYMBOL; }
    {COMMA}                 { return LCATS_SYMBOL; }
    {PIPE}                  { return LCATS_SYMBOL; }
    {STRINGV}               { return LCATS_STRING; }
    .                       { yybegin(COMMENT_DATA); return LCATS_TEXT; }
}

<OPTION> {
    {NUMBER}                { return LCATS_NUMBER; }
    {STRINGD}               { return LCATS_STRING; }
    {STRINGS}               { return LCATS_STRING; }
    {CODE}                  { return LCATS_CODE; }
    {POUND}                 { yybegin(COMMENT_DATA); return LCATS_SYMBOL; }
    .                       { yybegin(COMMENT_DATA); return LCATS_TEXT; }

}