package net.internetisalie.lunar.lang.psi;

import com.intellij.psi.tree.IElementType;
import net.internetisalie.lunar.lang.LuaLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class LuaTokenType extends IElementType {

    public LuaTokenType(@NotNull @NonNls String debugName) {
        super(debugName, LuaLanguage.INSTANCE);
    }

    @Override
    public String toString() {
        return "LuaTokenType." + super.toString();
    }

}