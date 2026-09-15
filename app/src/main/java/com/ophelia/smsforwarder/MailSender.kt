package com.ophelia.smsforwarder

import java.util.Date
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/** SMTP でテキストメールを 1 通送る。呼び出し元はバックグラウンドスレッドであること。 */
object MailSender {

    fun send(settings: MailSettings, subject: String, body: String) {
        require(settings.isComplete()) { "メール設定が未入力です" }

        val props = Properties().apply {
            put("mail.smtp.host", settings.smtpHost)
            put("mail.smtp.port", settings.smtpPort.toString())
            put("mail.smtp.auth", "true")
            put("mail.smtp.connectiontimeout", "20000")
            put("mail.smtp.timeout", "20000")
            put("mail.smtp.writetimeout", "20000")
            put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3")
            if (settings.useSsl) {
                put("mail.smtp.ssl.enable", "true")
            } else {
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
            }
        }

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() =
                PasswordAuthentication(settings.username, settings.password)
        })

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(settings.fromAddress))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(settings.toAddress))
            setSubject(subject, "UTF-8")
            setText(body, "UTF-8")
            sentDate = Date()
        }

        Transport.send(message)
    }
}
