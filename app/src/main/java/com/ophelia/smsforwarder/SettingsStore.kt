package com.ophelia.smsforwarder

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 設定は EncryptedSharedPreferences に暗号化して保存する。
 * 動作ログは通常の SharedPreferences に保存する (SMS 本文は記録しない)。
 */
class SettingsStore(context: Context) {

    private val appContext = context.applicationContext

    private val secure: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "settings_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val plain: SharedPreferences by lazy {
        appContext.getSharedPreferences("settings_plain", Context.MODE_PRIVATE)
    }

    fun load(): MailSettings = MailSettings(
        enabled = secure.getBoolean(KEY_ENABLED, false),
        smtpHost = secure.getString(KEY_HOST, "") ?: "",
        smtpPort = secure.getInt(KEY_PORT, 465),
        useSsl = secure.getBoolean(KEY_USE_SSL, true),
        username = secure.getString(KEY_USERNAME, "") ?: "",
        password = secure.getString(KEY_PASSWORD, "") ?: "",
        fromAddress = secure.getString(KEY_FROM, "") ?: "",
        toAddress = secure.getString(KEY_TO, "") ?: "",
        subjectPrefix = secure.getString(KEY_SUBJECT_PREFIX, "[SMS]") ?: "[SMS]",
    )

    fun save(s: MailSettings) {
        secure.edit {
            putBoolean(KEY_ENABLED, s.enabled)
            putString(KEY_HOST, s.smtpHost)
            putInt(KEY_PORT, s.smtpPort)
            putBoolean(KEY_USE_SSL, s.useSsl)
            putString(KEY_USERNAME, s.username)
            putString(KEY_PASSWORD, s.password)
            putString(KEY_FROM, s.fromAddress)
            putString(KEY_TO, s.toAddress)
            putString(KEY_SUBJECT_PREFIX, s.subjectPrefix)
        }
    }

    // ---- 動作ログ ----

    @Synchronized
    fun appendLog(message: String) {
        val stamp = SimpleDateFormat("MM/dd HH:mm:ss", Locale.JAPAN).format(Date())
        val lines = readLog().toMutableList()
        lines.add(0, "$stamp  $message")
        while (lines.size > MAX_LOG_LINES) lines.removeAt(lines.size - 1)
        plain.edit { putString(KEY_LOG, lines.joinToString("\n")) }
    }

    fun readLog(): List<String> {
        val raw = plain.getString(KEY_LOG, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split('\n')
    }

    fun clearLog() {
        plain.edit { remove(KEY_LOG) }
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_HOST = "smtp_host"
        const val KEY_PORT = "smtp_port"
        const val KEY_USE_SSL = "use_ssl"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_FROM = "from"
        const val KEY_TO = "to"
        const val KEY_SUBJECT_PREFIX = "subject_prefix"
        const val KEY_LOG = "log"
        const val MAX_LOG_LINES = 50
    }
}
