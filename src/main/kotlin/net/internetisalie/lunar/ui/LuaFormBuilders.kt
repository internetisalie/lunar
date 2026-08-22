package net.internetisalie.lunar.ui

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.DialogUtil
import com.intellij.util.ui.FormBuilder
import javax.swing.AbstractButton
import javax.swing.JComponent

/** Marks the mnemonic letter in the label texts passed to the helpers below. */
private const val MNEMONIC_MARKER = '&'

/**
 * Adds a labelled row whose label satisfies the platform text rules of engineering contract §6:
 * the colon is written into [labelText], the letter after `&` becomes the mnemonic, and `labelFor`
 * points at [component] so the mnemonic moves focus there.
 *
 * `FormBuilder`'s own `String` overload cannot do this, which is why this helper exists. That
 * overload runs the text through `UIUtil.replaceMnemonicAmpersand`, which rewrites `&R` to
 * `U+001B R`, and hands the result to a bare `JLabel` — nothing in the platform then turns that
 * escape into a mnemonic. Measured on the GoLand 2026.1 test platform,
 * `addLabeledComponent("&Runtime:", combo)` produces a label whose text still carries the U+001B
 * control character, with `displayedMnemonic = 0` and `displayedMnemonicIndex = -1`. Only
 * `DialogUtil.registerMnemonic` sets one.
 */
fun FormBuilder.addMnemonicLabeledComponent(
    labelText: String,
    component: JComponent,
): FormBuilder {
    val label = JBLabel(labelText)
    DialogUtil.registerMnemonic(label, component, MNEMONIC_MARKER)
    label.labelFor = component
    return addLabeledComponent(label, component)
}

/**
 * Gives the receiver the mnemonic marked with `&` in its own text and strips the marker, so a
 * checkbox row carries a mnemonic just as a labelled row does.
 */
fun <T : AbstractButton> T.withMnemonic(): T {
    DialogUtil.registerMnemonic(this, MNEMONIC_MARKER)
    return this
}
