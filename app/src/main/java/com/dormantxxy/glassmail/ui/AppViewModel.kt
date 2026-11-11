package com.dormantxxy.glassmail.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dormantxxy.glassmail.GlassMailApplication
import com.dormantxxy.glassmail.data.ThemeMode
import com.dormantxxy.glassmail.data.UserPreferences
import com.dormantxxy.glassmail.data.model.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


data class MailListState(
    val messages: List<MailMessage> = emptyList(),
    val title: String = "收件箱",
    val selectedCategory: MailCategory? = MailCategory.PRIMARY,
    val selectedMailbox: Mailbox? = null,
    val smartKind: MailboxKind? = null,
    val loading: Boolean = false
)

data class DetailState(
    val selected: MailMessage? = null,
    val thread: List<MailMessage> = emptyList(),
    val attachments: Map<String, List<MailAttachment>> = emptyMap(),
    val loading: Boolean = false
)

data class ComposeSeed(
    val accountId: String = "",
    val to: String = "",
    val cc: String = "",
    val subject: String = "",
    val body: String = "",
    val inReplyTo: String = "",
    val references: String = ""
)

data class UiEvent(val message: String, val action: String? = null, val token: String = "")

@OptIn(FlowPreview::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as GlassMailApplication).repository

    val accounts = repository.accounts
    val mailboxes = repository.mailboxes
    val syncState = repository.syncState
    val preferences = repository.preferences.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    private val _selectedAccountId = MutableStateFlow("")
    val selectedAccountId = _selectedAccountId.asStateFlow()

    private val _listState = MutableStateFlow(MailListState())
    val listState = _listState.asStateFlow()

    private val _detailState = MutableStateFlow(DetailState())
    val detailState = _detailState.asStateFlow()

    private val _composeSeed = MutableStateFlow(ComposeSeed())
    val composeSeed = _composeSeed.asStateFlow()

    private val _setupBusy = MutableStateFlow(false)
    val setupBusy = _setupBusy.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    private val searchQuery = MutableStateFlow("")
    private val _searchResults = MutableStateFlow<List<MailMessage>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.map { it.selectedAccountId }.distinctUntilChanged().collect { saved ->
                if (_selectedAccountId.value.isBlank() && saved.isNotBlank()) _selectedAccountId.value = saved
            }
        }
        viewModelScope.launch {
            repository.changeVersion.collect { reloadList() }
        }
        viewModelScope.launch {
            repository.events.collect { _events.emit(UiEvent(it)) }
        }
        viewModelScope.launch {
            searchQuery.debounce(280).distinctUntilChanged().collectLatest { query ->
                _searchResults.value = repository.search(query, _selectedAccountId.value)
            }
        }
        reloadList()
    }

    fun selectAccount(accountId: String) {
        _selectedAccountId.value = accountId
        viewModelScope.launch { repository.preferences.setSelectedAccount(accountId); reloadList() }
    }

    fun showInbox() {
        _listState.value = _listState.value.copy(title = "收件箱", selectedMailbox = null, smartKind = null)
        reloadList()
    }

    fun selectCategory(category: MailCategory?) {
        _listState.value = _listState.value.copy(selectedCategory = category, selectedMailbox = null, smartKind = null, title = "收件箱")
        reloadList()
    }

    fun selectMailbox(mailbox: Mailbox) {
        _listState.value = _listState.value.copy(selectedMailbox = mailbox, smartKind = null, title = mailbox.displayName, selectedCategory = null)
        reloadList()
    }

    fun selectSmart(kind: MailboxKind) {
        _listState.value = _listState.value.copy(selectedMailbox = null, smartKind = kind, title = kind.label, selectedCategory = null)
        reloadList()
    }

    fun reloadList() {
        viewModelScope.launch {
            val snapshot = _listState.value
            _listState.value = snapshot.copy(loading = true)
            val account = _selectedAccountId.value.takeIf { it.isNotBlank() }
            val values = when {
                snapshot.selectedMailbox != null -> repository.mailbox(snapshot.selectedMailbox)
                snapshot.smartKind != null -> {
                    val synthetic = Mailbox("smart-${snapshot.smartKind}", account.orEmpty(), "", snapshot.smartKind.label, snapshot.smartKind)
                    repository.mailbox(synthetic)
                }
                else -> repository.inbox(account, snapshot.selectedCategory)
            }
            _listState.value = _listState.value.copy(messages = values, loading = false)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching {
                val account = _selectedAccountId.value
                if (account.isBlank()) repository.syncAll() else repository.syncAccount(account)
            }
            reloadList()
        }
    }

    fun addDemo() { viewModelScope.launch { repository.addDemoAccount(); showInbox() } }

    fun addAccount(setup: AccountSetup, onSuccess: () -> Unit) {
        if (_setupBusy.value) return
        viewModelScope.launch {
            _setupBusy.value = true
            runCatching { repository.addAccount(setup) }
                .onSuccess { account ->
                    selectAccount(account.id)
                    _events.emit(UiEvent("账户已添加"))
                    onSuccess()
                }
                .onFailure { _events.emit(UiEvent(it.message ?: "无法添加账户")) }
            _setupBusy.value = false
        }
    }

    fun deleteAccount(id: String) {
        viewModelScope.launch {
            repository.deleteAccount(id)
            if (_selectedAccountId.value == id) _selectedAccountId.value = ""
            _events.emit(UiEvent("账户已删除"))
        }
    }

    fun updateAccount(account: MailAccount) { viewModelScope.launch { repository.updateAccount(account) } }

    fun openMessage(id: String) {
        viewModelScope.launch {
            _detailState.value = DetailState(loading = true)
            val message = repository.message(id)
            if (message == null) {
                _detailState.value = DetailState()
                _events.emit(UiEvent("邮件不存在或已被删除"))
                return@launch
            }
            if (!message.isRead) repository.setRead(message.id, true)
            val thread = repository.thread(message)
            val attachments = thread.associate { it.id to repository.attachments(it.id) }
            _detailState.value = DetailState(message.copy(isRead = true), thread, attachments, false)
            reloadList()
        }
    }

    fun toggleRead(message: MailMessage) { viewModelScope.launch { repository.setRead(message.id, !message.isRead); reloadList() } }
    fun toggleStar(message: MailMessage) { viewModelScope.launch { repository.setStarred(message.id, !message.isStarred); openMessage(message.id); reloadList() } }
    fun archive(message: MailMessage) { viewModelScope.launch { repository.archive(message.id); reloadList() } }
    fun trash(message: MailMessage) { viewModelScope.launch { repository.trash(message.id); reloadList() } }
    fun deleteForever(message: MailMessage) { viewModelScope.launch { repository.deleteForever(message.id); reloadList() } }
    fun move(message: MailMessage, mailbox: Mailbox) { viewModelScope.launch { repository.move(message.id, mailbox.id); reloadList() } }

    fun downloadAttachment(messageId: String, attachment: MailAttachment, destination: android.net.Uri) {
        viewModelScope.launch { repository.downloadAttachment(messageId, attachment, destination) }
    }

    fun setSearchQuery(value: String) { searchQuery.value = value }

    fun startNewMessage() {
        _composeSeed.value = ComposeSeed(accountId = preferredAccountId())
    }

    fun reply(message: MailMessage, replyAll: Boolean = false) {
        val recipients = (message.replyTo.ifEmpty { listOf(message.from) } + if (replyAll) message.to else emptyList())
            .map { it.address }.distinct().joinToString(", ")
        val cc = if (replyAll) message.cc.joinToString(", ") { it.address } else ""
        _composeSeed.value = ComposeSeed(
            accountId = message.accountId,
            to = recipients,
            cc = cc,
            subject = if (message.subject.startsWith("Re:", true)) message.subject else "Re: ${message.subject}",
            body = "\n\n在此回复\n\n—— 原始邮件 ——\n发件人：${message.from.formatted}\n日期：${java.text.DateFormat.getDateTimeInstance().format(message.sentAt)}\n主题：${message.subject}\n\n${message.bodyText}",
            inReplyTo = message.messageId,
            references = listOf(message.headers.substringAfter("References:", ""), message.messageId).filter { it.isNotBlank() }.joinToString(" ")
        )
    }

    fun forward(message: MailMessage) {
        _composeSeed.value = ComposeSeed(
            accountId = message.accountId,
            subject = if (message.subject.startsWith("Fwd:", true)) message.subject else "Fwd: ${message.subject}",
            body = "\n\n—— 转发邮件 ——\n发件人：${message.from.formatted}\n日期：${java.text.DateFormat.getDateTimeInstance().format(message.sentAt)}\n主题：${message.subject}\n\n${message.bodyText}"
        )
    }

    fun send(
        accountId: String,
        to: String,
        cc: String,
        bcc: String,
        subject: String,
        body: String,
        attachments: List<LocalAttachment>,
        scheduledAt: Long = 0L,
        onQueued: () -> Unit
    ) {
        val recipients = parseRecipients(to)
        if (recipients.isEmpty()) { _events.tryEmit(UiEvent("请填写收件人")); return }
        val seed = _composeSeed.value
        viewModelScope.launch {
            runCatching {
                val draft = MailDraft(
                    accountId = accountId.ifBlank { preferredAccountId() },
                    to = recipients,
                    cc = parseRecipients(cc),
                    bcc = parseRecipients(bcc),
                    subject = subject,
                    body = body,
                    attachments = attachments,
                    inReplyTo = seed.inReplyTo,
                    references = seed.references,
                    scheduledAt = scheduledAt
                )
                val undo = if (scheduledAt > System.currentTimeMillis()) 0 else preferences.value.undoSendSeconds
                repository.enqueueSend(draft, undo)
            }.onSuccess { id ->
                _events.emit(UiEvent(if (scheduledAt > 0) "邮件已安排发送" else "邮件将在 ${preferences.value.undoSendSeconds} 秒后发送", "撤销", id))
                onQueued()
            }.onFailure { _events.emit(UiEvent(it.message ?: "无法加入发件箱")) }
        }
    }

    fun handleEventAction(event: UiEvent) {
        if (event.action == "撤销" && event.token.isNotBlank()) viewModelScope.launch { repository.undoSend(event.token) }
    }

    fun setTheme(mode: ThemeMode) { viewModelScope.launch { repository.preferences.setTheme(mode) } }
    fun setCategories(enabled: Boolean) { viewModelScope.launch { repository.preferences.setCategories(enabled); selectCategory(if (enabled) MailCategory.PRIMARY else null) } }
    fun setRemoteImages(enabled: Boolean) { viewModelScope.launch { repository.preferences.setRemoteImages(enabled) } }
    fun setCompactRows(enabled: Boolean) { viewModelScope.launch { repository.preferences.setCompactRows(enabled) } }
    fun setConfirmDelete(enabled: Boolean) { viewModelScope.launch { repository.preferences.setConfirmDelete(enabled) } }
    fun setUndoSend(seconds: Int) { viewModelScope.launch { repository.preferences.setUndoSendSeconds(seconds) } }

    fun startExternalMessage(to: String, subject: String, body: String) {
        _composeSeed.value = ComposeSeed(
            accountId = preferredAccountId(),
            to = to,
            subject = subject,
            body = body
        )
    }

    private fun parseRecipients(value: String): List<String> = value
        .split(',', ';', '\n').map(String::trim).filter { it.isNotBlank() }.distinct()

    private fun preferredAccountId(): String = _selectedAccountId.value.ifBlank { accounts.value.firstOrNull()?.id.orEmpty() }
}
