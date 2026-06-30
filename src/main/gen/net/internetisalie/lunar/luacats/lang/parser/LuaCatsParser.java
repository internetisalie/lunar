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

  public ASTNode parse(IElementType root_, PsiBuilder builder_) {
    parseLight(root_, builder_);
    return builder_.getTreeBuilt();
  }

  public void parseLight(IElementType root_, PsiBuilder builder_) {
    boolean result_;
    builder_ = adapt_builder_(root_, builder_, this, null);
    Marker marker_ = enter_section_(builder_, 0, _COLLAPSE_, null);
    result_ = parse_root_(root_, builder_);
    exit_section_(builder_, 0, marker_, root_, result_, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType root_, PsiBuilder builder_) {
    return parse_root_(root_, builder_, 0);
  }

  static boolean parse_root_(IElementType root_, PsiBuilder builder_, int level_) {
    return root(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // <<child>>
  public static boolean ArgKeyword(PsiBuilder builder_, int level_, Parser child) {
    if (!recursion_guard_(builder_, level_, "ArgKeyword")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = child.parse(builder_, level_);
    exit_section_(builder_, marker_, ARG_KEYWORD, result_);
    return result_;
  }

  /* ********************************************************** */
  // <<child>>
  public static boolean ArgName(PsiBuilder builder_, int level_, Parser child) {
    if (!recursion_guard_(builder_, level_, "ArgName")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = child.parse(builder_, level_);
    exit_section_(builder_, marker_, ARG_NAME, result_);
    return result_;
  }

  /* ********************************************************** */
  // <<child>>
  public static boolean ArgSymbol(PsiBuilder builder_, int level_, Parser child) {
    if (!recursion_guard_(builder_, level_, "ArgSymbol")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = child.parse(builder_, level_);
    exit_section_(builder_, marker_, ARG_SYMBOL, result_);
    return result_;
  }

  /* ********************************************************** */
  // <<child>>
  public static boolean ArgType(PsiBuilder builder_, int level_, Parser child) {
    if (!recursion_guard_(builder_, level_, "ArgType")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = child.parse(builder_, level_);
    exit_section_(builder_, marker_, ARG_TYPE, result_);
    return result_;
  }

  /* ********************************************************** */
  // <<child>>
  public static boolean ArgValue(PsiBuilder builder_, int level_, Parser child) {
    if (!recursion_guard_(builder_, level_, "ArgValue")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = child.parse(builder_, level_);
    exit_section_(builder_, marker_, ARG_VALUE, result_);
    return result_;
  }

  /* ********************************************************** */
  // '+' | '-'
  public static boolean addOrSubtract(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "addOrSubtract")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ADD_OR_SUBTRACT, "<add or subtract>");
    result_ = consumeToken(builder_, "+");
    if (!result_) result_ = consumeToken(builder_, "-");
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // '@alias' <<ArgName NAME>> <<ArgType type>>? description?
  public static boolean aliasTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "aliasTag")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ALIAS_TAG, "<alias tag>");
    result_ = consumeToken(builder_, "@alias");
    result_ = result_ && ArgName(builder_, level_ + 1, NAME_parser_);
    result_ = result_ && aliasTag_2(builder_, level_ + 1);
    result_ = result_ && aliasTag_3(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // <<ArgType type>>?
  private static boolean aliasTag_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "aliasTag_2")) return false;
    ArgType(builder_, level_ + 1, LuaCatsParser::type);
    return true;
  }

  // description?
  private static boolean aliasTag_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "aliasTag_3")) return false;
    description(builder_, level_ + 1);
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
  static boolean anyTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "anyTag")) return false;
    boolean result_;
    result_ = aliasTag(builder_, level_ + 1);
    if (!result_) result_ = asyncTag(builder_, level_ + 1);
    if (!result_) result_ = castTag(builder_, level_ + 1);
    if (!result_) result_ = classTag(builder_, level_ + 1);
    if (!result_) result_ = deprecatedTag(builder_, level_ + 1);
    if (!result_) result_ = diagnosticTag(builder_, level_ + 1);
    if (!result_) result_ = enumTag(builder_, level_ + 1);
    if (!result_) result_ = fieldTag(builder_, level_ + 1);
    if (!result_) result_ = genericTag(builder_, level_ + 1);
    if (!result_) result_ = metaTag(builder_, level_ + 1);
    if (!result_) result_ = moduleTag(builder_, level_ + 1);
    if (!result_) result_ = nodiscardTag(builder_, level_ + 1);
    if (!result_) result_ = operatorTag(builder_, level_ + 1);
    if (!result_) result_ = overloadTag(builder_, level_ + 1);
    if (!result_) result_ = packageTag(builder_, level_ + 1);
    if (!result_) result_ = paramTag(builder_, level_ + 1);
    if (!result_) result_ = privateTag(builder_, level_ + 1);
    if (!result_) result_ = protectedTag(builder_, level_ + 1);
    if (!result_) result_ = returnTag(builder_, level_ + 1);
    if (!result_) result_ = seeTag(builder_, level_ + 1);
    if (!result_) result_ = sourceTag(builder_, level_ + 1);
    if (!result_) result_ = typeTag(builder_, level_ + 1);
    if (!result_) result_ = varargTag(builder_, level_ + 1);
    if (!result_) result_ = versionTag(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // NAME
  static boolean argumentName(PsiBuilder builder_, int level_) {
    return consumeToken(builder_, NAME);
  }

  /* ********************************************************** */
  // distinctType '[]'?
  public static boolean arrayType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "arrayType")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ARRAY_TYPE, "<array type>");
    result_ = distinctType(builder_, level_ + 1);
    result_ = result_ && arrayType_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // '[]'?
  private static boolean arrayType_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "arrayType_1")) return false;
    consumeToken(builder_, "[]");
    return true;
  }

  /* ********************************************************** */
  // '@async' description?
  public static boolean asyncTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asyncTag")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ASYNC_TAG, "<async tag>");
    result_ = consumeToken(builder_, "@async");
    result_ = result_ && asyncTag_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // description?
  private static boolean asyncTag_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asyncTag_1")) return false;
    description(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // 'nil' | 'any' | 'boolean' | 'string' | 'number' | 'integer' | 'function' | 'table' | 'thread' | 'userdata' | 'lightuserdata'
  public static boolean builtinType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "builtinType")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, BUILTIN_TYPE, "<builtin type>");
    result_ = consumeToken(builder_, "nil");
    if (!result_) result_ = consumeToken(builder_, "any");
    if (!result_) result_ = consumeToken(builder_, "boolean");
    if (!result_) result_ = consumeToken(builder_, "string");
    if (!result_) result_ = consumeToken(builder_, "number");
    if (!result_) result_ = consumeToken(builder_, "integer");
    if (!result_) result_ = consumeToken(builder_, "function");
    if (!result_) result_ = consumeToken(builder_, "table");
    if (!result_) result_ = consumeToken(builder_, "thread");
    if (!result_) result_ = consumeToken(builder_, "userdata");
    if (!result_) result_ = consumeToken(builder_, "lightuserdata");
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // addOrSubtract? type
  public static boolean castModifier(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "castModifier")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CAST_MODIFIER, "<cast modifier>");
    result_ = castModifier_0(builder_, level_ + 1);
    result_ = result_ && type(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // addOrSubtract?
  private static boolean castModifier_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "castModifier_0")) return false;
    addOrSubtract(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // NAME
  static boolean castName(PsiBuilder builder_, int level_) {
    return consumeToken(builder_, NAME);
  }

  /* ********************************************************** */
  // '@cast' <<ArgName castName>> <<ArgType castModifier>> { ',' <<ArgType castModifier>> }* description?
  public static boolean castTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "castTag")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CAST_TAG, "<cast tag>");
    result_ = consumeToken(builder_, "@cast");
    result_ = result_ && ArgName(builder_, level_ + 1, LuaCatsParser::castName);
    result_ = result_ && ArgType(builder_, level_ + 1, LuaCatsParser::castModifier);
    result_ = result_ && castTag_3(builder_, level_ + 1);
    result_ = result_ && castTag_4(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // { ',' <<ArgType castModifier>> }*
  private static boolean castTag_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "castTag_3")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!castTag_3_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "castTag_3", pos_)) break;
    }
    return true;
  }

  // ',' <<ArgType castModifier>>
  private static boolean castTag_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "castTag_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ",");
    result_ = result_ && ArgType(builder_, level_ + 1, LuaCatsParser::castModifier);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // description?
  private static boolean castTag_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "castTag_4")) return false;
    description(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // '@class' <<ArgKeyword exactKeyword>>? <<ArgType typeName>> [':' parentTypes] description?
  public static boolean classTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classTag")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CLASS_TAG, "<class tag>");
    result_ = consumeToken(builder_, "@class");
    result_ = result_ && classTag_1(builder_, level_ + 1);
    result_ = result_ && ArgType(builder_, level_ + 1, LuaCatsParser::typeName);
    result_ = result_ && classTag_3(builder_, level_ + 1);
    result_ = result_ && classTag_4(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // <<ArgKeyword exactKeyword>>?
  private static boolean classTag_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classTag_1")) return false;
    ArgKeyword(builder_, level_ + 1, LuaCatsParser::exactKeyword);
    return true;
  }

  // [':' parentTypes]
  private static boolean classTag_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classTag_3")) return false;
    classTag_3_0(builder_, level_ + 1);
    return true;
  }

  // ':' parentTypes
  private static boolean classTag_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classTag_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ":");
    result_ = result_ && parentTypes(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // description?
  private static boolean classTag_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classTag_4")) return false;
    description(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // commentLine*
  public static boolean comment(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "comment")) return false;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, COMMENT, "<comment>");
    while (true) {
      int pos_ = current_position_(builder_);
      if (!commentLine(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "comment", pos_)) break;
    }
    exit_section_(builder_, level_, marker_, true, false, null);
    return true;
  }

  /* ********************************************************** */
  // DASHES (anyTag | typeOption | description)?
  static boolean commentLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commentLine")) return false;
    if (!nextTokenIs(builder_, DASHES)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = consumeToken(builder_, DASHES);
    pinned_ = result_; // pin = 1
    result_ = result_ && commentLine_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // (anyTag | typeOption | description)?
  private static boolean commentLine_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commentLine_1")) return false;
    commentLine_1_0(builder_, level_ + 1);
    return true;
  }

  // anyTag | typeOption | description
  private static boolean commentLine_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "commentLine_1_0")) return false;
    boolean result_;
    result_ = anyTag(builder_, level_ + 1);
    if (!result_) result_ = typeOption(builder_, level_ + 1);
    if (!result_) result_ = description(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // '@deprecated' description?
  public static boolean deprecatedTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "deprecatedTag")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, DEPRECATED_TAG, "<deprecated tag>");
    result_ = consumeToken(builder_, "@deprecated");
    result_ = result_ && deprecatedTag_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // description?
  private static boolean deprecatedTag_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "deprecatedTag_1")) return false;
    description(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // (NAME | NUMBER | STRING | SYMBOL | TEXT)+
  public static boolean description(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "description")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, DESCRIPTION, "<description>");
    result_ = description_0(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!description_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "description", pos_)) break;
    }
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // NAME | NUMBER | STRING | SYMBOL | TEXT
  private static boolean description_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "description_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, NAME);
    if (!result_) result_ = consumeToken(builder_, NUMBER);
    if (!result_) result_ = consumeToken(builder_, STRING);
    if (!result_) result_ = consumeToken(builder_, SYMBOL);
    if (!result_) result_ = consumeToken(builder_, TEXT);
    return result_;
  }

  /* ********************************************************** */
  // NAME
  static boolean diagnosticName(PsiBuilder builder_, int level_) {
    return consumeToken(builder_, NAME);
  }

  /* ********************************************************** */
  // 'disable-next-line' | 'disable-line' | 'disable' | 'enable'
  public static boolean diagnosticState(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "diagnosticState")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, DIAGNOSTIC_STATE, "<diagnostic state>");
    result_ = consumeToken(builder_, "disable-next-line");
    if (!result_) result_ = consumeToken(builder_, "disable-line");
    if (!result_) result_ = consumeToken(builder_, "disable");
    if (!result_) result_ = consumeToken(builder_, "enable");
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // '@diagnostic' <<ArgKeyword diagnosticState>> [<<ArgSymbol (':')>> diagnostics] description?
  public static boolean diagnosticTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "diagnosticTag")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, DIAGNOSTIC_TAG, "<diagnostic tag>");
    result_ = consumeToken(builder_, "@diagnostic");
    result_ = result_ && ArgKeyword(builder_, level_ + 1, LuaCatsParser::diagnosticState);
    result_ = result_ && diagnosticTag_2(builder_, level_ + 1);
    result_ = result_ && diagnosticTag_3(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // [<<ArgSymbol (':')>> diagnostics]
  private static boolean diagnosticTag_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "diagnosticTag_2")) return false;
    diagnosticTag_2_0(builder_, level_ + 1);
    return true;
  }

  // <<ArgSymbol (':')>> diagnostics
  private static boolean diagnosticTag_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "diagnosticTag_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ArgSymbol(builder_, level_ + 1, LuaCatsParser::diagnosticTag_2_0_0_0);
    result_ = result_ && diagnostics(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (':')
  private static boolean diagnosticTag_2_0_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "diagnosticTag_2_0_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ":");
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // description?
  private static boolean diagnosticTag_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "diagnosticTag_3")) return false;
    description(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // <<ArgName diagnosticName>> {',' <<ArgName diagnosticName>>}*
  public static boolean diagnostics(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "diagnostics")) return false;
    if (!nextTokenIs(builder_, NAME)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ArgName(builder_, level_ + 1, LuaCatsParser::diagnosticName);
    result_ = result_ && diagnostics_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, DIAGNOSTICS, result_);
    return result_;
  }

  // {',' <<ArgName diagnosticName>>}*
  private static boolean diagnostics_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "diagnostics_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!diagnostics_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "diagnostics_1", pos_)) break;
    }
    return true;
  }

  // ',' <<ArgName diagnosticName>>
  private static boolean diagnostics_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "diagnostics_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ",");
    result_ = result_ && ArgName(builder_, level_ + 1, LuaCatsParser::diagnosticName);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // '{' '[' type ']' ':' type '}'
  public static boolean dictionaryType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "dictionaryType")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, DICTIONARY_TYPE, "<dictionary type>");
    result_ = consumeToken(builder_, "{");
    result_ = result_ && consumeToken(builder_, "[");
    result_ = result_ && type(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, "]");
    result_ = result_ && consumeToken(builder_, ":");
    result_ = result_ && type(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, "}");
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
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
  public static boolean distinctType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "distinctType")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, DISTINCT_TYPE, "<distinct type>");
    result_ = tupleType(builder_, level_ + 1);
    if (!result_) result_ = dictionaryType(builder_, level_ + 1);
    if (!result_) result_ = literalTableType(builder_, level_ + 1);
    if (!result_) result_ = functionSignatureType(builder_, level_ + 1);
    if (!result_) result_ = parameterizedName(builder_, level_ + 1);
    if (!result_) result_ = parameterName(builder_, level_ + 1);
    if (!result_) result_ = literalType(builder_, level_ + 1);
    if (!result_) result_ = simpleType(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // '(key)'
  static boolean enumKey(PsiBuilder builder_, int level_) {
    return consumeToken(builder_, "(key)");
  }

  /* ********************************************************** */
  // NAME
  static boolean enumName(PsiBuilder builder_, int level_) {
    return consumeToken(builder_, NAME);
  }

  /* ********************************************************** */
  // '@enum' <<ArgKeyword enumKey>>? <<ArgName enumName>> description?
  public static boolean enumTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enumTag")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ENUM_TAG, "<enum tag>");
    result_ = consumeToken(builder_, "@enum");
    result_ = result_ && enumTag_1(builder_, level_ + 1);
    result_ = result_ && ArgName(builder_, level_ + 1, LuaCatsParser::enumName);
    result_ = result_ && enumTag_3(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // <<ArgKeyword enumKey>>?
  private static boolean enumTag_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enumTag_1")) return false;
    ArgKeyword(builder_, level_ + 1, LuaCatsParser::enumKey);
    return true;
  }

  // description?
  private static boolean enumTag_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enumTag_3")) return false;
    description(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // '(exact)'
  static boolean exactKeyword(PsiBuilder builder_, int level_) {
    return consumeToken(builder_, "(exact)");
  }

  /* ********************************************************** */
  // (<<ArgKeyword fieldScope>>? <<ArgName fieldNameDescriptor>>)
  //     | (<<ArgKeyword fieldScope>>? <<ArgType fieldKeyDescriptor>>)
  public static boolean fieldDescriptor(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fieldDescriptor")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FIELD_DESCRIPTOR, "<field descriptor>");
    result_ = fieldDescriptor_0(builder_, level_ + 1);
    if (!result_) result_ = fieldDescriptor_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // <<ArgKeyword fieldScope>>? <<ArgName fieldNameDescriptor>>
  private static boolean fieldDescriptor_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fieldDescriptor_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = fieldDescriptor_0_0(builder_, level_ + 1);
    result_ = result_ && ArgName(builder_, level_ + 1, LuaCatsParser::fieldNameDescriptor);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // <<ArgKeyword fieldScope>>?
  private static boolean fieldDescriptor_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fieldDescriptor_0_0")) return false;
    ArgKeyword(builder_, level_ + 1, LuaCatsParser::fieldScope);
    return true;
  }

  // <<ArgKeyword fieldScope>>? <<ArgType fieldKeyDescriptor>>
  private static boolean fieldDescriptor_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fieldDescriptor_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = fieldDescriptor_1_0(builder_, level_ + 1);
    result_ = result_ && ArgType(builder_, level_ + 1, LuaCatsParser::fieldKeyDescriptor);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // <<ArgKeyword fieldScope>>?
  private static boolean fieldDescriptor_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fieldDescriptor_1_0")) return false;
    ArgKeyword(builder_, level_ + 1, LuaCatsParser::fieldScope);
    return true;
  }

  /* ********************************************************** */
  // '[' type ']'
  public static boolean fieldKeyDescriptor(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fieldKeyDescriptor")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FIELD_KEY_DESCRIPTOR, "<field key descriptor>");
    result_ = consumeToken(builder_, "[");
    result_ = result_ && type(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, "]");
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // NAME '?'?
  public static boolean fieldNameDescriptor(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fieldNameDescriptor")) return false;
    if (!nextTokenIs(builder_, NAME)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NAME);
    result_ = result_ && fieldNameDescriptor_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, FIELD_NAME_DESCRIPTOR, result_);
    return result_;
  }

  // '?'?
  private static boolean fieldNameDescriptor_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fieldNameDescriptor_1")) return false;
    consumeToken(builder_, "?");
    return true;
  }

  /* ********************************************************** */
  // 'private' | 'protected' | 'public'
  public static boolean fieldScope(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fieldScope")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FIELD_SCOPE, "<field scope>");
    result_ = consumeToken(builder_, "private");
    if (!result_) result_ = consumeToken(builder_, "protected");
    if (!result_) result_ = consumeToken(builder_, "public");
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // '@field' fieldDescriptor <<ArgType type>> description?
  public static boolean fieldTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fieldTag")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FIELD_TAG, "<field tag>");
    result_ = consumeToken(builder_, "@field");
    result_ = result_ && fieldDescriptor(builder_, level_ + 1);
    result_ = result_ && ArgType(builder_, level_ + 1, LuaCatsParser::type);
    result_ = result_ && fieldTag_3(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // description?
  private static boolean fieldTag_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fieldTag_3")) return false;
    description(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // <<ArgName argumentName>> <<ArgSymbol (':')>> <<ArgType type>>
  public static boolean functionSignatureArgument(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionSignatureArgument")) return false;
    if (!nextTokenIs(builder_, NAME)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ArgName(builder_, level_ + 1, LuaCatsParser::argumentName);
    result_ = result_ && ArgSymbol(builder_, level_ + 1, LuaCatsParser::functionSignatureArgument_1_0);
    result_ = result_ && ArgType(builder_, level_ + 1, LuaCatsParser::type);
    exit_section_(builder_, marker_, FUNCTION_SIGNATURE_ARGUMENT, result_);
    return result_;
  }

  // (':')
  private static boolean functionSignatureArgument_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionSignatureArgument_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ":");
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // functionSignatureArgument { ',' functionSignatureArgument }*
  static boolean functionSignatureArguments(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionSignatureArguments")) return false;
    if (!nextTokenIs(builder_, NAME)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = functionSignatureArgument(builder_, level_ + 1);
    result_ = result_ && functionSignatureArguments_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // { ',' functionSignatureArgument }*
  private static boolean functionSignatureArguments_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionSignatureArguments_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!functionSignatureArguments_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "functionSignatureArguments_1", pos_)) break;
    }
    return true;
  }

  // ',' functionSignatureArgument
  private static boolean functionSignatureArguments_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionSignatureArguments_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ",");
    result_ = result_ && functionSignatureArgument(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // <<ArgSymbol (':')>> <<ArgType type>>
  public static boolean functionSignatureReturnType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionSignatureReturnType")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FUNCTION_SIGNATURE_RETURN_TYPE, "<function signature return type>");
    result_ = ArgSymbol(builder_, level_ + 1, LuaCatsParser::functionSignatureReturnType_0_0);
    result_ = result_ && ArgType(builder_, level_ + 1, LuaCatsParser::type);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (':')
  private static boolean functionSignatureReturnType_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionSignatureReturnType_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ":");
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // 'fun' '(' functionSignatureArguments? ')' functionSignatureReturnType?
  public static boolean functionSignatureType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionSignatureType")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FUNCTION_SIGNATURE_TYPE, "<function signature type>");
    result_ = consumeToken(builder_, "fun");
    result_ = result_ && consumeToken(builder_, "(");
    result_ = result_ && functionSignatureType_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, ")");
    result_ = result_ && functionSignatureType_4(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // functionSignatureArguments?
  private static boolean functionSignatureType_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionSignatureType_2")) return false;
    functionSignatureArguments(builder_, level_ + 1);
    return true;
  }

  // functionSignatureReturnType?
  private static boolean functionSignatureType_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionSignatureType_4")) return false;
    functionSignatureReturnType(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // '@generic' genericTypeParams? description?
  public static boolean genericTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "genericTag")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, GENERIC_TAG, "<generic tag>");
    result_ = consumeToken(builder_, "@generic");
    result_ = result_ && genericTag_1(builder_, level_ + 1);
    result_ = result_ && genericTag_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // genericTypeParams?
  private static boolean genericTag_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "genericTag_1")) return false;
    genericTypeParams(builder_, level_ + 1);
    return true;
  }

  // description?
  private static boolean genericTag_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "genericTag_2")) return false;
    description(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // NAME
  public static boolean genericType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "genericType")) return false;
    if (!nextTokenIs(builder_, NAME)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NAME);
    exit_section_(builder_, marker_, GENERIC_TYPE, result_);
    return result_;
  }

  /* ********************************************************** */
  // <<ArgName typeParam>> ( <<ArgSymbol (':')>> <<ArgType parentType>> )?
  public static boolean genericTypeParam(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "genericTypeParam")) return false;
    if (!nextTokenIs(builder_, NAME)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ArgName(builder_, level_ + 1, LuaCatsParser::typeParam);
    result_ = result_ && genericTypeParam_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, GENERIC_TYPE_PARAM, result_);
    return result_;
  }

  // ( <<ArgSymbol (':')>> <<ArgType parentType>> )?
  private static boolean genericTypeParam_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "genericTypeParam_1")) return false;
    genericTypeParam_1_0(builder_, level_ + 1);
    return true;
  }

  // <<ArgSymbol (':')>> <<ArgType parentType>>
  private static boolean genericTypeParam_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "genericTypeParam_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ArgSymbol(builder_, level_ + 1, LuaCatsParser::genericTypeParam_1_0_0_0);
    result_ = result_ && ArgType(builder_, level_ + 1, LuaCatsParser::parentType);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (':')
  private static boolean genericTypeParam_1_0_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "genericTypeParam_1_0_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ":");
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // genericTypeParam { ',' genericTypeParam }*
  public static boolean genericTypeParams(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "genericTypeParams")) return false;
    if (!nextTokenIs(builder_, NAME)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = genericTypeParam(builder_, level_ + 1);
    result_ = result_ && genericTypeParams_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, GENERIC_TYPE_PARAMS, result_);
    return result_;
  }

  // { ',' genericTypeParam }*
  private static boolean genericTypeParams_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "genericTypeParams_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!genericTypeParams_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "genericTypeParams_1", pos_)) break;
    }
    return true;
  }

  // ',' genericTypeParam
  private static boolean genericTypeParams_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "genericTypeParams_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ",");
    result_ = result_ && genericTypeParam(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // '{' tableLiteralEntries? '}'
  public static boolean literalTableType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "literalTableType")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, LITERAL_TABLE_TYPE, "<literal table type>");
    result_ = consumeToken(builder_, "{");
    result_ = result_ && literalTableType_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, "}");
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // tableLiteralEntries?
  private static boolean literalTableType_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "literalTableType_1")) return false;
    tableLiteralEntries(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // STRING | NUMBER | 'true' | 'false'
  public static boolean literalType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "literalType")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, LITERAL_TYPE, "<literal type>");
    result_ = consumeToken(builder_, STRING);
    if (!result_) result_ = consumeToken(builder_, NUMBER);
    if (!result_) result_ = consumeToken(builder_, "true");
    if (!result_) result_ = consumeToken(builder_, "false");
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // NAME
  static boolean metaName(PsiBuilder builder_, int level_) {
    return consumeToken(builder_, NAME);
  }

  /* ********************************************************** */
  // '@meta' <<ArgName metaName>>? description?
  public static boolean metaTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "metaTag")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, META_TAG, "<meta tag>");
    result_ = consumeToken(builder_, "@meta");
    result_ = result_ && metaTag_1(builder_, level_ + 1);
    result_ = result_ && metaTag_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // <<ArgName metaName>>?
  private static boolean metaTag_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "metaTag_1")) return false;
    ArgName(builder_, level_ + 1, LuaCatsParser::metaName);
    return true;
  }

  // description?
  private static boolean metaTag_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "metaTag_2")) return false;
    description(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // STRING
  static boolean moduleName(PsiBuilder builder_, int level_) {
    return consumeToken(builder_, STRING);
  }

  /* ********************************************************** */
  // '@module' <<ArgValue moduleName>> description?
  public static boolean moduleTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "moduleTag")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, MODULE_TAG, "<module tag>");
    result_ = consumeToken(builder_, "@module");
    result_ = result_ && ArgValue(builder_, level_ + 1, LuaCatsParser::moduleName);
    result_ = result_ && moduleTag_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // description?
  private static boolean moduleTag_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "moduleTag_2")) return false;
    description(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // NAME
  public static boolean namedType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namedType")) return false;
    if (!nextTokenIs(builder_, NAME)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NAME);
    exit_section_(builder_, marker_, NAMED_TYPE, result_);
    return result_;
  }

  /* ********************************************************** */
  // '@nodiscard' description?
  public static boolean nodiscardTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nodiscardTag")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, NODISCARD_TAG, "<nodiscard tag>");
    result_ = consumeToken(builder_, "@nodiscard");
    result_ = result_ && nodiscardTag_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // description?
  private static boolean nodiscardTag_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nodiscardTag_1")) return false;
    description(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // type
  static boolean operatorArgumentType(PsiBuilder builder_, int level_) {
    return type(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // 'unm' | 'add' | 'sub' | 'mul' | 'div' | 'idiv' | 'mod' | 'pow' | 'concat' | 'len' | 'eq' | 'lt' | 'le' | 'call' | 'index' | 'newindex'
  static boolean operatorName(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "operatorName")) return false;
    boolean result_;
    result_ = consumeToken(builder_, "unm");
    if (!result_) result_ = consumeToken(builder_, "add");
    if (!result_) result_ = consumeToken(builder_, "sub");
    if (!result_) result_ = consumeToken(builder_, "mul");
    if (!result_) result_ = consumeToken(builder_, "div");
    if (!result_) result_ = consumeToken(builder_, "idiv");
    if (!result_) result_ = consumeToken(builder_, "mod");
    if (!result_) result_ = consumeToken(builder_, "pow");
    if (!result_) result_ = consumeToken(builder_, "concat");
    if (!result_) result_ = consumeToken(builder_, "len");
    if (!result_) result_ = consumeToken(builder_, "eq");
    if (!result_) result_ = consumeToken(builder_, "lt");
    if (!result_) result_ = consumeToken(builder_, "le");
    if (!result_) result_ = consumeToken(builder_, "call");
    if (!result_) result_ = consumeToken(builder_, "index");
    if (!result_) result_ = consumeToken(builder_, "newindex");
    return result_;
  }

  /* ********************************************************** */
  // type
  static boolean operatorReturnType(PsiBuilder builder_, int level_) {
    return type(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // <<ArgName operatorName>> [<<ArgSymbol ('(')>> <<ArgType operatorArgumentType>> <<ArgSymbol (')')>>] <<ArgSymbol (':')>> <<ArgType operatorReturnType>>
  public static boolean operatorSignature(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "operatorSignature")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, OPERATOR_SIGNATURE, "<operator signature>");
    result_ = ArgName(builder_, level_ + 1, LuaCatsParser::operatorName);
    result_ = result_ && operatorSignature_1(builder_, level_ + 1);
    result_ = result_ && ArgSymbol(builder_, level_ + 1, LuaCatsParser::operatorSignature_2_0);
    result_ = result_ && ArgType(builder_, level_ + 1, LuaCatsParser::operatorReturnType);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // [<<ArgSymbol ('(')>> <<ArgType operatorArgumentType>> <<ArgSymbol (')')>>]
  private static boolean operatorSignature_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "operatorSignature_1")) return false;
    operatorSignature_1_0(builder_, level_ + 1);
    return true;
  }

  // <<ArgSymbol ('(')>> <<ArgType operatorArgumentType>> <<ArgSymbol (')')>>
  private static boolean operatorSignature_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "operatorSignature_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ArgSymbol(builder_, level_ + 1, LuaCatsParser::operatorSignature_1_0_0_0);
    result_ = result_ && ArgType(builder_, level_ + 1, LuaCatsParser::operatorArgumentType);
    result_ = result_ && ArgSymbol(builder_, level_ + 1, LuaCatsParser::operatorSignature_1_0_2_0);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ('(')
  private static boolean operatorSignature_1_0_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "operatorSignature_1_0_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, "(");
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (')')
  private static boolean operatorSignature_1_0_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "operatorSignature_1_0_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ")");
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (':')
  private static boolean operatorSignature_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "operatorSignature_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ":");
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // '@operator' operatorSignature description?
  public static boolean operatorTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "operatorTag")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, OPERATOR_TAG, "<operator tag>");
    result_ = consumeToken(builder_, "@operator");
    result_ = result_ && operatorSignature(builder_, level_ + 1);
    result_ = result_ && operatorTag_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // description?
  private static boolean operatorTag_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "operatorTag_2")) return false;
    description(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // <<ArgKeyword ('fun')>>
  //     <<ArgSymbol ('(')>>
  //     functionSignatureArguments?
  //     <<ArgSymbol (')')>>
  //     functionSignatureReturnType?
  public static boolean overloadFunctionSignature(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "overloadFunctionSignature")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, OVERLOAD_FUNCTION_SIGNATURE, "<overload function signature>");
    result_ = ArgKeyword(builder_, level_ + 1, LuaCatsParser::overloadFunctionSignature_0_0);
    result_ = result_ && ArgSymbol(builder_, level_ + 1, LuaCatsParser::overloadFunctionSignature_1_0);
    result_ = result_ && overloadFunctionSignature_2(builder_, level_ + 1);
    result_ = result_ && ArgSymbol(builder_, level_ + 1, LuaCatsParser::overloadFunctionSignature_3_0);
    result_ = result_ && overloadFunctionSignature_4(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // ('fun')
  private static boolean overloadFunctionSignature_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "overloadFunctionSignature_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, "fun");
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ('(')
  private static boolean overloadFunctionSignature_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "overloadFunctionSignature_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, "(");
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // functionSignatureArguments?
  private static boolean overloadFunctionSignature_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "overloadFunctionSignature_2")) return false;
    functionSignatureArguments(builder_, level_ + 1);
    return true;
  }

  // (')')
  private static boolean overloadFunctionSignature_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "overloadFunctionSignature_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ")");
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // functionSignatureReturnType?
  private static boolean overloadFunctionSignature_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "overloadFunctionSignature_4")) return false;
    functionSignatureReturnType(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // '@overload' overloadFunctionSignature description?
  public static boolean overloadTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "overloadTag")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, OVERLOAD_TAG, "<overload tag>");
    result_ = consumeToken(builder_, "@overload");
    result_ = result_ && overloadFunctionSignature(builder_, level_ + 1);
    result_ = result_ && overloadTag_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // description?
  private static boolean overloadTag_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "overloadTag_2")) return false;
    description(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // '@package' description?
  public static boolean packageTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "packageTag")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PACKAGE_TAG, "<package tag>");
    result_ = consumeToken(builder_, "@package");
    result_ = result_ && packageTag_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // description?
  private static boolean packageTag_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "packageTag_1")) return false;
    description(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // '@param' ((<<ArgName NAME>> <<ArgSymbol ('?')>>?) | <<ArgSymbol ('...')>>) <<ArgType type>> description?
  public static boolean paramTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "paramTag")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PARAM_TAG, "<param tag>");
    result_ = consumeToken(builder_, "@param");
    result_ = result_ && paramTag_1(builder_, level_ + 1);
    result_ = result_ && ArgType(builder_, level_ + 1, LuaCatsParser::type);
    result_ = result_ && paramTag_3(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (<<ArgName NAME>> <<ArgSymbol ('?')>>?) | <<ArgSymbol ('...')>>
  private static boolean paramTag_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "paramTag_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = paramTag_1_0(builder_, level_ + 1);
    if (!result_) result_ = ArgSymbol(builder_, level_ + 1, LuaCatsParser::paramTag_1_1_0);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // <<ArgName NAME>> <<ArgSymbol ('?')>>?
  private static boolean paramTag_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "paramTag_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ArgName(builder_, level_ + 1, NAME_parser_);
    result_ = result_ && paramTag_1_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // <<ArgSymbol ('?')>>?
  private static boolean paramTag_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "paramTag_1_0_1")) return false;
    ArgSymbol(builder_, level_ + 1, LuaCatsParser::paramTag_1_0_1_0_0);
    return true;
  }

  // ('?')
  private static boolean paramTag_1_0_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "paramTag_1_0_1_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, "?");
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ('...')
  private static boolean paramTag_1_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "paramTag_1_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, "...");
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // description?
  private static boolean paramTag_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "paramTag_3")) return false;
    description(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // CODE
  public static boolean parameterName(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameterName")) return false;
    if (!nextTokenIs(builder_, CODE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CODE);
    exit_section_(builder_, marker_, PARAMETER_NAME, result_);
    return result_;
  }

  /* ********************************************************** */
  // genericType '<' typeParam {',' typeParam }* '>'
  public static boolean parameterizedName(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameterizedName")) return false;
    if (!nextTokenIs(builder_, NAME)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = genericType(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, "<");
    result_ = result_ && typeParam(builder_, level_ + 1);
    result_ = result_ && parameterizedName_3(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, ">");
    exit_section_(builder_, marker_, PARAMETERIZED_NAME, result_);
    return result_;
  }

  // {',' typeParam }*
  private static boolean parameterizedName_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameterizedName_3")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!parameterizedName_3_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "parameterizedName_3", pos_)) break;
    }
    return true;
  }

  // ',' typeParam
  private static boolean parameterizedName_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameterizedName_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ",");
    result_ = result_ && typeParam(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // type
  static boolean parentType(PsiBuilder builder_, int level_) {
    return type(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // <<ArgType parentType>> { ',' <<ArgType parentType>> }*
  public static boolean parentTypes(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parentTypes")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PARENT_TYPES, "<parent types>");
    result_ = ArgType(builder_, level_ + 1, LuaCatsParser::parentType);
    result_ = result_ && parentTypes_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // { ',' <<ArgType parentType>> }*
  private static boolean parentTypes_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parentTypes_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!parentTypes_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "parentTypes_1", pos_)) break;
    }
    return true;
  }

  // ',' <<ArgType parentType>>
  private static boolean parentTypes_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parentTypes_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ",");
    result_ = result_ && ArgType(builder_, level_ + 1, LuaCatsParser::parentType);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // '@private' description?
  public static boolean privateTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "privateTag")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PRIVATE_TAG, "<private tag>");
    result_ = consumeToken(builder_, "@private");
    result_ = result_ && privateTag_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // description?
  private static boolean privateTag_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "privateTag_1")) return false;
    description(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // '@protected' description?
  public static boolean protectedTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "protectedTag")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PROTECTED_TAG, "<protected tag>");
    result_ = consumeToken(builder_, "@protected");
    result_ = result_ && protectedTag_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // description?
  private static boolean protectedTag_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "protectedTag_1")) return false;
    description(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // (NAME | NUMBER | STRING | (!(',' returnTypeDescriptor) SYMBOL) | TEXT)+
  public static boolean returnDescription(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "returnDescription")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, RETURN_DESCRIPTION, "<return description>");
    result_ = returnDescription_0(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!returnDescription_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "returnDescription", pos_)) break;
    }
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // NAME | NUMBER | STRING | (!(',' returnTypeDescriptor) SYMBOL) | TEXT
  private static boolean returnDescription_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "returnDescription_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NAME);
    if (!result_) result_ = consumeToken(builder_, NUMBER);
    if (!result_) result_ = consumeToken(builder_, STRING);
    if (!result_) result_ = returnDescription_0_3(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, TEXT);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // !(',' returnTypeDescriptor) SYMBOL
  private static boolean returnDescription_0_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "returnDescription_0_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = returnDescription_0_3_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, SYMBOL);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // !(',' returnTypeDescriptor)
  private static boolean returnDescription_0_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "returnDescription_0_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !returnDescription_0_3_0_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // ',' returnTypeDescriptor
  private static boolean returnDescription_0_3_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "returnDescription_0_3_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ",");
    result_ = result_ && returnTypeDescriptor(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // '@return' returnTypeDescriptor { ',' returnTypeDescriptor }*
  public static boolean returnTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "returnTag")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, RETURN_TAG, "<return tag>");
    result_ = consumeToken(builder_, "@return");
    result_ = result_ && returnTypeDescriptor(builder_, level_ + 1);
    result_ = result_ && returnTag_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // { ',' returnTypeDescriptor }*
  private static boolean returnTag_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "returnTag_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!returnTag_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "returnTag_2", pos_)) break;
    }
    return true;
  }

  // ',' returnTypeDescriptor
  private static boolean returnTag_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "returnTag_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ",");
    result_ = result_ && returnTypeDescriptor(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // <<ArgType type>> [(<<ArgName NAME>>? '#' returnDescription? ) | ( <<ArgName NAME>> returnDescription ? ) | returnDescription]
  public static boolean returnTypeDescriptor(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "returnTypeDescriptor")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, RETURN_TYPE_DESCRIPTOR, "<return type descriptor>");
    result_ = ArgType(builder_, level_ + 1, LuaCatsParser::type);
    result_ = result_ && returnTypeDescriptor_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // [(<<ArgName NAME>>? '#' returnDescription? ) | ( <<ArgName NAME>> returnDescription ? ) | returnDescription]
  private static boolean returnTypeDescriptor_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "returnTypeDescriptor_1")) return false;
    returnTypeDescriptor_1_0(builder_, level_ + 1);
    return true;
  }

  // (<<ArgName NAME>>? '#' returnDescription? ) | ( <<ArgName NAME>> returnDescription ? ) | returnDescription
  private static boolean returnTypeDescriptor_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "returnTypeDescriptor_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = returnTypeDescriptor_1_0_0(builder_, level_ + 1);
    if (!result_) result_ = returnTypeDescriptor_1_0_1(builder_, level_ + 1);
    if (!result_) result_ = returnDescription(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // <<ArgName NAME>>? '#' returnDescription?
  private static boolean returnTypeDescriptor_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "returnTypeDescriptor_1_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = returnTypeDescriptor_1_0_0_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, "#");
    result_ = result_ && returnTypeDescriptor_1_0_0_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // <<ArgName NAME>>?
  private static boolean returnTypeDescriptor_1_0_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "returnTypeDescriptor_1_0_0_0")) return false;
    ArgName(builder_, level_ + 1, NAME_parser_);
    return true;
  }

  // returnDescription?
  private static boolean returnTypeDescriptor_1_0_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "returnTypeDescriptor_1_0_0_2")) return false;
    returnDescription(builder_, level_ + 1);
    return true;
  }

  // <<ArgName NAME>> returnDescription ?
  private static boolean returnTypeDescriptor_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "returnTypeDescriptor_1_0_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ArgName(builder_, level_ + 1, NAME_parser_);
    result_ = result_ && returnTypeDescriptor_1_0_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // returnDescription ?
  private static boolean returnTypeDescriptor_1_0_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "returnTypeDescriptor_1_0_1_1")) return false;
    returnDescription(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // comment
  static boolean root(PsiBuilder builder_, int level_) {
    return comment(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // '@see' <<ArgName NAME>> description?
  public static boolean seeTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "seeTag")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SEE_TAG, "<see tag>");
    result_ = consumeToken(builder_, "@see");
    result_ = result_ && ArgName(builder_, level_ + 1, NAME_parser_);
    result_ = result_ && seeTag_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // description?
  private static boolean seeTag_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "seeTag_2")) return false;
    description(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // builtinType | namedType
  static boolean simpleType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "simpleType")) return false;
    boolean result_;
    result_ = builtinType(builder_, level_ + 1);
    if (!result_) result_ = namedType(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // '@source' <<ArgValue STRING>> description?
  public static boolean sourceTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "sourceTag")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SOURCE_TAG, "<source tag>");
    result_ = consumeToken(builder_, "@source");
    result_ = result_ && ArgValue(builder_, level_ + 1, STRING_parser_);
    result_ = result_ && sourceTag_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // description?
  private static boolean sourceTag_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "sourceTag_2")) return false;
    description(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // tableLiteralEntry { ',' tableLiteralEntry }*
  static boolean tableLiteralEntries(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "tableLiteralEntries")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = tableLiteralEntry(builder_, level_ + 1);
    result_ = result_ && tableLiteralEntries_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // { ',' tableLiteralEntry }*
  private static boolean tableLiteralEntries_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "tableLiteralEntries_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!tableLiteralEntries_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "tableLiteralEntries_1", pos_)) break;
    }
    return true;
  }

  // ',' tableLiteralEntry
  private static boolean tableLiteralEntries_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "tableLiteralEntries_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ",");
    result_ = result_ && tableLiteralEntry(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // simpleType ':' type
  public static boolean tableLiteralEntry(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "tableLiteralEntry")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, TABLE_LITERAL_ENTRY, "<table literal entry>");
    result_ = simpleType(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, ":");
    result_ = result_ && type(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // '[' type { ',' type }* ']'
  public static boolean tupleType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "tupleType")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, TUPLE_TYPE, "<tuple type>");
    result_ = consumeToken(builder_, "[");
    result_ = result_ && type(builder_, level_ + 1);
    result_ = result_ && tupleType_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, "]");
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // { ',' type }*
  private static boolean tupleType_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "tupleType_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!tupleType_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "tupleType_2", pos_)) break;
    }
    return true;
  }

  // ',' type
  private static boolean tupleType_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "tupleType_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ",");
    result_ = result_ && type(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // '(' unionType ')' | unionType
  public static boolean type(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, TYPE, "<type>");
    result_ = type_0(builder_, level_ + 1);
    if (!result_) result_ = unionType(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // '(' unionType ')'
  private static boolean type_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, "(");
    result_ = result_ && unionType(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, ")");
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // parameterizedName | NAME
  static boolean typeName(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typeName")) return false;
    if (!nextTokenIs(builder_, NAME)) return false;
    boolean result_;
    result_ = parameterizedName(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, NAME);
    return result_;
  }

  /* ********************************************************** */
  // '|' <<ArgValue (STRING | CODE | NUMBER | NAME)>> [ '#' description ]
  public static boolean typeOption(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typeOption")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, TYPE_OPTION, "<type option>");
    result_ = consumeToken(builder_, "|");
    result_ = result_ && ArgValue(builder_, level_ + 1, LuaCatsParser::typeOption_1_0);
    result_ = result_ && typeOption_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // STRING | CODE | NUMBER | NAME
  private static boolean typeOption_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typeOption_1_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, STRING);
    if (!result_) result_ = consumeToken(builder_, CODE);
    if (!result_) result_ = consumeToken(builder_, NUMBER);
    if (!result_) result_ = consumeToken(builder_, NAME);
    return result_;
  }

  // [ '#' description ]
  private static boolean typeOption_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typeOption_2")) return false;
    typeOption_2_0(builder_, level_ + 1);
    return true;
  }

  // '#' description
  private static boolean typeOption_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typeOption_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, "#");
    result_ = result_ && description(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // NAME
  public static boolean typeParam(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typeParam")) return false;
    if (!nextTokenIs(builder_, NAME)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NAME);
    exit_section_(builder_, marker_, TYPE_PARAM, result_);
    return result_;
  }

  /* ********************************************************** */
  // '@type' <<ArgType type>> description?
  public static boolean typeTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typeTag")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, TYPE_TAG, "<type tag>");
    result_ = consumeToken(builder_, "@type");
    result_ = result_ && ArgType(builder_, level_ + 1, LuaCatsParser::type);
    result_ = result_ && typeTag_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // description?
  private static boolean typeTag_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typeTag_2")) return false;
    description(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // arrayType { '|' arrayType }*
  public static boolean unionType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unionType")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, UNION_TYPE, "<union type>");
    result_ = arrayType(builder_, level_ + 1);
    result_ = result_ && unionType_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // { '|' arrayType }*
  private static boolean unionType_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unionType_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!unionType_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "unionType_1", pos_)) break;
    }
    return true;
  }

  // '|' arrayType
  private static boolean unionType_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unionType_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, "|");
    result_ = result_ && arrayType(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // '@vararg' <<ArgType type>> description?
  public static boolean varargTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varargTag")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, VARARG_TAG, "<vararg tag>");
    result_ = consumeToken(builder_, "@vararg");
    result_ = result_ && ArgType(builder_, level_ + 1, LuaCatsParser::type);
    result_ = result_ && varargTag_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // description?
  private static boolean varargTag_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varargTag_2")) return false;
    description(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // '<' | '>'
  public static boolean versionComparison(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "versionComparison")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, VERSION_COMPARISON, "<version comparison>");
    result_ = consumeToken(builder_, "<");
    if (!result_) result_ = consumeToken(builder_, ">");
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // '5.1' | '5.2' | '5.3' | '5.4' | 'JIT'
  public static boolean versionNumber(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "versionNumber")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, VERSION_NUMBER, "<version number>");
    result_ = consumeToken(builder_, "5.1");
    if (!result_) result_ = consumeToken(builder_, "5.2");
    if (!result_) result_ = consumeToken(builder_, "5.3");
    if (!result_) result_ = consumeToken(builder_, "5.4");
    if (!result_) result_ = consumeToken(builder_, "JIT");
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // <<ArgSymbol versionComparison>>? <<ArgValue versionNumber>>
  public static boolean versionSpec(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "versionSpec")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, VERSION_SPEC, "<version spec>");
    result_ = versionSpec_0(builder_, level_ + 1);
    result_ = result_ && ArgValue(builder_, level_ + 1, LuaCatsParser::versionNumber);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // <<ArgSymbol versionComparison>>?
  private static boolean versionSpec_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "versionSpec_0")) return false;
    ArgSymbol(builder_, level_ + 1, LuaCatsParser::versionComparison);
    return true;
  }

  /* ********************************************************** */
  // versionSpec { <<ArgSymbol (',')>> versionSpec }*
  public static boolean versionSpecs(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "versionSpecs")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, VERSION_SPECS, "<version specs>");
    result_ = versionSpec(builder_, level_ + 1);
    result_ = result_ && versionSpecs_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // { <<ArgSymbol (',')>> versionSpec }*
  private static boolean versionSpecs_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "versionSpecs_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!versionSpecs_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "versionSpecs_1", pos_)) break;
    }
    return true;
  }

  // <<ArgSymbol (',')>> versionSpec
  private static boolean versionSpecs_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "versionSpecs_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ArgSymbol(builder_, level_ + 1, LuaCatsParser::versionSpecs_1_0_0_0);
    result_ = result_ && versionSpec(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (',')
  private static boolean versionSpecs_1_0_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "versionSpecs_1_0_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ",");
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // '@version' versionSpecs description?
  public static boolean versionTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "versionTag")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, VERSION_TAG, "<version tag>");
    result_ = consumeToken(builder_, "@version");
    result_ = result_ && versionSpecs(builder_, level_ + 1);
    result_ = result_ && versionTag_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // description?
  private static boolean versionTag_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "versionTag_2")) return false;
    description(builder_, level_ + 1);
    return true;
  }

  static final Parser NAME_parser_ = (builder_, level_) -> consumeToken(builder_, NAME);
  static final Parser STRING_parser_ = (builder_, level_) -> consumeToken(builder_, STRING);
}
