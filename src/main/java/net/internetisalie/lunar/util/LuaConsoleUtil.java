package net.internetisalie.lunar.util;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.content.impl.ContentImpl;
import org.jetbrains.annotations.NotNull;

public class LuaConsoleUtil {
    private static final Key<ConsoleView> CONSOLE_VIEW_KEY = new Key<ConsoleView>("LuaConsoleView");
    final static String toolWindowId = "Lua Console Output";

    public static void printMessageToConsole(@NotNull Project project, @NotNull String s,
                                             @NotNull ConsoleViewContentType contentType) {
        activateConsoleToolWindow(project);
        final ConsoleView consoleView = project.getUserData(CONSOLE_VIEW_KEY);

        if (consoleView != null) {
            consoleView.print(s + '\n', contentType);
        }
    }

    public static void clearConsoleToolWindow(@NotNull Project project) {
        final ToolWindowManager manager = ToolWindowManager.getInstance(project);
        ToolWindow toolWindow = manager.getToolWindow(toolWindowId);
        if (toolWindow == null) return;
        toolWindow.getContentManager().removeAllContents(false);
        toolWindow.hide(null);
    }

    private static void activateConsoleToolWindow(@NotNull Project project) {
        final ToolWindowManager manager = ToolWindowManager.getInstance(project);


        ToolWindow toolWindow = manager.getToolWindow(toolWindowId);
        if (toolWindow != null) {
            return;
        }

        toolWindow = manager.registerToolWindow(toolWindowId, true, ToolWindowAnchor.BOTTOM);
        final ConsoleView console = new ConsoleViewImpl(project, false);
        project.putUserData(CONSOLE_VIEW_KEY, console);
        toolWindow.getContentManager().addContent(new ContentImpl(console.getComponent(), "", false));

        final ToolWindowManagerListener listener = new ToolWindowManagerListener() {
            @Override
            public void stateChanged() {
                // TODO : Deactivate
//                ToolWindow window = manager.getToolWindow(toolWindowId);
//                if (window != null && !window.isVisible()) {
//                    manager.unregisterToolWindow(toolWindowId);
//                    ((ToolWindowManagerEx) manager).removeToolWindowManagerListener(this);
//                }
            }
        };

        toolWindow.show(new Runnable() {
            @Override
            public void run() {
                ((ToolWindowManagerEx) manager).addToolWindowManagerListener(listener);
            }
        });
    }

}
