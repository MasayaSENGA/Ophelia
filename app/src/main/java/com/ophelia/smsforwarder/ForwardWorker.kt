package com.ophelia.smsforwarder

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 1 通の SMS をメールで転送するジョブ。失敗時は指数バックオフで再試行する。 */
class ForwardWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val sender = inputData.getString(KEY_SENDER).orEmpty().ifBlank { "(不明)" }
        val body = inputData.getString(KEY_BODY).orEmpty()
        val timestamp = inputData.getLong(KEY_TIMESTAMP, System.currentTimeMillis())

        val store = SettingsStore(applicationContext)
        val settings = store.load()

        if (!settings.isComplete()) {
            store.appendLog("送信中止: メール設定が未入力 ($sender)")
            return Result.failure()
        }

        val subject = buildSubject(settings.subjectPrefix, sender)
        val text = buildBody(sender, body, timestamp)

        return try {
            MailSender.send(settings, subject, text)
            store.appendLog("送信成功: $sender → ${settings.toAddress}")
            Result.success()
        } catch (e: Exception) {
            val attempt = runAttemptCount + 1
            val reason = e.message ?: e.javaClass.simpleName
            store.appendLog("送信失敗 (${attempt}回目): $reason")
            if (attempt >= MAX_ATTEMPTS) {
                ErrorNotifier.notifyFailure(applicationContext, sender, reason)
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    companion object {
        private const val KEY_SENDER = "sender"
        private const val KEY_BODY = "body"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val MAX_ATTEMPTS = 5
        const val TAG = "sms_forward"

        fun enqueue(context: Context, sender: String, body: String, timestamp: Long) {
            val request = OneTimeWorkRequestBuilder<ForwardWorker>()
                .setInputData(
                    workDataOf(
                        KEY_SENDER to sender,
                        KEY_BODY to body,
                        KEY_TIMESTAMP to timestamp,
                    )
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun buildSubject(prefix: String, sender: String): String =
            listOf(prefix.trim(), "$sender からのSMS")
                .filter { it.isNotBlank() }
                .joinToString(" ")

        fun buildBody(sender: String, body: String, timestamp: Long): String {
            val received = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN)
                .format(Date(timestamp))
            return buildString {
                appendLine("送信元: $sender")
                appendLine("受信日時: $received")
                appendLine()
                appendLine("本文:")
                appendLine(body)
                appendLine()
                appendLine("--")
                appendLine("SMS転送アプリより自動送信")
            }
        }
    }
}
