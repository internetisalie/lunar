package net.internetisalie.lunar

import com.intellij.CommonBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.lang.ref.Reference
import java.lang.ref.SoftReference
import java.util.ResourceBundle

/**
 * Created by IntelliJ IDEA.
 * User: Max
 * Date: 26.07.2009
 * Time: 14:30:36
 */
object LuaBundle {
    private var ourBundle: Reference<ResourceBundle?>? = null

    private const val BUNDLE: @NonNls String = "net.internetisalie.lunar.LuaBundle"

    @JvmStatic
    fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any?): String {
        return CommonBundle.message(bundle, key, *params)
    }

    private val bundle: ResourceBundle
        get() {
            var bundle: ResourceBundle? = null
            if (ourBundle != null) bundle = ourBundle!!.get()
            if (bundle == null) {
                bundle = ResourceBundle.getBundle(BUNDLE)
                ourBundle = SoftReference<ResourceBundle?>(bundle)
            }
            return bundle
        }
}