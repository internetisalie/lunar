package net.internetisalie.lunar.util;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import net.internetisalie.lunar.LuaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.regex.Pattern;

public class LuaFileUtil {
    @NotNull
    public static String getPathToDisplay(final VirtualFile file) {
        if (file == null) {
            return "";
        }
        return FileUtil.toSystemDependentName(file.getPath());
    }

    @Nullable
    public static VirtualFile getPluginVirtualDirectory() {
        IdeaPluginDescriptor descriptor = PluginManager.getPlugin(PluginId.getId(LuaPlugin.ID));
        if (descriptor != null) {
            File pluginPath = descriptor.getPath();

            String url = VfsUtil.pathToUrl(pluginPath.getAbsolutePath());

            return VirtualFileManager.getInstance().findFileByUrl(url);
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

    public static boolean isGlob(String filename) {
        return filename.contains("*") || filename.contains("?");
    }

    public static boolean matchesGlob(String glob, String filename) {
        Pattern p = patternFromGlob(glob);
        return p.matcher(filename).matches();
    }

    // http://stackoverflow.com/questions/1247772
    public static Pattern patternFromGlob(String glob) {
        String out = "^";
        for(int i = 0; i < glob.length(); ++i)
        {
            final char c = glob.charAt(i);
            switch(c)
            {
                case '*': out += ".*"; break;
                case '?': out += '.'; break;
                case '.': out += "\\."; break;
                case '\\': out += "\\\\"; break;
                default: out += c;
            }
        }
        out += '$';
        return Pattern.compile(out);
    }
}
