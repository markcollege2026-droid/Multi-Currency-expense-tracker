package com.spendingtracker.app.ui.compact

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendingtracker.app.ui.compact.compactTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QinLayoutTest {

    @Test
    fun qinWindowSizesAreCompact() {
        // 480x640 at density 2 (240dpi) and at density 1.5 (320dpi).
        assertTrue(QinLayout.isCompactScreen(320, 427))
        assertTrue(QinLayout.isCompactScreen(240, 320))
    }

    @Test
    fun regularPhonesAreNotCompact() {
        assertFalse(QinLayout.isCompactScreen(411, 891))
        assertFalse(QinLayout.isCompactScreen(360, 800))
    }

    @Test
    fun zeroConfigurationIsNotTreatedAsCompact() {
        assertFalse(QinLayout.isCompactScreen(0, 0))
        assertFalse(QinLayout.isCompactScreen(0, 320))
        assertFalse(QinLayout.isCompactScreen(320, 0))
    }

    @Test
    fun compactDimensAreSmallerThanRegular() {
        assertTrue(CompactDimens.screenPadding < RegularDimens.screenPadding)
        assertTrue(CompactDimens.topBarHeight < RegularDimens.topBarHeight)
        assertTrue(CompactDimens.navBarHeight < RegularDimens.navBarHeight)
        assertTrue(CompactDimens.iconButtonSize < RegularDimens.iconButtonSize)
        assertEquals(11.dp, CompactDimens.screenPadding)
        assertEquals(45.dp, CompactDimens.topBarHeight)
        assertEquals(57.dp, CompactDimens.navBarHeight)
    }

    @Test
    fun compactDimensSitBetweenTightAndRegular() {
        // The converter keeps TightDimens; the rest of the app uses the midpoint.
        assertTrue(TightDimens.screenPadding < CompactDimens.screenPadding)
        assertTrue(CompactDimens.screenPadding < RegularDimens.screenPadding)
        assertTrue(TightDimens.topBarHeight < CompactDimens.topBarHeight)
        assertTrue(CompactDimens.topBarHeight < RegularDimens.topBarHeight)
        assertTrue(TightDimens.navBarHeight < CompactDimens.navBarHeight)
        assertTrue(CompactDimens.navBarHeight < RegularDimens.navBarHeight)
        assertEquals(6.dp, TightDimens.screenPadding)
        assertEquals(34.dp, TightDimens.topBarHeight)
        assertEquals(42.dp, TightDimens.navBarHeight)
    }

    @Test
    fun compactTypeKeepsReadableBodyText() {
        val type = compactTypography()
        assertEquals(12.sp, type.bodyMedium.fontSize)
        assertEquals(13.sp, type.bodyLarge.fontSize)
        assertEquals(9.sp, type.labelSmall.fontSize)
        assertEquals(15.sp, type.titleLarge.fontSize)
    }

    @Test
    fun relaxedCompactTypeSitsBetweenTightAndRegular() {
        val tight = compactTypography()
        val relaxed = relaxedCompactTypography()
        val regular = androidx.compose.material3.Typography()
        assertEquals(13.sp, relaxed.bodyMedium.fontSize)
        assertEquals(14.5.sp, relaxed.bodyLarge.fontSize)
        assertEquals(10.sp, relaxed.labelSmall.fontSize)
        assertEquals(18.5.sp, relaxed.titleLarge.fontSize)
        assertTrue(tight.bodyMedium.fontSize.value < relaxed.bodyMedium.fontSize.value)
        assertTrue(relaxed.bodyMedium.fontSize.value < regular.bodyMedium.fontSize.value)
        assertTrue(tight.titleLarge.fontSize.value < relaxed.titleLarge.fontSize.value)
        assertTrue(relaxed.titleLarge.fontSize.value < regular.titleLarge.fontSize.value)
    }
}
