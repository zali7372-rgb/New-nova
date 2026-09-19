package com.nova.assistant.system.device

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.provider.Settings

sealed class DeviceActionResult {
    data class Success(val spokenConfirmation: String) : DeviceActionResult()
    data class Unsupported(val reason: String) : DeviceActionResult()
}

/**
 * Executes the "open X system screen / feature" actions NOVA supports.
 * Every intent here is a standard, documented Android intent - nothing here
 * bypasses permissions or security in any way; if Android refuses to resolve
 * the intent (e.g. OEM removed a settings screen), NOVA reports that clearly
 * instead of crashing.
 */
class DeviceActionManager(private val context: Context) {

    fun execute(action: String): DeviceActionResult {
        val intent = when (action) {
            "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "notification_settings" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            "app_settings" -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
            "phone_settings" -> Intent(Settings.ACTION_SETTINGS)
            "browser" -> Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com"))
            "camera" -> Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            "calendar" -> Intent(Intent.ACTION_VIEW, android.net.Uri.parse("content://com.android.calendar/time"))
            "clock" -> Intent("android.intent.action.SHOW_ALARMS")
            "contacts" -> Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI)
            else -> null
        }

        if (intent == null) {
            return DeviceActionResult.Unsupported("Ezt a funkciót nem ismerem fel: $action")
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            DeviceActionResult.Success(confirmationFor(action))
        } catch (e: ActivityNotFoundException) {
            DeviceActionResult.Unsupported(
                "A készüléked nem támogatja ezt a funkciót, vagy nincs hozzá alkalmazás telepítve."
            )
        }
    }

    private fun confirmationFor(action: String): String = when (action) {
        "wifi" -> "Megnyitottam a Wi-Fi beállításokat."
        "bluetooth" -> "Megnyitottam a Bluetooth beállításokat."
        "notification_settings" -> "Megnyitottam az értesítési beállításokat."
        "app_settings" -> "Megnyitottam az alkalmazás beállításokat."
        "phone_settings" -> "Megnyitottam a beállításokat."
        "browser" -> "Megnyitottam a böngészőt."
        "camera" -> "Elindítottam a kamerát."
        "calendar" -> "Megnyitottam a naptárat."
        "clock" -> "Megnyitottam az órát."
        "contacts" -> "Megnyitottam a névjegyeket."
        else -> "Rendben."
    }
}
