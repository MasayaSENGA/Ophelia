package com.ophelia.smsforwarder

data class MailSettings(
    val enabled: Boolean = false,
    val smtpHost: String = "",
    val smtpPort: Int = 465,
    /** true: SSL/TLS (通常 465)、false: STARTTLS (通常 587) */
    val useSsl: Boolean = true,
    val username: String = "",
    val password: String = "",
    val fromAddress: String = "",
    val toAddress: String = "",
    val subjectPrefix: String = "[SMS]",
) {
    fun isComplete(): Boolean =
        smtpHost.isNotBlank() &&
            smtpPort in 1..65535 &&
            username.isNotBlank() &&
            password.isNotBlank() &&
            fromAddress.isNotBlank() &&
            toAddress.isNotBlank()
}
