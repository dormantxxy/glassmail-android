package com.dormantxxy.glassmail.data.mail

import android.content.Context
import android.net.Uri
import com.dormantxxy.glassmail.data.model.*
import java.io.InputStream
import java.io.OutputStream
import java.util.Date
import java.util.Properties
import java.util.UUID
import javax.activation.DataHandler
import javax.activation.DataSource
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.internet.MimeUtility

class MailClient(private val context: Context) {

    data class SyncBatch(
        val messages: List<Pair<MailMessage, List<MailAttachment>>>,
        val totalCount: Int,
        val unreadCount: Int
    )

    suspend fun test(account: MailAccount, secret: String) {
        require(account.email.contains('@')) { "邮箱地址格式不正确" }
        require(account.imapHost.isNotBlank() && account.smtpHost.isNotBlank()) { "服务器地址不能为空" }
        openStore(account, secret).use { store ->
            val folder = store.defaultFolder
            check(folder.exists()) { "无法访问邮箱根目录" }
        }
        openTransport(account, secret).use { }
    }

    fun discoverMailboxes(account: MailAccount, secret: String): List<Mailbox> = openStore(account, secret).use { store ->
        val folders = runCatching { store.defaultFolder.list("*") }.getOrElse { store.defaultFolder.list() }
        folders
            .filter { runCatching { it.type and Folder.HOLDS_MESSAGES != 0 }.getOrDefault(false) }
            .mapIndexed { index, folder ->
                var total = 0
                var unread = 0
                runCatching {
                    folder.open(Folder.READ_ONLY)
                    total = folder.messageCount.coerceAtLeast(0)
                    unread = folder.unreadMessageCount.coerceAtLeast(0)
                }
                runCatching { if (folder.isOpen) folder.close(false) }
                val kind = folderKind(folder)
                Mailbox(
                    id = stableMailboxId(account.id, folder.fullName),
                    accountId = account.id,
                    serverName = folder.fullName,
                    displayName = displayName(folder, kind),
                    kind = kind,
                    unreadCount = unread,
                    totalCount = total,
                    sortOrder = kindOrder(kind) + index
                )
            }
            .sortedBy { it.sortOrder }
    }

    fun fetchRecent(account: MailAccount, secret: String, mailbox: Mailbox, maxCount: Int = 80): SyncBatch =
        openStore(account, secret).use { store ->
            val folder = store.getFolder(mailbox.serverName)
            if (!folder.exists()) return@use SyncBatch(emptyList(), 0, 0)
            folder.open(Folder.READ_ONLY)
            try {
                val count = folder.messageCount.coerceAtLeast(0)
                if (count == 0) return@use SyncBatch(emptyList(), 0, 0)
                val start = (count - maxCount + 1).coerceAtLeast(1)
                val remoteMessages = folder.getMessages(start, count)
                val profile = FetchProfile().apply {
                    add(FetchProfile.Item.ENVELOPE)
                    add(FetchProfile.Item.FLAGS)
                    add(FetchProfile.Item.CONTENT_INFO)
                    add(UIDFolder.FetchProfileItem.UID)
                }
                folder.fetch(remoteMessages, profile)
                val uidFolder = folder as? UIDFolder
                val parsed = remoteMessages.mapNotNull { remote ->
                    runCatching { parseMessage(account, mailbox, remote, uidFolder?.getUID(remote) ?: remote.messageNumber.toLong()) }.getOrNull()
                }
                SyncBatch(parsed, count, folder.unreadMessageCount.coerceAtLeast(parsed.count { !it.first.isRead }))
            } finally {
                runCatching { folder.close(false) }
            }
        }

    fun setRead(account: MailAccount, secret: String, mailbox: Mailbox, uid: Long, value: Boolean) =
        setFlag(account, secret, mailbox, uid, Flags.Flag.SEEN, value)

    fun setStarred(account: MailAccount, secret: String, mailbox: Mailbox, uid: Long, value: Boolean) =
        setFlag(account, secret, mailbox, uid, Flags.Flag.FLAGGED, value)

    fun move(account: MailAccount, secret: String, source: Mailbox, target: Mailbox, uid: Long) {
        openStore(account, secret).use { store ->
            val sourceFolder = store.getFolder(source.serverName)
            val targetFolder = store.getFolder(target.serverName)
            require(sourceFolder.exists() && targetFolder.exists()) { "服务端文件夹不存在" }
            sourceFolder.open(Folder.READ_WRITE)
            try {
                val message = (sourceFolder as? UIDFolder)?.getMessageByUID(uid)
                    ?: sourceFolder.getMessage(uid.toInt())
                sourceFolder.copyMessages(arrayOf(message), targetFolder)
                message.setFlag(Flags.Flag.DELETED, true)
            } finally {
                runCatching { sourceFolder.close(true) }
            }
        }
    }

