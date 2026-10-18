package com.spendingtracker.app.util

import android.content.Context
import android.os.Build
import android.view.InputDevice

/**
 * Detects a phone-style physical keypad so the app can keep the soft keyboard
 * hidden when a form is opened. This is only used for initial focus behaviour;
 * tapping a field still raises the IME, so touchscreen entry keeps working.
 */
object DeviceInput {

    fun isKnownQinDevice(
        model: String = Build.MODEL ?: "",
        manufacturer: String = Build.MANUFACTURER ?: ""
    ): Boolean {
        val name = "$manufacturer $model".lowercase()
        return name.contains("qin") || name.contains("duoqin") || name.contains("f21")
    }

    fun hasPhysicalKeypad(context: Context): Boolean {
        if (isKnownQinDevice()) return true
        for (id in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(id) ?: continue
            val sources = device.sources
            val keyboard = sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD
            if (keyboard && (device.keyboardType == InputDevice.KEYBOARD_QWERTY ||
                    device.keyboardType == InputDevice.KEYBOARD_12KEY)) {
                return true
            }
        }
        return false
    }
}
