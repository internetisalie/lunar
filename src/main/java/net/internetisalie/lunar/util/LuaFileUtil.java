package net.internetisalie.lunar.util;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import net.internetisalie.lunar.LuaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LuaFileUtil {
    public static final Key<Boolean> PREDEFINED_KEY = Key.create("lua.lib.predefined");

    @NotNull
    public static String getPathToDisplay(final VirtualFile file) {
        if (file == null) {
            return "";
        }
        return FileUtil.toSystemDependentName(file.getPath());
    }

    @Nullable
    public static VirtualFile getPluginVirtualDirectory() {
        var descriptor = PluginManagerCore.getPlugin(PluginId.getId(LuaPlugin.ID));
        if (descriptor != null) {
            return VirtualFileManager.getInstance().findFileByNioPath(descriptor.getPluginPath());
        }

        return null;
    }

    @Nullable
    public static VirtualFile getPluginVirtualDirectoryChild(String ...args) {
        VirtualFile dir = LuaFileUtil.getPluginVirtualDirectory();
        for (String arg : args) {
            if (dir == null) break;
            dir = dir.findChild(arg);
        }
        return dir;
    }
}
