package net.internetisalie.lunar.lang.psi;

import com.intellij.psi.tree.IElementType;
import net.internetisalie.lunar.LuaLanguage;

public class LuaElementType extends IElementType {
    private final String name;

    public LuaElementType(String name) {
        super(name, LuaLanguage.INSTANCE);
        this.name = name;
    }

    public String toString() {
        return name;
    }
}
