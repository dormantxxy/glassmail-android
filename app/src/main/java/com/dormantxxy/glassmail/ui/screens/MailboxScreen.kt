package com.dormantxxy.glassmail.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dormantxxy.glassmail.data.model.*
import com.dormantxxy.glassmail.ui.AppViewModel
import com.dormantxxy.glassmail.ui.components.*
import com.dormantxxy.glassmail.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailboxScreen(
    viewModel: AppViewModel,
    onOpenMessage: (String) -> Unit,
    onCompose: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onAddAccount: () -> Unit
) {
    val list by viewModel.listState.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val mailboxes by viewModel.mailboxes.collectAsState()
    val selectedAccount by viewModel.selectedAccountId.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val sync by viewModel.syncState.collectAsState()
    var showMailboxes by rememberSaveable { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var selecting by remember { mutableStateOf(false) }
    var digestSender by remember { mutableStateOf<String?>(null) }

    if (showMailboxes) {
        BackHandler { showMailboxes = false }
        MailboxesPane(
            accounts = accounts,
            mailboxes = mailboxes,
            selectedAccountId = selectedAccount,
            onAccount = { viewModel.selectAccount(it) },
            onInbox = { accountId -> viewModel.selectAccount(accountId); viewModel.showInbox(); showMailboxes = false },
            onMailbox = { box -> viewModel.selectAccount(box.accountId); viewModel.selectMailbox(box); showMailboxes = false },
            onSmart = { kind -> viewModel.selectSmart(kind); showMailboxes = false },
            onAddAccount = onAddAccount,
            onManageAccounts = onSettings,
            onDone = { showMailboxes = false },
            onSearch = onSearch
        )
        return
    }

    val digestMessages = digestSender?.let { sender -> list.messages.filter { it.from.address == sender } }.orEmpty()
    if (digestSender != null) {
        SenderDigestPane(
            messages = digestMessages,
            compact = preferences.compactRows,
            onBack = { digestSender = null },
            onOpen = onOpenMessage,
            onStar = viewModel::toggleStar
        )
        return
    }

    val selectionMode = selecting || selectedIds.isNotEmpty()
    val categoryOrder = listOf(MailCategory.PRIMARY, MailCategory.TRANSACTIONS, MailCategory.UPDATES, MailCategory.PROMOTIONS)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PullToRefreshBox(
            isRefreshing = sync is SyncState.Running,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                Modifier.fillMaxSize().pointerInput(preferences.categoriesEnabled, list.selectedCategory) {
                    var drag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onHorizontalDrag = { _, amount -> drag += amount },
                        onDragEnd = {
                            if (preferences.categoriesEnabled && kotlin.math.abs(drag) > 90f) {
                                val current = categoryOrder.indexOf(list.selectedCategory).coerceAtLeast(0)
                                val next = if (drag < 0) (current + 1).coerceAtMost(categoryOrder.lastIndex) else (current - 1).coerceAtLeast(0)
                                viewModel.selectCategory(categoryOrder[next])
                            }
                        }
                    )
                },
                contentPadding = PaddingValues(bottom = 104.dp)
            ) {
                item {
                    Spacer(Modifier.statusBarsPadding())
                    InboxControls(
                        selectionMode = selectionMode,
                        selectedCount = selectedIds.size,
                        onMailboxes = { showMailboxes = true },
                        onSelect = { selecting = true },
                        onCancelSelection = { selecting = false; selectedIds = emptySet() },
                        onMore = { showOptions = true }
                    )
                    IOSLargeTitle(
                        title = list.title,
                        subtitle = "${accountSubtitle(accounts, selectedAccount)} · ${if (sync is SyncState.Running) "正在更新…" else "刚刚更新"}",
                        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                    )
                    if (preferences.categoriesEnabled && list.selectedMailbox == null && list.smartKind == null) {
                        IOSCategoryTabs(list.selectedCategory ?: MailCategory.PRIMARY, list.messages) { viewModel.selectCategory(it) }
                    }
                }

                when {
                    list.loading && list.messages.isEmpty() -> item {
                        Box(Modifier.fillParentMaxHeight(.72f), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                    }
                    list.messages.isEmpty() -> item {
                        EmptyPane("无邮件", "下拉以检查新邮件", Modifier.fillParentMaxHeight(.72f), actionLabel = "检查邮件", onAction = viewModel::refresh)
                    }
                    preferences.categoriesEnabled && list.selectedCategory != null && list.selectedCategory != MailCategory.PRIMARY -> {
                        val groups = list.messages.groupBy { it.from.address }.values
                            .sortedByDescending { group -> group.maxOfOrNull(MailMessage::receivedAt) ?: 0L }
                        item { MessageSectionHeader("新邮件") }
                        items(groups, key = { it.first().from.address }) { group ->
                            SenderDigestRow(group) { digestSender = group.first().from.address }
                            RowDivider()
                        }
                    }
                    else -> {
                        val (newMail, older) = splitMessages(list.messages)
                        if (newMail.isNotEmpty()) {
                            item { MessageSectionHeader("新邮件") }
                            items(newMail, key = { it.id }) { message ->
                                MessageListItem(
                                    message, preferences.compactRows, selectionMode, selectedIds,
                                    onSelectionChange = { selectedIds = it },
                                    onOpen = onOpenMessage,
                                    onStar = viewModel::toggleStar,
                                    onArchive = viewModel::archive,
                                    onTrash = viewModel::trash
                                )
                            }
                        }
                        if (older.isNotEmpty()) {
                            item { MessageSectionHeader("较早邮件") }
                            items(older, key = { it.id }) { message ->
                                MessageListItem(
                                    message, preferences.compactRows, selectionMode, selectedIds,
                                    onSelectionChange = { selectedIds = it },
                                    onOpen = onOpenMessage,
                                    onStar = viewModel::toggleStar,
                                    onArchive = viewModel::archive,
                                    onTrash = viewModel::trash
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selectionMode) {
            SelectionToolbar(
                Modifier.align(Alignment.BottomCenter),
                onRead = {
                    list.messages.filter { it.id in selectedIds }.forEach(viewModel::toggleRead)
                    selectedIds = emptySet(); selecting = false
                },
                onArchive = {
                    list.messages.filter { it.id in selectedIds }.forEach(viewModel::archive)
                    selectedIds = emptySet(); selecting = false
                },
                onTrash = {
                    list.messages.filter { it.id in selectedIds }.forEach(viewModel::trash)
                    selectedIds = emptySet(); selecting = false
                }
            )
        } else {
            InboxBottomBar(
                unread = list.messages.count { !it.isRead },
                onFilter = { viewModel.selectSmart(MailboxKind.STARRED) },
                onCompose = { viewModel.startNewMessage(); onCompose() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (showOptions) {
        ViewOptionsSheet(
            categories = preferences.categoriesEnabled,
            compact = preferences.compactRows,
            onCategories = { viewModel.setCategories(it) },
            onCompact = { viewModel.setCompactRows(it) },
            onSearch = { showOptions = false; onSearch() },
            onDismiss = { showOptions = false }
        )
    }
}

@Composable
private fun InboxControls(
    selectionMode: Boolean,
    selectedCount: Int,
    onMailboxes: () -> Unit,
    onSelect: () -> Unit,
    onCancelSelection: () -> Unit,
    onMore: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        LiquidCircleButton(Icons.Rounded.ChevronLeft, "邮箱", onMailboxes)
        Spacer(Modifier.weight(1f))
        if (selectionMode) {
            Text("已选择 $selectedCount 封", Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LiquidPillButton("完成", onCancelSelection, tint = IOSBlue)
        } else {
            LiquidPillButton("选择", onSelect)
            Spacer(Modifier.width(9.dp))
            LiquidCircleButton(Icons.Rounded.MoreHoriz, "更多", onMore)
        }
    }
}

@Composable
private fun IOSCategoryTabs(selected: MailCategory, messages: List<MailMessage>, onSelect: (MailCategory) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        MailCategory.entries.forEach { category ->
            CategoryPill(category.label, selected == category, messages.count { !it.isRead && it.category == category }) { onSelect(category) }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun MessageSectionHeader(title: String) {
    Text(
        title,
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .62f)).padding(horizontal = 15.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageListItem(
    message: MailMessage,
    compact: Boolean,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onOpen: (String) -> Unit,
    onStar: (MailMessage) -> Unit,
    onArchive: (MailMessage) -> Unit,
    onTrash: (MailMessage) -> Unit
) {
    if (selectionMode) {
        MailMessageRow(
            message, compact, message.id in selectedIds,
            onClick = { onSelectionChange(toggle(selectedIds, message.id)) },
            onLongClick = { onSelectionChange(toggle(selectedIds, message.id)) },
            onToggleStar = { onStar(message) }
        )
        RowDivider()
        return
    }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            when (it) {
                SwipeToDismissBoxValue.StartToEnd -> onArchive(message)
                SwipeToDismissBoxValue.EndToStart -> onTrash(message)
                SwipeToDismissBoxValue.Settled -> return@rememberSwipeToDismissBoxState false
            }
            true
        },
        positionalThreshold = { it * .32f }
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val archive = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                Modifier.fillMaxSize().background(if (archive) IOSGreen else IOSRed).padding(horizontal = 22.dp),
                contentAlignment = if (archive) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Icon(if (archive) Icons.Rounded.Archive else Icons.Rounded.Delete, null, tint = Color.White)
            }
        }
    ) {
        MailMessageRow(
            message, compact,
            onClick = { onOpen(message.id) },
            onLongClick = { onSelectionChange(setOf(message.id)) },
            onToggleStar = { onStar(message) }
        )
    }
    RowDivider()
}

@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 65.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SenderDigestRow(messages: List<MailMessage>, onClick: () -> Unit) {
    val latest = messages.maxByOrNull { it.receivedAt } ?: return
    val unread = messages.any { !it.isRead }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
        SenderAvatar(latest.from.name, latest.from.address, size = 44.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(latest.from.name.ifBlank { latest.from.address }, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatMailDate(latest.receivedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(latest.subject, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(latest.preview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
            Text(messages.size.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, null, Modifier.padding(top = 3.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InboxBottomBar(unread: Int, onFilter: () -> Unit, onCompose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiquidCircleButton(Icons.Rounded.FilterList, "过滤", onFilter, tint = IOSBlue)
        Text(
            if (unread == 0) "无未读邮件" else "$unread 封未读",
            Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LiquidCircleButton(Icons.Rounded.Edit, "新邮件", onCompose, tint = IOSBlue)
    }
}

@Composable
private fun SelectionToolbar(modifier: Modifier, onRead: () -> Unit, onArchive: () -> Unit, onTrash: () -> Unit) {
    Row(
        modifier.navigationBarsPadding().padding(16.dp).height(54.dp).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .96f), CircleShape).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        IconButton(onClick = onRead) { Icon(Icons.Rounded.MarkEmailRead, "标记") }
        IconButton(onClick = onArchive) { Icon(Icons.Rounded.Archive, "归档") }
        IconButton(onClick = onTrash) { Icon(Icons.Rounded.Delete, "删除", tint = IOSRed) }
    }
}

@Composable
private fun MailboxesPane(
    accounts: List<MailAccount>,
    mailboxes: List<Mailbox>,
    selectedAccountId: String,
    onAccount: (String) -> Unit,
    onInbox: (String) -> Unit,
    onMailbox: (Mailbox) -> Unit,
    onSmart: (MailboxKind) -> Unit,
    onAddAccount: () -> Unit,
    onManageAccounts: () -> Unit,
    onDone: () -> Unit,
    onSearch: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        contentPadding = PaddingValues(bottom = 38.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Spacer(Modifier.statusBarsPadding())
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                LiquidPillButton(if (editing) "完成" else "编辑", { editing = !editing }, tint = IOSBlue)
            }
            IOSLargeTitle("邮箱", modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(42.dp).background(MaterialTheme.colorScheme.surface, CircleShape).clickable(onClick = onSearch).padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(7.dp)); Text("搜索", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            IOSSectionLabel("邮箱")
            MailboxGroup {
                MailboxRow(Icons.Rounded.AllInbox, IOSBlue, "所有收件箱", mailboxes.filter { it.kind == MailboxKind.INBOX }.sumOf { it.unreadCount }, selectedAccountId.isBlank()) { onInbox("") }
                GroupLine()
                MailboxRow(Icons.Rounded.MarkEmailUnread, IOSBlue, "未读", mailboxes.sumOf { it.unreadCount }) { /* inbox already exposes unread filter */ }
                GroupLine()
                MailboxRow(Icons.Rounded.Star, IOSOrange, "有旗标", 0) { onSmart(MailboxKind.STARRED) }
                GroupLine()
                MailboxRow(Icons.Rounded.AttachFile, Color(0xFF8E8E93), "附件", 0) { onSmart(MailboxKind.ALL) }
            }
        }
        items(accounts, key = { it.id }) { account ->
            Column {
                IOSSectionLabel(account.displayName)
                MailboxGroup {
                    val boxes = mailboxes.filter { it.accountId == account.id }
                    val inbox = boxes.firstOrNull { it.kind == MailboxKind.INBOX }
                    MailboxRow(Icons.Rounded.Inbox, IOSBlue, "收件箱", inbox?.unreadCount ?: 0, selectedAccountId == account.id) { onInbox(account.id) }
                    boxes.filter { it.kind != MailboxKind.INBOX }.forEach { box ->
                        GroupLine()
                        MailboxRow(mailboxIcon(box.kind), mailboxColor(box.kind), box.displayName, box.unreadCount) { onMailbox(box) }
                    }
                }
            }
        }
        item {
            MailboxGroup {
                MailboxActionRow(Icons.Rounded.Add, "添加账户", onAddAccount)
                GroupLine()
                MailboxActionRow(Icons.Rounded.Settings, "账户设置", onManageAccounts)
            }
        }
    }
}

@Composable
private fun MailboxGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)), content = content)
}

@Composable
private fun GroupLine() { HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.outlineVariant) }

@Composable
private fun MailboxRow(icon: ImageVector, color: Color, title: String, count: Int, selected: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).background(color, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(18.dp), tint = Color.White) }
        Spacer(Modifier.width(12.dp)); Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = if (selected) IOSBlue else MaterialTheme.colorScheme.onSurface)
        if (count > 0) Text(count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MailboxActionRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = IOSBlue); Spacer(Modifier.width(12.dp)); Text(title, color = IOSBlue, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SenderDigestPane(messages: List<MailMessage>, compact: Boolean, onBack: () -> Unit, onOpen: (String) -> Unit, onStar: (MailMessage) -> Unit) {
    val sender = messages.firstOrNull()?.from
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Spacer(Modifier.statusBarsPadding())
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                LiquidCircleButton(Icons.Rounded.ChevronLeft, "返回", onBack)
                Spacer(Modifier.weight(1f)); LiquidCircleButton(Icons.Rounded.MoreHoriz, "更多", {})
            }
            IOSLargeTitle(sender?.name?.ifBlank { sender.address } ?: "邮件", "${messages.size} 封邮件", Modifier.padding(top = 8.dp, bottom = 10.dp))
        }
        items(messages.sortedByDescending { it.receivedAt }, key = { it.id }) { message ->
            MailMessageRow(message, compact, onClick = { onOpen(message.id) }, onLongClick = {}, onToggleStar = { onStar(message) })
            RowDivider()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewOptionsSheet(
    categories: Boolean,
    compact: Boolean,
    onCategories: (Boolean) -> Unit,
    onCompact: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("显示方式", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 6.dp))
            MailboxGroup {
                SheetChoice("类别", categories) { onCategories(true); onDismiss() }
                GroupLine(); SheetChoice("列表视图", !categories) { onCategories(false); onDismiss() }
            }
            MailboxGroup {
                SheetChoice("两行预览", !compact) { onCompact(false); onDismiss() }
                GroupLine(); SheetChoice("紧凑预览", compact) { onCompact(true); onDismiss() }
            }
            MailboxGroup { MailboxActionRow(Icons.Rounded.Search, "搜索", onSearch) }
        }
    }
}

@Composable
private fun SheetChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (selected) Icon(Icons.Rounded.Check, null, tint = IOSBlue)
    }
}

private fun splitMessages(messages: List<MailMessage>): Pair<List<MailMessage>, List<MailMessage>> {
    val threshold = System.currentTimeMillis() - 24L * 60 * 60 * 1000
    return messages.partition { !it.isRead || it.receivedAt >= threshold }
}

private fun toggle(values: Set<String>, id: String) = if (id in values) values - id else values + id
private fun accountSubtitle(accounts: List<MailAccount>, selectedId: String) = accounts.firstOrNull { it.id == selectedId }?.displayName ?: "所有邮箱"
private fun mailboxIcon(kind: MailboxKind): ImageVector = when (kind) {
    MailboxKind.INBOX -> Icons.Rounded.Inbox
    MailboxKind.DRAFTS -> Icons.Rounded.EditNote
    MailboxKind.SENT -> Icons.Rounded.Send
    MailboxKind.ARCHIVE -> Icons.Rounded.Archive
    MailboxKind.TRASH -> Icons.Rounded.Delete
    MailboxKind.SPAM -> Icons.Rounded.Report
    MailboxKind.STARRED -> Icons.Rounded.Star
    MailboxKind.ALL -> Icons.Rounded.AllInbox
    MailboxKind.CUSTOM -> Icons.Rounded.Folder
}
private fun mailboxColor(kind: MailboxKind): Color = when (kind) {
    MailboxKind.DRAFTS -> IOSOrange
    MailboxKind.SENT -> IOSBlue
    MailboxKind.ARCHIVE -> Color(0xFF8E8E93)
    MailboxKind.TRASH, MailboxKind.SPAM -> IOSRed
    MailboxKind.STARRED -> IOSOrange
    else -> IOSBlue
}
