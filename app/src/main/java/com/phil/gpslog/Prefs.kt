package com.phil.gpslog

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Small wrapper around EncryptedSharedPreferences so the Gmail app-password is
 * never stored in clear text on the device.
 */
object Prefs {

    private const val FILE = "sync_secure_prefs"

    private const val KEY_FROM = "from_email"
    private const val KEY_APP_PASSWORD = "app_password"
    private const val KEY_TO = "to_email"
    private const val KEY_LAST_SEND = "last_send_ms"
    private const val KEY_ENABLED = "tracking_enabled"

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(context: Context, from: String, appPassword: String, to: String) {
        prefs(context).edit()
            .putString(KEY_FROM, from.trim())
            .putString(KEY_APP_PASSWORD, appPassword.replace(" ", ""))
            .putString(KEY_TO, to.trim())
            .apply()
    }

    fun fromEmail(context: Context): String = prefs(context).getString(KEY_FROM, "") ?: ""
    fun appPassword(context: Context): String = prefs(context).getString(KEY_APP_PASSWORD, "") ?: ""
    fun toEmail(context: Context): String =
        prefs(context).getString(KEY_TO, "cashredac@gmail.com") ?: "cashredac@gmail.com"

    fun isConfigured(context: Context): Boolean =
        fromEmail(context).isNotEmpty() && appPassword(context).isNotEmpty() && toEmail(context).isNotEmpty()

    fun lastSend(context: Context): Long = prefs(context).getLong(KEY_LAST_SEND, 0L)
    fun setLastSend(context: Context, ms: Long) = prefs(context).edit().putLong(KEY_LAST_SEND, ms).apply()

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
}
