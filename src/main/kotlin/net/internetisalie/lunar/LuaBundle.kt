package net.internetisalie.lunar

import com.intellij.BundleBase
import org.jetbrains.annotations.PropertyKey
import java.lang.ref.Reference
import java.lang.ref.SoftReference
import java.util.*

/**
 * Created by IntelliJ IDEA.
 * User: Max
 * Date: 26.07.2009
 * Time: 14:30:36
 */
object LuaBundle {
    private var myBundle: Reference<ResourceBundle?>? = null

    private const val BUNDLE: String = "net.internetisalie.lunar.LuaBundle"

    @JvmStatic
    fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): String {
        return BundleBase.message(bundle, key, *params)
    }

    private val bundle: ResourceBundle
        get() {
            var bundle: ResourceBundle? = null
            if (myBundle != null) bundle = myBundle!!.get()
            if (bundle == null) {
                bundle = ResourceBundle.getBundle(BUNDLE)
                myBundle = SoftReference<ResourceBundle?>(bundle)
            }
            return bundle
        }
}
