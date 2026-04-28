// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiComment;
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment;

public interface LuaLocalFuncDecl extends LuaStatement, LuaCommentOwner, LuaBlockParent {

  @NotNull
  LuaBlock getBlock();

  @NotNull
  LuaNameRef getNameRef();

  @Nullable
  LuaParList getParList();

  @Nullable PsiComment getComment();

  //WARNING: getDocComment(...) is skipped
  //matching getDocComment(LuaLocalFuncDecl, ...)
  //methods are not found in LuaPsiImplUtil

  @Nullable LuaCatsComment getCatsComment();

  @NotNull List<@NotNull LuaBlock> getBlockList();

}
