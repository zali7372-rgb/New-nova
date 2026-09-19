package com.nova.assistant.system.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

data class PermissionInfo(
    val permission: String,
    val rationaleHu: String,
    val requiredForFeature: String
)

/**
 * Single source of truth for "which permissions does NOVA need and why".
 * Requests are only ever triggered by explicit user action (enabling voice,
 * etc.) - never silently at app startup for something the user hasn't asked
 * for yet.
 */
class PermissionManager(private val context: Context) {

    fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun missingPermissions(requested: List<String>): List<String> =
        requested.filterNot { isGranted(it) }

    fun requiredForVoice(): List<String> = listOf(Manifest.permission.RECORD_AUDIO)

    fun requiredForNotifications(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }

    fun allKnownPermissions(): List<PermissionInfo> = listOf(
        PermissionInfo(
            Manifest.permission.RECORD_AUDIO,
            "A mikrofonra a hangalapú vezérléshez van szükség (pl. „Nova, nyisd meg a YouTube-ot”).",
            "Hangvezérlés"
        ),
        PermissionInfo(
            Manifest.permission.POST_NOTIFICATIONS,
            "Az értesítésre azért van szükség, hogy lásd, amikor NOVA háttérben hallgat.",
            "Háttér-hallgatás állapotjelzése"
        ),
        PermissionInfo(
            Manifest.permission.INTERNET,
            "Az internet-hozzáférésre a távoli AI válaszokhoz, időjáráshoz és kereséshez van szükség.",
            "Online funkciók"
        )
    )
}
