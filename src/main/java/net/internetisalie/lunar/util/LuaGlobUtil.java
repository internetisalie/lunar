package net.internetisalie.lunar.util;

import java.util.regex.Pattern;

public class LuaGlobUtil {

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
