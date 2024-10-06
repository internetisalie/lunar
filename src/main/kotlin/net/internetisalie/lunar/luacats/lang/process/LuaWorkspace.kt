package net.internetisalie.lunar.luacats.lang.process

data class LuaWorkspace(val trees : List<String>) {
    val luaPath : String
        get() {
            val sb = StringBuilder()
            for (tree in trees) {
                sb
                    .append(tree).append("/?.lua;")
                    .append(tree).append("/?/init.lua;")
            }
            sb.append(";")  // append default package path
            return sb.toString()
        }

    val luaCPath : String
        get() {
            val sb = StringBuilder()
            for (tree in trees) {
                sb.append(tree).append("/?.so;")
            }
            sb.append(";")  // append default package path
            return sb.toString()
        }
}
