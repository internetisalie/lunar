package net.internetisalie.lunar.lang;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import net.internetisalie.lunar.lang.psi.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class LuaNameUtil {
    public static List<LuaLabel> findLabels(PsiFile containingFile, String name) {
        List<LuaLabel> result = new ArrayList<>();
        Collection<LuaLabel> labels = PsiTreeUtil.findChildrenOfType(containingFile, LuaLabel.class);
        for (LuaLabel label : labels) {
            if (label.getLabelName().getIdentifier().getText().equals(name)) {
                result.add(label);
            }
        }
        return result;
    }
}
