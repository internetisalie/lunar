// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.lang.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static net.internetisalie.lunar.lang.psi.LuaElementTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import net.internetisalie.lunar.lang.psi.*;

public class LuaLocalFuncDeclImpl extends ASTWrapperPsiElement implements LuaLocalFuncDecl {

  public LuaLocalFuncDeclImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull LuaVisitor visitor) {
    visitor.visitLocalFuncDecl(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaVisitor) accept((LuaVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public LuaFuncBody getFuncBody() {
    return findNotNullChildByClass(LuaFuncBody.class);
  }

  @Override
  @NotNull
  public LuaLocalFuncName getLocalFuncName() {
    return findNotNullChildByClass(LuaLocalFuncName.class);
  }

}
