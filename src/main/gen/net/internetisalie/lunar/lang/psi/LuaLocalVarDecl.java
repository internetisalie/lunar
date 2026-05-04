// This is a generated file. Not intended for manual editing.
package net.internetisalie.lunar.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiComment;
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment;

import com.intellij.psi.StubBasedPsiElement;
import net.internetisalie.lunar.lang.psi.stubs.LuaLocalVarStub;

public interface LuaLocalVarDecl extends LuaStatement, LuaCommentOwner, StubBasedPsiElement<LuaLocalVarStub> {

  @NotNull
  List<LuaAttName> getAttNameList();

  @Nullable
  LuaExprList getExprList();

  @Nullable PsiComment getComment();

  //WARNING: getDocComment(...) is skipped
  //matching getDocComment(LuaLocalVarDecl, ...)
  //methods are not found in LuaPsiImplUtil

  @Nullable LuaCatsComment getCatsComment();

}
