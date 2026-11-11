package com.dormantxxy.glassmail.data

import android.content.Context
import android.net.Uri
import androidx.work.*
import com.dormantxxy.glassmail.data.local.MailDatabase
import com.dormantxxy.glassmail.data.mail.MailClient
import com.dormantxxy.glassmail.data.model.*
import com.dormantxxy.glassmail.data.security.SecureVault
import com.dormantxxy.glassmail.sync.MailSyncWorker
import com.dormantxxy.glassmail.sync.SendMailWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class MailRepository(private val context: Context) {
    private val database = MailDatabase(context)
    private val vault = SecureVault(context)
    private val client = MailClient(context)
    val preferences = AppPreferences(context)

    private val _accounts = MutableStateFlow(database.listAccounts())
    val accounts: StateFlow<List<MailAccount>> = _accounts.asStateFlow()

    private val _mailboxes = MutableStateFlow(database.listMailboxes())
    val mailboxes: StateFlow<List<Mailbox>> = _mailboxes.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _changeVersion = MutableStateFlow(0L)
    val changeVersion: StateFlow<Long> = _changeVersion.asStateFlow()

    val events = MutableSharedFlow<String>(extraBufferCapacity = 16)

    suspend fun addAccount(setup: AccountSetup): MailAccount = withContext(Dispatchers.IO) {
        val account = setup.toAccount()
        require(setup.secret.isNotBlank()) { "密码、授权码或令牌不能为空" }
        _syncState.value = SyncState.Running("正在验证服务器…")
        try {
            client.test(account, setup.secret)
            database.upsertAccount(account)
            vault.put(account.id, setup.secret)
            val boxes = client.discoverMailboxes(account, setup.secret)
            boxes.forEach(database::upsertMailbox)
            ensureCoreMailbox(account.id)
            refreshState()
            schedulePeriodicSync(account)
            syncAccount(account.id)
            account
        } catch (error: Throwable) {
            database.deleteAccount(account.id)
            vault.remove(account.id)
            _syncState.value = SyncState.Error(readableError(error))
            throw IllegalStateException(readableError(error), error)
        }
    }

    suspend fun addDemoAccount() = withContext(Dispatchers.IO) {
        database.seedDemo()
        refreshState()
        _syncState.value = SyncState.Success(System.currentTimeMillis(), 6)
    }

    suspend fun deleteAccount(accountId: String) = withContext(Dispatchers.IO) {
        database.deleteAccount(accountId)
        vault.remove(accountId)
        WorkManager.getInstance(context).cancelUniqueWork(syncWorkName(accountId))
        refreshState()
    }

    suspend fun updateAccount(account: MailAccount) = withContext(Dispatchers.IO) {
        database.updateAccount(account)
        schedulePeriodicSync(account)
        refreshState()
    }

    suspend fun syncAll(): Int = withContext(Dispatchers.IO) {
        var total = 0
        val active = database.listAccounts().filter { it.enabled && !it.isDemo }
        if (active.isEmpty()) {
            _syncState.value = SyncState.Success(System.currentTimeMillis(), 0)
            return@withContext 0
        }
        active.forEach { total += syncAccountInternal(it) }
        refreshState()
        _syncState.value = SyncState.Success(System.currentTimeMillis(), total)
        total
    }

    suspend fun syncAccount(accountId: String): Int = withContext(Dispatchers.IO) {
        val account = database.getAccount(accountId) ?: return@withContext 0
        if (account.isDemo) {
            _syncState.value = SyncState.Success(System.currentTimeMillis(), 0)
            return@withContext 0
        }
        try {
            val count = syncAccountInternal(account)
            refreshState()
            _syncState.value = SyncState.Success(System.currentTimeMillis(), count)
            count
        } catch (error: Throwable) {
            _syncState.value = SyncState.Error(readableError(error))
            events.tryEmit("同步失败：${readableError(error)}")
            throw error
        }
    }

    private fun syncAccountInternal(account: MailAccount): Int {
        _syncState.value = SyncState.Running("正在同步 ${account.email}")
        val secret = vault.get(account.id) ?: error("账户凭据已失效，请重新添加账户")
        val remoteBoxes = client.discoverMailboxes(account, secret)
        remoteBoxes.forEach(database::upsertMailbox)
        ensureCoreMailbox(account.id)
        var inserted = 0
        val preferred = database.listMailboxes(account.id)
            .filter { it.kind in setOf(MailboxKind.INBOX, MailboxKind.SENT, MailboxKind.DRAFTS, MailboxKind.ARCHIVE) }
            .take(6)
        preferred.forEachIndexed { index, mailbox ->
            _syncState.value = SyncState.Running("正在同步 ${mailbox.displayName}")
            val batch = client.fetchRecent(account, secret, mailbox, if (index == 0) 100 else 40)
            batch.messages.forEach { (message, attachments) ->
                if (database.upsertMessage(message, attachments)) inserted++
            }
            database.upsertMailbox(mailbox.copy(totalCount = batch.totalCount, unreadCount = batch.unreadCount))
        }
        return inserted
    }

    suspend fun inbox(accountId: String?, category: MailCategory?): List<MailMessage> = withContext(Dispatchers.IO) {
        database.listInboxMessages(accountId?.takeIf { it.isNotBlank() }, category)
    }

    suspend fun mailbox(mailbox: Mailbox): List<MailMessage> = withContext(Dispatchers.IO) {
        when (mailbox.kind) {
            MailboxKind.STARRED, MailboxKind.ALL -> database.smartMessages(mailbox.accountId.takeIf { it.isNotBlank() }, mailbox.kind)
            else -> database.listMessages(mailbox.id)
        }
    }

    suspend fun search(query: String, accountId: String?): List<MailMessage> = withContext(Dispatchers.IO) {
        if (query.isBlank()) emptyList() else database.search(query, accountId?.takeIf { it.isNotBlank() })
    }

    suspend fun message(id: String): MailMessage? = withContext(Dispatchers.IO) { database.getMessage(id) }
    suspend fun thread(message: MailMessage): List<MailMessage> = withContext(Dispatchers.IO) { database.getThread(message.accountId, message.threadKey) }
    suspend fun attachments(messageId: String): List<MailAttachment> = withContext(Dispatchers.IO) { database.listAttachments(messageId) }

    suspend fun setRead(messageId: String, read: Boolean) = mutateMessage(messageId, read, Mutation.READ)
    suspend fun setStarred(messageId: String, starred: Boolean) = mutateMessage(messageId, starred, Mutation.STAR)

    suspend fun archive(messageId: String) = moveToKind(messageId, MailboxKind.ARCHIVE)
    suspend fun trash(messageId: String) = moveToKind(messageId, MailboxKind.TRASH)

    suspend fun deleteForever(messageId: String) = withContext(Dispatchers.IO) {
        val message = database.getMessage(messageId) ?: return@withContext
        val account = database.getAccount(message.accountId) ?: return@withContext
        val source = database.getMailbox(message.mailboxId) ?: return@withContext
        if (!account.isDemo && message.remoteUid > 0) {
            val secret = vault.get(account.id) ?: error("账户凭据已失效")
            client.delete(account, secret, source, message.remoteUid)
        }
        database.deleteMessage(message.id)
        touch()
    }

    suspend fun move(messageId: String, targetMailboxId: String) = withContext(Dispatchers.IO) {
        val message = database.getMessage(messageId) ?: return@withContext
        val account = database.getAccount(message.accountId) ?: return@withContext
        val source = database.getMailbox(message.mailboxId) ?: return@withContext
        val target = database.getMailbox(targetMailboxId) ?: return@withContext
        if (!account.isDemo && message.remoteUid > 0) {
            val secret = vault.get(account.id) ?: error("账户凭据已失效")
            client.move(account, secret, source, target, message.remoteUid)
        }
        database.moveMessage(message.id, target.id)
        touch()
    }

    suspend fun enqueueSend(draft: MailDraft, undoDelaySeconds: Int): String = withContext(Dispatchers.IO) {
        val persisted = persistAttachments(draft)
        val sendAt = when {
            persisted.scheduledAt > System.currentTimeMillis() -> persisted.scheduledAt
            else -> System.currentTimeMillis() + undoDelaySeconds.coerceAtLeast(0) * 1000L
        }
        val queued = persisted.copy(scheduledAt = sendAt)
        database.saveOutbox(queued)
        val request = OneTimeWorkRequestBuilder<SendMailWorker>()
            .setInputData(workDataOf(SendMailWorker.DRAFT_ID to queued.id))
            .setInitialDelay((sendAt - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(sendWorkName(queued.id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(sendWorkName(queued.id), ExistingWorkPolicy.REPLACE, request)
        queued.id
    }

    suspend fun undoSend(draftId: String) = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).cancelUniqueWork(sendWorkName(draftId))
        database.deleteOutbox(draftId)
        File(context.filesDir, "outbox/$draftId").deleteRecursively()
        events.tryEmit("已撤销发送")
    }

    suspend fun sendQueued(draftId: String): Boolean = withContext(Dispatchers.IO) {
        val draft = database.getOutbox(draftId) ?: return@withContext true
        val account = database.getAccount(draft.accountId) ?: return@withContext true
        try {
            if (!account.isDemo) {
                val secret = vault.get(account.id) ?: error("账户凭据已失效")
                client.send(account, secret, draft)
            }
            database.deleteOutbox(draftId)
            File(context.filesDir, "outbox/$draftId").deleteRecursively()
            events.tryEmit(if (account.isDemo) "演示邮件已模拟发送" else "邮件已发送")
            true
        } catch (error: Throwable) {
            database.markOutboxError(draftId, readableError(error))
            events.tryEmit("发送失败：${readableError(error)}")
            false
        }
    }

    fun schedulePeriodicSync(account: MailAccount) {
        if (account.isDemo || !account.enabled) return
        val request = PeriodicWorkRequestBuilder<MailSyncWorker>(
            account.syncIntervalMinutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES
        )
            .setInputData(workDataOf(MailSyncWorker.ACCOUNT_ID to account.id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            syncWorkName(account.id), ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    private suspend fun mutateMessage(messageId: String, value: Boolean, mutation: Mutation) = withContext(Dispatchers.IO) {
        val message = database.getMessage(messageId) ?: return@withContext
        val account = database.getAccount(message.accountId) ?: return@withContext
        val mailbox = database.getMailbox(message.mailboxId) ?: return@withContext
        when (mutation) {
            Mutation.READ -> database.setRead(message.id, value)
            Mutation.STAR -> database.setStarred(message.id, value)
        }
        touch()
        if (!account.isDemo && message.remoteUid > 0) {
            try {
                val secret = vault.get(account.id) ?: error("账户凭据已失效")
                when (mutation) {
                    Mutation.READ -> client.setRead(account, secret, mailbox, message.remoteUid, value)
                    Mutation.STAR -> client.setStarred(account, secret, mailbox, message.remoteUid, value)
                }
            } catch (error: Throwable) {
                when (mutation) {
                    Mutation.READ -> database.setRead(message.id, !value)
                    Mutation.STAR -> database.setStarred(message.id, !value)
                }
                touch()
                events.tryEmit("操作失败：${readableError(error)}")
            }
        }
    }

    private suspend fun moveToKind(messageId: String, kind: MailboxKind) {
        val message = withContext(Dispatchers.IO) { database.getMessage(messageId) } ?: return
        val target = withContext(Dispatchers.IO) { database.findMailbox(message.accountId, kind) }
        if (target != null) move(messageId, target.id) else if (kind == MailboxKind.TRASH) deleteForever(messageId)
        else events.tryEmit("未找到${kind.label}文件夹")
    }

    private fun persistAttachments(draft: MailDraft): MailDraft {
        if (draft.attachments.isEmpty()) return draft
        val dir = File(context.filesDir, "outbox/${draft.id}").apply { mkdirs() }
        val copied = draft.attachments.mapIndexed { index, attachment ->
            val safeName = attachment.name.replace(Regex("[^a-zA-Z0-9._\\-\u4e00-\u9fa5]"), "_").take(100).ifBlank { "attachment-$index" }
            val target = File(dir, "${index}_$safeName")
            val source = Uri.parse(attachment.uri)
            val input = if (source.scheme == "file") FileInputStream(File(source.path!!)) else context.contentResolver.openInputStream(source)
            requireNotNull(input) { "无法读取附件 ${attachment.name}" }
            input.use { stream -> FileOutputStream(target).use(stream::copyTo) }
            attachment.copy(uri = Uri.fromFile(target).toString(), sizeBytes = target.length())
        }
        return draft.copy(attachments = copied)
    }

    private fun ensureCoreMailbox(accountId: String) {
        if (database.findMailbox(accountId, MailboxKind.INBOX) == null) {
            database.upsertMailbox(Mailbox("$accountId-local-inbox", accountId, "INBOX", "收件箱", MailboxKind.INBOX, sortOrder = 0))
        }
    }

    private fun refreshState() {
        _accounts.value = database.listAccounts()
        _mailboxes.value = database.listMailboxes()
        touch()
    }

    private fun touch() { _changeVersion.value = System.nanoTime() }

    private fun readableError(error: Throwable): String {
        val raw = generateSequence(error) { it.cause }.mapNotNull { it.message }.firstOrNull { it.isNotBlank() }
            ?: error::class.java.simpleName
        return raw.replace(Regex("(?i)(password|token|secret)=?\\S*"), "凭据").take(240)
    }

    private enum class Mutation { READ, STAR }
    private fun syncWorkName(accountId: String) = "glassmail.sync.$accountId"
    private fun sendWorkName(draftId: String) = "glassmail.send.$draftId"
}