    fun delete(account: MailAccount, secret: String, source: Mailbox, uid: Long) {
        openStore(account, secret).use { store ->
            val folder = store.getFolder(source.serverName)
            require(folder.exists()) { "服务端文件夹不存在" }
            folder.open(Folder.READ_WRITE)
            try {
                val message = (folder as? UIDFolder)?.getMessageByUID(uid) ?: folder.getMessage(uid.toInt())
                message.setFlag(Flags.Flag.DELETED, true)
            } finally {
                runCatching { folder.close(true) }
            }
        }
    }

    fun downloadAttachment(
        account: MailAccount,
        secret: String,
        mailbox: Mailbox,
        uid: Long,
        remotePart: String,
        output: OutputStream
    ) {
        openStore(account, secret).use { store ->
            val folder = store.getFolder(mailbox.serverName)
            require(folder.exists()) { "服务端文件夹不存在" }
            folder.open(Folder.READ_ONLY)
            try {
                val message = (folder as? UIDFolder)?.getMessageByUID(uid) ?: folder.getMessage(uid.toInt())
                val part = findPart(message, remotePart)
                part.inputStream.use { input -> input.copyTo(output) }
                output.flush()
            } finally {
                runCatching { folder.close(false) }
            }
        }
    }

    fun send(account: MailAccount, secret: String, draft: MailDraft) {
        require(draft.to.isNotEmpty() || draft.cc.isNotEmpty() || draft.bcc.isNotEmpty()) { "请至少填写一位收件人" }
        val session = Session.getInstance(smtpProperties(account))
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(account.email, account.displayName, Charsets.UTF_8.name()))
            addAddresses(this, Message.RecipientType.TO, draft.to)
            addAddresses(this, Message.RecipientType.CC, draft.cc)
            addAddresses(this, Message.RecipientType.BCC, draft.bcc)
            setSubject(draft.subject.ifBlank { "(无主题)" }, Charsets.UTF_8.name())
            sentDate = Date()
            if (draft.inReplyTo.isNotBlank()) setHeader("In-Reply-To", draft.inReplyTo)
            if (draft.references.isNotBlank()) setHeader("References", draft.references)

