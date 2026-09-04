package com.github.erjotek.stickyprojectfolder.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Color
import javax.swing.JTree

/**
 * Sticky bars are non-opaque components painted over a scrolling tree, so their fill must be
 * fully opaque. Some themes give `Tree.background` (or a file color) an alpha channel, which
 * makes the bars look see-through. Flatten anything translucent onto an opaque base.
 */
internal fun stickyFillColor(tree: JTree, overlay: Color? = null): Color {
    val base = flattenColor(tree.background ?: UIUtil.getTreeBackground(), OPAQUE_FALLBACK)
    return if (overlay == null) base else flattenColor(overlay, base)
}

// Last resort when even the theme's tree background is translucent.
private val OPAQUE_FALLBACK: Color = JBColor(Color(0xFFFFFF), Color(0x2B2B2B))

internal fun flattenColor(top: Color, base: Color): Color {
    if (top.alpha == 255) return top
    val a = top.alpha / 255f
    fun mix(t: Int, b: Int) = (t * a + b * (1 - a)).toInt().coerceIn(0, 255)
    return Color(mix(top.red, base.red), mix(top.green, base.green), mix(top.blue, base.blue))
}
