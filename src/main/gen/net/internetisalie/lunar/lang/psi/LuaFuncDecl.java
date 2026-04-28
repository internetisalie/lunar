// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiComment;
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment;

public interface LuaFuncDecl extends LuaStatement, LuaCommentOwner, LuaBlockParent {

  @NotNull
  LuaBlock getBlock();

  @NotNull
  LuaFuncName getFuncName();

  @Nullable
  LuaParList getParList();

  @Nullable PsiComment getComment();

  //WARNING: getDocComment(...) is skipped
  //matching getDocComment(LuaFuncDecl, ...)
  //methods are not found in LuaPsiImplUtil

  @Nullable LuaCatsComment getCatsComment();

  @NotNull List<@NotNull LuaBlock> getBlockList();

}
