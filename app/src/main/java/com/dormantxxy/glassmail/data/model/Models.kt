package com.dormantxxy.glassmail.data.model

import java.util.UUID

enum class MailProvider(val label: String) {
    GMAIL("Google"),
    OUTLOOK("Outlook"),
    YAHOO("Yahoo"),
    ICLOUD("iCloud"),
    QQ("QQ 邮箱"),
    NETEASE("网易邮箱"),
    CUSTOM("其他 IMAP")
}

enum class SecurityType { SSL_TLS, STARTTLS }
enum class AuthType { PASSWORD, XOAUTH2 }

enum class MailboxKind(val label: String) {
    INBOX("收件箱"),
    DRAFTS("草稿箱"),
    SENT("已发送"),
    ARCHIVE("归档"),
    TRASH("废纸篓"),
    SPAM("垃圾邮件"),
    STARRED("有旗标"),
    VIP("VIP"),
    ALL("所有邮件"),
    CUSTOM("文件夹")
}

enum class MailCategory(val label: String) {
    PRIMARY("主要"),
    TRANSACTIONS("交易"),
    UPDATES("动态"),
    PROMOTIONS("推广")
}

data class MailAccount(
    val id: String = UUID.randomUUID().toString(),
    val email: String,
    val displayName: String,
    val provider: MailProvider,
    val imapHost: String,
    val imapPort: Int,
    val imapSecurity: SecurityType = SecurityType.SSL_TLS,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpSecurity: SecurityType = SecurityType.STARTTLS,
    val username: String = email,
    val authType: AuthType = AuthType.PASSWORD,
    val color: Long = 0xFF0A84FF,
    val signature: String = "",
    val syncIntervalMinutes: Int = 15,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val isDemo: Boolean = false
)

data class Mailbox(
    val id: String,
    val accountId: String,
    val serverName: String,
    val displayName: String,
    val kind: MailboxKind,
    val unreadCount: Int = 0,
    val totalCount: Int = 0,
    val sortOrder: Int = 0
)

data class MailAddress(val name: String = "", val address: String) {
    val formatted: String get() = if (name.isBlank()) address else "$name <$address>"
}

data class MailMessage(
    val id: String = UUID.randomUUID().toString(),
    val accountId: String,
    val mailboxId: String,
    val remoteUid: Long = 0L,
    val messageId: String = "",
    val threadKey: String,
    val from: MailAddress,
    val to: List<MailAddress> = emptyList(),
    val cc: List<MailAddress> = emptyList(),
    val bcc: List<MailAddress> = emptyList(),
    val replyTo: List<MailAddress> = emptyList(),
    val subject: String = "(无主题)",
    val preview: String = "",
    val bodyText: String = "",
    val bodyHtml: String = "",
    val sentAt: Long = System.currentTimeMillis(),
    val receivedAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val isStarred: Boolean = false,
    val isFlagged: Boolean = false,
    val isDraft: Boolean = false,
    val hasAttachments: Boolean = false,
    val category: MailCategory = MailCategory.PRIMARY,
    val importance: Int = 0,
    val sizeBytes: Long = 0,
    val headers: String = ""
)

data class MailAttachment(
    val id: String = UUID.randomUUID().toString(),
    val messageId: String,
    val fileName: String,
    val mimeType: String = "application/octet-stream",
    val sizeBytes: Long = 0,
    val contentId: String = "",
    val localPath: String = "",
    val remotePart: String = ""
)

data class LocalAttachment(
    val uri: String,
    val name: String,
    val mimeType: String = "application/octet-stream",
    val sizeBytes: Long = 0
)

data class MailDraft(
    val id: String = UUID.randomUUID().toString(),
    val accountId: String,
    val to: List<String> = emptyList(),
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String = "",
    val body: String = "",
    val attachments: List<LocalAttachment> = emptyList(),
    val inReplyTo: String = "",
    val references: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val scheduledAt: Long = 0L
)

data class AccountSetup(
    val provider: MailProvider,
    val email: String,
    val displayName: String,
    val username: String,
    val secret: String,
    val authType: AuthType = AuthType.PASSWORD,
    val imapHost: String,
    val imapPort: Int,
    val imapSecurity: SecurityType,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpSecurity: SecurityType
) {
    fun toAccount(): MailAccount = MailAccount(
        email = email.trim(),
        displayName = displayName.trim().ifBlank { email.substringBefore('@') },
        provider = provider,
        username = username.trim().ifBlank { email.trim() },
        authType = authType,
        imapHost = imapHost.trim(),
        imapPort = imapPort,
        imapSecurity = imapSecurity,
        smtpHost = smtpHost.trim(),
        smtpPort = smtpPort,
        smtpSecurity = smtpSecurity
    )
}

sealed interface SyncState {
    data object Idle : SyncState
    data class Running(val label: String) : SyncState
    data class Success(val finishedAt: Long, val newMessages: Int) : SyncState
    data class Error(val message: String) : SyncState
}

data class ProviderSettings(
    val imapHost: String,
    val imapPort: Int = 993,
    val imapSecurity: SecurityType = SecurityType.SSL_TLS,
    val smtpHost: String,
    val smtpPort: Int = 587,
    val smtpSecurity: SecurityType = SecurityType.STARTTLS,
    val help: String = ""
)

object ProviderPresets {
    fun forProvider(provider: MailProvider, email: String = ""): ProviderSettings = when (provider) {
        MailProvider.GMAIL -> ProviderSettings("imap.gmail.com", smtpHost = "smtp.gmail.com", help = "请使用 Google 应用专用密码或 OAuth2 令牌")
        MailProvider.OUTLOOK -> ProviderSettings("outlook.office365.com", smtpHost = "smtp.office365.com", help = "Microsoft 账户通常需要 OAuth2 或应用密码")
        MailProvider.YAHOO -> ProviderSettings("imap.mail.yahoo.com", smtpHost = "smtp.mail.yahoo.com", smtpPort = 465, smtpSecurity = SecurityType.SSL_TLS, help = "请在 Yahoo 安全设置中创建应用密码")
        MailProvider.ICLOUD -> ProviderSettings("imap.mail.me.com", smtpHost = "smtp.mail.me.com", help = "请在 Apple ID 设置中创建应用专用密码")
        MailProvider.QQ -> ProviderSettings("imap.qq.com", smtpHost = "smtp.qq.com", smtpPort = 465, smtpSecurity = SecurityType.SSL_TLS, help = "需要在 QQ 邮箱设置中开启 IMAP 并生成授权码")
        MailProvider.NETEASE -> {
            val domain = email.substringAfter('@', "163.com")
            val prefix = when {
                domain.contains("126") -> "126"
                domain.contains("yeah") -> "yeah"
                else -> "163"
            }
            ProviderSettings("imap.$prefix.com", smtpHost = "smtp.$prefix.com", smtpPort = 465, smtpSecurity = SecurityType.SSL_TLS, help = "需要在邮箱设置中开启 IMAP/SMTP 并使用授权码")
        }
        MailProvider.CUSTOM -> ProviderSettings("", smtpHost = "", help = "请输入邮件服务商提供的 IMAP 与 SMTP 参数")
    }
}
