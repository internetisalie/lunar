// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.lang.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import net.internetisalie.lunar.lang.psi.impl.*;

public interface LuaElementTypes {

  IElementType ARGS = new LuaElementType("ARGS");
  IElementType ASSIGNMENT_STATEMENT = new LuaElementType("ASSIGNMENT_STATEMENT");
  IElementType ATTRIB = new LuaElementType("ATTRIB");
  IElementType ATTRIB_NAME = new LuaElementType("ATTRIB_NAME");
  IElementType ATT_NAME = new LuaElementType("ATT_NAME");
  IElementType BIN_OP = new LuaElementType("BIN_OP");
  IElementType BIN_OP_EXPR = new LuaElementType("BIN_OP_EXPR");
  IElementType BLOCK = new LuaElementType("BLOCK");
  IElementType BREAK_STATEMENT = new LuaElementType("BREAK_STATEMENT");
  IElementType DO_STATEMENT = new LuaElementType("DO_STATEMENT");
  IElementType EMPTY_STATEMENT = new LuaElementType("EMPTY_STATEMENT");
  IElementType EXPR = new LuaElementType("EXPR");
  IElementType EXPR_LIST = new LuaElementType("EXPR_LIST");
  IElementType FIELD = new LuaElementType("FIELD");
  IElementType FIELD_LIST = new LuaElementType("FIELD_LIST");
  IElementType FIELD_SEP = new LuaElementType("FIELD_SEP");
  IElementType FINAL_STATEMENT = new LuaElementType("FINAL_STATEMENT");
  IElementType FUNC_CALL = new LuaElementType("FUNC_CALL");
  IElementType FUNC_CALL_STATEMENT = new LuaElementType("FUNC_CALL_STATEMENT");
  IElementType FUNC_DECL = new LuaElementType("FUNC_DECL");
  IElementType FUNC_DEF = new LuaElementType("FUNC_DEF");
  IElementType FUNC_NAME = new LuaElementType("FUNC_NAME");
  IElementType FUNC_NAME_METHOD = new LuaElementType("FUNC_NAME_METHOD");
  IElementType FUNC_NAME_PROPERTY = new LuaElementType("FUNC_NAME_PROPERTY");
  IElementType GENERIC_FOR_STATEMENT = new LuaElementType("GENERIC_FOR_STATEMENT");
  IElementType GOTO_STATEMENT = new LuaElementType("GOTO_STATEMENT");
  IElementType IF_STATEMENT = new LuaElementType("IF_STATEMENT");
  IElementType INDEX_EXPR = new LuaElementType("INDEX_EXPR");
  IElementType LABEL = new LuaElementType("LABEL");
  IElementType LABEL_NAME = new LuaElementType("LABEL_NAME");
  IElementType LABEL_REF = new LuaElementType("LABEL_REF");
  IElementType LOCAL_FUNC_DECL = new LuaElementType("LOCAL_FUNC_DECL");
  IElementType LOCAL_VAR_DECL = new LuaElementType("LOCAL_VAR_DECL");
  IElementType METHOD_EXPR = new LuaElementType("METHOD_EXPR");
  IElementType NAME_AND_ARGS = new LuaElementType("NAME_AND_ARGS");
  IElementType NAME_LIST = new LuaElementType("NAME_LIST");
  IElementType NAME_REF = new LuaElementType("NAME_REF");
  IElementType NUMERIC_FOR_STATEMENT = new LuaElementType("NUMERIC_FOR_STATEMENT");
  IElementType PAR_LIST = new LuaElementType("PAR_LIST");
  IElementType PREFIX_EXPR = new LuaElementType("PREFIX_EXPR");
  IElementType REPEAT_STATEMENT = new LuaElementType("REPEAT_STATEMENT");
  IElementType STATEMENT = new LuaElementType("STATEMENT");
  IElementType TABLE_CONSTRUCTOR = new LuaElementType("TABLE_CONSTRUCTOR");
  IElementType TERMINAL_EXPR = new LuaElementType("TERMINAL_EXPR");
  IElementType UN_OP = new LuaElementType("UN_OP");
  IElementType UN_OP_EXPR = new LuaElementType("UN_OP_EXPR");
  IElementType VAR = new LuaElementType("VAR");
  IElementType VAR_LIST = new LuaElementType("VAR_LIST");
  IElementType VAR_OR_EXP = new LuaElementType("VAR_OR_EXP");
  IElementType VAR_SUFFIX = new LuaElementType("VAR_SUFFIX");
  IElementType WHILE_STATEMENT = new LuaElementType("WHILE_STATEMENT");

