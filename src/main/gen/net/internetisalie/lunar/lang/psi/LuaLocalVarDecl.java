// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiComment;
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment;
import net.internetisalie.lunar.luadoc.lang.psi.LuaDocComment;

public interface LuaLocalVarDecl extends LuaStatement, LuaCommentOwner {

  @NotNull
  List<LuaAttName> getAttNameList();

  @Nullable
  LuaExprList getExprList();

  @Nullable PsiComment getComment();

  @Nullable LuaDocComment getDocComment();

  @Nullable LuaCatsComment getCatsComment();

}
