// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.lang.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static net.internetisalie.lunar.lang.psi.LuaElementTypes.*;
import net.internetisalie.lunar.lang.psi.LuaBaseElement;
import net.internetisalie.lunar.lang.psi.*;

public class LuaFieldListImpl extends LuaBaseElement implements LuaFieldList {

  public LuaFieldListImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull LuaVisitor visitor) {
    visitor.visitFieldList(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaVisitor) accept((LuaVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<LuaField> getFieldList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaField.class);
  }

  @Override
  @NotNull
  public List<LuaFieldSep> getFieldSepList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaFieldSep.class);
  }

}
