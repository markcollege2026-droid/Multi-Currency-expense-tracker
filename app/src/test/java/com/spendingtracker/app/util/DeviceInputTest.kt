package com.spendingtracker.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceInputTest {

    @Test
    fun qinVariantsAreDetected() {
        assertTrue(DeviceInput.isKnownQinDevice(model = "F21 Pro", manufacturer = "Duoqin"))
        assertTrue(DeviceInput.isKnownQinDevice(model = "Qin F21 Pro", manufacturer = "unknown"))
        assertTrue(DeviceInput.isKnownQinDevice(model = "F21", manufacturer = "QIN"))
    }

    @Test
    fun otherPhonesAreNotDetected() {
        assertFalse(DeviceInput.isKnownQinDevice(model = "Pixel 7", manufacturer = "Google"))
        assertFalse(DeviceInput.isKnownQinDevice(model = "", manufacturer = ""))
        assertFalse(DeviceInput.isKnownQinDevice(model = "M21", manufacturer = "Samsung"))
    }
}
