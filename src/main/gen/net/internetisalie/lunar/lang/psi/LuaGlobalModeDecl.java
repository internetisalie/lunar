// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiComment;
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment;

public interface LuaGlobalModeDecl extends LuaStatement, LuaCommentOwner {

  @Nullable
  LuaAttrib getAttrib();

  @Nullable
  PsiComment getComment();

  //WARNING: getDocComment(...) is skipped
  //matching getDocComment(LuaGlobalModeDecl, ...)
  //methods are not found in LuaPsiImplUtil

  @Nullable
  LuaCatsComment getCatsComment();

}
