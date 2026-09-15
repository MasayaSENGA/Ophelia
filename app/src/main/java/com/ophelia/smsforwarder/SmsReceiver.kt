package com.ophelia.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * SMS 受信ブロードキャストを受け取り、転送ジョブを WorkManager に積む。
 * レシーバー内では通信しない (実行時間に 10 秒の制限があるため)。
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            ?.filterNotNull()
            ?.takeIf { it.isNotEmpty() }
            ?: return

        val store = SettingsStore(context)
        val settings = store.load()
        if (!settings.enabled) return

        // 長文 SMS は分割して届くので、送信元ごとに連結する
        val grouped = messages.groupBy { it.originatingAddress ?: "" }
        for ((sender, parts) in grouped) {
            val body = parts.joinToString(separator = "") { it.messageBody ?: "" }
            val timestamp = parts.first().timestampMillis
            ForwardWorker.enqueue(context, sender, body, timestamp)
            store.appendLog("受信: ${sender.ifBlank { "(不明)" }} → 転送待ちに追加")
        }
    }
}
