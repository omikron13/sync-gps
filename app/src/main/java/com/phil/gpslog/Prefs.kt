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
    private const val KEY_SMTP_HOST = "smtp_host"
    private const val KEY_SMTP_PORT = "smtp_port"

    // Defaults: OVH mail (MX Plan). Username = full email address.
    const val DEFAULT_FROM = "phil@clicauto.com"
    const val DEFAULT_TO = "cashredac@gmail.com"
    const val DEFAULT_SMTP_HOST = "ssl0.ovh.net"
    const val DEFAULT_SMTP_PORT = 465

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

    fun save(
        context: Context,
        from: String,
        password: String,
        to: String,
        smtpHost: String,
        smtpPort: Int
    ) {
        prefs(context).edit()
            .putString(KEY_FROM, from.trim())
            .putString(KEY_APP_PASSWORD, password)
            .putString(KEY_TO, to.trim())
            .putString(KEY_SMTP_HOST, smtpHost.trim())
            .putInt(KEY_SMTP_PORT, smtpPort)
            .apply()
    }

    fun fromEmail(context: Context): String = prefs(context).getString(KEY_FROM, DEFAULT_FROM) ?: DEFAULT_FROM
    fun appPassword(context: Context): String = prefs(context).getString(KEY_APP_PASSWORD, "") ?: ""
    fun toEmail(context: Context): String = prefs(context).getString(KEY_TO, DEFAULT_TO) ?: DEFAULT_TO
    fun smtpHost(context: Context): String =
        prefs(context).getString(KEY_SMTP_HOST, DEFAULT_SMTP_HOST) ?: DEFAULT_SMTP_HOST
    fun smtpPort(context: Context): Int = prefs(context).getInt(KEY_SMTP_PORT, DEFAULT_SMTP_PORT)

    fun isConfigured(context: Context): Boolean =
        fromEmail(context).isNotEmpty() && appPassword(context).isNotEmpty() &&
            toEmail(context).isNotEmpty() && smtpHost(context).isNotEmpty()

    fun lastSend(context: Context): Long = prefs(context).getLong(KEY_LAST_SEND, 0L)
    fun setLastSend(context: Context, ms: Long) = prefs(context).edit().putLong(KEY_LAST_SEND, ms).apply()

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
}
