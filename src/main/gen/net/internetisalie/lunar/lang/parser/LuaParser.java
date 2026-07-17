// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.lang.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static net.internetisalie.lunar.lang.psi.LuaElementTypes.*;
import static net.internetisalie.lunar.lang.parser.LuaParserUtil.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class LuaParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType root_, PsiBuilder builder_) {
    parseLight(root_, builder_);
    return builder_.getTreeBuilt();
  }

  public void parseLight(IElementType root_, PsiBuilder builder_) {
    boolean result_;
    builder_ = adapt_builder_(root_, builder_, this, EXTENDS_SETS_);
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

  public static final TokenSet[] EXTENDS_SETS_ = new TokenSet[] {
    create_token_set_(BIN_OP_EXPR, EXPR, FUNC_CALL, FUNC_DEF,
      PREFIX_EXPR, TABLE_CONSTRUCTOR, TERMINAL_EXPR, UN_OP_EXPR),
    create_token_set_(ASSIGNMENT_STATEMENT, BREAK_STATEMENT, DO_STATEMENT, EMPTY_STATEMENT,
      EXPR_STATEMENT, FINAL_STATEMENT, FUNC_DECL, GENERIC_FOR_STATEMENT,
      GLOBAL_FUNC_DECL, GLOBAL_MODE_DECL, GLOBAL_VAR_DECL, GOTO_STATEMENT,
      IF_STATEMENT, LABEL, LOCAL_FUNC_DECL, LOCAL_VAR_DECL,
      NUMERIC_FOR_STATEMENT, REPEAT_STATEMENT, STATEMENT, WHILE_STATEMENT),
  };

  /* ********************************************************** */
  // '(' [exprList] ')' | tableConstructor | STRING
  public static boolean args(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "args")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ARGS, "<args>");
    result_ = args_0(builder_, level_ + 1);
    if (!result_) result_ = tableConstructor(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, STRING);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // '(' [exprList] ')'
  private static boolean args_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "args_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && args_0_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [exprList]
  private static boolean args_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "args_0_1")) return false;
    exprList(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // varList '=' exprList
  public static boolean assignmentStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "assignmentStatement")) return false;
    if (!nextTokenIs(builder_, "<assignment statement>", IDENTIFIER, LPAREN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ASSIGNMENT_STATEMENT, "<assignment statement>");
    result_ = varList(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, ASSIGN);
    result_ = result_ && exprList(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // nameRef attrib?
  public static boolean attName(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "attName")) return false;
    if (!nextTokenIs(builder_, IDENTIFIER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = nameRef(builder_, level_ + 1);
    result_ = result_ && attName_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, ATT_NAME, result_);
    return result_;
  }

  // attrib?
  private static boolean attName_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "attName_1")) return false;
    attrib(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // attName {',' attName}*
  static boolean attNameList(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "attNameList")) return false;
    if (!nextTokenIs(builder_, IDENTIFIER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = attName(builder_, level_ + 1);
    result_ = result_ && attNameList_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // {',' attName}*
  private static boolean attNameList_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "attNameList_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!attNameList_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "attNameList_1", pos_)) break;
    }
    return true;
  }

  // ',' attName
  private static boolean attNameList_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "attNameList_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && attName(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // '<' attribName '>'
  public static boolean attrib(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "attrib")) return false;
    if (!nextTokenIs(builder_, LT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LT);
    result_ = result_ && attribName(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, GT);
    exit_section_(builder_, marker_, ATTRIB, result_);
    return result_;
  }

  /* ********************************************************** */
  // 'const' | 'close'
  public static boolean attribName(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "attribName")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ATTRIB_NAME, "<attrib name>");
    result_ = consumeToken(builder_, "const");
    if (!result_) result_ = consumeToken(builder_, "close");
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // '+' | '-' | '*' | '/' | '//' | '^' | '%' |
  //     '&' | '~' | '|' | '>>' | '<<' | '..' |
  //     '<' | '<=' | '>' | '>=' | '==' | '~=' |
  //     AND | OR
  public static boolean binOp(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "binOp")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, BIN_OP, "<bin op>");
    result_ = consumeToken(builder_, PLUS);
    if (!result_) result_ = consumeToken(builder_, MINUS);
    if (!result_) result_ = consumeToken(builder_, MULT);
    if (!result_) result_ = consumeToken(builder_, DIV);
    if (!result_) result_ = consumeToken(builder_, INTDIV);
    if (!result_) result_ = consumeToken(builder_, EXP);
    if (!result_) result_ = consumeToken(builder_, MOD);
    if (!result_) result_ = consumeToken(builder_, AMP);
    if (!result_) result_ = consumeToken(builder_, NEG);
    if (!result_) result_ = consumeToken(builder_, PIPE);
    if (!result_) result_ = consumeToken(builder_, BSR);
    if (!result_) result_ = consumeToken(builder_, BSL);
    if (!result_) result_ = consumeToken(builder_, CONCAT);
    if (!result_) result_ = consumeToken(builder_, LT);
    if (!result_) result_ = consumeToken(builder_, LE);
    if (!result_) result_ = consumeToken(builder_, GT);
    if (!result_) result_ = consumeToken(builder_, GE);
    if (!result_) result_ = consumeToken(builder_, EQ);
    if (!result_) result_ = consumeToken(builder_, NE);
    if (!result_) result_ = consumeToken(builder_, AND);
    if (!result_) result_ = consumeToken(builder_, OR);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // <<choices>>
  public static boolean binOps(PsiBuilder builder_, int level_, Parser choices) {
    if (!recursion_guard_(builder_, level_, "binOps")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, BIN_OP, null);
    result_ = choices.parse(builder_, level_);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // not_eof {statement}* [finalStatement SEMI?]
  public static boolean block(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, BLOCK, "<block>");
    result_ = not_eof(builder_, level_ + 1);
    result_ = result_ && block_1(builder_, level_ + 1);
    result_ = result_ && block_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // {statement}*
  private static boolean block_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!block_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "block_1", pos_)) break;
    }
    return true;
  }

  // {statement}
  private static boolean block_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = statement(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [finalStatement SEMI?]
  private static boolean block_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_2")) return false;
    block_2_0(builder_, level_ + 1);
    return true;
  }

  // finalStatement SEMI?
  private static boolean block_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = finalStatement(builder_, level_ + 1);
    result_ = result_ && block_2_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // SEMI?
  private static boolean block_2_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_2_0_1")) return false;
    consumeToken(builder_, SEMI);
    return true;
  }

  /* ********************************************************** */
  // BREAK
  public static boolean breakStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "breakStatement")) return false;
    if (!nextTokenIs(builder_, BREAK)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, BREAK);
    exit_section_(builder_, marker_, BREAK_STATEMENT, result_);
    return result_;
  }

  /* ********************************************************** */
  // DO block END
  public static boolean doStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "doStatement")) return false;
    if (!nextTokenIs(builder_, DO)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, DO_STATEMENT, null);
    result_ = consumeToken(builder_, DO);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, block(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, END) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // ';'
  public static boolean emptyStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "emptyStatement")) return false;
    if (!nextTokenIs(builder_, SEMI)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SEMI);
    exit_section_(builder_, marker_, EMPTY_STATEMENT, result_);
    return result_;
  }

  /* ********************************************************** */
  // expr {',' expr}*
  public static boolean exprList(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "exprList")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, EXPR_LIST, "<expr list>");
    result_ = expr(builder_, level_ + 1, -1);
    result_ = result_ && exprList_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // {',' expr}*
  private static boolean exprList_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "exprList_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!exprList_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "exprList_1", pos_)) break;
    }
    return true;
  }

  // ',' expr
  private static boolean exprList_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "exprList_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && expr(builder_, level_ + 1, -1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // expr
  public static boolean exprStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "exprStatement")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, EXPR_STATEMENT, "<expr statement>");
    result_ = expr(builder_, level_ + 1, -1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // '[' expr ']' '=' expr | IDENTIFIER '=' expr | expr
  public static boolean field(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "field")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FIELD, "<field>");
    result_ = field_0(builder_, level_ + 1);
    if (!result_) result_ = field_1(builder_, level_ + 1);
    if (!result_) result_ = expr(builder_, level_ + 1, -1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // '[' expr ']' '=' expr
  private static boolean field_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "field_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LBRACK);
    result_ = result_ && expr(builder_, level_ + 1, -1);
    result_ = result_ && consumeTokens(builder_, 0, RBRACK, ASSIGN);
    result_ = result_ && expr(builder_, level_ + 1, -1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // IDENTIFIER '=' expr
  private static boolean field_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "field_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, IDENTIFIER, ASSIGN);
    result_ = result_ && expr(builder_, level_ + 1, -1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // field {fieldSep field}* [fieldSep]
  public static boolean fieldList(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fieldList")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FIELD_LIST, "<field list>");
    result_ = field(builder_, level_ + 1);
    result_ = result_ && fieldList_1(builder_, level_ + 1);
    result_ = result_ && fieldList_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // {fieldSep field}*
  private static boolean fieldList_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fieldList_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!fieldList_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "fieldList_1", pos_)) break;
    }
    return true;
  }

  // fieldSep field
  private static boolean fieldList_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fieldList_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = fieldSep(builder_, level_ + 1);
    result_ = result_ && field(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [fieldSep]
  private static boolean fieldList_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fieldList_2")) return false;
    fieldSep(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // ',' | ';'
  public static boolean fieldSep(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fieldSep")) return false;
    if (!nextTokenIs(builder_, "<field sep>", COMMA, SEMI)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FIELD_SEP, "<field sep>");
    result_ = consumeToken(builder_, COMMA);
    if (!result_) result_ = consumeToken(builder_, SEMI);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // RETURN [exprList] [';']
  public static boolean finalStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalStatement")) return false;
    if (!nextTokenIs(builder_, RETURN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, RETURN);
    result_ = result_ && finalStatement_1(builder_, level_ + 1);
    result_ = result_ && finalStatement_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, FINAL_STATEMENT, result_);
    return result_;
  }

  // [exprList]
  private static boolean finalStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalStatement_1")) return false;
    exprList(builder_, level_ + 1);
    return true;
  }

  // [';']
  private static boolean finalStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalStatement_2")) return false;
    consumeToken(builder_, SEMI);
    return true;
  }

  /* ********************************************************** */
  // '(' [parList] ')' block END
  static boolean funcBody(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "funcBody")) return false;
    if (!nextTokenIs(builder_, LPAREN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && funcBody_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    result_ = result_ && block(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, END);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [parList]
  private static boolean funcBody_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "funcBody_1")) return false;
    parList(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // FUNCTION funcName funcBody
  public static boolean funcDecl(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "funcDecl")) return false;
    if (!nextTokenIs(builder_, FUNCTION)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FUNC_DECL, null);
    result_ = consumeToken(builder_, FUNCTION);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, funcName(builder_, level_ + 1));
    result_ = pinned_ && funcBody(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // nameRef funcNameProperty* funcNameMethod?
  public static boolean funcName(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "funcName")) return false;
    if (!nextTokenIs(builder_, IDENTIFIER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = nameRef(builder_, level_ + 1);
    result_ = result_ && funcName_1(builder_, level_ + 1);
    result_ = result_ && funcName_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, FUNC_NAME, result_);
    return result_;
  }

  // funcNameProperty*
  private static boolean funcName_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "funcName_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!funcNameProperty(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "funcName_1", pos_)) break;
    }
    return true;
  }

  // funcNameMethod?
  private static boolean funcName_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "funcName_2")) return false;
    funcNameMethod(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // ':' nameRef
  public static boolean funcNameMethod(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "funcNameMethod")) return false;
    if (!nextTokenIs(builder_, COLON)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && nameRef(builder_, level_ + 1);
    exit_section_(builder_, marker_, FUNC_NAME_METHOD, result_);
    return result_;
  }

  /* ********************************************************** */
  // '.' nameRef
  public static boolean funcNameProperty(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "funcNameProperty")) return false;
    if (!nextTokenIs(builder_, DOT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOT);
    result_ = result_ && nameRef(builder_, level_ + 1);
    exit_section_(builder_, marker_, FUNC_NAME_PROPERTY, result_);
    return result_;
  }

  /* ********************************************************** */
  // FOR nameList IN exprList DO block END
  public static boolean genericForStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "genericForStatement")) return false;
    if (!nextTokenIs(builder_, FOR)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, GENERIC_FOR_STATEMENT, null);
    result_ = consumeToken(builder_, FOR);
    result_ = result_ && nameList(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, IN);
    pinned_ = result_; // pin = 3
    result_ = result_ && report_error_(builder_, exprList(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, consumeToken(builder_, DO)) && result_;
    result_ = pinned_ && report_error_(builder_, block(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, END) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // <<globalKeyword>> FUNCTION nameRef funcBody
  public static boolean globalFuncDecl(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "globalFuncDecl")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, GLOBAL_FUNC_DECL, "<global func decl>");
    result_ = globalKeyword(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, FUNCTION);
    pinned_ = result_; // pin = 2
    result_ = result_ && report_error_(builder_, nameRef(builder_, level_ + 1));
    result_ = pinned_ && funcBody(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // <<globalKeyword>> [attrib] '*'
  public static boolean globalModeDecl(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "globalModeDecl")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, GLOBAL_MODE_DECL, "<global mode decl>");
    result_ = globalKeyword(builder_, level_ + 1);
    result_ = result_ && globalModeDecl_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, MULT);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // [attrib]
  private static boolean globalModeDecl_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "globalModeDecl_1")) return false;
    attrib(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // <<globalKeyword>> attNameList ['=' exprList]
  public static boolean globalVarDecl(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "globalVarDecl")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, GLOBAL_VAR_DECL, "<global var decl>");
    result_ = globalKeyword(builder_, level_ + 1);
    result_ = result_ && attNameList(builder_, level_ + 1);
    result_ = result_ && globalVarDecl_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // ['=' exprList]
  private static boolean globalVarDecl_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "globalVarDecl_2")) return false;
    globalVarDecl_2_0(builder_, level_ + 1);
    return true;
  }

  // '=' exprList
  private static boolean globalVarDecl_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "globalVarDecl_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ASSIGN);
    result_ = result_ && exprList(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // GOTO labelRef
  public static boolean gotoStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "gotoStatement")) return false;
    if (!nextTokenIs(builder_, GOTO)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, GOTO);
    result_ = result_ && labelRef(builder_, level_ + 1);
    exit_section_(builder_, marker_, GOTO_STATEMENT, result_);
    return result_;
  }

  /* ********************************************************** */
  // IF expr THEN block {ELSEIF expr THEN block}* [ELSE block] END
  public static boolean ifStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ifStatement")) return false;
    if (!nextTokenIs(builder_, IF)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, IF_STATEMENT, null);
    result_ = consumeToken(builder_, IF);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, expr(builder_, level_ + 1, -1));
    result_ = pinned_ && report_error_(builder_, consumeToken(builder_, THEN)) && result_;
    result_ = pinned_ && report_error_(builder_, block(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, ifStatement_4(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, ifStatement_5(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, END) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // {ELSEIF expr THEN block}*
  private static boolean ifStatement_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ifStatement_4")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!ifStatement_4_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "ifStatement_4", pos_)) break;
    }
    return true;
  }

  // ELSEIF expr THEN block
  private static boolean ifStatement_4_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ifStatement_4_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ELSEIF);
    result_ = result_ && expr(builder_, level_ + 1, -1);
    result_ = result_ && consumeToken(builder_, THEN);
    result_ = result_ && block(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [ELSE block]
  private static boolean ifStatement_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ifStatement_5")) return false;
    ifStatement_5_0(builder_, level_ + 1);
    return true;
  }

  // ELSE block
  private static boolean ifStatement_5_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ifStatement_5_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ELSE);
    result_ = result_ && block(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // ('[' expr ']') | ('.' nameRef)
  public static boolean indexExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "indexExpr")) return false;
    if (!nextTokenIs(builder_, "<index expr>", DOT, LBRACK)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, INDEX_EXPR, "<index expr>");
    result_ = indexExpr_0(builder_, level_ + 1);
    if (!result_) result_ = indexExpr_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // '[' expr ']'
  private static boolean indexExpr_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "indexExpr_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LBRACK);
    result_ = result_ && expr(builder_, level_ + 1, -1);
    result_ = result_ && consumeToken(builder_, RBRACK);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // '.' nameRef
  private static boolean indexExpr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "indexExpr_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOT);
    result_ = result_ && nameRef(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // '::' labelName '::'
  public static boolean label(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "label")) return false;
    if (!nextTokenIs(builder_, MARKER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, MARKER);
    result_ = result_ && labelName(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, MARKER);
    exit_section_(builder_, marker_, LABEL, result_);
    return result_;
  }

  /* ********************************************************** */
  // IDENTIFIER
  public static boolean labelName(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "labelName")) return false;
    if (!nextTokenIs(builder_, IDENTIFIER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, IDENTIFIER);
    exit_section_(builder_, marker_, LABEL_NAME, result_);
    return result_;
  }

  /* ********************************************************** */
  // IDENTIFIER
  public static boolean labelRef(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "labelRef")) return false;
    if (!nextTokenIs(builder_, IDENTIFIER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, IDENTIFIER);
    exit_section_(builder_, marker_, LABEL_REF, result_);
    return result_;
  }

  /* ********************************************************** */
  // LOCAL FUNCTION nameRef funcBody
  public static boolean localFuncDecl(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "localFuncDecl")) return false;
    if (!nextTokenIs(builder_, LOCAL)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, LOCAL_FUNC_DECL, null);
    result_ = consumeTokens(builder_, 2, LOCAL, FUNCTION);
    pinned_ = result_; // pin = 2
    result_ = result_ && report_error_(builder_, nameRef(builder_, level_ + 1));
    result_ = pinned_ && funcBody(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // LOCAL attNameList ['=' exprList]
  public static boolean localVarDecl(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "localVarDecl")) return false;
    if (!nextTokenIs(builder_, LOCAL)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LOCAL);
    result_ = result_ && attNameList(builder_, level_ + 1);
    result_ = result_ && localVarDecl_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, LOCAL_VAR_DECL, result_);
    return result_;
  }

  // ['=' exprList]
  private static boolean localVarDecl_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "localVarDecl_2")) return false;
    localVarDecl_2_0(builder_, level_ + 1);
    return true;
  }

  // '=' exprList
  private static boolean localVarDecl_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "localVarDecl_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ASSIGN);
    result_ = result_ && exprList(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // ':' nameRef
  public static boolean methodExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "methodExpr")) return false;
    if (!nextTokenIs(builder_, COLON)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && nameRef(builder_, level_ + 1);
    exit_section_(builder_, marker_, METHOD_EXPR, result_);
    return result_;
  }

  /* ********************************************************** */
  // methodExpr? args
  public static boolean nameAndArgs(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nameAndArgs")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, NAME_AND_ARGS, "<name and args>");
    result_ = nameAndArgs_0(builder_, level_ + 1);
    result_ = result_ && args(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // methodExpr?
  private static boolean nameAndArgs_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nameAndArgs_0")) return false;
    methodExpr(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // nameRef {',' nameRef}*
  public static boolean nameList(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nameList")) return false;
    if (!nextTokenIs(builder_, IDENTIFIER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = nameRef(builder_, level_ + 1);
    result_ = result_ && nameList_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, NAME_LIST, result_);
    return result_;
  }

  // {',' nameRef}*
  private static boolean nameList_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nameList_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!nameList_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "nameList_1", pos_)) break;
    }
    return true;
  }

  // ',' nameRef
  private static boolean nameList_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nameList_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && nameRef(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // IDENTIFIER
  public static boolean nameRef(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nameRef")) return false;
    if (!nextTokenIs(builder_, IDENTIFIER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, IDENTIFIER);
    exit_section_(builder_, marker_, NAME_REF, result_);
    return result_;
  }

  /* ********************************************************** */
  // !<<eof>>
  static boolean not_eof(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "not_eof")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !eof(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // FOR IDENTIFIER '=' expr ',' expr [',' expr] DO block END
  public static boolean numericForStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "numericForStatement")) return false;
    if (!nextTokenIs(builder_, FOR)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, NUMERIC_FOR_STATEMENT, null);
    result_ = consumeTokens(builder_, 3, FOR, IDENTIFIER, ASSIGN);
    pinned_ = result_; // pin = 3
    result_ = result_ && report_error_(builder_, expr(builder_, level_ + 1, -1));
    result_ = pinned_ && report_error_(builder_, consumeToken(builder_, COMMA)) && result_;
    result_ = pinned_ && report_error_(builder_, expr(builder_, level_ + 1, -1)) && result_;
    result_ = pinned_ && report_error_(builder_, numericForStatement_6(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, consumeToken(builder_, DO)) && result_;
    result_ = pinned_ && report_error_(builder_, block(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, END) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // [',' expr]
  private static boolean numericForStatement_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "numericForStatement_6")) return false;
    numericForStatement_6_0(builder_, level_ + 1);
    return true;
  }

  // ',' expr
  private static boolean numericForStatement_6_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "numericForStatement_6_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && expr(builder_, level_ + 1, -1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // nameList [',' '...'] | '...'
  public static boolean parList(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parList")) return false;
    if (!nextTokenIs(builder_, "<par list>", ELLIPSIS, IDENTIFIER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PAR_LIST, "<par list>");
    result_ = parList_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, ELLIPSIS);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // nameList [',' '...']
  private static boolean parList_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parList_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = nameList(builder_, level_ + 1);
    result_ = result_ && parList_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [',' '...']
  private static boolean parList_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parList_0_1")) return false;
    parseTokens(builder_, 0, COMMA, ELLIPSIS);
    return true;
  }

  /* ********************************************************** */
  // REPEAT block UNTIL expr
  public static boolean repeatStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "repeatStatement")) return false;
    if (!nextTokenIs(builder_, REPEAT)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, REPEAT_STATEMENT, null);
    result_ = consumeToken(builder_, REPEAT);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, block(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, consumeToken(builder_, UNTIL)) && result_;
    result_ = pinned_ && expr(builder_, level_ + 1, -1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // block*
  static boolean root(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "root")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!block(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "root", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // emptyStatement
  //     | globalFuncDecl
  //     | globalModeDecl
  //     | globalVarDecl
  //     | assignmentStatement
  //     | exprStatement
  //     | label
  //     | breakStatement
  //     | gotoStatement
  //     | doStatement
  //     | whileStatement
  //     | repeatStatement
  //     | ifStatement
  //     | numericForStatement
  //     | genericForStatement
  //     | funcDecl
  //     | localFuncDecl
  //     | localVarDecl
  public static boolean statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "statement")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, STATEMENT, "<statement>");
    result_ = emptyStatement(builder_, level_ + 1);
    if (!result_) result_ = globalFuncDecl(builder_, level_ + 1);
    if (!result_) result_ = globalModeDecl(builder_, level_ + 1);
    if (!result_) result_ = globalVarDecl(builder_, level_ + 1);
    if (!result_) result_ = assignmentStatement(builder_, level_ + 1);
    if (!result_) result_ = exprStatement(builder_, level_ + 1);
    if (!result_) result_ = label(builder_, level_ + 1);
    if (!result_) result_ = breakStatement(builder_, level_ + 1);
    if (!result_) result_ = gotoStatement(builder_, level_ + 1);
    if (!result_) result_ = doStatement(builder_, level_ + 1);
    if (!result_) result_ = whileStatement(builder_, level_ + 1);
    if (!result_) result_ = repeatStatement(builder_, level_ + 1);
    if (!result_) result_ = ifStatement(builder_, level_ + 1);
    if (!result_) result_ = numericForStatement(builder_, level_ + 1);
    if (!result_) result_ = genericForStatement(builder_, level_ + 1);
    if (!result_) result_ = funcDecl(builder_, level_ + 1);
    if (!result_) result_ = localFuncDecl(builder_, level_ + 1);
    if (!result_) result_ = localVarDecl(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // '-' | NOT | '#' | '~'
  public static boolean unOp(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unOp")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, UN_OP, "<un op>");
    result_ = consumeToken(builder_, MINUS);
    if (!result_) result_ = consumeToken(builder_, NOT);
    if (!result_) result_ = consumeToken(builder_, GETN);
    if (!result_) result_ = consumeToken(builder_, NEG);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // nameRef varSuffix*
  //     | '(' expr ')' varSuffix+
  public static boolean var_$(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "var_$")) return false;
    if (!nextTokenIs(builder_, "<var>", IDENTIFIER, LPAREN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, VAR, "<var>");
    result_ = var_0(builder_, level_ + 1);
    if (!result_) result_ = var_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // nameRef varSuffix*
  private static boolean var_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "var_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = nameRef(builder_, level_ + 1);
    result_ = result_ && var_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // varSuffix*
  private static boolean var_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "var_0_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!varSuffix(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "var_0_1", pos_)) break;
    }
    return true;
  }

  // '(' expr ')' varSuffix+
  private static boolean var_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "var_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && expr(builder_, level_ + 1, -1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    result_ = result_ && var_1_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // varSuffix+
  private static boolean var_1_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "var_1_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = varSuffix(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!varSuffix(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "var_1_3", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // var {',' var}*
  public static boolean varList(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varList")) return false;
    if (!nextTokenIs(builder_, "<var list>", IDENTIFIER, LPAREN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, VAR_LIST, "<var list>");
    result_ = var_$(builder_, level_ + 1);
    result_ = result_ && varList_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // {',' var}*
  private static boolean varList_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varList_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!varList_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "varList_1", pos_)) break;
    }
    return true;
  }

  // ',' var
  private static boolean varList_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varList_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && var_$(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // var | '(' expr ')'
  public static boolean varOrExp(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varOrExp")) return false;
    if (!nextTokenIs(builder_, "<var or exp>", IDENTIFIER, LPAREN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, VAR_OR_EXP, "<var or exp>");
    result_ = var_$(builder_, level_ + 1);
    if (!result_) result_ = varOrExp_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // '(' expr ')'
  private static boolean varOrExp_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varOrExp_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && expr(builder_, level_ + 1, -1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // nameAndArgs* indexExpr
  public static boolean varSuffix(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varSuffix")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, VAR_SUFFIX, "<var suffix>");
    result_ = varSuffix_0(builder_, level_ + 1);
    result_ = result_ && indexExpr(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // nameAndArgs*
  private static boolean varSuffix_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varSuffix_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!nameAndArgs(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "varSuffix_0", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // WHILE expr DO block END
  public static boolean whileStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "whileStatement")) return false;
    if (!nextTokenIs(builder_, WHILE)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, WHILE_STATEMENT, null);
    result_ = consumeToken(builder_, WHILE);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, expr(builder_, level_ + 1, -1));
    result_ = pinned_ && report_error_(builder_, consumeToken(builder_, DO)) && result_;
    result_ = pinned_ && report_error_(builder_, block(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, END) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // Expression root: expr
  // Operator priority table:
  // 0: ATOM(terminalExpr)
  // 1: ATOM(funcDef)
  // 2: ATOM(funcCall)
  // 3: ATOM(prefixExpr)
  // 4: ATOM(tableConstructor)
  // 5: BINARY(orBinOpExpr)
  // 6: BINARY(andBinOpExpr)
  // 7: BINARY(relBinOpExpr)
  // 8: BINARY(borBinOpExpr)
  // 9: BINARY(bnotBinOpExpr)
  // 10: BINARY(bandBinOpExpr)
  // 11: BINARY(bshiftBinOpExpr)
  // 12: BINARY(concatBinOpExpr)
  // 13: BINARY(addBinOpExpr)
  // 14: BINARY(mulBinOpExpr)
  // 15: PREFIX(unOpExpr)
  // 16: BINARY(expBinOpExpr)
  public static boolean expr(PsiBuilder builder_, int level_, int priority_) {
    if (!recursion_guard_(builder_, level_, "expr")) return false;
    addVariant(builder_, "<expr>");
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, "<expr>");
    result_ = terminalExpr(builder_, level_ + 1);
    if (!result_) result_ = funcDef(builder_, level_ + 1);
    if (!result_) result_ = funcCall(builder_, level_ + 1);
    if (!result_) result_ = prefixExpr(builder_, level_ + 1);
    if (!result_) result_ = tableConstructor(builder_, level_ + 1);
    if (!result_) result_ = unOpExpr(builder_, level_ + 1);
    pinned_ = result_;
    result_ = result_ && expr_0(builder_, level_ + 1, priority_);
    exit_section_(builder_, level_, marker_, null, result_, pinned_, null);
    return result_ || pinned_;
  }

  public static boolean expr_0(PsiBuilder builder_, int level_, int priority_) {
    if (!recursion_guard_(builder_, level_, "expr_0")) return false;
    boolean result_ = true;
    while (true) {
      Marker marker_ = enter_section_(builder_, level_, _LEFT_, null);
      if (priority_ < 5 && binOps(builder_, level_ + 1, OR_parser_)) {
        result_ = expr(builder_, level_, 5);
        exit_section_(builder_, level_, marker_, BIN_OP_EXPR, result_, true, null);
      }
      else if (priority_ < 6 && binOps(builder_, level_ + 1, AND_parser_)) {
        result_ = expr(builder_, level_, 6);
        exit_section_(builder_, level_, marker_, BIN_OP_EXPR, result_, true, null);
      }
      else if (priority_ < 7 && binOps(builder_, level_ + 1, LuaParser::relBinOpExpr_0_0)) {
        result_ = expr(builder_, level_, 7);
        exit_section_(builder_, level_, marker_, BIN_OP_EXPR, result_, true, null);
      }
      else if (priority_ < 8 && binOps(builder_, level_ + 1, PIPE_parser_)) {
        result_ = expr(builder_, level_, 8);
        exit_section_(builder_, level_, marker_, BIN_OP_EXPR, result_, true, null);
      }
      else if (priority_ < 9 && binOps(builder_, level_ + 1, NEG_parser_)) {
        result_ = expr(builder_, level_, 9);
        exit_section_(builder_, level_, marker_, BIN_OP_EXPR, result_, true, null);
      }
      else if (priority_ < 10 && binOps(builder_, level_ + 1, AMP_parser_)) {
        result_ = expr(builder_, level_, 10);
        exit_section_(builder_, level_, marker_, BIN_OP_EXPR, result_, true, null);
      }
      else if (priority_ < 11 && binOps(builder_, level_ + 1, LuaParser::bshiftBinOpExpr_0_0)) {
        result_ = expr(builder_, level_, 11);
        exit_section_(builder_, level_, marker_, BIN_OP_EXPR, result_, true, null);
      }
      else if (priority_ < 12 && binOps(builder_, level_ + 1, CONCAT_parser_)) {
        result_ = expr(builder_, level_, 11);
        exit_section_(builder_, level_, marker_, BIN_OP_EXPR, result_, true, null);
      }
      else if (priority_ < 13 && binOps(builder_, level_ + 1, LuaParser::addBinOpExpr_0_0)) {
        result_ = expr(builder_, level_, 13);
        exit_section_(builder_, level_, marker_, BIN_OP_EXPR, result_, true, null);
      }
      else if (priority_ < 14 && binOps(builder_, level_ + 1, LuaParser::mulBinOpExpr_0_0)) {
        result_ = expr(builder_, level_, 14);
        exit_section_(builder_, level_, marker_, BIN_OP_EXPR, result_, true, null);
      }
      else if (priority_ < 16 && binOps(builder_, level_ + 1, EXP_parser_)) {
        result_ = expr(builder_, level_, 15);
        exit_section_(builder_, level_, marker_, BIN_OP_EXPR, result_, true, null);
      }
      else {
        exit_section_(builder_, level_, marker_, null, false, false, null);
        break;
      }
    }
    return result_;
  }

  // NIL | FALSE | TRUE | NUMBER | STRING | ELLIPSIS
  public static boolean terminalExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "terminalExpr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, TERMINAL_EXPR, "<terminal expr>");
    result_ = consumeTokenSmart(builder_, NIL);
    if (!result_) result_ = consumeTokenSmart(builder_, FALSE);
    if (!result_) result_ = consumeTokenSmart(builder_, TRUE);
    if (!result_) result_ = consumeTokenSmart(builder_, NUMBER);
    if (!result_) result_ = consumeTokenSmart(builder_, STRING);
    if (!result_) result_ = consumeTokenSmart(builder_, ELLIPSIS);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // FUNCTION funcBody
  public static boolean funcDef(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "funcDef")) return false;
    if (!nextTokenIsSmart(builder_, FUNCTION)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokenSmart(builder_, FUNCTION);
    result_ = result_ && funcBody(builder_, level_ + 1);
    exit_section_(builder_, marker_, FUNC_DEF, result_);
    return result_;
  }

  // varOrExp nameAndArgs+
  public static boolean funcCall(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "funcCall")) return false;
    if (!nextTokenIsSmart(builder_, IDENTIFIER, LPAREN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FUNC_CALL, "<func call>");
    result_ = varOrExp(builder_, level_ + 1);
    result_ = result_ && funcCall_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // nameAndArgs+
  private static boolean funcCall_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "funcCall_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = nameAndArgs(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!nameAndArgs(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "funcCall_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // varOrExp
  public static boolean prefixExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "prefixExpr")) return false;
    if (!nextTokenIsSmart(builder_, IDENTIFIER, LPAREN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PREFIX_EXPR, "<prefix expr>");
    result_ = varOrExp(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // '{' [fieldList] '}'
  public static boolean tableConstructor(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "tableConstructor")) return false;
    if (!nextTokenIsSmart(builder_, LCURLY)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokenSmart(builder_, LCURLY);
    result_ = result_ && tableConstructor_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RCURLY);
    exit_section_(builder_, marker_, TABLE_CONSTRUCTOR, result_);
    return result_;
  }

  // [fieldList]
  private static boolean tableConstructor_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "tableConstructor_1")) return false;
    fieldList(builder_, level_ + 1);
    return true;
  }

  // '=='|'~='|'<'|'<='|'>='|'>'
  private static boolean relBinOpExpr_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "relBinOpExpr_0_0")) return false;
    boolean result_;
    result_ = consumeTokenSmart(builder_, EQ);
    if (!result_) result_ = consumeTokenSmart(builder_, NE);
    if (!result_) result_ = consumeTokenSmart(builder_, LT);
    if (!result_) result_ = consumeTokenSmart(builder_, LE);
    if (!result_) result_ = consumeTokenSmart(builder_, GE);
    if (!result_) result_ = consumeTokenSmart(builder_, GT);
    return result_;
  }

  // '<<'|'>>'
  private static boolean bshiftBinOpExpr_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bshiftBinOpExpr_0_0")) return false;
    boolean result_;
    result_ = consumeTokenSmart(builder_, BSL);
    if (!result_) result_ = consumeTokenSmart(builder_, BSR);
    return result_;
  }

  // '+'|'-'
  private static boolean addBinOpExpr_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "addBinOpExpr_0_0")) return false;
    boolean result_;
    result_ = consumeTokenSmart(builder_, PLUS);
    if (!result_) result_ = consumeTokenSmart(builder_, MINUS);
    return result_;
  }

  // '*'|'/'|'//'|'%'
  private static boolean mulBinOpExpr_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mulBinOpExpr_0_0")) return false;
    boolean result_;
    result_ = consumeTokenSmart(builder_, MULT);
    if (!result_) result_ = consumeTokenSmart(builder_, DIV);
    if (!result_) result_ = consumeTokenSmart(builder_, INTDIV);
    if (!result_) result_ = consumeTokenSmart(builder_, MOD);
    return result_;
  }

  public static boolean unOpExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unOpExpr")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, null);
    result_ = unOp(builder_, level_ + 1);
    pinned_ = result_;
    result_ = pinned_ && expr(builder_, level_, 15);
    exit_section_(builder_, level_, marker_, UN_OP_EXPR, result_, pinned_, null);
    return result_ || pinned_;
  }

  static final Parser AMP_parser_ = (builder_, level_) -> consumeToken(builder_, AMP);
  static final Parser AND_parser_ = (builder_, level_) -> consumeToken(builder_, AND);
  static final Parser CONCAT_parser_ = (builder_, level_) -> consumeToken(builder_, CONCAT);
  static final Parser EXP_parser_ = (builder_, level_) -> consumeToken(builder_, EXP);
  static final Parser NEG_parser_ = (builder_, level_) -> consumeToken(builder_, NEG);
  static final Parser OR_parser_ = (builder_, level_) -> consumeToken(builder_, OR);
  static final Parser PIPE_parser_ = (builder_, level_) -> consumeToken(builder_, PIPE);
}
