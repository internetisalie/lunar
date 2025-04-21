// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment;
import net.internetisalie.lunar.luadoc.lang.psi.LuaDocComment;

public interface LuaLocalFuncDecl extends LuaCommentOwner {

  @NotNull
  LuaBlock getBlock();

  @NotNull
  LuaNameRef getNameRef();

  @Nullable
  LuaParList getParList();

  @NotNull LuaComment getComment();

  @Nullable LuaDocComment getDocComment();

  @Nullable LuaCatsComment getCatsComment();

}
