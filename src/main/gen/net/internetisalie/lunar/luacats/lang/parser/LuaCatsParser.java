// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.luacats.lang.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static net.internetisalie.lunar.luacats.lang.psi.LuaCatsElementTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class LuaCatsParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType t, PsiBuilder b) {
    parseLight(t, b);
    return b.getTreeBuilt();
  }

  public void parseLight(IElementType t, PsiBuilder b) {
    boolean r;
    b = adapt_builder_(t, b, this, null);
    Marker m = enter_section_(b, 0, _COLLAPSE_, null);
    r = parse_root_(t, b);
    exit_section_(b, 0, m, t, r, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType t, PsiBuilder b) {
    return parse_root_(t, b, 0);
  }

  static boolean parse_root_(IElementType t, PsiBuilder b, int l) {
    return root(b, l + 1);
  }

  /* ********************************************************** */
  // <<child>>
  public static boolean ArgKeyword(PsiBuilder b, int l, Parser _child) {
    if (!recursion_guard_(b, l, "ArgKeyword")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = _child.parse(b, l);
    exit_section_(b, m, ARG_KEYWORD, r);
    return r;
  }

  /* ********************************************************** */
  // <<child>>
  public static boolean ArgName(PsiBuilder b, int l, Parser _child) {
    if (!recursion_guard_(b, l, "ArgName")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = _child.parse(b, l);
    exit_section_(b, m, ARG_NAME, r);
    return r;
  }

  /* ********************************************************** */
  // <<child>>
  public static boolean ArgSymbol(PsiBuilder b, int l, Parser _child) {
    if (!recursion_guard_(b, l, "ArgSymbol")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = _child.parse(b, l);
    exit_section_(b, m, ARG_SYMBOL, r);
    return r;
  }

  /* ********************************************************** */
  // <<child>>
  public static boolean ArgType(PsiBuilder b, int l, Parser _child) {
    if (!recursion_guard_(b, l, "ArgType")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = _child.parse(b, l);
    exit_section_(b, m, ARG_TYPE, r);
    return r;
  }

  /* ********************************************************** */
  // <<child>>
  public static boolean ArgValue(PsiBuilder b, int l, Parser _child) {
    if (!recursion_guard_(b, l, "ArgValue")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = _child.parse(b, l);
    exit_section_(b, m, ARG_VALUE, r);
    return r;
  }

  /* ********************************************************** */
  // '+' | '-'
  public static boolean addOrSubtract(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "addOrSubtract")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ADD_OR_SUBTRACT, "<add or subtract>");
    r = consumeToken(b, "+");
    if (!r) r = consumeToken(b, "-");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '@alias' <<ArgName NAME>> <<ArgType type>>? description?
  public static boolean aliasTag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "aliasTag")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ALIAS_TAG, "<alias tag>");
    r = consumeToken(b, "@alias");
    r = r && ArgName(b, l + 1, NAME_parser_);
    r = r && aliasTag_2(b, l + 1);
    r = r && aliasTag_3(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // <<ArgType type>>?
  private static boolean aliasTag_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "aliasTag_2")) return false;
    ArgType(b, l + 1, LuaCatsParser::type);
    return true;
  }

  // description?
  private static boolean aliasTag_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "aliasTag_3")) return false;
    description(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // aliasTag
  //     | asyncTag
  //     | castTag
  //     | classTag
  //     | deprecatedTag
  //     | diagnosticTag
  //     | enumTag
  //     | fieldTag
  //     | genericTag
  //     | metaTag
  //     | moduleTag
  //     | nodiscardTag
  //     | operatorTag
  //     | overloadTag
  //     | packageTag
  //     | paramTag
  //     | privateTag
  //     | protectedTag
  //     | returnTag
  //     | seeTag
  //     | sourceTag
  //     | typeTag
  //     | varargTag
  //     | versionTag
  static boolean anyTag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "anyTag")) return false;
    boolean r;
    r = aliasTag(b, l + 1);
    if (!r) r = asyncTag(b, l + 1);
    if (!r) r = castTag(b, l + 1);
    if (!r) r = classTag(b, l + 1);
    if (!r) r = deprecatedTag(b, l + 1);
    if (!r) r = diagnosticTag(b, l + 1);
    if (!r) r = enumTag(b, l + 1);
    if (!r) r = fieldTag(b, l + 1);
    if (!r) r = genericTag(b, l + 1);
    if (!r) r = metaTag(b, l + 1);
    if (!r) r = moduleTag(b, l + 1);
    if (!r) r = nodiscardTag(b, l + 1);
    if (!r) r = operatorTag(b, l + 1);
    if (!r) r = overloadTag(b, l + 1);
    if (!r) r = packageTag(b, l + 1);
    if (!r) r = paramTag(b, l + 1);
    if (!r) r = privateTag(b, l + 1);
    if (!r) r = protectedTag(b, l + 1);
    if (!r) r = returnTag(b, l + 1);
    if (!r) r = seeTag(b, l + 1);
    if (!r) r = sourceTag(b, l + 1);
    if (!r) r = typeTag(b, l + 1);
    if (!r) r = varargTag(b, l + 1);
    if (!r) r = versionTag(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // NAME
  static boolean argumentName(PsiBuilder b, int l) {
    return consumeToken(b, NAME);
  }

  /* ********************************************************** */
  // distinctType '[]'?
  public static boolean arrayType(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arrayType")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ARRAY_TYPE, "<array type>");
    r = distinctType(b, l + 1);
    r = r && arrayType_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '[]'?
  private static boolean arrayType_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arrayType_1")) return false;
    consumeToken(b, "[]");
    return true;
  }

  /* ********************************************************** */
  // '@async' description?
  public static boolean asyncTag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "asyncTag")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ASYNC_TAG, "<async tag>");
    r = consumeToken(b, "@async");
    r = r && asyncTag_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // description?
  private static boolean asyncTag_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "asyncTag_1")) return false;
    description(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // 'nil' | 'any' | 'boolean' | 'string' | 'number' | 'integer' | 'function' | 'table' | 'thread' | 'userdata' | 'lightuserdata'
  public static boolean builtinType(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "builtinType")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, BUILTIN_TYPE, "<builtin type>");
    r = consumeToken(b, "nil");
    if (!r) r = consumeToken(b, "any");
    if (!r) r = consumeToken(b, "boolean");
    if (!r) r = consumeToken(b, "string");
    if (!r) r = consumeToken(b, "number");
    if (!r) r = consumeToken(b, "integer");
    if (!r) r = consumeToken(b, "function");
    if (!r) r = consumeToken(b, "table");
    if (!r) r = consumeToken(b, "thread");
    if (!r) r = consumeToken(b, "userdata");
    if (!r) r = consumeToken(b, "lightuserdata");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // addOrSubtract? type
  public static boolean castModifier(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "castModifier")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, CAST_MODIFIER, "<cast modifier>");
    r = castModifier_0(b, l + 1);
    r = r && type(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // addOrSubtract?
  private static boolean castModifier_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "castModifier_0")) return false;
    addOrSubtract(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // NAME
  static boolean castName(PsiBuilder b, int l) {
    return consumeToken(b, NAME);
  }

  /* ********************************************************** */
  // '@cast' <<ArgName castName>> <<ArgType castModifier>> { ',' <<ArgType castModifier>> }* description?
  public static boolean castTag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "castTag")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, CAST_TAG, "<cast tag>");
    r = consumeToken(b, "@cast");
    r = r && ArgName(b, l + 1, LuaCatsParser::castName);
    r = r && ArgType(b, l + 1, LuaCatsParser::castModifier);
    r = r && castTag_3(b, l + 1);
    r = r && castTag_4(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // { ',' <<ArgType castModifier>> }*
  private static boolean castTag_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "castTag_3")) return false;
    while (true) {
      int c = current_position_(b);
      if (!castTag_3_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "castTag_3", c)) break;
    }
    return true;
  }

  // ',' <<ArgType castModifier>>
  private static boolean castTag_3_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "castTag_3_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ",");
    r = r && ArgType(b, l + 1, LuaCatsParser::castModifier);
    exit_section_(b, m, null, r);
    return r;
  }

  // description?
  private static boolean castTag_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "castTag_4")) return false;
    description(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // '@class' <<ArgKeyword exactKeyword>>? <<ArgType typeName>> [':' parentTypes] description?
  public static boolean classTag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "classTag")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, CLASS_TAG, "<class tag>");
    r = consumeToken(b, "@class");
    r = r && classTag_1(b, l + 1);
    r = r && ArgType(b, l + 1, LuaCatsParser::typeName);
    r = r && classTag_3(b, l + 1);
    r = r && classTag_4(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // <<ArgKeyword exactKeyword>>?
  private static boolean classTag_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "classTag_1")) return false;
    ArgKeyword(b, l + 1, LuaCatsParser::exactKeyword);
    return true;
  }

  // [':' parentTypes]
  private static boolean classTag_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "classTag_3")) return false;
    classTag_3_0(b, l + 1);
    return true;
  }

  // ':' parentTypes
  private static boolean classTag_3_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "classTag_3_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ":");
    r = r && parentTypes(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // description?
  private static boolean classTag_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "classTag_4")) return false;
    description(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // commentLine*
  public static boolean comment(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "comment")) return false;
    Marker m = enter_section_(b, l, _NONE_, COMMENT, "<comment>");
    while (true) {
      int c = current_position_(b);
      if (!commentLine(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "comment", c)) break;
    }
    exit_section_(b, l, m, true, false, null);
    return true;
  }

  /* ********************************************************** */
  // DASHES (anyTag | typeOption | description)?
  static boolean commentLine(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commentLine")) return false;
    if (!nextTokenIs(b, DASHES)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, DASHES);
    p = r; // pin = 1
    r = r && commentLine_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (anyTag | typeOption | description)?
  private static boolean commentLine_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commentLine_1")) return false;
    commentLine_1_0(b, l + 1);
    return true;
  }

  // anyTag | typeOption | description
  private static boolean commentLine_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commentLine_1_0")) return false;
    boolean r;
    r = anyTag(b, l + 1);
    if (!r) r = typeOption(b, l + 1);
    if (!r) r = description(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // '@deprecated' description?
  public static boolean deprecatedTag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "deprecatedTag")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, DEPRECATED_TAG, "<deprecated tag>");
    r = consumeToken(b, "@deprecated");
    r = r && deprecatedTag_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // description?
  private static boolean deprecatedTag_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "deprecatedTag_1")) return false;
    description(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // (NAME | NUMBER | STRING | SYMBOL | TEXT)+
  public static boolean description(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "description")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, DESCRIPTION, "<description>");
    r = description_0(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!description_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "description", c)) break;
    }
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // NAME | NUMBER | STRING | SYMBOL | TEXT
  private static boolean description_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "description_0")) return false;
    boolean r;
    r = consumeToken(b, NAME);
    if (!r) r = consumeToken(b, NUMBER);
    if (!r) r = consumeToken(b, STRING);
    if (!r) r = consumeToken(b, SYMBOL);
    if (!r) r = consumeToken(b, TEXT);
    return r;
  }

  /* ********************************************************** */
  // NAME
  static boolean diagnosticName(PsiBuilder b, int l) {
    return consumeToken(b, NAME);
  }

  /* ********************************************************** */
  // 'disable-next-line' | 'disable-line' | 'disable' | 'enable'
  public static boolean diagnosticState(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "diagnosticState")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, DIAGNOSTIC_STATE, "<diagnostic state>");
    r = consumeToken(b, "disable-next-line");
    if (!r) r = consumeToken(b, "disable-line");
    if (!r) r = consumeToken(b, "disable");
    if (!r) r = consumeToken(b, "enable");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '@diagnostic' <<ArgKeyword diagnosticState>> [<<ArgSymbol (':')>> diagnostics] description?
  public static boolean diagnosticTag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "diagnosticTag")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, DIAGNOSTIC_TAG, "<diagnostic tag>");
    r = consumeToken(b, "@diagnostic");
    r = r && ArgKeyword(b, l + 1, LuaCatsParser::diagnosticState);
    r = r && diagnosticTag_2(b, l + 1);
    r = r && diagnosticTag_3(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // [<<ArgSymbol (':')>> diagnostics]
  private static boolean diagnosticTag_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "diagnosticTag_2")) return false;
    diagnosticTag_2_0(b, l + 1);
    return true;
  }

  // <<ArgSymbol (':')>> diagnostics
  private static boolean diagnosticTag_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "diagnosticTag_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = ArgSymbol(b, l + 1, LuaCatsParser::diagnosticTag_2_0_0_0);
    r = r && diagnostics(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (':')
  private static boolean diagnosticTag_2_0_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "diagnosticTag_2_0_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ":");
    exit_section_(b, m, null, r);
    return r;
  }

  // description?
  private static boolean diagnosticTag_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "diagnosticTag_3")) return false;
    description(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // <<ArgName diagnosticName>> {',' <<ArgName diagnosticName>>}*
  public static boolean diagnostics(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "diagnostics")) return false;
    if (!nextTokenIs(b, NAME)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = ArgName(b, l + 1, LuaCatsParser::diagnosticName);
    r = r && diagnostics_1(b, l + 1);
    exit_section_(b, m, DIAGNOSTICS, r);
    return r;
  }

  // {',' <<ArgName diagnosticName>>}*
  private static boolean diagnostics_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "diagnostics_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!diagnostics_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "diagnostics_1", c)) break;
    }
    return true;
  }

  // ',' <<ArgName diagnosticName>>
  private static boolean diagnostics_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "diagnostics_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ",");
    r = r && ArgName(b, l + 1, LuaCatsParser::diagnosticName);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '{' '[' type ']' ':' type '}'
  public static boolean dictionaryType(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dictionaryType")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, DICTIONARY_TYPE, "<dictionary type>");
    r = consumeToken(b, "{");
    r = r && consumeToken(b, "[");
    r = r && type(b, l + 1);
    r = r && consumeToken(b, "]");
    r = r && consumeToken(b, ":");
    r = r && type(b, l + 1);
    r = r && consumeToken(b, "}");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // tupleType
  //     | dictionaryType
  //     | literalTableType
  //     | functionSignatureType
  //     | parameterizedName
  //     | parameterName
  //     | literalType
  //     | simpleType
  public static boolean distinctType(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "distinctType")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, DISTINCT_TYPE, "<distinct type>");
    r = tupleType(b, l + 1);
    if (!r) r = dictionaryType(b, l + 1);
    if (!r) r = literalTableType(b, l + 1);
    if (!r) r = functionSignatureType(b, l + 1);
    if (!r) r = parameterizedName(b, l + 1);
    if (!r) r = parameterName(b, l + 1);
    if (!r) r = literalType(b, l + 1);
    if (!r) r = simpleType(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '(key)'
  static boolean enumKey(PsiBuilder b, int l) {
    return consumeToken(b, "(key)");
  }

  /* ********************************************************** */
  // NAME
  static boolean enumName(PsiBuilder b, int l) {
    return consumeToken(b, NAME);
  }

  /* ********************************************************** */
  // '@enum' <<ArgKeyword enumKey>>? <<ArgName enumName>> description?
  public static boolean enumTag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enumTag")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ENUM_TAG, "<enum tag>");
    r = consumeToken(b, "@enum");
    r = r && enumTag_1(b, l + 1);
    r = r && ArgName(b, l + 1, LuaCatsParser::enumName);
    r = r && enumTag_3(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // <<ArgKeyword enumKey>>?
  private static boolean enumTag_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enumTag_1")) return false;
    ArgKeyword(b, l + 1, LuaCatsParser::enumKey);
    return true;
  }

  // description?
  private static boolean enumTag_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enumTag_3")) return false;
    description(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // '(exact)'
  static boolean exactKeyword(PsiBuilder b, int l) {
    return consumeToken(b, "(exact)");
  }

  /* ********************************************************** */
  // (<<ArgKeyword fieldScope>>? <<ArgName fieldNameDescriptor>>)
  //     | (<<ArgKeyword fieldScope>>? <<ArgType fieldKeyDescriptor>>)
  public static boolean fieldDescriptor(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fieldDescriptor")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FIELD_DESCRIPTOR, "<field descriptor>");
    r = fieldDescriptor_0(b, l + 1);
    if (!r) r = fieldDescriptor_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // <<ArgKeyword fieldScope>>? <<ArgName fieldNameDescriptor>>
  private static boolean fieldDescriptor_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fieldDescriptor_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = fieldDescriptor_0_0(b, l + 1);
    r = r && ArgName(b, l + 1, LuaCatsParser::fieldNameDescriptor);
    exit_section_(b, m, null, r);
    return r;
  }

  // <<ArgKeyword fieldScope>>?
  private static boolean fieldDescriptor_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fieldDescriptor_0_0")) return false;
    ArgKeyword(b, l + 1, LuaCatsParser::fieldScope);
    return true;
  }

  // <<ArgKeyword fieldScope>>? <<ArgType fieldKeyDescriptor>>
  private static boolean fieldDescriptor_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fieldDescriptor_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = fieldDescriptor_1_0(b, l + 1);
    r = r && ArgType(b, l + 1, LuaCatsParser::fieldKeyDescriptor);
    exit_section_(b, m, null, r);
    return r;
  }

  // <<ArgKeyword fieldScope>>?
  private static boolean fieldDescriptor_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fieldDescriptor_1_0")) return false;
    ArgKeyword(b, l + 1, LuaCatsParser::fieldScope);
    return true;
  }

  /* ********************************************************** */
  // '[' type ']'
  public static boolean fieldKeyDescriptor(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fieldKeyDescriptor")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FIELD_KEY_DESCRIPTOR, "<field key descriptor>");
    r = consumeToken(b, "[");
    r = r && type(b, l + 1);
    r = r && consumeToken(b, "]");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // NAME '?'?
  public static boolean fieldNameDescriptor(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fieldNameDescriptor")) return false;
    if (!nextTokenIs(b, NAME)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, NAME);
    r = r && fieldNameDescriptor_1(b, l + 1);
    exit_section_(b, m, FIELD_NAME_DESCRIPTOR, r);
    return r;
  }

  // '?'?
  private static boolean fieldNameDescriptor_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fieldNameDescriptor_1")) return false;
    consumeToken(b, "?");
    return true;
  }

  /* ********************************************************** */
  // 'private' | 'protected' | 'public'
  public static boolean fieldScope(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fieldScope")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FIELD_SCOPE, "<field scope>");
    r = consumeToken(b, "private");
    if (!r) r = consumeToken(b, "protected");
    if (!r) r = consumeToken(b, "public");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '@field' fieldDescriptor <<ArgType type>> description?
  public static boolean fieldTag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fieldTag")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FIELD_TAG, "<field tag>");
    r = consumeToken(b, "@field");
    r = r && fieldDescriptor(b, l + 1);
    r = r && ArgType(b, l + 1, LuaCatsParser::type);
    r = r && fieldTag_3(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // description?
  private static boolean fieldTag_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fieldTag_3")) return false;
    description(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // <<ArgName argumentName>> <<ArgSymbol (':')>> <<ArgType type>>
  public static boolean functionSignatureArgument(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionSignatureArgument")) return false;
    if (!nextTokenIs(b, NAME)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = ArgName(b, l + 1, LuaCatsParser::argumentName);
    r = r && ArgSymbol(b, l + 1, LuaCatsParser::functionSignatureArgument_1_0);
    r = r && ArgType(b, l + 1, LuaCatsParser::type);
    exit_section_(b, m, FUNCTION_SIGNATURE_ARGUMENT, r);
    return r;
  }

  // (':')
  private static boolean functionSignatureArgument_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionSignatureArgument_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ":");
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // functionSignatureArgument { ',' functionSignatureArgument }*
  static boolean functionSignatureArguments(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionSignatureArguments")) return false;
    if (!nextTokenIs(b, NAME)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = functionSignatureArgument(b, l + 1);
    r = r && functionSignatureArguments_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // { ',' functionSignatureArgument }*
  private static boolean functionSignatureArguments_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionSignatureArguments_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!functionSignatureArguments_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "functionSignatureArguments_1", c)) break;
    }
    return true;
  }

  // ',' functionSignatureArgument
  private static boolean functionSignatureArguments_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionSignatureArguments_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ",");
    r = r && functionSignatureArgument(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // <<ArgSymbol (':')>> <<ArgType type>>
  public static boolean functionSignatureReturnType(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionSignatureReturnType")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FUNCTION_SIGNATURE_RETURN_TYPE, "<function signature return type>");
    r = ArgSymbol(b, l + 1, LuaCatsParser::functionSignatureReturnType_0_0);
    r = r && ArgType(b, l + 1, LuaCatsParser::type);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (':')
  private static boolean functionSignatureReturnType_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionSignatureReturnType_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ":");
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // 'fun' '(' functionSignatureArguments? ')' functionSignatureReturnType?
  public static boolean functionSignatureType(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionSignatureType")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FUNCTION_SIGNATURE_TYPE, "<function signature type>");
    r = consumeToken(b, "fun");
    r = r && consumeToken(b, "(");
    r = r && functionSignatureType_2(b, l + 1);
    r = r && consumeToken(b, ")");
    r = r && functionSignatureType_4(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // functionSignatureArguments?
  private static boolean functionSignatureType_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionSignatureType_2")) return false;
    functionSignatureArguments(b, l + 1);
    return true;
  }

  // functionSignatureReturnType?
  private static boolean functionSignatureType_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionSignatureType_4")) return false;
    functionSignatureReturnType(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // '@generic' genericTypeParams? description?
  public static boolean genericTag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "genericTag")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, GENERIC_TAG, "<generic tag>");
    r = consumeToken(b, "@generic");
    r = r && genericTag_1(b, l + 1);
    r = r && genericTag_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // genericTypeParams?
  private static boolean genericTag_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "genericTag_1")) return false;
    genericTypeParams(b, l + 1);
    return true;
  }

  // description?
  private static boolean genericTag_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "genericTag_2")) return false;
    description(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // NAME
  public static boolean genericType(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "genericType")) return false;
    if (!nextTokenIs(b, NAME)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, NAME);
    exit_section_(b, m, GENERIC_TYPE, r);
    return r;
  }

  /* ********************************************************** */
  // <<ArgName typeParam>> ( <<ArgSymbol (':')>> <<ArgType parentType>> )?
  public static boolean genericTypeParam(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "genericTypeParam")) return false;
    if (!nextTokenIs(b, NAME)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = ArgName(b, l + 1, LuaCatsParser::typeParam);
    r = r && genericTypeParam_1(b, l + 1);
    exit_section_(b, m, GENERIC_TYPE_PARAM, r);
    return r;
  }

  // ( <<ArgSymbol (':')>> <<ArgType parentType>> )?
  private static boolean genericTypeParam_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "genericTypeParam_1")) return false;
    genericTypeParam_1_0(b, l + 1);
    return true;
  }

  // <<ArgSymbol (':')>> <<ArgType parentType>>
  private static boolean genericTypeParam_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "genericTypeParam_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = ArgSymbol(b, l + 1, LuaCatsParser::genericTypeParam_1_0_0_0);
    r = r && ArgType(b, l + 1, LuaCatsParser::parentType);
    exit_section_(b, m, null, r);
    return r;
  }

  // (':')
  private static boolean genericTypeParam_1_0_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "genericTypeParam_1_0_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ":");
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // genericTypeParam { ',' genericTypeParam }*
  public static boolean genericTypeParams(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "genericTypeParams")) return false;
    if (!nextTokenIs(b, NAME)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = genericTypeParam(b, l + 1);
    r = r && genericTypeParams_1(b, l + 1);
    exit_section_(b, m, GENERIC_TYPE_PARAMS, r);
    return r;
  }

  // { ',' genericTypeParam }*
  private static boolean genericTypeParams_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "genericTypeParams_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!genericTypeParams_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "genericTypeParams_1", c)) break;
    }
    return true;
  }

  // ',' genericTypeParam
  private static boolean genericTypeParams_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "genericTypeParams_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ",");
    r = r && genericTypeParam(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '{' tableLiteralEntries? '}'
  public static boolean literalTableType(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literalTableType")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LITERAL_TABLE_TYPE, "<literal table type>");
    r = consumeToken(b, "{");
    r = r && literalTableType_1(b, l + 1);
    r = r && consumeToken(b, "}");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // tableLiteralEntries?
  private static boolean literalTableType_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literalTableType_1")) return false;
    tableLiteralEntries(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // STRING | NUMBER
  public static boolean literalType(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literalType")) return false;
    if (!nextTokenIs(b, "<literal type>", NUMBER, STRING)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LITERAL_TYPE, "<literal type>");
    r = consumeToken(b, STRING);
    if (!r) r = consumeToken(b, NUMBER);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // NAME
  static boolean metaName(PsiBuilder b, int l) {
    return consumeToken(b, NAME);
  }

  /* ********************************************************** */
  // '@meta' <<ArgName metaName>>? description?
  public static boolean metaTag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "metaTag")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, META_TAG, "<meta tag>");
    r = consumeToken(b, "@meta");
    r = r && metaTag_1(b, l + 1);
    r = r && metaTag_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // <<ArgName metaName>>?
  private static boolean metaTag_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "metaTag_1")) return false;
    ArgName(b, l + 1, LuaCatsParser::metaName);
    return true;
  }

  // description?
  private static boolean metaTag_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "metaTag_2")) return false;
    description(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // STRING
  static boolean moduleName(PsiBuilder b, int l) {
    return consumeToken(b, STRING);
  }

  /* ********************************************************** */
  // '@module' <<ArgValue moduleName>> description?
  public static boolean moduleTag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "moduleTag")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, MODULE_TAG, "<module tag>");
    r = consumeToken(b, "@module");
    r = r && ArgValue(b, l + 1, LuaCatsParser::moduleName);
    r = r && moduleTag_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // description?
  private static boolean moduleTag_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "moduleTag_2")) return false;
    description(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // NAME
  public static boolean namedType(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "namedType")) return false;
    if (!nextTokenIs(b, NAME)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, NAME);
    exit_section_(b, m, NAMED_TYPE, r);
    return r;
  }

  /* ********************************************************** */
  // '@nodiscard' description?
  public static boolean nodiscardTag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nodiscardTag")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, NODISCARD_TAG, "<nodiscard tag>");
    r = consumeToken(b, "@nodiscard");
    r = r && nodiscardTag_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // description?
  private static boolean nodiscardTag_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nodiscardTag_1")) return false;
    description(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // type
  static boolean operatorArgumentType(PsiBuilder b, int l) {
    return type(b, l + 1);
  }

  /* ********************************************************** */
  // 'unm' | 'add' | 'sub' | 'mul' | 'div' | 'idiv' | 'mod' | 'pow' | 'concat' | 'len' | 'eq' | 'lt' | 'le' | 'call' | 'index' | 'newindex'
  static boolean operatorName(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "operatorName")) return false;
    boolean r;
    r = consumeToken(b, "unm");
    if (!r) r = consumeToken(b, "add");
    if (!r) r = consumeToken(b, "sub");
    if (!r) r = consumeToken(b, "mul");
    if (!r) r = consumeToken(b, "div");
    if (!r) r = consumeToken(b, "idiv");
    if (!r) r = consumeToken(b, "mod");
    if (!r) r = consumeToken(b, "pow");
    if (!r) r = consumeToken(b, "concat");
    if (!r) r = consumeToken(b, "len");
    if (!r) r = consumeToken(b, "eq");
    if (!r) r = consumeToken(b, "lt");
    if (!r) r = consumeToken(b, "le");
    if (!r) r = consumeToken(b, "call");
    if (!r) r = consumeToken(b, "index");
    if (!r) r = consumeToken(b, "newindex");
    return r;
  }

  /* ********************************************************** */
  // type
  static boolean operatorReturnType(PsiBuilder b, int l) {
    return type(b, l + 1);
  }

  /* ********************************************************** */
  // <<ArgName operatorName>> [<<ArgSymbol ('(')>> <<ArgType operatorArgumentType>> <<ArgSymbol (')')>>] <<ArgSymbol (':')>> <<ArgType operatorReturnType>>
  public static boolean operatorSignature(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "operatorSignature")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, OPERATOR_SIGNATURE, "<operator signature>");
    r = ArgName(b, l + 1, LuaCatsParser::operatorName);
    r = r && operatorSignature_1(b, l + 1);
    r = r && ArgSymbol(b, l + 1, LuaCatsParser::operatorSignature_2_0);
    r = r && ArgType(b, l + 1, LuaCatsParser::operatorReturnType);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // [<<ArgSymbol ('(')>> <<ArgType operatorArgumentType>> <<ArgSymbol (')')>>]
  private static boolean operatorSignature_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "operatorSignature_1")) return false;
    operatorSignature_1_0(b, l + 1);
    return true;
  }

  // <<ArgSymbol ('(')>> <<ArgType operatorArgumentType>> <<ArgSymbol (')')>>
  private static boolean operatorSignature_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "operatorSignature_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = ArgSymbol(b, l + 1, LuaCatsParser::operatorSignature_1_0_0_0);
    r = r && ArgType(b, l + 1, LuaCatsParser::operatorArgumentType);
    r = r && ArgSymbol(b, l + 1, LuaCatsParser::operatorSignature_1_0_2_0);
    exit_section_(b, m, null, r);
    return r;
  }

  // ('(')
  private static boolean operatorSignature_1_0_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "operatorSignature_1_0_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, "(");
    exit_section_(b, m, null, r);
    return r;
  }

  // (')')
  private static boolean operatorSignature_1_0_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "operatorSignature_1_0_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ")");
    exit_section_(b, m, null, r);
    return r;
  }

  // (':')
  private static boolean operatorSignature_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "operatorSignature_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ":");
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '@operator' operatorSignature description?
  public static boolean operatorTag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "operatorTag")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, OPERATOR_TAG, "<operator tag>");
    r = consumeToken(b, "@operator");
    r = r && operatorSignature(b, l + 1);
    r = r && operatorTag_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // description?
  private static boolean operatorTag_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "operatorTag_2")) return false;
    description(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // <<ArgKeyword ('fun')>>
  //     <<ArgSymbol ('(')>>
  //     functionSignatureArguments?
  //     <<ArgSymbol (')')>>
  //     functionSignatureReturnType?
  public static boolean overloadFunctionSignature(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "overloadFunctionSignature")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, OVERLOAD_FUNCTION_SIGNATURE, "<overload function signature>");
    r = ArgKeyword(b, l + 1, LuaCatsParser::overloadFunctionSignature_0_0);
    r = r && ArgSymbol(b, l + 1, LuaCatsParser::overloadFunctionSignature_1_0);
    r = r && overloadFunctionSignature_2(b, l + 1);
    r = r && ArgSymbol(b, l + 1, LuaCatsParser::overloadFunctionSignature_3_0);
    r = r && overloadFunctionSignature_4(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // ('fun')
  private static boolean overloadFunctionSignature_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "overloadFunctionSignature_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, "fun");
    exit_section_(b, m, null, r);
    return r;
  }

  // ('(')
  private static boolean overloadFunctionSignature_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "overloadFunctionSignature_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, "(");
    exit_section_(b, m, null, r);
    return r;
  }

  // functionSignatureArguments?
  private static boolean overloadFunctionSignature_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "overloadFunctionSignature_2")) return false;
    functionSignatureArguments(b, l + 1);
    return true;
  }

  // (')')
  private static boolean overloadFunctionSignature_3_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "overloadFunctionSignature_3_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ")");
    exit_section_(b, m, null, r);
    return r;
  }

  // functionSignatureReturnType?
  private static boolean overloadFunctionSignature_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "overloadFunctionSignature_4")) return false;
    functionSignatureReturnType(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // '@overload' overloadFunctionSignature description?
  public static boolean overloadTag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "overloadTag")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, OVERLOAD_TAG, "<overload tag>");
    r = consumeToken(b, "@overload");
    r = r && overloadFunctionSignature(b, l + 1);
    r = r && overloadTag_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // description?
  private static boolean overloadTag_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "overloadTag_2")) return false;
    description(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // '@package' description?
  public static boolean packageTag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "packageTag")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PACKAGE_TAG, "<package tag>");
    r = consumeToken(b, "@package");
    r = r && packageTag_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // description?
  private static boolean packageTag_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "packageTag_1")) return false;
    description(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // '@param' ((<<ArgName NAME>> <<ArgSymbol ('?')>>?) | <<ArgSymbol ('...')>>) <<ArgType type>> description?
  public static boolean paramTag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "paramTag")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PARAM_TAG, "<param tag>");
    r = consumeToken(b, "@param");
    r = r && paramTag_1(b, l + 1);
    r = r && ArgType(b, l + 1, LuaCatsParser::type);
    r = r && paramTag_3(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (<<ArgName NAME>> <<ArgSymbol ('?')>>?) | <<ArgSymbol ('...')>>
  private static boolean paramTag_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "paramTag_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = paramTag_1_0(b, l + 1);
    if (!r) r = ArgSymbol(b, l + 1, LuaCatsParser::paramTag_1_1_0);
    exit_section_(b, m, null, r);
    return r;
  }

  // <<ArgName NAME>> <<ArgSymbol ('?')>>?
  private static boolean paramTag_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "paramTag_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = ArgName(b, l + 1, NAME_parser_);
    r = r && paramTag_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // <<ArgSymbol ('?')>>?
  private static boolean paramTag_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "paramTag_1_0_1")) return false;
    ArgSymbol(b, l + 1, LuaCatsParser::paramTag_1_0_1_0_0);
    return true;
  }

  // ('?')
  private static boolean paramTag_1_0_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "paramTag_1_0_1_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, "?");
    exit_section_(b, m, null, r);
    return r;
  }

  // ('...')
  private static boolean paramTag_1_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "paramTag_1_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, "...");
    exit_section_(b, m, null, r);
    return r;
  }

  // description?
  private static boolean paramTag_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "paramTag_3")) return false;
    description(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // CODE
  public static boolean parameterName(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameterName")) return false;
    if (!nextTokenIs(b, CODE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, CODE);
    exit_section_(b, m, PARAMETER_NAME, r);
    return r;
  }

  /* ********************************************************** */
  // genericType '<' typeParam {',' typeParam }* '>'
  public static boolean parameterizedName(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameterizedName")) return false;
    if (!nextTokenIs(b, NAME)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = genericType(b, l + 1);
    r = r && consumeToken(b, "<");
    r = r && typeParam(b, l + 1);
    r = r && parameterizedName_3(b, l + 1);
    r = r && consumeToken(b, ">");
    exit_section_(b, m, PARAMETERIZED_NAME, r);
    return r;
  }

  // {',' typeParam }*
  private static boolean parameterizedName_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameterizedName_3")) return false;
    while (true) {
      int c = current_position_(b);
      if (!parameterizedName_3_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "parameterizedName_3", c)) break;
    }
    return true;
  }

  // ',' typeParam
  private static boolean parameterizedName_3_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parameterizedName_3_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ",");
    r = r && typeParam(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // type
  static boolean parentType(PsiBuilder b, int l) {
    return type(b, l + 1);
  }

  /* ********************************************************** */
  // <<ArgType parentType>> { ',' <<ArgType parentType>> }*
  public static boolean parentTypes(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parentTypes")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PARENT_TYPES, "<parent types>");
    r = ArgType(b, l + 1, LuaCatsParser::parentType);
    r = r && parentTypes_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // { ',' <<ArgType parentType>> }*
  private static boolean parentTypes_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parentTypes_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!parentTypes_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "parentTypes_1", c)) break;
    }
    return true;
  }

  // ',' <<ArgType parentType>>
  private static boolean parentTypes_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parentTypes_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ",");
    r = r && ArgType(b, l + 1, LuaCatsParser::parentType);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '@private' description?
  public static boolean privateTag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "privateTag")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PRIVATE_TAG, "<private tag>");
    r = consumeToken(b, "@private");
    r = r && privateTag_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // description?
  private static boolean privateTag_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "privateTag_1")) return false;
    description(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // '@protected' description?
  public static boolean protectedTag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "protectedTag")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PROTECTED_TAG, "<protected tag>");
    r = consumeToken(b, "@protected");
    r = r && protectedTag_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // description?
  private static boolean protectedTag_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "protectedTag_1")) return false;
    description(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // '@return' <<ArgType type>> [(<<ArgName NAME>>? '#' description? ) | ( <<ArgName NAME>> description ? )]
  public static boolean returnTag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "returnTag")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, RETURN_TAG, "<return tag>");
    r = consumeToken(b, "@return");
    r = r && ArgType(b, l + 1, LuaCatsParser::type);
    r = r && returnTag_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // [(<<ArgName NAME>>? '#' description? ) | ( <<ArgName NAME>> description ? )]
  private static boolean returnTag_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "returnTag_2")) return false;
    returnTag_2_0(b, l + 1);
    return true;
  }

  // (<<ArgName NAME>>? '#' description? ) | ( <<ArgName NAME>> description ? )
  private static boolean returnTag_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "returnTag_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = returnTag_2_0_0(b, l + 1);
    if (!r) r = returnTag_2_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // <<ArgName NAME>>? '#' description?
  private static boolean returnTag_2_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "returnTag_2_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = returnTag_2_0_0_0(b, l + 1);
    r = r && consumeToken(b, "#");
    r = r && returnTag_2_0_0_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // <<ArgName NAME>>?
  private static boolean returnTag_2_0_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "returnTag_2_0_0_0")) return false;
    ArgName(b, l + 1, NAME_parser_);
    return true;
  }

  // description?
  private static boolean returnTag_2_0_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "returnTag_2_0_0_2")) return false;
    description(b, l + 1);
    return true;
  }

  // <<ArgName NAME>> description ?
  private static boolean returnTag_2_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "returnTag_2_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = ArgName(b, l + 1, NAME_parser_);
    r = r && returnTag_2_0_1_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // description ?
  private static boolean returnTag_2_0_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "returnTag_2_0_1_1")) return false;
    description(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // comment
  static boolean root(PsiBuilder b, int l) {
    return comment(b, l + 1);
  }

  /* ********************************************************** */
  // '@see' <<ArgName NAME>> description?
  public static boolean seeTag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "seeTag")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, SEE_TAG, "<see tag>");
    r = consumeToken(b, "@see");
    r = r && ArgName(b, l + 1, NAME_parser_);
    r = r && seeTag_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // description?
  private static boolean seeTag_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "seeTag_2")) return false;
    description(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // builtinType | namedType
  static boolean simpleType(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simpleType")) return false;
    boolean r;
    r = builtinType(b, l + 1);
    if (!r) r = namedType(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // '@source' <<ArgValue STRING>> description?
  public static boolean sourceTag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "sourceTag")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, SOURCE_TAG, "<source tag>");
    r = consumeToken(b, "@source");
    r = r && ArgValue(b, l + 1, STRING_parser_);
    r = r && sourceTag_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // description?
  private static boolean sourceTag_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "sourceTag_2")) return false;
    description(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // tableLiteralEntry { ',' tableLiteralEntry }*
  static boolean tableLiteralEntries(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tableLiteralEntries")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = tableLiteralEntry(b, l + 1);
    r = r && tableLiteralEntries_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // { ',' tableLiteralEntry }*
  private static boolean tableLiteralEntries_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tableLiteralEntries_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!tableLiteralEntries_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "tableLiteralEntries_1", c)) break;
    }
    return true;
  }

  // ',' tableLiteralEntry
  private static boolean tableLiteralEntries_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tableLiteralEntries_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ",");
    r = r && tableLiteralEntry(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // simpleType ':' type
  public static boolean tableLiteralEntry(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tableLiteralEntry")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, TABLE_LITERAL_ENTRY, "<table literal entry>");
    r = simpleType(b, l + 1);
    r = r && consumeToken(b, ":");
    r = r && type(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '[' type { ',' type }* ']'
  public static boolean tupleType(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tupleType")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, TUPLE_TYPE, "<tuple type>");
    r = consumeToken(b, "[");
    r = r && type(b, l + 1);
    r = r && tupleType_2(b, l + 1);
    r = r && consumeToken(b, "]");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // { ',' type }*
  private static boolean tupleType_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tupleType_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!tupleType_2_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "tupleType_2", c)) break;
    }
    return true;
  }

  // ',' type
  private static boolean tupleType_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tupleType_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ",");
    r = r && type(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '(' unionType ')' | unionType
  public static boolean type(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, TYPE, "<type>");
    r = type_0(b, l + 1);
    if (!r) r = unionType(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '(' unionType ')'
  private static boolean type_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, "(");
    r = r && unionType(b, l + 1);
    r = r && consumeToken(b, ")");
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // parameterizedName | NAME
  static boolean typeName(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeName")) return false;
    if (!nextTokenIs(b, NAME)) return false;
    boolean r;
    r = parameterizedName(b, l + 1);
    if (!r) r = consumeToken(b, NAME);
    return r;
  }

  /* ********************************************************** */
  // '|' <<ArgValue (STRING | CODE | NUMBER | NAME)>> [ '#' description ]
  public static boolean typeOption(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeOption")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, TYPE_OPTION, "<type option>");
    r = consumeToken(b, "|");
    r = r && ArgValue(b, l + 1, LuaCatsParser::typeOption_1_0);
    r = r && typeOption_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // STRING | CODE | NUMBER | NAME
  private static boolean typeOption_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeOption_1_0")) return false;
    boolean r;
    r = consumeToken(b, STRING);
    if (!r) r = consumeToken(b, CODE);
    if (!r) r = consumeToken(b, NUMBER);
    if (!r) r = consumeToken(b, NAME);
    return r;
  }

  // [ '#' description ]
  private static boolean typeOption_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeOption_2")) return false;
    typeOption_2_0(b, l + 1);
    return true;
  }

  // '#' description
  private static boolean typeOption_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeOption_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, "#");
    r = r && description(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // NAME
  public static boolean typeParam(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeParam")) return false;
    if (!nextTokenIs(b, NAME)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, NAME);
    exit_section_(b, m, TYPE_PARAM, r);
    return r;
  }

  /* ********************************************************** */
  // '@type' <<ArgType type>> description?
  public static boolean typeTag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeTag")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, TYPE_TAG, "<type tag>");
    r = consumeToken(b, "@type");
    r = r && ArgType(b, l + 1, LuaCatsParser::type);
    r = r && typeTag_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // description?
  private static boolean typeTag_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeTag_2")) return false;
    description(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // arrayType { '|' arrayType }*
  public static boolean unionType(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unionType")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, UNION_TYPE, "<union type>");
    r = arrayType(b, l + 1);
    r = r && unionType_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // { '|' arrayType }*
  private static boolean unionType_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unionType_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!unionType_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "unionType_1", c)) break;
    }
    return true;
  }

  // '|' arrayType
  private static boolean unionType_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unionType_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, "|");
    r = r && arrayType(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '@vararg' <<ArgType type>> description?
  public static boolean varargTag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "varargTag")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, VARARG_TAG, "<vararg tag>");
    r = consumeToken(b, "@vararg");
    r = r && ArgType(b, l + 1, LuaCatsParser::type);
    r = r && varargTag_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // description?
  private static boolean varargTag_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "varargTag_2")) return false;
    description(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // '<' | '>'
  public static boolean versionComparison(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "versionComparison")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, VERSION_COMPARISON, "<version comparison>");
    r = consumeToken(b, "<");
    if (!r) r = consumeToken(b, ">");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '5.1' | '5.2' | '5.3' | '5.4' | 'JIT'
  public static boolean versionNumber(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "versionNumber")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, VERSION_NUMBER, "<version number>");
    r = consumeToken(b, "5.1");
    if (!r) r = consumeToken(b, "5.2");
    if (!r) r = consumeToken(b, "5.3");
    if (!r) r = consumeToken(b, "5.4");
    if (!r) r = consumeToken(b, "JIT");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // <<ArgSymbol versionComparison>>? <<ArgValue versionNumber>>
  public static boolean versionSpec(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "versionSpec")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, VERSION_SPEC, "<version spec>");
    r = versionSpec_0(b, l + 1);
    r = r && ArgValue(b, l + 1, LuaCatsParser::versionNumber);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // <<ArgSymbol versionComparison>>?
  private static boolean versionSpec_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "versionSpec_0")) return false;
    ArgSymbol(b, l + 1, LuaCatsParser::versionComparison);
    return true;
  }

  /* ********************************************************** */
  // versionSpec { <<ArgSymbol (',')>> versionSpec }*
  public static boolean versionSpecs(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "versionSpecs")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, VERSION_SPECS, "<version specs>");
    r = versionSpec(b, l + 1);
    r = r && versionSpecs_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // { <<ArgSymbol (',')>> versionSpec }*
  private static boolean versionSpecs_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "versionSpecs_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!versionSpecs_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "versionSpecs_1", c)) break;
    }
    return true;
  }

  // <<ArgSymbol (',')>> versionSpec
  private static boolean versionSpecs_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "versionSpecs_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = ArgSymbol(b, l + 1, LuaCatsParser::versionSpecs_1_0_0_0);
    r = r && versionSpec(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (',')
  private static boolean versionSpecs_1_0_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "versionSpecs_1_0_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ",");
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '@version' versionSpecs description?
  public static boolean versionTag(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "versionTag")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, VERSION_TAG, "<version tag>");
    r = consumeToken(b, "@version");
    r = r && versionSpecs(b, l + 1);
    r = r && versionTag_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // description?
  private static boolean versionTag_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "versionTag_2")) return false;
    description(b, l + 1);
    return true;
  }

  static final Parser NAME_parser_ = (b, l) -> consumeToken(b, NAME);
  static final Parser STRING_parser_ = (b, l) -> consumeToken(b, STRING);
}