  IElementType AMP = new LuaTokenType("&");
  IElementType AND = new LuaTokenType("and");
  IElementType ASSIGN = new LuaTokenType("=");
  IElementType BREAK = new LuaTokenType("break");
  IElementType BSL = new LuaTokenType("<<");
  IElementType BSR = new LuaTokenType(">>");
  IElementType COLON = new LuaTokenType(":");
  IElementType COMMA = new LuaTokenType(",");
  IElementType CONCAT = new LuaTokenType("..");
  IElementType DIV = new LuaTokenType("/");
  IElementType DO = new LuaTokenType("do");
  IElementType DOT = new LuaTokenType(".");
  IElementType ELLIPSIS = new LuaTokenType("...");
  IElementType ELSE = new LuaTokenType("else");
  IElementType ELSEIF = new LuaTokenType("elseif");
  IElementType END = new LuaTokenType("end");
  IElementType EQ = new LuaTokenType("==");
  IElementType EXP = new LuaTokenType("^");
  IElementType FALSE = new LuaTokenType("false");
  IElementType FOR = new LuaTokenType("for");
  IElementType FUNCTION = new LuaTokenType("function");
  IElementType GE = new LuaTokenType(">=");
  IElementType GETN = new LuaTokenType("#");
  IElementType GOTO = new LuaTokenType("goto");
  IElementType GT = new LuaTokenType(">");
  IElementType IDENTIFIER = new LuaTokenType("IDENTIFIER");
  IElementType IF = new LuaTokenType("if");
  IElementType IN = new LuaTokenType("in");
  IElementType INTDIV = new LuaTokenType("//");
  IElementType LBRACK = new LuaTokenType("[");
  IElementType LCURLY = new LuaTokenType("{");
  IElementType LE = new LuaTokenType("<=");
  IElementType LOCAL = new LuaTokenType("local");
  IElementType LONGCOMMENT = new LuaTokenType("--[[]]");
  IElementType LPAREN = new LuaTokenType("(");
  IElementType LT = new LuaTokenType("<");
  IElementType MARKER = new LuaTokenType("::");
  IElementType MINUS = new LuaTokenType("-");
  IElementType MOD = new LuaTokenType("%");
  IElementType MULT = new LuaTokenType("*");
  IElementType NE = new LuaTokenType("~=");
  IElementType NEG = new LuaTokenType("~");
  IElementType NIL = new LuaTokenType("nil");
  IElementType NOT = new LuaTokenType("not");
  IElementType NUMBER = new LuaTokenType("NUMBER");
  IElementType OR = new LuaTokenType("or");
  IElementType PIPE = new LuaTokenType("|");
  IElementType PLUS = new LuaTokenType("+");
  IElementType RBRACK = new LuaTokenType("]");
  IElementType RCURLY = new LuaTokenType("}");
  IElementType REPEAT = new LuaTokenType("repeat");
  IElementType RETURN = new LuaTokenType("return");
  IElementType RPAREN = new LuaTokenType(")");
  IElementType SEMI = new LuaTokenType(";");
  IElementType SHEBANG = new LuaTokenType("#!");
  IElementType SHORTCOMMENT = new LuaTokenType("--");
  IElementType STRING = new LuaTokenType("STRING");
  IElementType THEN = new LuaTokenType("then");
  IElementType TRUE = new LuaTokenType("true");
  IElementType UNTIL = new LuaTokenType("until");
  IElementType WHILE = new LuaTokenType("while");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == ARGS) {
        return new LuaArgsImpl(node);
      }
      else if (type == ASSIGNMENT_STATEMENT) {
        return new LuaAssignmentStatementImpl(node);
      }
      else if (type == ATTRIB) {
        return new LuaAttribImpl(node);
      }
      else if (type == ATTRIB_NAME) {
        return new LuaAttribNameImpl(node);
      }
      else if (type == ATT_NAME) {
        return new LuaAttNameImpl(node);
      }
      else if (type == BIN_OP) {
        return new LuaBinOpImpl(node);
      }
      else if (type == BIN_OP_EXPR) {
        return new LuaBinOpExprImpl(node);
      }
      else if (type == BLOCK) {
        return new LuaBlockImpl(node);
      }
      else if (type == BREAK_STATEMENT) {
        return new LuaBreakStatementImpl(node);
      }
      else if (type == DO_STATEMENT) {
        return new LuaDoStatementImpl(node);
      }
      else if (type == EMPTY_STATEMENT) {
        return new LuaEmptyStatementImpl(node);
      }
      else if (type == EXPR_LIST) {
        return new LuaExprListImpl(node);
      }
      else if (type == FIELD) {
        return new LuaFieldImpl(node);
      }
      else if (type == FIELD_LIST) {
        return new LuaFieldListImpl(node);
      }
      else if (type == FIELD_SEP) {
        return new LuaFieldSepImpl(node);
      }
      else if (type == FINAL_STATEMENT) {
        return new LuaFinalStatementImpl(node);
      }
      else if (type == FUNC_CALL) {
        return new LuaFuncCallImpl(node);
      }
      else if (type == FUNC_CALL_STATEMENT) {
        return new LuaFuncCallStatementImpl(node);
      }
      else if (type == FUNC_DECL) {
        return new LuaFuncDeclImpl(node);
      }
      else if (type == FUNC_DEF) {
        return new LuaFuncDefImpl(node);
      }
      else if (type == FUNC_NAME) {
        return new LuaFuncNameImpl(node);
      }
      else if (type == FUNC_NAME_METHOD) {
        return new LuaFuncNameMethodImpl(node);
      }
      else if (type == FUNC_NAME_PROPERTY) {
        return new LuaFuncNamePropertyImpl(node);
      }
      else if (type == GENERIC_FOR_STATEMENT) {
        return new LuaGenericForStatementImpl(node);
      }
      else if (type == GOTO_STATEMENT) {
        return new LuaGotoStatementImpl(node);
      }
      else if (type == IF_STATEMENT) {
        return new LuaIfStatementImpl(node);
      }
      else if (type == INDEX_EXPR) {
        return new LuaIndexExprImpl(node);
      }
      else if (type == LABEL) {
        return new LuaLabelImpl(node);
      }
      else if (type == LABEL_NAME) {
        return new LuaLabelNameImpl(node);
      }
      else if (type == LABEL_REF) {
        return new LuaLabelRefImpl(node);
      }
      else if (type == LOCAL_FUNC_DECL) {
        return new LuaLocalFuncDeclImpl(node);
      }
      else if (type == LOCAL_VAR_DECL) {
        return new LuaLocalVarDeclImpl(node);
      }
      else if (type == METHOD_EXPR) {
        return new LuaMethodExprImpl(node);
      }
      else if (type == NAME_AND_ARGS) {
        return new LuaNameAndArgsImpl(node);
      }
      else if (type == NAME_LIST) {
        return new LuaNameListImpl(node);
      }
      else if (type == NAME_REF) {
        return new LuaNameRefImpl(node);
      }
      else if (type == NUMERIC_FOR_STATEMENT) {
        return new LuaNumericForStatementImpl(node);
      }
      else if (type == PAR_LIST) {
        return new LuaParListImpl(node);
      }
      else if (type == PREFIX_EXPR) {
        return new LuaPrefixExprImpl(node);
      }
      else if (type == REPEAT_STATEMENT) {
        return new LuaRepeatStatementImpl(node);
      }
      else if (type == TABLE_CONSTRUCTOR) {
        return new LuaTableConstructorImpl(node);
      }
      else if (type == TERMINAL_EXPR) {
        return new LuaTerminalExprImpl(node);
      }
      else if (type == UN_OP) {
        return new LuaUnOpImpl(node);
      }
      else if (type == UN_OP_EXPR) {
        return new LuaUnOpExprImpl(node);
      }
      else if (type == VAR) {
        return new LuaVarImpl(node);
      }
      else if (type == VAR_LIST) {
        return new LuaVarListImpl(node);
      }
      else if (type == VAR_OR_EXP) {
        return new LuaVarOrExpImpl(node);
      }
      else if (type == VAR_SUFFIX) {
        return new LuaVarSuffixImpl(node);
      }
      else if (type == WHILE_STATEMENT) {
        return new LuaWhileStatementImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
