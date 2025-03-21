package net.internetisalie.lunar.lang;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import net.internetisalie.lunar.lang.psi.*;
import net.internetisalie.lunar.project.PlatformLibraryIndex;
import net.internetisalie.lunar.settings.LuaApplicationSettings;
import net.internetisalie.lunar.settings.LuaProjectSettings;
import org.jetbrains.annotations.NotNull;

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

    @NotNull
    public static List<PsiElement> findVars(@NotNull PsiElement element, @NotNull String name) {
        List<PsiElement> results = new ArrayList<>();

        element = findContainingStatement(element);
        if (element == null) { return results; }

        while (!(element instanceof PsiFile)) {
            if (element instanceof LuaStatement) {
                var statement = element.getFirstChild();
                if (statement instanceof LuaLocalVarDecl luaLocalVarDecl) {
                    for (var luaLocalVarName : luaLocalVarDecl.getNameList().getNameDeclList() ) {
                        if (luaLocalVarName.getIdentifier().getText().equals(name)) {
                            results.add(luaLocalVarName);
                        }
                    }
                } else if (statement instanceof LuaAssignmentStatement luaAssignmentStatement) {
                    for (var luaVar : luaAssignmentStatement.getVarList().getVarList()) {
                        if (luaVar.getVarName() != null) {
                            var luaVarName = luaVar.getVarName();
                            if (luaVarName.getIdentifier().getText().equals(name)) {
                                results.add(luaVarName);
                            }
                        }
                    }
                } else if (statement instanceof LuaFuncDecl luaFuncDecl) {
                    var funcName = luaFuncDecl.getFuncName();
                    if (funcName.getText().equals(name)) {
                        results.add(funcName);
                    }
                } else if (statement instanceof LuaLocalFuncDecl luaLocalFuncDecl) {
                    var localFuncName =  luaLocalFuncDecl.getLocalFuncName();
                    if (localFuncName.getIdentifier().getText().equals(name)) {
                        results.add(localFuncName);
                    }
                }
            }

            if (element.getPrevSibling() != null) {
                element = element.getPrevSibling();
            } else if (element.getParent() != null) {
                element = element.getParent();
            } else {
                break;
            }
        }

        if (element instanceof PsiFile) {
            var platformLibraryIndex = PlatformLibraryIndex.Companion.getInstance();

            // See if the name refers to a platform package
            var packageNames = platformLibraryIndex.getPackageNames();
            if (packageNames.containsKey(name)) {
                var packageFile = findVirtualFile(element.getProject(), packageNames.get(name));
                if (packageFile != null) {
                    results.add(packageFile);
                }
            }

            // See if the name refers to a platform global
            var globalNames = platformLibraryIndex.getGlobalNames();
            if (globalNames.containsKey(name)) {
                var globalFile = findVirtualFile(element.getProject(), globalNames.get(name));
                if (globalFile != null) {
                    results.add(globalFile);
                }
            }

            // TODO: require imports
        }


        return results;
    }

    private static PsiFile findVirtualFile(Project project , VirtualFile virtualFile) {
        var psiManager = PsiManager.getInstance(project);
        return psiManager.findFile(virtualFile);
    }

    private static PsiElement findContainingStatement(@NotNull PsiElement element) {
        while (element != null && !(element instanceof LuaStatement)) {
            element = element.getParent();
        }
        return element;
    }
}