            val fullBody = buildString {
                append(draft.body)
                if (account.signature.isNotBlank()) {
                    append("\n\n-- \n")
                    append(account.signature)
                }
            }
            if (draft.attachments.isEmpty()) {
                setText(fullBody, Charsets.UTF_8.name())
            } else {
                setContent(MimeMultipart("mixed").apply {
                    addBodyPart(MimeBodyPart().apply { setText(fullBody, Charsets.UTF_8.name()) })
                    draft.attachments.forEach { attachment ->
                        addBodyPart(MimeBodyPart().apply {
                            dataHandler = DataHandler(UriAttachmentSource(context, attachment))
                            fileName = MimeUtility.encodeText(attachment.name, Charsets.UTF_8.name(), null)
                            disposition = Part.ATTACHMENT
                        })
                    }
                })
            }
            saveChanges()
        }
        openTransport(account, secret, session).use { transport ->
            transport.sendMessage(message, message.allRecipients)
        }
    }

    private fun parseMessage(
        account: MailAccount,
        mailbox: Mailbox,
        remote: Message,
        uid: Long
    ): Pair<MailMessage, List<MailAttachment>> {
        val content = ContentCollector()
        runCatching { collectPart(remote, "1", content) }
        val subject = decode(remote.subject).ifBlank { "(无主题)" }
        val from = addresses(remote.from).firstOrNull() ?: MailAddress(address = "未知发件人")
        val selectedHeaders = listOf("List-Unsubscribe", "Auto-Submitted", "Precedence", "Importance", "X-Priority")
            .mapNotNull { name -> remote.getHeader(name)?.firstOrNull()?.let { "$name: $it" } }
            .joinToString("\n")
        val rawPreview = content.text.ifBlank { stripHtml(content.html) }
        val messageId = remote.getHeader("Message-ID")?.firstOrNull().orEmpty()
        val received = remote.receivedDate?.time ?: remote.sentDate?.time ?: System.currentTimeMillis()
        val sent = remote.sentDate?.time ?: received
        val flags = remote.flags
        val id = UUID.nameUUIDFromBytes("${account.id}|${mailbox.id}|$uid".toByteArray()).toString()
        val message = MailMessage(
            id = id,
            accountId = account.id,
            mailboxId = mailbox.id,
            remoteUid = uid,
            messageId = messageId,
            threadKey = MailRules.threadKey(subject),
            from = from,
            to = addresses(remote.getRecipients(Message.RecipientType.TO)),
            cc = addresses(remote.getRecipients(Message.RecipientType.CC)),
            bcc = addresses(remote.getRecipients(Message.RecipientType.BCC)),
            replyTo = addresses(remote.replyTo),
            subject = subject,
            preview = MailRules.cleanPreview(rawPreview),
            bodyText = content.text,
            bodyHtml = content.html,
            sentAt = sent,
            receivedAt = received,
            isRead = flags.contains(Flags.Flag.SEEN),
            isStarred = flags.contains(Flags.Flag.FLAGGED),
            isFlagged = flags.contains(Flags.Flag.FLAGGED),
            isDraft = flags.contains(Flags.Flag.DRAFT),
            hasAttachments = content.attachments.isNotEmpty(),
            category = MailRules.categorize(subject, from.address, selectedHeaders),
            importance = importance(selectedHeaders),
            sizeBytes = remote.size.coerceAtLeast(0).toLong(),
            headers = selectedHeaders
        )
        return message to content.attachments.map { it.copy(messageId = id) }
    }

    private fun collectPart(part: Part, partNumber: String, collector: ContentCollector) {
        val fileName = decode(part.fileName)
        val disposition = part.disposition.orEmpty()
        val attachment = fileName.isNotBlank() || disposition.equals(Part.ATTACHMENT, true)
        when {
            attachment -> collector.attachments += MailAttachment(
                id = UUID.randomUUID().toString(),
                messageId = "",
                fileName = fileName.ifBlank { "附件-$partNumber" },
                mimeType = part.contentType.substringBefore(';').trim().ifBlank { "application/octet-stream" },
                sizeBytes = part.size.coerceAtLeast(0).toLong(),
                contentId = part.getHeader("Content-ID")?.firstOrNull()?.trim('<', '>').orEmpty(),
                remotePart = partNumber
            )
            part.isMimeType("text/plain") -> {
                val value = part.content?.toString().orEmpty()
                if (collector.text.isBlank()) collector.text = value else collector.text += "\n\n$value"
            }
            part.isMimeType("text/html") -> {
                val value = part.content?.toString().orEmpty()
                if (collector.html.isBlank()) collector.html = value else collector.html += "<hr>$value"
            }
            part.isMimeType("multipart/*") -> {
                val multipart = part.content as? Multipart ?: return
                for (index in 0 until multipart.count) collectPart(multipart.getBodyPart(index), "$partNumber.${index + 1}", collector)
            }
            part.isMimeType("message/rfc822") -> (part.content as? Part)?.let { collectPart(it, "$partNumber.1", collector) }
        }
    }

    private fun findPart(root: Part, partNumber: String): Part {
        var current = root
        val path = partNumber.split('.').mapNotNull(String::toIntOrNull).drop(1)
        for (number in path) {
            current = when {
                current.isMimeType("multipart/*") -> {
                    val multipart = current.content as? Multipart ?: error("附件结构不可读")
                    multipart.getBodyPart(number - 1)
                }
                current.isMimeType("message/rfc822") -> current.content as? Part ?: error("嵌套邮件不可读")
                else -> error("找不到附件正文")
            }
        }
        return current
    }

    private fun setFlag(account: MailAccount, secret: String, mailbox: Mailbox, uid: Long, flag: Flags.Flag, value: Boolean) {
        openStore(account, secret).use { store ->
            val folder = store.getFolder(mailbox.serverName)
            require(folder.exists()) { "服务端文件夹不存在" }
            folder.open(Folder.READ_WRITE)
            try {
                val message = (folder as? UIDFolder)?.getMessageByUID(uid) ?: folder.getMessage(uid.toInt())
                message.setFlag(flag, value)
            } finally {
                runCatching { folder.close(false) }
            }
        }
    }

    private fun openStore(account: MailAccount, secret: String): Store {
        val protocol = if (account.imapSecurity == SecurityType.SSL_TLS) "imaps" else "imap"
        val session = Session.getInstance(imapProperties(account, protocol))
        return session.getStore(protocol).apply { connect(account.imapHost, account.imapPort, account.username, secret) }
    }

    private fun openTransport(account: MailAccount, secret: String, session: Session = Session.getInstance(smtpProperties(account))): Transport {
        val protocol = if (account.smtpSecurity == SecurityType.SSL_TLS) "smtps" else "smtp"
        return session.getTransport(protocol).apply { connect(account.smtpHost, account.smtpPort, account.username, secret) }
    }

    private fun imapProperties(account: MailAccount, protocol: String) = Properties().apply {
        put("mail.store.protocol", protocol)
        put("mail.$protocol.connectiontimeout", "15000")
        put("mail.$protocol.timeout", "30000")
        put("mail.$protocol.writetimeout", "30000")
        put("mail.$protocol.ssl.checkserveridentity", "true")
        if (account.imapSecurity == SecurityType.STARTTLS) {
            put("mail.imap.starttls.enable", "true")
            put("mail.imap.starttls.required", "true")
        }
        if (account.authType == AuthType.XOAUTH2) {
            put("mail.$protocol.auth.mechanisms", "XOAUTH2")
            put("mail.$protocol.auth.login.disable", "true")
            put("mail.$protocol.auth.plain.disable", "true")
        }
    }

    private fun smtpProperties(account: MailAccount) = Properties().apply {
        val protocol = if (account.smtpSecurity == SecurityType.SSL_TLS) "smtps" else "smtp"
        put("mail.transport.protocol", protocol)
        put("mail.$protocol.auth", "true")
        put("mail.$protocol.connectiontimeout", "15000")
        put("mail.$protocol.timeout", "30000")
        put("mail.$protocol.writetimeout", "30000")
        put("mail.$protocol.ssl.checkserveridentity", "true")
        if (account.smtpSecurity == SecurityType.STARTTLS) {
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.starttls.required", "true")
        }
        if (account.authType == AuthType.XOAUTH2) put("mail.$protocol.auth.mechanisms", "XOAUTH2")
    }

    private fun addAddresses(message: MimeMessage, type: Message.RecipientType, values: List<String>) {
        values.flatMap { InternetAddress.parse(it, true).toList() }.forEach { message.addRecipient(type, it) }
    }

    private fun addresses(values: Array<out Address>?): List<MailAddress> = values.orEmpty().map { address ->
        val internet = address as? InternetAddress
        MailAddress(decode(internet?.personal.orEmpty()), internet?.address ?: address.toString())
    }

    private fun folderKind(folder: Folder): MailboxKind {
        val attributes = (folder as? com.sun.mail.imap.IMAPFolder)?.attributes.orEmpty()
        val marker = (attributes.joinToString(" ") + " " + folder.fullName).lowercase()
        return when {
            "\\inbox" in marker || folder.fullName.equals("INBOX", true) -> MailboxKind.INBOX
            "\\drafts" in marker || "draft" in marker || "草稿" in marker -> MailboxKind.DRAFTS
            "\\sent" in marker || "sent" in marker || "已发送" in marker -> MailboxKind.SENT
            "\\archive" in marker || "archive" in marker || "归档" in marker -> MailboxKind.ARCHIVE
            "\\trash" in marker || "trash" in marker || "deleted" in marker || "废纸" in marker -> MailboxKind.TRASH
            "\\junk" in marker || "spam" in marker || "junk" in marker || "垃圾" in marker -> MailboxKind.SPAM
            "\\all" in marker || "all mail" in marker || "所有邮件" in marker -> MailboxKind.ALL
            else -> MailboxKind.CUSTOM
        }
    }

    private fun displayName(folder: Folder, kind: MailboxKind): String = when (kind) {
        MailboxKind.CUSTOM -> folder.name
        else -> kind.label
    }

    private fun kindOrder(kind: MailboxKind): Int = when (kind) {
        MailboxKind.INBOX -> 0
        MailboxKind.STARRED -> 10
        MailboxKind.VIP -> 11
        MailboxKind.DRAFTS -> 20
        MailboxKind.SENT -> 30
        MailboxKind.ARCHIVE -> 40
        MailboxKind.ALL -> 50
        MailboxKind.SPAM -> 80
        MailboxKind.TRASH -> 90
        MailboxKind.CUSTOM -> 100
    } * 100

    private fun stableMailboxId(accountId: String, fullName: String): String =
        UUID.nameUUIDFromBytes("$accountId|$fullName".toByteArray()).toString()

    private fun decode(value: String?): String = runCatching { MimeUtility.decodeText(value.orEmpty()) }.getOrDefault(value.orEmpty())

    private fun stripHtml(value: String): String = value
        .replace(Regex("(?is)<(script|style).*?>.*?</\\1>"), " ")
        .replace(Regex("(?s)<[^>]+>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")

    private fun importance(headers: String): Int = when {
        Regex("(?i)(importance:\\s*high|x-priority:\\s*[12])").containsMatchIn(headers) -> 2
        Regex("(?i)(importance:\\s*low|x-priority:\\s*5)").containsMatchIn(headers) -> -1
        else -> 0
    }

    private data class ContentCollector(
        var text: String = "",
        var html: String = "",
        val attachments: MutableList<MailAttachment> = mutableListOf()
    )

    private class UriAttachmentSource(
        private val context: Context,
        private val attachment: LocalAttachment
    ) : DataSource {
        override fun getInputStream(): InputStream {
            val uri = Uri.parse(attachment.uri)
            return if (uri.scheme == "file") java.io.FileInputStream(requireNotNull(uri.path))
            else context.contentResolver.openInputStream(uri) ?: error("无法读取附件 ${attachment.name}")
        }
        override fun getOutputStream(): OutputStream = throw UnsupportedOperationException("Read only")
        override fun getContentType(): String = attachment.mimeType
        override fun getName(): String = attachment.name
    }
}
