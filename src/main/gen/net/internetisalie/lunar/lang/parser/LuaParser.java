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
  // not_eof {statement SEMI?}* [finalStatement SEMI?]
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

  // {statement SEMI?}*
  private static boolean block_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!block_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "block_1", c)) break;
    }
    return true;
  }

  // statement SEMI?
  private static boolean block_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = statement(b, l + 1);
    r = r && block_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // SEMI?
  private static boolean block_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_1_0_1")) return false;
    consumeToken(b, SEMI);
    return true;
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
  // (NIL | FALSE | TRUE | NUMBER | STRING | ELLIPSIS | funcDef | prefixExpr | tableConstructor | unOp expr) {binOp expr}*
  public static boolean expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, EXPR, "<expr>");
    r = expr_0(b, l + 1);
    r = r && expr_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // NIL | FALSE | TRUE | NUMBER | STRING | ELLIPSIS | funcDef | prefixExpr | tableConstructor | unOp expr
  private static boolean expr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, NIL);
    if (!r) r = consumeToken(b, FALSE);
    if (!r) r = consumeToken(b, TRUE);
    if (!r) r = consumeToken(b, NUMBER);
    if (!r) r = consumeToken(b, STRING);
    if (!r) r = consumeToken(b, ELLIPSIS);
    if (!r) r = funcDef(b, l + 1);
    if (!r) r = prefixExpr(b, l + 1);
    if (!r) r = tableConstructor(b, l + 1);
    if (!r) r = expr_0_9(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // unOp expr
  private static boolean expr_0_9(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expr_0_9")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = unOp(b, l + 1);
    r = r && expr(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // {binOp expr}*
  private static boolean expr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expr_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!expr_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "expr_1", c)) break;
    }
    return true;
  }

  // binOp expr
  private static boolean expr_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expr_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = binOp(b, l + 1);
    r = r && expr(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // expr {',' expr}*
  public static boolean exprList(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "exprList")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, EXPR_LIST, "<expr list>");
    r = expr(b, l + 1);
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
    r = r && expr(b, l + 1);
    exit_section_(b, m, null, r);
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
    if (!r) r = expr(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '[' expr ']' '=' expr
  private static boolean field_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "field_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LBRACK);
    r = r && expr(b, l + 1);
    r = r && consumeTokens(b, 0, RBRACK, ASSIGN);
    r = r && expr(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // IDENTIFIER '=' expr
  private static boolean field_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "field_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, IDENTIFIER, ASSIGN);
    r = r && expr(b, l + 1);
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
  public static boolean funcBody(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "funcBody")) return false;
    if (!nextTokenIs(b, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && funcBody_1(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    r = r && block(b, l + 1);
    r = r && consumeToken(b, END);
    exit_section_(b, m, FUNC_BODY, r);
    return r;
  }

  // [parList]
  private static boolean funcBody_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "funcBody_1")) return false;
    parList(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // varOrExp nameAndArgs+
  public static boolean funcCall(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "funcCall")) return false;
    if (!nextTokenIs(b, "<func call>", IDENTIFIER, LPAREN)) return false;
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

  /* ********************************************************** */
  // FUNCTION funcBody
  public static boolean funcDef(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "funcDef")) return false;
    if (!nextTokenIs(b, FUNCTION)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, FUNCTION);
    r = r && funcBody(b, l + 1);
    exit_section_(b, m, FUNC_DEF, r);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER {'.' IDENTIFIER}* [':' IDENTIFIER]
  public static boolean funcName(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "funcName")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    r = r && funcName_1(b, l + 1);
    r = r && funcName_2(b, l + 1);
    exit_section_(b, m, FUNC_NAME, r);
    return r;
  }

  // {'.' IDENTIFIER}*
  private static boolean funcName_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "funcName_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!funcName_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "funcName_1", c)) break;
    }
    return true;
  }

  // '.' IDENTIFIER
  private static boolean funcName_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "funcName_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, DOT, IDENTIFIER);
    exit_section_(b, m, null, r);
    return r;
  }

  // [':' IDENTIFIER]
  private static boolean funcName_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "funcName_2")) return false;
    parseTokens(b, 0, COLON, IDENTIFIER);
    return true;
  }

  /* ********************************************************** */
  // '::' IDENTIFIER '::'
  public static boolean label(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "label")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LABEL, "<label>");
    r = consumeToken(b, "::");
    r = r && consumeToken(b, IDENTIFIER);
    r = r && consumeToken(b, "::");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // (':' IDENTIFIER)? args
  public static boolean nameAndArgs(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nameAndArgs")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, NAME_AND_ARGS, "<name and args>");
    r = nameAndArgs_0(b, l + 1);
    r = r && args(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (':' IDENTIFIER)?
  private static boolean nameAndArgs_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nameAndArgs_0")) return false;
    nameAndArgs_0_0(b, l + 1);
    return true;
  }

  // ':' IDENTIFIER
  private static boolean nameAndArgs_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nameAndArgs_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, COLON, IDENTIFIER);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER {',' IDENTIFIER}*
  public static boolean nameList(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nameList")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    r = r && nameList_1(b, l + 1);
    exit_section_(b, m, NAME_LIST, r);
    return r;
  }

  // {',' IDENTIFIER}*
  private static boolean nameList_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nameList_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!nameList_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "nameList_1", c)) break;
    }
    return true;
  }

  // ',' IDENTIFIER
  private static boolean nameList_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nameList_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, COMMA, IDENTIFIER);
    exit_section_(b, m, null, r);
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
  // varOrExp nameAndArgs*
  public static boolean prefixExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "prefixExpr")) return false;
    if (!nextTokenIs(b, "<prefix expr>", IDENTIFIER, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PREFIX_EXPR, "<prefix expr>");
    r = varOrExp(b, l + 1);
    r = r && prefixExpr_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // nameAndArgs*
  private static boolean prefixExpr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "prefixExpr_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!nameAndArgs(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "prefixExpr_1", c)) break;
    }
    return true;
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
  // varList '=' exprList
  //     | funcCall
  //     | label
  //     | BREAK
  //     | GOTO IDENTIFIER
  //     | DO block END
  //     | WHILE expr DO block END
  //     | REPEAT block UNTIL expr
  //     | IF expr THEN block {ELSEIF expr THEN block}* [ELSE block] END
  //     | FOR IDENTIFIER '=' expr ',' expr [',' expr] DO block END
  //     | FOR nameList IN exprList DO block END
  //     | FUNCTION funcName funcBody
  //     | LOCAL FUNCTION IDENTIFIER funcBody
  //     | LOCAL nameList ['=' exprList]
  public static boolean statement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, STATEMENT, "<statement>");
    r = statement_0(b, l + 1);
    if (!r) r = funcCall(b, l + 1);
    if (!r) r = label(b, l + 1);
    if (!r) r = consumeToken(b, BREAK);
    if (!r) r = parseTokens(b, 0, GOTO, IDENTIFIER);
    if (!r) r = statement_5(b, l + 1);
    if (!r) r = statement_6(b, l + 1);
    if (!r) r = statement_7(b, l + 1);
    if (!r) r = statement_8(b, l + 1);
    if (!r) r = statement_9(b, l + 1);
    if (!r) r = statement_10(b, l + 1);
    if (!r) r = statement_11(b, l + 1);
    if (!r) r = statement_12(b, l + 1);
    if (!r) r = statement_13(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // varList '=' exprList
  private static boolean statement_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = varList(b, l + 1);
    r = r && consumeToken(b, ASSIGN);
    r = r && exprList(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // DO block END
  private static boolean statement_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement_5")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, DO);
    r = r && block(b, l + 1);
    r = r && consumeToken(b, END);
    exit_section_(b, m, null, r);
    return r;
  }

  // WHILE expr DO block END
  private static boolean statement_6(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement_6")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, WHILE);
    r = r && expr(b, l + 1);
    r = r && consumeToken(b, DO);
    r = r && block(b, l + 1);
    r = r && consumeToken(b, END);
    exit_section_(b, m, null, r);
    return r;
  }

  // REPEAT block UNTIL expr
  private static boolean statement_7(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement_7")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, REPEAT);
    r = r && block(b, l + 1);
    r = r && consumeToken(b, UNTIL);
    r = r && expr(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // IF expr THEN block {ELSEIF expr THEN block}* [ELSE block] END
  private static boolean statement_8(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement_8")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IF);
    r = r && expr(b, l + 1);
    r = r && consumeToken(b, THEN);
    r = r && block(b, l + 1);
    r = r && statement_8_4(b, l + 1);
    r = r && statement_8_5(b, l + 1);
    r = r && consumeToken(b, END);
    exit_section_(b, m, null, r);
    return r;
  }

  // {ELSEIF expr THEN block}*
  private static boolean statement_8_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement_8_4")) return false;
    while (true) {
      int c = current_position_(b);
      if (!statement_8_4_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "statement_8_4", c)) break;
    }
    return true;
  }

  // ELSEIF expr THEN block
  private static boolean statement_8_4_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement_8_4_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ELSEIF);
    r = r && expr(b, l + 1);
    r = r && consumeToken(b, THEN);
    r = r && block(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ELSE block]
  private static boolean statement_8_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement_8_5")) return false;
    statement_8_5_0(b, l + 1);
    return true;
  }

  // ELSE block
  private static boolean statement_8_5_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement_8_5_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ELSE);
    r = r && block(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // FOR IDENTIFIER '=' expr ',' expr [',' expr] DO block END
  private static boolean statement_9(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement_9")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, FOR, IDENTIFIER, ASSIGN);
    r = r && expr(b, l + 1);
    r = r && consumeToken(b, COMMA);
    r = r && expr(b, l + 1);
    r = r && statement_9_6(b, l + 1);
    r = r && consumeToken(b, DO);
    r = r && block(b, l + 1);
    r = r && consumeToken(b, END);
    exit_section_(b, m, null, r);
    return r;
  }

  // [',' expr]
  private static boolean statement_9_6(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement_9_6")) return false;
    statement_9_6_0(b, l + 1);
    return true;
  }

  // ',' expr
  private static boolean statement_9_6_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement_9_6_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && expr(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // FOR nameList IN exprList DO block END
  private static boolean statement_10(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement_10")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, FOR);
    r = r && nameList(b, l + 1);
    r = r && consumeToken(b, IN);
    r = r && exprList(b, l + 1);
    r = r && consumeToken(b, DO);
    r = r && block(b, l + 1);
    r = r && consumeToken(b, END);
    exit_section_(b, m, null, r);
    return r;
  }

  // FUNCTION funcName funcBody
  private static boolean statement_11(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement_11")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, FUNCTION);
    r = r && funcName(b, l + 1);
    r = r && funcBody(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // LOCAL FUNCTION IDENTIFIER funcBody
  private static boolean statement_12(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement_12")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, LOCAL, FUNCTION, IDENTIFIER);
    r = r && funcBody(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // LOCAL nameList ['=' exprList]
  private static boolean statement_13(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement_13")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LOCAL);
    r = r && nameList(b, l + 1);
    r = r && statement_13_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // ['=' exprList]
  private static boolean statement_13_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement_13_2")) return false;
    statement_13_2_0(b, l + 1);
    return true;
  }

  // '=' exprList
  private static boolean statement_13_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement_13_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ASSIGN);
    r = r && exprList(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '{' [fieldList] '}'
  public static boolean tableConstructor(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tableConstructor")) return false;
    if (!nextTokenIs(b, LCURLY)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LCURLY);
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
  // (IDENTIFIER | '(' expr ')' varSuffix) {varSuffix}*
  public static boolean var(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "var")) return false;
    if (!nextTokenIs(b, "<var>", IDENTIFIER, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, VAR, "<var>");
    r = var_0(b, l + 1);
    r = r && var_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // IDENTIFIER | '(' expr ')' varSuffix
  private static boolean var_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "var_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    if (!r) r = var_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '(' expr ')' varSuffix
  private static boolean var_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "var_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && expr(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    r = r && varSuffix(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // {varSuffix}*
  private static boolean var_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "var_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!var_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "var_1", c)) break;
    }
    return true;
  }

  // {varSuffix}
  private static boolean var_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "var_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = varSuffix(b, l + 1);
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
    r = r && expr(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // nameAndArgs* ('[' expr ']' | '.' IDENTIFIER)
  public static boolean varSuffix(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "varSuffix")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, VAR_SUFFIX, "<var suffix>");
    r = varSuffix_0(b, l + 1);
    r = r && varSuffix_1(b, l + 1);
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

  // '[' expr ']' | '.' IDENTIFIER
  private static boolean varSuffix_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "varSuffix_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = varSuffix_1_0(b, l + 1);
    if (!r) r = parseTokens(b, 0, DOT, IDENTIFIER);
    exit_section_(b, m, null, r);
    return r;
  }

  // '[' expr ']'
  private static boolean varSuffix_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "varSuffix_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LBRACK);
    r = r && expr(b, l + 1);
    r = r && consumeToken(b, RBRACK);
    exit_section_(b, m, null, r);
    return r;
  }

}
