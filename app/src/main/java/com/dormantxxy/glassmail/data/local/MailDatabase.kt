package com.dormantxxy.glassmail.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.dormantxxy.glassmail.data.model.*
import java.util.UUID

class MailDatabase(context: Context) : SQLiteOpenHelper(context, "glassmail.db", null, VERSION) {
    companion object {
        private const val VERSION = 2
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE accounts (
                id TEXT PRIMARY KEY,
                email TEXT NOT NULL,
                display_name TEXT NOT NULL,
                provider TEXT NOT NULL,
                imap_host TEXT NOT NULL,
                imap_port INTEGER NOT NULL,
                imap_security TEXT NOT NULL,
                smtp_host TEXT NOT NULL,
                smtp_port INTEGER NOT NULL,
                smtp_security TEXT NOT NULL,
                username TEXT NOT NULL,
                auth_type TEXT NOT NULL,
                color INTEGER NOT NULL,
                signature TEXT NOT NULL,
                sync_interval INTEGER NOT NULL,
                enabled INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                is_demo INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE mailboxes (
                id TEXT PRIMARY KEY,
                account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
                server_name TEXT NOT NULL,
                display_name TEXT NOT NULL,
                kind TEXT NOT NULL,
                unread_count INTEGER NOT NULL DEFAULT 0,
                total_count INTEGER NOT NULL DEFAULT 0,
                sort_order INTEGER NOT NULL DEFAULT 0,
                UNIQUE(account_id, server_name)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE messages (
                id TEXT PRIMARY KEY,
                account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
                mailbox_id TEXT NOT NULL REFERENCES mailboxes(id) ON DELETE CASCADE,
                remote_uid INTEGER NOT NULL DEFAULT 0,
                message_id TEXT NOT NULL DEFAULT '',
                thread_key TEXT NOT NULL,
                from_name TEXT NOT NULL DEFAULT '',
                from_address TEXT NOT NULL DEFAULT '',
                recipients_to TEXT NOT NULL DEFAULT '',
                recipients_cc TEXT NOT NULL DEFAULT '',
                recipients_bcc TEXT NOT NULL DEFAULT '',
                reply_to TEXT NOT NULL DEFAULT '',
                subject TEXT NOT NULL DEFAULT '',
                preview TEXT NOT NULL DEFAULT '',
                body_text TEXT NOT NULL DEFAULT '',
                body_html TEXT NOT NULL DEFAULT '',
                sent_at INTEGER NOT NULL,
                received_at INTEGER NOT NULL,
                is_read INTEGER NOT NULL DEFAULT 0,
                is_starred INTEGER NOT NULL DEFAULT 0,
                is_flagged INTEGER NOT NULL DEFAULT 0,
                is_draft INTEGER NOT NULL DEFAULT 0,
                has_attachments INTEGER NOT NULL DEFAULT 0,
                category TEXT NOT NULL,
                importance INTEGER NOT NULL DEFAULT 0,
                size_bytes INTEGER NOT NULL DEFAULT 0,
                headers TEXT NOT NULL DEFAULT '',
                UNIQUE(account_id, mailbox_id, remote_uid)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_messages_box_date ON messages(mailbox_id, received_at DESC)")
        db.execSQL("CREATE INDEX idx_messages_thread ON messages(account_id, thread_key, received_at)")
        db.execSQL("CREATE INDEX idx_messages_category ON messages(category, received_at DESC)")
        db.execSQL("""
            CREATE TABLE attachments (
                id TEXT PRIMARY KEY,
                message_id TEXT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
                file_name TEXT NOT NULL,
                mime_type TEXT NOT NULL,
                size_bytes INTEGER NOT NULL DEFAULT 0,
                content_id TEXT NOT NULL DEFAULT '',
                local_path TEXT NOT NULL DEFAULT '',
                remote_part TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE outbox (
                id TEXT PRIMARY KEY,
                account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
                recipients_to TEXT NOT NULL,
                recipients_cc TEXT NOT NULL,
                recipients_bcc TEXT NOT NULL,
                subject TEXT NOT NULL,
                body TEXT NOT NULL,
                attachments TEXT NOT NULL,
                in_reply_to TEXT NOT NULL,
                references_header TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                scheduled_at INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'pending',
                last_error TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())
        createSenderPreferences(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createSenderPreferences(db)
    }

    private fun createSenderPreferences(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sender_preferences (
                account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
                sender TEXT NOT NULL COLLATE NOCASE,
                category TEXT,
                is_vip INTEGER NOT NULL DEFAULT 0,
                is_blocked INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(account_id, sender)
            )
        """.trimIndent())
    }

    fun listAccounts(): List<MailAccount> = readableDatabase.query(
        "accounts", null, null, null, null, null, "created_at ASC"
    ).use { cursor -> generateSequence { if (cursor.moveToNext()) readAccount(cursor) else null }.toList() }

    fun getAccount(id: String): MailAccount? = readableDatabase.query(
        "accounts", null, "id=?", arrayOf(id), null, null, null, "1"
    ).use { if (it.moveToFirst()) readAccount(it) else null }

    fun upsertAccount(account: MailAccount) {
        val db = writableDatabase
        val values = accountValues(account)
        if (db.update("accounts", values, "id=?", arrayOf(account.id)) == 0) {
            db.insertOrThrow("accounts", null, values)
        }
    }

    fun updateAccount(account: MailAccount) = upsertAccount(account)

    fun deleteAccount(id: String) {
        writableDatabase.delete("accounts", "id=?", arrayOf(id))
    }

    fun upsertMailbox(mailbox: Mailbox) {
        val values = ContentValues().apply {
            put("id", mailbox.id); put("account_id", mailbox.accountId); put("server_name", mailbox.serverName)
            put("display_name", mailbox.displayName); put("kind", mailbox.kind.name)
            put("unread_count", mailbox.unreadCount); put("total_count", mailbox.totalCount); put("sort_order", mailbox.sortOrder)
        }
        val db = writableDatabase
        if (db.update("mailboxes", values, "id=?", arrayOf(mailbox.id)) == 0) {
            db.insertWithOnConflict("mailboxes", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun listMailboxes(accountId: String? = null): List<Mailbox> {
        val where = accountId?.let { "account_id=?" }
        val args = accountId?.let { arrayOf(it) }
        return readableDatabase.query("mailboxes", null, where, args, null, null, "sort_order, display_name COLLATE NOCASE").use { c ->
            generateSequence { if (c.moveToNext()) readMailbox(c) else null }.toList()
        }
    }

    fun getMailbox(id: String): Mailbox? = readableDatabase.query(
        "mailboxes", null, "id=?", arrayOf(id), null, null, null, "1"
    ).use { if (it.moveToFirst()) readMailbox(it) else null }

    fun findMailbox(accountId: String, kind: MailboxKind): Mailbox? = readableDatabase.query(
        "mailboxes", null, "account_id=? AND kind=?", arrayOf(accountId, kind.name), null, null, "sort_order", "1"
    ).use { if (it.moveToFirst()) readMailbox(it) else null }

    fun upsertMessage(message: MailMessage, attachments: List<MailAttachment> = emptyList()): Boolean {
        val db = writableDatabase
        var inserted = false
        db.beginTransaction()
        try {
            val existingId = if (message.remoteUid > 0L) findMessageId(db, message.accountId, message.mailboxId, message.remoteUid) else null
            val value = messageValues(if (existingId != null) message.copy(id = existingId) else message)
            inserted = existingId == null
            if (existingId != null) {
                db.update("messages", value, "id=?", arrayOf(existingId))
            } else {
                db.insertOrThrow("messages", null, value)
            }
            val finalId = existingId ?: message.id
            if (attachments.isNotEmpty()) {
                db.delete("attachments", "message_id=?", arrayOf(finalId))
                attachments.forEach { attachment ->
                    val a = attachment.copy(messageId = finalId)
                    db.insertOrThrow("attachments", null, attachmentValues(a))
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return inserted
    }

    fun listInboxMessages(
        accountId: String? = null,
        category: MailCategory? = null,
        limit: Int = 250
    ): List<MailMessage> {
        val clauses = mutableListOf("b.kind=?")
        val args = mutableListOf(MailboxKind.INBOX.name)
        if (accountId != null) { clauses += "m.account_id=?"; args += accountId }
        if (category != null) { clauses += "m.category=?"; args += category.name }
        val sql = """
            SELECT m.* FROM messages m
            JOIN mailboxes b ON b.id=m.mailbox_id
            WHERE ${clauses.joinToString(" AND ")}
            ORDER BY m.received_at DESC LIMIT ?
        """.trimIndent()
        args += limit.toString()
        return readableDatabase.rawQuery(sql, args.toTypedArray()).use(::readMessages)
    }

    fun listMessages(mailboxId: String, limit: Int = 250): List<MailMessage> = readableDatabase.query(
        "messages", null, "mailbox_id=?", arrayOf(mailboxId), null, null, "received_at DESC", limit.toString()
    ).use(::readMessages)

    fun smartMessages(accountId: String?, kind: MailboxKind, limit: Int = 250): List<MailMessage> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (accountId != null) { clauses += "account_id=?"; args += accountId }
        when (kind) {
            MailboxKind.STARRED -> clauses += "is_starred=1"
            MailboxKind.VIP -> clauses += "EXISTS (SELECT 1 FROM sender_preferences p WHERE p.account_id=messages.account_id AND p.sender=messages.from_address AND p.is_vip=1)"
            MailboxKind.ALL -> Unit
            else -> return emptyList()
        }
        val where = clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND ")
        return readableDatabase.query("messages", null, where, args.takeIf { it.isNotEmpty() }?.toTypedArray(), null, null, "received_at DESC", limit.toString()).use(::readMessages)
    }

    fun getMessage(id: String): MailMessage? = readableDatabase.query(
        "messages", null, "id=?", arrayOf(id), null, null, null, "1"
    ).use { if (it.moveToFirst()) readMessage(it) else null }

    fun getThread(accountId: String, threadKey: String): List<MailMessage> = readableDatabase.query(
        "messages", null, "account_id=? AND thread_key=?", arrayOf(accountId, threadKey), null, null, "received_at ASC"
    ).use(::readMessages)

    fun search(query: String, accountId: String? = null, limit: Int = 200): List<MailMessage> {
        val term = "%${query.trim().replace("%", "\\%").replace("_", "\\_")}%"
        val clauses = mutableListOf("(subject LIKE ? ESCAPE '\\' OR from_name LIKE ? ESCAPE '\\' OR from_address LIKE ? ESCAPE '\\' OR preview LIKE ? ESCAPE '\\' OR body_text LIKE ? ESCAPE '\\')")
        val args = mutableListOf(term, term, term, term, term)
        if (accountId != null) { clauses += "account_id=?"; args += accountId }
        args += limit.toString()
        return readableDatabase.rawQuery(
            "SELECT * FROM messages WHERE ${clauses.joinToString(" AND ")} ORDER BY received_at DESC LIMIT ?",
            args.toTypedArray()
        ).use(::readMessages)
    }

    fun listAttachments(messageId: String): List<MailAttachment> = readableDatabase.query(
        "attachments", null, "message_id=?", arrayOf(messageId), null, null, "file_name"
    ).use { c -> generateSequence { if (c.moveToNext()) readAttachment(c) else null }.toList() }

    fun setRead(id: String, value: Boolean) = updateMessageFlag(id, "is_read", value)
    fun setStarred(id: String, value: Boolean) = updateMessageFlag(id, "is_starred", value)
    fun setFlagged(id: String, value: Boolean) = updateMessageFlag(id, "is_flagged", value)

    fun moveMessage(id: String, targetMailboxId: String) {
        writableDatabase.update("messages", ContentValues().apply { put("mailbox_id", targetMailboxId) }, "id=?", arrayOf(id))
    }

    fun moveSenderMessages(accountId: String, sender: String, targetMailboxId: String) {
        writableDatabase.update(
            "messages",
            ContentValues().apply { put("mailbox_id", targetMailboxId) },
            "account_id=? AND from_address=?",
            arrayOf(accountId, sender)
        )
    }

    fun deleteMessage(id: String) {
        writableDatabase.delete("messages", "id=?", arrayOf(id))
    }

    fun senderCategory(accountId: String, sender: String): MailCategory? = readableDatabase.query(
        "sender_preferences", arrayOf("category"), "account_id=? AND sender=?", arrayOf(accountId, sender), null, null, null, "1"
    ).use { cursor ->
        if (!cursor.moveToFirst() || cursor.isNull(0)) null else runCatching { MailCategory.valueOf(cursor.getString(0)) }.getOrNull()
    }

    fun setSenderCategory(accountId: String, sender: String, category: MailCategory) {
        updateSenderPreference(accountId, sender, ContentValues().apply { put("category", category.name) })
        writableDatabase.update("messages", ContentValues().apply { put("category", category.name) }, "account_id=? AND from_address=?", arrayOf(accountId, sender))
    }

    fun setSenderVip(accountId: String, sender: String, value: Boolean) {
        updateSenderPreference(accountId, sender, ContentValues().apply { put("is_vip", if (value) 1 else 0) })
    }

    fun isSenderVip(accountId: String, sender: String): Boolean = readableDatabase.query(
        "sender_preferences", arrayOf("is_vip"), "account_id=? AND sender=?", arrayOf(accountId, sender), null, null, null, "1"
    ).use { it.moveToFirst() && it.getInt(0) != 0 }

    fun setSenderBlocked(accountId: String, sender: String, value: Boolean) {
        updateSenderPreference(accountId, sender, ContentValues().apply { put("is_blocked", if (value) 1 else 0) })
    }

    fun isSenderBlocked(accountId: String, sender: String): Boolean = readableDatabase.query(
        "sender_preferences", arrayOf("is_blocked"), "account_id=? AND sender=?", arrayOf(accountId, sender), null, null, null, "1"
    ).use { it.moveToFirst() && it.getInt(0) != 0 }

    private fun updateSenderPreference(accountId: String, sender: String, changes: ContentValues) {
        val db = writableDatabase
        val values = ContentValues(changes).apply { put("account_id", accountId); put("sender", sender) }
        if (db.update("sender_preferences", changes, "account_id=? AND sender=?", arrayOf(accountId, sender)) == 0) {
            db.insertOrThrow("sender_preferences", null, values)
        }
    }

    fun saveOutbox(draft: MailDraft, status: String = "pending", error: String = "") {
        val v = ContentValues().apply {
            put("id", draft.id); put("account_id", draft.accountId)
            put("recipients_to", ValueCodec.strings(draft.to)); put("recipients_cc", ValueCodec.strings(draft.cc)); put("recipients_bcc", ValueCodec.strings(draft.bcc))
            put("subject", draft.subject); put("body", draft.body); put("attachments", ValueCodec.attachments(draft.attachments))
            put("in_reply_to", draft.inReplyTo); put("references_header", draft.references)
            put("created_at", draft.createdAt); put("scheduled_at", draft.scheduledAt); put("status", status); put("last_error", error)
        }
        writableDatabase.insertWithOnConflict("outbox", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun listOutbox(status: String, accountId: String? = null): List<MailDraft> {
        val where = if (accountId == null) "status=?" else "status=? AND account_id=?"
        val args = if (accountId == null) arrayOf(status) else arrayOf(status, accountId)
        return readableDatabase.query("outbox", null, where, args, null, null, "created_at DESC").use { cursor ->
            generateSequence { if (cursor.moveToNext()) readDraft(cursor) else null }.toList()
        }
    }

    fun getOutbox(id: String): MailDraft? = readableDatabase.query(
        "outbox", null, "id=?", arrayOf(id), null, null, null, "1"
    ).use { if (!it.moveToFirst()) null else readDraft(it) }

    fun deleteOutbox(id: String) { writableDatabase.delete("outbox", "id=?", arrayOf(id)) }

    fun markOutboxError(id: String, error: String) {
        writableDatabase.update("outbox", ContentValues().apply { put("status", "failed"); put("last_error", error.take(500)) }, "id=?", arrayOf(id))
    }

    fun seedDemo() {
        if (getAccount("demo-account") != null) return
        val account = MailAccount(
            id = "demo-account", email = "hello@glassmail.demo", displayName = "GlassMail",
            provider = MailProvider.CUSTOM, imapHost = "demo.invalid", imapPort = 993,
            smtpHost = "demo.invalid", smtpPort = 587, color = 0xFF0A84FF,
            signature = "来自澄邮", isDemo = true
        )
        upsertAccount(account)
        val inbox = Mailbox("demo-inbox", account.id, "INBOX", "收件箱", MailboxKind.INBOX, 3, 6, 0)
        val archive = Mailbox("demo-archive", account.id, "Archive", "归档", MailboxKind.ARCHIVE, 0, 0, 30)
        val sent = Mailbox("demo-sent", account.id, "Sent", "已发送", MailboxKind.SENT, 0, 1, 20)
        listOf(inbox, sent, archive).forEach(::upsertMailbox)
        val now = System.currentTimeMillis()
        val samples = listOf(
            Demo("Apple 设计团队", "design@example.com", "欢迎体验更清澈的收件箱", "澄邮把重要内容放在视觉中心，同时让操作栏保持轻盈。向左滑动可归档，点按星标可稍后处理。", MailCategory.PRIMARY, 8, false, true),
            Demo("云端服务", "billing@example.com", "您的 11 月账单已生成", "本期账单 ¥36.00，付款方式尾号 2026。附件中可查看完整账单。", MailCategory.TRANSACTIONS, 65, false, false),
            Demo("GitHub", "noreply@github.com", "Workflow run completed", "glassmail-android 的 Android CI 已成功完成。", MailCategory.UPDATES, 120, false, false),
            Demo("晨光周刊", "weekly@example.com", "本周产品与设计精选", "五篇值得阅读的产品设计文章，以及本周最受欢迎的开源项目。", MailCategory.UPDATES, 250, true, false),
            Demo("Travel Club", "offers@example.com", "冬季旅行限时优惠", "预订精选酒店可享受额外折扣。", MailCategory.PROMOTIONS, 410, true, false),
            Demo("林然", "linran@example.com", "Re: 周末见面", "没问题，周六下午三点老地方见。", MailCategory.PRIMARY, 1440, true, false)
        )
        samples.forEachIndexed { index, d ->
            val body = "${d.preview}\n\n这是一封演示邮件，用于在没有真实账号时体验澄邮的完整界面。\n\n祝好，\n${d.name}"
            upsertMessage(MailMessage(
                id = "demo-message-$index", accountId = account.id, mailboxId = inbox.id,
                remoteUid = (index + 1).toLong(), messageId = "<demo-$index@glassmail>",
                threadKey = MailRules.threadKey(d.subject), from = MailAddress(d.name, d.address),
                to = listOf(MailAddress("你", account.email)), subject = d.subject, preview = d.preview,
                bodyText = body, sentAt = now - d.minutesAgo * 60_000L, receivedAt = now - d.minutesAgo * 60_000L,
                isRead = d.read, isStarred = d.starred, hasAttachments = index == 1,
                category = d.category, importance = if (index == 0) 2 else 0
            ), if (index == 1) listOf(MailAttachment("demo-attachment", "demo-message-$index", "11月账单.pdf", "application/pdf", 245_760)) else emptyList())
        }
    }

    private data class Demo(val name: String, val address: String, val subject: String, val preview: String, val category: MailCategory, val minutesAgo: Int, val read: Boolean, val starred: Boolean)

    private fun updateMessageFlag(id: String, column: String, value: Boolean) {
        writableDatabase.update("messages", ContentValues().apply { put(column, if (value) 1 else 0) }, "id=?", arrayOf(id))
    }

    private fun findMessageId(db: SQLiteDatabase, accountId: String, mailboxId: String, uid: Long): String? = db.query(
        "messages", arrayOf("id"), "account_id=? AND mailbox_id=? AND remote_uid=?", arrayOf(accountId, mailboxId, uid.toString()), null, null, null, "1"
    ).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun accountValues(a: MailAccount) = ContentValues().apply {
        put("id", a.id); put("email", a.email); put("display_name", a.displayName); put("provider", a.provider.name)
        put("imap_host", a.imapHost); put("imap_port", a.imapPort); put("imap_security", a.imapSecurity.name)
        put("smtp_host", a.smtpHost); put("smtp_port", a.smtpPort); put("smtp_security", a.smtpSecurity.name)
        put("username", a.username); put("auth_type", a.authType.name); put("color", a.color); put("signature", a.signature)
        put("sync_interval", a.syncIntervalMinutes); put("enabled", if (a.enabled) 1 else 0); put("created_at", a.createdAt); put("is_demo", if (a.isDemo) 1 else 0)
    }

    private fun messageValues(m: MailMessage) = ContentValues().apply {
        put("id", m.id); put("account_id", m.accountId); put("mailbox_id", m.mailboxId); put("remote_uid", m.remoteUid)
        put("message_id", m.messageId); put("thread_key", m.threadKey); put("from_name", m.from.name); put("from_address", m.from.address)
        put("recipients_to", ValueCodec.addresses(m.to)); put("recipients_cc", ValueCodec.addresses(m.cc)); put("recipients_bcc", ValueCodec.addresses(m.bcc)); put("reply_to", ValueCodec.addresses(m.replyTo))
        put("subject", m.subject); put("preview", m.preview); put("body_text", m.bodyText); put("body_html", m.bodyHtml)
        put("sent_at", m.sentAt); put("received_at", m.receivedAt); put("is_read", if (m.isRead) 1 else 0); put("is_starred", if (m.isStarred) 1 else 0)
        put("is_flagged", if (m.isFlagged) 1 else 0); put("is_draft", if (m.isDraft) 1 else 0); put("has_attachments", if (m.hasAttachments) 1 else 0)
        put("category", m.category.name); put("importance", m.importance); put("size_bytes", m.sizeBytes); put("headers", m.headers)
    }

    private fun attachmentValues(a: MailAttachment) = ContentValues().apply {
        put("id", a.id); put("message_id", a.messageId); put("file_name", a.fileName); put("mime_type", a.mimeType)
        put("size_bytes", a.sizeBytes); put("content_id", a.contentId); put("local_path", a.localPath); put("remote_part", a.remotePart)
    }

    private fun readAccount(c: Cursor) = MailAccount(
        id = c.s("id"), email = c.s("email"), displayName = c.s("display_name"), provider = c.enum("provider", MailProvider.CUSTOM),
        imapHost = c.s("imap_host"), imapPort = c.i("imap_port"), imapSecurity = c.enum("imap_security", SecurityType.SSL_TLS),
        smtpHost = c.s("smtp_host"), smtpPort = c.i("smtp_port"), smtpSecurity = c.enum("smtp_security", SecurityType.STARTTLS),
        username = c.s("username"), authType = c.enum("auth_type", AuthType.PASSWORD), color = c.l("color"), signature = c.s("signature"),
        syncIntervalMinutes = c.i("sync_interval"), enabled = c.b("enabled"), createdAt = c.l("created_at"), isDemo = c.b("is_demo")
    )

    private fun readMailbox(c: Cursor) = Mailbox(
        id = c.s("id"), accountId = c.s("account_id"), serverName = c.s("server_name"), displayName = c.s("display_name"),
        kind = c.enum("kind", MailboxKind.CUSTOM), unreadCount = c.i("unread_count"), totalCount = c.i("total_count"), sortOrder = c.i("sort_order")
    )

    private fun readMessages(c: Cursor): List<MailMessage> = generateSequence { if (c.moveToNext()) readMessage(c) else null }.toList()

    private fun readMessage(c: Cursor) = MailMessage(
        id = c.s("id"), accountId = c.s("account_id"), mailboxId = c.s("mailbox_id"), remoteUid = c.l("remote_uid"), messageId = c.s("message_id"),
        threadKey = c.s("thread_key"), from = MailAddress(c.s("from_name"), c.s("from_address")), to = ValueCodec.readAddresses(c.s("recipients_to")),
        cc = ValueCodec.readAddresses(c.s("recipients_cc")), bcc = ValueCodec.readAddresses(c.s("recipients_bcc")), replyTo = ValueCodec.readAddresses(c.s("reply_to")),
        subject = c.s("subject"), preview = c.s("preview"), bodyText = c.s("body_text"), bodyHtml = c.s("body_html"), sentAt = c.l("sent_at"), receivedAt = c.l("received_at"),
        isRead = c.b("is_read"), isStarred = c.b("is_starred"), isFlagged = c.b("is_flagged"), isDraft = c.b("is_draft"), hasAttachments = c.b("has_attachments"),
        category = c.enum("category", MailCategory.PRIMARY), importance = c.i("importance"), sizeBytes = c.l("size_bytes"), headers = c.s("headers")
    )

    private fun readAttachment(c: Cursor) = MailAttachment(
        id = c.s("id"), messageId = c.s("message_id"), fileName = c.s("file_name"), mimeType = c.s("mime_type"), sizeBytes = c.l("size_bytes"),
        contentId = c.s("content_id"), localPath = c.s("local_path"), remotePart = c.s("remote_part")
    )

    private fun readDraft(c: Cursor) = MailDraft(
        id = c.s("id"), accountId = c.s("account_id"), to = ValueCodec.readStrings(c.s("recipients_to")), cc = ValueCodec.readStrings(c.s("recipients_cc")),
        bcc = ValueCodec.readStrings(c.s("recipients_bcc")), subject = c.s("subject"), body = c.s("body"), attachments = ValueCodec.readAttachments(c.s("attachments")),
        inReplyTo = c.s("in_reply_to"), references = c.s("references_header"), createdAt = c.l("created_at"), scheduledAt = c.l("scheduled_at")
    )

    private fun Cursor.s(name: String) = getString(getColumnIndexOrThrow(name)) ?: ""
    private fun Cursor.i(name: String) = getInt(getColumnIndexOrThrow(name))
    private fun Cursor.l(name: String) = getLong(getColumnIndexOrThrow(name))
    private fun Cursor.b(name: String) = i(name) != 0
    private inline fun <reified T : Enum<T>> Cursor.enum(name: String, fallback: T): T = runCatching { enumValueOf<T>(s(name)) }.getOrDefault(fallback)
}
