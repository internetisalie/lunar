// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.luacats.lang.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static net.internetisalie.lunar.luacats.lang.psi.LuaCatsElementTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import net.internetisalie.lunar.luacats.lang.psi.*;

public class LuaCatsCommentImpl extends ASTWrapperPsiElement implements LuaCatsComment {

  public LuaCatsCommentImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull LuaCatsVisitor visitor) {
    visitor.visitComment(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaCatsVisitor) accept((LuaCatsVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<LuaCatsAliasTag> getAliasTagList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsAliasTag.class);
  }

  @Override
  @NotNull
  public List<LuaCatsAsyncTag> getAsyncTagList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsAsyncTag.class);
  }

  @Override
  @NotNull
  public List<LuaCatsCastTag> getCastTagList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsCastTag.class);
  }

  @Override
  @NotNull
  public List<LuaCatsClassTag> getClassTagList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsClassTag.class);
  }

  @Override
  @NotNull
  public List<LuaCatsDeprecatedTag> getDeprecatedTagList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsDeprecatedTag.class);
  }

  @Override
  @NotNull
  public List<LuaCatsDescription> getDescriptionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsDescription.class);
  }

  @Override
  @NotNull
  public List<LuaCatsDiagnosticTag> getDiagnosticTagList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsDiagnosticTag.class);
  }

  @Override
  @NotNull
  public List<LuaCatsEnumTag> getEnumTagList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsEnumTag.class);
  }

  @Override
  @NotNull
  public List<LuaCatsFieldTag> getFieldTagList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsFieldTag.class);
  }

  @Override
  @NotNull
  public List<LuaCatsGenericTag> getGenericTagList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsGenericTag.class);
  }

  @Override
  @NotNull
  public List<LuaCatsMetaTag> getMetaTagList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsMetaTag.class);
  }

  @Override
  @NotNull
  public List<LuaCatsModuleTag> getModuleTagList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsModuleTag.class);
  }

  @Override
  @NotNull
  public List<LuaCatsNodiscardTag> getNodiscardTagList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsNodiscardTag.class);
  }

  @Override
  @NotNull
  public List<LuaCatsOperatorTag> getOperatorTagList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsOperatorTag.class);
  }

  @Override
  @NotNull
  public List<LuaCatsOverloadTag> getOverloadTagList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsOverloadTag.class);
  }

  @Override
  @NotNull
  public List<LuaCatsPackageTag> getPackageTagList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsPackageTag.class);
  }

  @Override
  @NotNull
  public List<LuaCatsParamTag> getParamTagList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsParamTag.class);
  }

  @Override
  @NotNull
  public List<LuaCatsPrivateTag> getPrivateTagList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsPrivateTag.class);
  }

  @Override
  @NotNull
  public List<LuaCatsProtectedTag> getProtectedTagList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsProtectedTag.class);
  }

  @Override
  @NotNull
  public List<LuaCatsReturnTag> getReturnTagList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsReturnTag.class);
  }

  @Override
  @NotNull
  public List<LuaCatsSeeTag> getSeeTagList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsSeeTag.class);
  }

  @Override
  @NotNull
  public List<LuaCatsSourceTag> getSourceTagList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsSourceTag.class);
  }

  @Override
  @NotNull
  public List<LuaCatsTypeOption> getTypeOptionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsTypeOption.class);
  }

  @Override
  @NotNull
  public List<LuaCatsTypeTag> getTypeTagList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsTypeTag.class);
  }

  @Override
  @NotNull
  public List<LuaCatsVarargTag> getVarargTagList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsVarargTag.class);
  }

  @Override
  @NotNull
  public List<LuaCatsVersionTag> getVersionTagList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaCatsVersionTag.class);
  }

}
