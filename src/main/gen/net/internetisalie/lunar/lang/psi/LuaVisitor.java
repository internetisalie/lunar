// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.lang.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;
import net.internetisalie.lunar.luadoc.lang.psi.LuaDocCommentOwner;
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsCommentOwner;

public class LuaVisitor extends PsiElementVisitor {

  public void visitArgs(@NotNull LuaArgs o) {
    visitPsiElement(o);
  }

  public void visitAssignmentStatement(@NotNull LuaAssignmentStatement o) {
    visitPsiElement(o);
  }

  public void visitBinOp(@NotNull LuaBinOp o) {
    visitPsiElement(o);
  }

  public void visitBlock(@NotNull LuaBlock o) {
    visitPsiElement(o);
  }

  public void visitDoStatement(@NotNull LuaDoStatement o) {
    visitPsiElement(o);
  }

  public void visitExpr(@NotNull LuaExpr o) {
    visitPsiElement(o);
  }

  public void visitExprList(@NotNull LuaExprList o) {
    visitPsiElement(o);
  }

  public void visitField(@NotNull LuaField o) {
    visitPsiElement(o);
  }

  public void visitFieldList(@NotNull LuaFieldList o) {
    visitPsiElement(o);
  }

  public void visitFieldSep(@NotNull LuaFieldSep o) {
    visitPsiElement(o);
  }

  public void visitFinalStatement(@NotNull LuaFinalStatement o) {
    visitPsiElement(o);
  }

  public void visitFuncCall(@NotNull LuaFuncCall o) {
    visitPsiElement(o);
  }

  public void visitFuncDecl(@NotNull LuaFuncDecl o) {
    visitDocCommentOwner(o);
    // visitCatsCommentOwner(o);
  }

  public void visitFuncDef(@NotNull LuaFuncDef o) {
    visitPsiElement(o);
  }

  public void visitFuncName(@NotNull LuaFuncName o) {
    visitPsiElement(o);
  }

  public void visitFuncNameMethod(@NotNull LuaFuncNameMethod o) {
    visitPsiElement(o);
  }

  public void visitFuncNameProperty(@NotNull LuaFuncNameProperty o) {
    visitPsiElement(o);
  }

  public void visitGenericForStatement(@NotNull LuaGenericForStatement o) {
    visitPsiElement(o);
  }

  public void visitGotoStatement(@NotNull LuaGotoStatement o) {
    visitPsiElement(o);
  }

  public void visitIfStatement(@NotNull LuaIfStatement o) {
    visitPsiElement(o);
  }

  public void visitIndexExpr(@NotNull LuaIndexExpr o) {
    visitPsiElement(o);
  }

  public void visitLabel(@NotNull LuaLabel o) {
    visitPsiElement(o);
  }

  public void visitLabelName(@NotNull LuaLabelName o) {
    visitNameDeclElement(o);
  }

  public void visitLabelRef(@NotNull LuaLabelRef o) {
    visitNameRefElement(o);
  }

  public void visitLocalFuncDecl(@NotNull LuaLocalFuncDecl o) {
    visitPsiElement(o);
  }

  public void visitLocalVarDecl(@NotNull LuaLocalVarDecl o) {
    visitPsiElement(o);
  }

  public void visitMethodExpr(@NotNull LuaMethodExpr o) {
    visitPsiElement(o);
  }

  public void visitNameAndArgs(@NotNull LuaNameAndArgs o) {
    visitPsiElement(o);
  }

  public void visitNameList(@NotNull LuaNameList o) {
    visitPsiElement(o);
  }

  public void visitNameRef(@NotNull LuaNameRef o) {
    visitNameRefElement(o);
  }

  public void visitNumericForStatement(@NotNull LuaNumericForStatement o) {
    visitPsiElement(o);
  }

  public void visitParList(@NotNull LuaParList o) {
    visitPsiElement(o);
  }

  public void visitPrefixExpr(@NotNull LuaPrefixExpr o) {
    visitPsiElement(o);
  }

  public void visitRepeatStatement(@NotNull LuaRepeatStatement o) {
    visitPsiElement(o);
  }

  public void visitStatement(@NotNull LuaStatement o) {
    visitPsiElement(o);
  }

  public void visitTableConstructor(@NotNull LuaTableConstructor o) {
    visitPsiElement(o);
  }

  public void visitUnOp(@NotNull LuaUnOp o) {
    visitPsiElement(o);
  }

  public void visitVar(@NotNull LuaVar o) {
    visitPsiElement(o);
  }

  public void visitVarList(@NotNull LuaVarList o) {
    visitPsiElement(o);
  }

  public void visitVarOrExp(@NotNull LuaVarOrExp o) {
    visitPsiElement(o);
  }

  public void visitVarSuffix(@NotNull LuaVarSuffix o) {
    visitPsiElement(o);
  }

  public void visitWhileStatement(@NotNull LuaWhileStatement o) {
    visitPsiElement(o);
  }

  public void visitNameDeclElement(@NotNull LuaNameDeclElement o) {
    visitPsiElement(o);
  }

  public void visitNameRefElement(@NotNull LuaNameRefElement o) {
    visitPsiElement(o);
  }

  public void visitDocCommentOwner(@NotNull LuaDocCommentOwner o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
