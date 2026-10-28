package com.spendingtracker.app.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Settings theme choice must map to a live dark/light decision. */
class ThemeResolutionTest {

    @Test fun explicitChoicesWinOverTheSystem() {
        assertFalse(resolveDarkTheme("LIGHT", systemDark = true))
        assertFalse(resolveDarkTheme("LIGHT", systemDark = false))
        assertTrue(resolveDarkTheme("DARK", systemDark = true))
        assertTrue(resolveDarkTheme("DARK", systemDark = false))
    }

    @Test fun systemAndUnknownValuesFollowTheSystem() {
        assertTrue(resolveDarkTheme("SYSTEM", systemDark = true))
        assertFalse(resolveDarkTheme("SYSTEM", systemDark = false))
        assertTrue(resolveDarkTheme(null, systemDark = true))
        assertFalse(resolveDarkTheme(null, systemDark = false))
        assertTrue(resolveDarkTheme("something-else", systemDark = true))
    }
}
