// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.lang.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static net.internetisalie.lunar.lang.psi.LuaElementTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class LuaParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType t, PsiBuilder b) {
    parseLight(t, b);
    return b.getTreeBuilt();
  }

  public void parseLight(IElementType t, PsiBuilder b) {
    boolean r;
    b = adapt_builder_(t, b, this, EXTENDS_SETS_);
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

  public static final TokenSet[] EXTENDS_SETS_ = new TokenSet[] {
    create_token_set_(BIN_OP_EXPR, EXPR, FUNC_CALL, FUNC_DEF,
      PREFIX_EXPR, TABLE_CONSTRUCTOR, TERMINAL_EXPR, UN_OP_EXPR),
    create_token_set_(ASSIGNMENT_STATEMENT, BREAK_STATEMENT, DO_STATEMENT, EMPTY_STATEMENT,
      EXPR_STATEMENT, FINAL_STATEMENT, FUNC_DECL, GENERIC_FOR_STATEMENT,
      GOTO_STATEMENT, IF_STATEMENT, LABEL, LOCAL_FUNC_DECL,
      LOCAL_VAR_DECL, NUMERIC_FOR_STATEMENT, REPEAT_STATEMENT, STATEMENT,
      WHILE_STATEMENT),
  };

  /* ********************************************************** */
  // '(' [exprList] ')' | tableConstructor | STRING
  public static boolean args(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "args")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ARGS, "<args>");
    r = args_0(b, l + 1);
    if (!r) r = tableConstructor(b, l + 1);
    if (!r) r = consumeToken(b, STRING);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '(' [exprList] ')'
  private static boolean args_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "args_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && args_0_1(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  // [exprList]
  private static boolean args_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "args_0_1")) return false;
    exprList(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // varList '=' exprList
  public static boolean assignmentStatement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assignmentStatement")) return false;
    if (!nextTokenIs(b, "<assignment statement>", IDENTIFIER, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ASSIGNMENT_STATEMENT, "<assignment statement>");
    r = varList(b, l + 1);
    r = r && consumeToken(b, ASSIGN);
    r = r && exprList(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // nameRef attrib?
  public static boolean attName(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "attName")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = nameRef(b, l + 1);
    r = r && attName_1(b, l + 1);
    exit_section_(b, m, ATT_NAME, r);
    return r;
  }

  // attrib?
  private static boolean attName_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "attName_1")) return false;
    attrib(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // attName {',' attName}*
  static boolean attNameList(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "attNameList")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = attName(b, l + 1);
    r = r && attNameList_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // {',' attName}*
  private static boolean attNameList_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "attNameList_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!attNameList_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "attNameList_1", c)) break;
    }
    return true;
  }

  // ',' attName
  private static boolean attNameList_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "attNameList_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && attName(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '<' attribName '>'
  public static boolean attrib(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "attrib")) return false;
    if (!nextTokenIs(b, LT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LT);
    r = r && attribName(b, l + 1);
    r = r && consumeToken(b, GT);
    exit_section_(b, m, ATTRIB, r);
    return r;
  }

  /* ********************************************************** */
  // 'const' | 'close'
  public static boolean attribName(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "attribName")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ATTRIB_NAME, "<attrib name>");
    r = consumeToken(b, "const");
    if (!r) r = consumeToken(b, "close");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '+' | '-' | '*' | '/' | '//' | '^' | '%' |
  //     '&' | '~' | '|' | '>>' | '<<' | '..' |
  //     '<' | '<=' | '>' | '>=' | '==' | '~=' |
  //     AND | OR
  public static boolean binOp(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "binOp")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, BIN_OP, "<bin op>");
    r = consumeToken(b, PLUS);
    if (!r) r = consumeToken(b, MINUS);
    if (!r) r = consumeToken(b, MULT);
    if (!r) r = consumeToken(b, DIV);
    if (!r) r = consumeToken(b, INTDIV);
    if (!r) r = consumeToken(b, EXP);
    if (!r) r = consumeToken(b, MOD);
    if (!r) r = consumeToken(b, AMP);
    if (!r) r = consumeToken(b, NEG);
    if (!r) r = consumeToken(b, PIPE);
    if (!r) r = consumeToken(b, BSR);
    if (!r) r = consumeToken(b, BSL);
    if (!r) r = consumeToken(b, CONCAT);
    if (!r) r = consumeToken(b, LT);
    if (!r) r = consumeToken(b, LE);
    if (!r) r = consumeToken(b, GT);
    if (!r) r = consumeToken(b, GE);
    if (!r) r = consumeToken(b, EQ);
    if (!r) r = consumeToken(b, NE);
    if (!r) r = consumeToken(b, AND);
    if (!r) r = consumeToken(b, OR);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // <<choices>>
  public static boolean binOps(PsiBuilder b, int l, Parser _choices) {
    if (!recursion_guard_(b, l, "binOps")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, BIN_OP, null);
    r = _choices.parse(b, l);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // not_eof {statement}* [finalStatement SEMI?]
  public static boolean block(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, BLOCK, "<block>");
    r = not_eof(b, l + 1);
    r = r && block_1(b, l + 1);
    r = r && block_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // {statement}*
  private static boolean block_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!block_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "block_1", c)) break;
    }
    return true;
  }

  // {statement}
  private static boolean block_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = statement(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [finalStatement SEMI?]
  private static boolean block_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_2")) return false;
    block_2_0(b, l + 1);
    return true;
  }

  // finalStatement SEMI?
  private static boolean block_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = finalStatement(b, l + 1);
    r = r && block_2_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // SEMI?
  private static boolean block_2_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_2_0_1")) return false;
    consumeToken(b, SEMI);
    return true;
  }

  /* ********************************************************** */
  // BREAK
  public static boolean breakStatement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "breakStatement")) return false;
    if (!nextTokenIs(b, BREAK)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, BREAK);
    exit_section_(b, m, BREAK_STATEMENT, r);
    return r;
  }

  /* ********************************************************** */
  // DO block END
  public static boolean doStatement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "doStatement")) return false;
    if (!nextTokenIs(b, DO)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, DO);
    r = r && block(b, l + 1);
    r = r && consumeToken(b, END);
    exit_section_(b, m, DO_STATEMENT, r);
    return r;
  }

  /* ********************************************************** */
  // ';'
  public static boolean emptyStatement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "emptyStatement")) return false;
    if (!nextTokenIs(b, SEMI)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SEMI);
    exit_section_(b, m, EMPTY_STATEMENT, r);
    return r;
  }

  /* ********************************************************** */
  // expr {',' expr}*
  public static boolean exprList(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "exprList")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, EXPR_LIST, "<expr list>");
    r = expr(b, l + 1, -1);
    r = r && exprList_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // {',' expr}*
  private static boolean exprList_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "exprList_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!exprList_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "exprList_1", c)) break;
    }
    return true;
  }

  // ',' expr
  private static boolean exprList_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "exprList_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // expr
  public static boolean exprStatement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "exprStatement")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, EXPR_STATEMENT, "<expr statement>");
    r = expr(b, l + 1, -1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '[' expr ']' '=' expr | IDENTIFIER '=' expr | expr
  public static boolean field(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "field")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FIELD, "<field>");
    r = field_0(b, l + 1);
    if (!r) r = field_1(b, l + 1);
    if (!r) r = expr(b, l + 1, -1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '[' expr ']' '=' expr
  private static boolean field_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "field_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LBRACK);
    r = r && expr(b, l + 1, -1);
    r = r && consumeTokens(b, 0, RBRACK, ASSIGN);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  // IDENTIFIER '=' expr
  private static boolean field_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "field_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, IDENTIFIER, ASSIGN);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // field {fieldSep field}* [fieldSep]
  public static boolean fieldList(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fieldList")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FIELD_LIST, "<field list>");
    r = field(b, l + 1);
    r = r && fieldList_1(b, l + 1);
    r = r && fieldList_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // {fieldSep field}*
  private static boolean fieldList_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fieldList_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!fieldList_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "fieldList_1", c)) break;
    }
    return true;
  }

  // fieldSep field
  private static boolean fieldList_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fieldList_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = fieldSep(b, l + 1);
    r = r && field(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [fieldSep]
  private static boolean fieldList_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fieldList_2")) return false;
    fieldSep(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // ',' | ';'
  public static boolean fieldSep(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fieldSep")) return false;
    if (!nextTokenIs(b, "<field sep>", COMMA, SEMI)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FIELD_SEP, "<field sep>");
    r = consumeToken(b, COMMA);
    if (!r) r = consumeToken(b, SEMI);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // RETURN [exprList] [';']
  public static boolean finalStatement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "finalStatement")) return false;
    if (!nextTokenIs(b, RETURN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, RETURN);
    r = r && finalStatement_1(b, l + 1);
    r = r && finalStatement_2(b, l + 1);
    exit_section_(b, m, FINAL_STATEMENT, r);
    return r;
  }

  // [exprList]
  private static boolean finalStatement_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "finalStatement_1")) return false;
    exprList(b, l + 1);
    return true;
  }

  // [';']
  private static boolean finalStatement_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "finalStatement_2")) return false;
    consumeToken(b, SEMI);
    return true;
  }

  /* ********************************************************** */
  // '(' [parList] ')' block END
  static boolean funcBody(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "funcBody")) return false;
    if (!nextTokenIs(b, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && funcBody_1(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    r = r && block(b, l + 1);
    r = r && consumeToken(b, END);
    exit_section_(b, m, null, r);
    return r;
  }

  // [parList]
  private static boolean funcBody_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "funcBody_1")) return false;
    parList(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // FUNCTION funcName funcBody
  public static boolean funcDecl(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "funcDecl")) return false;
    if (!nextTokenIs(b, FUNCTION)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, FUNCTION);
    r = r && funcName(b, l + 1);
    r = r && funcBody(b, l + 1);
    exit_section_(b, m, FUNC_DECL, r);
    return r;
  }

  /* ********************************************************** */
  // nameRef funcNameProperty* funcNameMethod?
  public static boolean funcName(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "funcName")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = nameRef(b, l + 1);
    r = r && funcName_1(b, l + 1);
    r = r && funcName_2(b, l + 1);
    exit_section_(b, m, FUNC_NAME, r);
    return r;
  }

  // funcNameProperty*
  private static boolean funcName_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "funcName_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!funcNameProperty(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "funcName_1", c)) break;
    }
    return true;
  }

  // funcNameMethod?
  private static boolean funcName_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "funcName_2")) return false;
    funcNameMethod(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // ':' nameRef
  public static boolean funcNameMethod(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "funcNameMethod")) return false;
    if (!nextTokenIs(b, COLON)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COLON);
    r = r && nameRef(b, l + 1);
    exit_section_(b, m, FUNC_NAME_METHOD, r);
    return r;
  }

  /* ********************************************************** */
  // '.' nameRef
  public static boolean funcNameProperty(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "funcNameProperty")) return false;
    if (!nextTokenIs(b, DOT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, DOT);
    r = r && nameRef(b, l + 1);
    exit_section_(b, m, FUNC_NAME_PROPERTY, r);
    return r;
  }

  /* ********************************************************** */
  // FOR nameList IN exprList DO block END
  public static boolean genericForStatement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "genericForStatement")) return false;
    if (!nextTokenIs(b, FOR)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, FOR);
    r = r && nameList(b, l + 1);
    r = r && consumeToken(b, IN);
    r = r && exprList(b, l + 1);
    r = r && consumeToken(b, DO);
    r = r && block(b, l + 1);
    r = r && consumeToken(b, END);
    exit_section_(b, m, GENERIC_FOR_STATEMENT, r);
    return r;
  }

  /* ********************************************************** */
  // GOTO labelRef
  public static boolean gotoStatement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "gotoStatement")) return false;
    if (!nextTokenIs(b, GOTO)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, GOTO);
    r = r && labelRef(b, l + 1);
    exit_section_(b, m, GOTO_STATEMENT, r);
    return r;
  }

  /* ********************************************************** */
  // IF expr THEN block {ELSEIF expr THEN block}* [ELSE block] END
  public static boolean ifStatement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ifStatement")) return false;
    if (!nextTokenIs(b, IF)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IF);
    r = r && expr(b, l + 1, -1);
    r = r && consumeToken(b, THEN);
    r = r && block(b, l + 1);
    r = r && ifStatement_4(b, l + 1);
    r = r && ifStatement_5(b, l + 1);
    r = r && consumeToken(b, END);
    exit_section_(b, m, IF_STATEMENT, r);
    return r;
  }

  // {ELSEIF expr THEN block}*
  private static boolean ifStatement_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ifStatement_4")) return false;
    while (true) {
      int c = current_position_(b);
      if (!ifStatement_4_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "ifStatement_4", c)) break;
    }
    return true;
  }

  // ELSEIF expr THEN block
  private static boolean ifStatement_4_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ifStatement_4_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ELSEIF);
    r = r && expr(b, l + 1, -1);
    r = r && consumeToken(b, THEN);
    r = r && block(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ELSE block]
  private static boolean ifStatement_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ifStatement_5")) return false;
    ifStatement_5_0(b, l + 1);
    return true;
  }

  // ELSE block
  private static boolean ifStatement_5_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ifStatement_5_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ELSE);
    r = r && block(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // ('[' expr ']') | ('.' nameRef)
  public static boolean indexExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "indexExpr")) return false;
    if (!nextTokenIs(b, "<index expr>", DOT, LBRACK)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, INDEX_EXPR, "<index expr>");
    r = indexExpr_0(b, l + 1);
    if (!r) r = indexExpr_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '[' expr ']'
  private static boolean indexExpr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "indexExpr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LBRACK);
    r = r && expr(b, l + 1, -1);
    r = r && consumeToken(b, RBRACK);
    exit_section_(b, m, null, r);
    return r;
  }

  // '.' nameRef
  private static boolean indexExpr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "indexExpr_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, DOT);
    r = r && nameRef(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '::' labelName '::'
  public static boolean label(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "label")) return false;
    if (!nextTokenIs(b, MARKER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, MARKER);
    r = r && labelName(b, l + 1);
    r = r && consumeToken(b, MARKER);
    exit_section_(b, m, LABEL, r);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER
  public static boolean labelName(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "labelName")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    exit_section_(b, m, LABEL_NAME, r);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER
  public static boolean labelRef(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "labelRef")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    exit_section_(b, m, LABEL_REF, r);
    return r;
  }

  /* ********************************************************** */
  // LOCAL FUNCTION nameRef funcBody
  public static boolean localFuncDecl(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "localFuncDecl")) return false;
    if (!nextTokenIs(b, LOCAL)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, LOCAL, FUNCTION);
    r = r && nameRef(b, l + 1);
    r = r && funcBody(b, l + 1);
    exit_section_(b, m, LOCAL_FUNC_DECL, r);
    return r;
  }

  /* ********************************************************** */
  // LOCAL attNameList ['=' exprList]
  public static boolean localVarDecl(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "localVarDecl")) return false;
    if (!nextTokenIs(b, LOCAL)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LOCAL);
    r = r && attNameList(b, l + 1);
    r = r && localVarDecl_2(b, l + 1);
    exit_section_(b, m, LOCAL_VAR_DECL, r);
    return r;
  }

  // ['=' exprList]
  private static boolean localVarDecl_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "localVarDecl_2")) return false;
    localVarDecl_2_0(b, l + 1);
    return true;
  }

  // '=' exprList
  private static boolean localVarDecl_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "localVarDecl_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ASSIGN);
    r = r && exprList(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // ':' nameRef
  public static boolean methodExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "methodExpr")) return false;
    if (!nextTokenIs(b, COLON)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COLON);
    r = r && nameRef(b, l + 1);
    exit_section_(b, m, METHOD_EXPR, r);
    return r;
  }

  /* ********************************************************** */
  // methodExpr? args
  public static boolean nameAndArgs(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nameAndArgs")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, NAME_AND_ARGS, "<name and args>");
    r = nameAndArgs_0(b, l + 1);
    r = r && args(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // methodExpr?
  private static boolean nameAndArgs_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nameAndArgs_0")) return false;
    methodExpr(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // nameRef {',' nameRef}*
  public static boolean nameList(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nameList")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = nameRef(b, l + 1);
    r = r && nameList_1(b, l + 1);
    exit_section_(b, m, NAME_LIST, r);
    return r;
  }

  // {',' nameRef}*
  private static boolean nameList_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nameList_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!nameList_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "nameList_1", c)) break;
    }
    return true;
  }

  // ',' nameRef
  private static boolean nameList_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nameList_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && nameRef(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER
  public static boolean nameRef(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nameRef")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    exit_section_(b, m, NAME_REF, r);
    return r;
  }

  /* ********************************************************** */
  // !<<eof>>
  static boolean not_eof(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "not_eof")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !eof(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // FOR IDENTIFIER '=' expr ',' expr [',' expr] DO block END
  public static boolean numericForStatement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "numericForStatement")) return false;
    if (!nextTokenIs(b, FOR)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, FOR, IDENTIFIER, ASSIGN);
    r = r && expr(b, l + 1, -1);
    r = r && consumeToken(b, COMMA);
    r = r && expr(b, l + 1, -1);
    r = r && numericForStatement_6(b, l + 1);
    r = r && consumeToken(b, DO);
    r = r && block(b, l + 1);
    r = r && consumeToken(b, END);
    exit_section_(b, m, NUMERIC_FOR_STATEMENT, r);
    return r;
  }

  // [',' expr]
  private static boolean numericForStatement_6(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "numericForStatement_6")) return false;
    numericForStatement_6_0(b, l + 1);
    return true;
  }

  // ',' expr
  private static boolean numericForStatement_6_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "numericForStatement_6_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // nameList [',' '...'] | '...'
  public static boolean parList(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parList")) return false;
    if (!nextTokenIs(b, "<par list>", ELLIPSIS, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PAR_LIST, "<par list>");
    r = parList_0(b, l + 1);
    if (!r) r = consumeToken(b, ELLIPSIS);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // nameList [',' '...']
  private static boolean parList_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parList_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = nameList(b, l + 1);
    r = r && parList_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [',' '...']
  private static boolean parList_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parList_0_1")) return false;
    parseTokens(b, 0, COMMA, ELLIPSIS);
    return true;
  }

  /* ********************************************************** */
  // REPEAT block UNTIL expr
  public static boolean repeatStatement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "repeatStatement")) return false;
    if (!nextTokenIs(b, REPEAT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, REPEAT);
    r = r && block(b, l + 1);
    r = r && consumeToken(b, UNTIL);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, REPEAT_STATEMENT, r);
    return r;
  }

  /* ********************************************************** */
  // block*
  static boolean root(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "root")) return false;
    while (true) {
      int c = current_position_(b);
      if (!block(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "root", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // emptyStatement
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
  public static boolean statement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, STATEMENT, "<statement>");
    r = emptyStatement(b, l + 1);
    if (!r) r = assignmentStatement(b, l + 1);
    if (!r) r = exprStatement(b, l + 1);
    if (!r) r = label(b, l + 1);
    if (!r) r = breakStatement(b, l + 1);
    if (!r) r = gotoStatement(b, l + 1);
    if (!r) r = doStatement(b, l + 1);
    if (!r) r = whileStatement(b, l + 1);
    if (!r) r = repeatStatement(b, l + 1);
    if (!r) r = ifStatement(b, l + 1);
    if (!r) r = numericForStatement(b, l + 1);
    if (!r) r = genericForStatement(b, l + 1);
    if (!r) r = funcDecl(b, l + 1);
    if (!r) r = localFuncDecl(b, l + 1);
    if (!r) r = localVarDecl(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '-' | NOT | '#' | '~'
  public static boolean unOp(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unOp")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, UN_OP, "<un op>");
    r = consumeToken(b, MINUS);
    if (!r) r = consumeToken(b, NOT);
    if (!r) r = consumeToken(b, GETN);
    if (!r) r = consumeToken(b, NEG);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // nameRef varSuffix*
  //     | '(' expr ')' varSuffix+
  public static boolean var(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "var")) return false;
    if (!nextTokenIs(b, "<var>", IDENTIFIER, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, VAR, "<var>");
    r = var_0(b, l + 1);
    if (!r) r = var_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // nameRef varSuffix*
  private static boolean var_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "var_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = nameRef(b, l + 1);
    r = r && var_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // varSuffix*
  private static boolean var_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "var_0_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!varSuffix(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "var_0_1", c)) break;
    }
    return true;
  }

  // '(' expr ')' varSuffix+
  private static boolean var_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "var_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && expr(b, l + 1, -1);
    r = r && consumeToken(b, RPAREN);
    r = r && var_1_3(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // varSuffix+
  private static boolean var_1_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "var_1_3")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = varSuffix(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!varSuffix(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "var_1_3", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // var {',' var}*
  public static boolean varList(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "varList")) return false;
    if (!nextTokenIs(b, "<var list>", IDENTIFIER, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, VAR_LIST, "<var list>");
    r = var(b, l + 1);
    r = r && varList_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // {',' var}*
  private static boolean varList_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "varList_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!varList_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "varList_1", c)) break;
    }
    return true;
  }

  // ',' var
  private static boolean varList_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "varList_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && var(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // var | '(' expr ')'
  public static boolean varOrExp(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "varOrExp")) return false;
    if (!nextTokenIs(b, "<var or exp>", IDENTIFIER, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, VAR_OR_EXP, "<var or exp>");
    r = var(b, l + 1);
    if (!r) r = varOrExp_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '(' expr ')'
  private static boolean varOrExp_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "varOrExp_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && expr(b, l + 1, -1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // nameAndArgs* indexExpr
  public static boolean varSuffix(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "varSuffix")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, VAR_SUFFIX, "<var suffix>");
    r = varSuffix_0(b, l + 1);
    r = r && indexExpr(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // nameAndArgs*
  private static boolean varSuffix_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "varSuffix_0")) return false;
    while (true) {
      int c = current_position_(b);
      if (!nameAndArgs(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "varSuffix_0", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // WHILE expr DO block END
  public static boolean whileStatement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "whileStatement")) return false;
    if (!nextTokenIs(b, WHILE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, WHILE);
    r = r && expr(b, l + 1, -1);
    r = r && consumeToken(b, DO);
    r = r && block(b, l + 1);
    r = r && consumeToken(b, END);
    exit_section_(b, m, WHILE_STATEMENT, r);
    return r;
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
  public static boolean expr(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "expr")) return false;
    addVariant(b, "<expr>");
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, "<expr>");
    r = terminalExpr(b, l + 1);
    if (!r) r = funcDef(b, l + 1);
    if (!r) r = funcCall(b, l + 1);
    if (!r) r = prefixExpr(b, l + 1);
    if (!r) r = tableConstructor(b, l + 1);
    if (!r) r = unOpExpr(b, l + 1);
    p = r;
    r = r && expr_0(b, l + 1, g);
    exit_section_(b, l, m, null, r, p, null);
    return r || p;
  }

  public static boolean expr_0(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "expr_0")) return false;
    boolean r = true;
    while (true) {
      Marker m = enter_section_(b, l, _LEFT_, null);
      if (g < 5 && binOps(b, l + 1, OR_parser_)) {
        r = expr(b, l, 5);
        exit_section_(b, l, m, BIN_OP_EXPR, r, true, null);
      }
      else if (g < 6 && binOps(b, l + 1, AND_parser_)) {
        r = expr(b, l, 6);
        exit_section_(b, l, m, BIN_OP_EXPR, r, true, null);
      }
      else if (g < 7 && binOps(b, l + 1, LuaParser::relBinOpExpr_0_0)) {
        r = expr(b, l, 7);
        exit_section_(b, l, m, BIN_OP_EXPR, r, true, null);
      }
      else if (g < 8 && binOps(b, l + 1, PIPE_parser_)) {
        r = expr(b, l, 8);
        exit_section_(b, l, m, BIN_OP_EXPR, r, true, null);
      }
      else if (g < 9 && binOps(b, l + 1, NEG_parser_)) {
        r = expr(b, l, 9);
        exit_section_(b, l, m, BIN_OP_EXPR, r, true, null);
      }
      else if (g < 10 && binOps(b, l + 1, AMP_parser_)) {
        r = expr(b, l, 10);
        exit_section_(b, l, m, BIN_OP_EXPR, r, true, null);
      }
      else if (g < 11 && binOps(b, l + 1, LuaParser::bshiftBinOpExpr_0_0)) {
        r = expr(b, l, 11);
        exit_section_(b, l, m, BIN_OP_EXPR, r, true, null);
      }
      else if (g < 12 && binOps(b, l + 1, CONCAT_parser_)) {
        r = expr(b, l, 11);
        exit_section_(b, l, m, BIN_OP_EXPR, r, true, null);
      }
      else if (g < 13 && binOps(b, l + 1, LuaParser::addBinOpExpr_0_0)) {
        r = expr(b, l, 13);
        exit_section_(b, l, m, BIN_OP_EXPR, r, true, null);
      }
      else if (g < 14 && binOps(b, l + 1, LuaParser::mulBinOpExpr_0_0)) {
        r = expr(b, l, 14);
        exit_section_(b, l, m, BIN_OP_EXPR, r, true, null);
      }
      else if (g < 16 && binOps(b, l + 1, EXP_parser_)) {
        r = expr(b, l, 15);
        exit_section_(b, l, m, BIN_OP_EXPR, r, true, null);
      }
      else {
        exit_section_(b, l, m, null, false, false, null);
        break;
      }
    }
    return r;
  }

  // NIL | FALSE | TRUE | NUMBER | STRING | ELLIPSIS
  public static boolean terminalExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "terminalExpr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, TERMINAL_EXPR, "<terminal expr>");
    r = consumeTokenSmart(b, NIL);
    if (!r) r = consumeTokenSmart(b, FALSE);
    if (!r) r = consumeTokenSmart(b, TRUE);
    if (!r) r = consumeTokenSmart(b, NUMBER);
    if (!r) r = consumeTokenSmart(b, STRING);
    if (!r) r = consumeTokenSmart(b, ELLIPSIS);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // FUNCTION funcBody
  public static boolean funcDef(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "funcDef")) return false;
    if (!nextTokenIsSmart(b, FUNCTION)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, FUNCTION);
    r = r && funcBody(b, l + 1);
    exit_section_(b, m, FUNC_DEF, r);
    return r;
  }

  // varOrExp nameAndArgs+
  public static boolean funcCall(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "funcCall")) return false;
    if (!nextTokenIsSmart(b, IDENTIFIER, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FUNC_CALL, "<func call>");
    r = varOrExp(b, l + 1);
    r = r && funcCall_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // nameAndArgs+
  private static boolean funcCall_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "funcCall_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = nameAndArgs(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!nameAndArgs(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "funcCall_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // varOrExp
  public static boolean prefixExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "prefixExpr")) return false;
    if (!nextTokenIsSmart(b, IDENTIFIER, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PREFIX_EXPR, "<prefix expr>");
    r = varOrExp(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '{' [fieldList] '}'
  public static boolean tableConstructor(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tableConstructor")) return false;
    if (!nextTokenIsSmart(b, LCURLY)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, LCURLY);
    r = r && tableConstructor_1(b, l + 1);
    r = r && consumeToken(b, RCURLY);
    exit_section_(b, m, TABLE_CONSTRUCTOR, r);
    return r;
  }

  // [fieldList]
  private static boolean tableConstructor_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tableConstructor_1")) return false;
    fieldList(b, l + 1);
    return true;
  }

  // '=='|'~='|'<'|'<='|'>='|'>'
  private static boolean relBinOpExpr_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "relBinOpExpr_0_0")) return false;
    boolean r;
    r = consumeTokenSmart(b, EQ);
    if (!r) r = consumeTokenSmart(b, NE);
    if (!r) r = consumeTokenSmart(b, LT);
    if (!r) r = consumeTokenSmart(b, LE);
    if (!r) r = consumeTokenSmart(b, GE);
    if (!r) r = consumeTokenSmart(b, GT);
    return r;
  }

  // '<<'|'>>'
  private static boolean bshiftBinOpExpr_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bshiftBinOpExpr_0_0")) return false;
    boolean r;
    r = consumeTokenSmart(b, BSL);
    if (!r) r = consumeTokenSmart(b, BSR);
    return r;
  }

  // '+'|'-'
  private static boolean addBinOpExpr_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "addBinOpExpr_0_0")) return false;
    boolean r;
    r = consumeTokenSmart(b, PLUS);
    if (!r) r = consumeTokenSmart(b, MINUS);
    return r;
  }

  // '*'|'/'|'//'|'%'
  private static boolean mulBinOpExpr_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mulBinOpExpr_0_0")) return false;
    boolean r;
    r = consumeTokenSmart(b, MULT);
    if (!r) r = consumeTokenSmart(b, DIV);
    if (!r) r = consumeTokenSmart(b, INTDIV);
    if (!r) r = consumeTokenSmart(b, MOD);
    return r;
  }

  public static boolean unOpExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unOpExpr")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = unOp(b, l + 1);
    p = r;
    r = p && expr(b, l, 15);
    exit_section_(b, l, m, UN_OP_EXPR, r, p, null);
    return r || p;
  }

  static final Parser AMP_parser_ = (b, l) -> consumeToken(b, AMP);
  static final Parser AND_parser_ = (b, l) -> consumeToken(b, AND);
  static final Parser CONCAT_parser_ = (b, l) -> consumeToken(b, CONCAT);
  static final Parser EXP_parser_ = (b, l) -> consumeToken(b, EXP);
  static final Parser NEG_parser_ = (b, l) -> consumeToken(b, NEG);
  static final Parser OR_parser_ = (b, l) -> consumeToken(b, OR);
  static final Parser PIPE_parser_ = (b, l) -> consumeToken(b, PIPE);
}
