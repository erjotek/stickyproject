package com.github.erjotek.stickyprojectfolder.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.Color

class StickyColorsTest {

    @Test
    fun opaqueColorIsReturnedAsIs() {
        val c = Color(10, 20, 30)
        assertEquals(c, flattenColor(c, Color.WHITE))
    }

    @Test
    fun translucentColorIsFlattenedOntoBase() {
        val result = flattenColor(Color(255, 0, 0, 0), Color(0, 0, 255))
        assertEquals(Color(0, 0, 255), result)
        assertEquals(255, result.alpha)
    }

    @Test
    fun halfTransparentBlendsBothColors() {
        val result = flattenColor(Color(0, 0, 0, 128), Color(255, 255, 255))
        assertEquals(255, result.alpha)
        assertEquals(126, result.red)
    }
}
