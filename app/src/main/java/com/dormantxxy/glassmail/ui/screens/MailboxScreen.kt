package com.dormantxxy.glassmail.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dormantxxy.glassmail.data.model.*
import com.dormantxxy.glassmail.ui.AppViewModel
import com.dormantxxy.glassmail.ui.components.*
import kotlinx.coroutines.launch

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
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val selectionMode = selectedIds.isNotEmpty()

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            MailDrawer(
                accounts = accounts,
                mailboxes = mailboxes,
                selectedAccountId = selectedAccount,
                selectedMailbox = list.selectedMailbox,
                onAccount = { viewModel.selectAccount(it); viewModel.showInbox(); scope.launch { drawer.close() } },
                onInbox = { viewModel.showInbox(); scope.launch { drawer.close() } },
                onSmart = { viewModel.selectSmart(it); scope.launch { drawer.close() } },
                onMailbox = { viewModel.selectMailbox(it); scope.launch { drawer.close() } },
                onAddAccount = onAddAccount,
                onSettings = onSettings
            )
        }
    ) {
        AuroraBackground(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    if (selectionMode) SelectionTopBar(
                        count = selectedIds.size,
                        onClose = { selectedIds = emptySet() },
                        onRead = {
                            list.messages.filter { it.id in selectedIds }.forEach(viewModel::toggleRead)
                            selectedIds = emptySet()
                        },
                        onArchive = {
                            list.messages.filter { it.id in selectedIds }.forEach(viewModel::archive)
                            selectedIds = emptySet()
                        },
                        onTrash = {
                            list.messages.filter { it.id in selectedIds }.forEach(viewModel::trash)
                            selectedIds = emptySet()
                        }
                    ) else MailTopBar(
                        title = list.title,
                        subtitle = accountSubtitle(accounts, selectedAccount),
                        onMenu = { scope.launch { drawer.open() } },
                        onSearch = onSearch,
                        onMore = onSettings
                    )
                },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.startNewMessage(); onCompose() },
                        icon = { Icon(Icons.Rounded.Edit, null) },
                        text = { Text("写邮件") },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                }
            ) { padding ->
                PullToRefreshBox(
                    isRefreshing = sync is SyncState.Running,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    Column(Modifier.fillMaxSize()) {
                        if (preferences.categoriesEnabled && list.selectedMailbox == null && list.smartKind == null) {
                            CategoryBar(list.selectedCategory, list.messages, viewModel::selectCategory)
                        }
                        if (list.loading && list.messages.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        } else if (list.messages.isEmpty()) {
                            EmptyPane(
                                title = if (list.selectedCategory != null) "此分类暂无邮件" else "收件箱已清空",
                                detail = "下拉即可检查新邮件",
                                modifier = Modifier.fillMaxSize(),
                                actionLabel = "立即同步",
                                onAction = viewModel::refresh
                            )
                        } else {
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 104.dp)
                            ) {
                                items(list.messages, key = { it.id }) { message ->
                                    if (selectionMode) {
                                        MailMessageRow(
                                            message = message,
                                            compact = preferences.compactRows,
                                            selected = message.id in selectedIds,
                                            onClick = { selectedIds = toggle(selectedIds, message.id) },
                                            onLongClick = { selectedIds = toggle(selectedIds, message.id) },
                                            onToggleStar = { viewModel.toggleStar(message) }
                                        )
                                    } else {
                                        SwipeMailRow(
                                            message = message,
                                            compact = preferences.compactRows,
                                            onOpen = { onOpenMessage(message.id) },
                                            onSelect = { selectedIds = setOf(message.id) },
                                            onStar = { viewModel.toggleStar(message) },
                                            onArchive = { viewModel.archive(message) },
                                            onTrash = { viewModel.trash(message) }
                                        )
                                    }
                                    HorizontalDivider(Modifier.padding(start = if (preferences.compactRows) 66.dp else 72.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .16f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeMailRow(
    message: MailMessage,
    compact: Boolean,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onStar: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            when (it) {
                SwipeToDismissBoxValue.StartToEnd -> onArchive()
                SwipeToDismissBoxValue.EndToStart -> onTrash()
                SwipeToDismissBoxValue.Settled -> return@rememberSwipeToDismissBoxState false
            }
            true
        },
        positionalThreshold = { it * .34f }
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val archive = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                Modifier.fillMaxSize().background(if (archive) Color(0xFF34C759) else Color(0xFFFF3B30)).padding(horizontal = 24.dp),
                contentAlignment = if (archive) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (archive) Icons.Rounded.Archive else Icons.Rounded.Delete, null, tint = Color.White)
                    Spacer(Modifier.width(6.dp)); Text(if (archive) "归档" else "删除", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) {
        MailMessageRow(message, compact, onClick = onOpen, onLongClick = onSelect, onToggleStar = onStar, modifier = Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = .62f)))
    }
}

@Composable
private fun CategoryBar(selected: MailCategory?, messages: List<MailMessage>, onSelect: (MailCategory?) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { CategoryPill("全部", selected == null, if (selected == null) messages.count { !it.isRead } else 0) { onSelect(null) } }
        items(MailCategory.entries) { category ->
            CategoryPill(category.label, selected == category, if (selected == category) messages.count { !it.isRead } else 0) { onSelect(category) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MailTopBar(title: String, subtitle: String, onMenu: () -> Unit, onSearch: () -> Unit, onMore: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        },
        navigationIcon = { IconButton(onClick = onMenu) { Icon(Icons.Rounded.Menu, "邮箱") } },
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "搜索") }
            IconButton(onClick = onMore) { Icon(Icons.Rounded.MoreHoriz, "更多") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .78f))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(count: Int, onClose: () -> Unit, onRead: () -> Unit, onArchive: () -> Unit, onTrash: () -> Unit) {
    TopAppBar(
        title = { Text("已选择 $count 封") },
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "取消") } },
        actions = {
            IconButton(onClick = onRead) { Icon(Icons.Rounded.MarkEmailRead, "切换已读") }
            IconButton(onClick = onArchive) { Icon(Icons.Rounded.Archive, "归档") }
            IconButton(onClick = onTrash) { Icon(Icons.Rounded.Delete, "删除") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .92f))
    )
}

@Composable
private fun MailDrawer(
    accounts: List<MailAccount>,
    mailboxes: List<Mailbox>,
    selectedAccountId: String,
    selectedMailbox: Mailbox?,
    onAccount: (String) -> Unit,
    onInbox: () -> Unit,
    onSmart: (MailboxKind) -> Unit,
    onMailbox: (Mailbox) -> Unit,
    onAddAccount: () -> Unit,
    onSettings: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.widthIn(max = 340.dp).fillMaxHeight(),
        drawerContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = .96f)
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(Brush.linearGradient(listOf(Color(0xFF5AC8FA), Color(0xFF0A84FF))), androidx.compose.foundation.shape.RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Mail, null, tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp)); Column { Text("澄邮", style = MaterialTheme.typography.titleLarge); Text("所有邮箱", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                }
            }
            item { Text("账户", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            item { DrawerRow(Icons.Rounded.AllInbox, "统一收件箱", selectedAccountId.isBlank()) { onAccount("") } }
            items(accounts, key = { it.id }) { account ->
                DrawerRow(Icons.Rounded.AccountCircle, account.email, selectedAccountId == account.id, badge = if (account.isDemo) "演示" else null) { onAccount(account.id) }
            }
            item { DrawerRow(Icons.Rounded.AddCircleOutline, "添加账户", false, onClick = onAddAccount) }
            item { HorizontalDivider(Modifier.padding(vertical = 9.dp)) }
            item { Text("邮箱", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            item { DrawerRow(Icons.Rounded.Inbox, "收件箱", selectedMailbox == null, onClick = onInbox) }
            item { DrawerRow(Icons.Rounded.Star, "已加星标", false, onClick = { onSmart(MailboxKind.STARRED) }) }
            val visibleBoxes = mailboxes.filter { selectedAccountId.isNotBlank() && it.accountId == selectedAccountId && it.kind != MailboxKind.INBOX }
            items(visibleBoxes, key = { it.id }) { mailbox ->
                DrawerRow(iconForMailbox(mailbox.kind), mailbox.displayName, selectedMailbox?.id == mailbox.id, badge = mailbox.unreadCount.takeIf { it > 0 }?.toString()) { onMailbox(mailbox) }
            }
            item { Spacer(Modifier.height(8.dp)); HorizontalDivider(); DrawerRow(Icons.Rounded.Settings, "设置", false, onClick = onSettings) }
        }
    }
}

@Composable
private fun DrawerRow(icon: ImageVector, title: String, selected: Boolean, badge: String? = null, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, null) },
        badge = badge?.let { { Text(it) } },
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
    )
}

private fun iconForMailbox(kind: MailboxKind): ImageVector = when (kind) {
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

private fun accountSubtitle(accounts: List<MailAccount>, selectedId: String): String =
    accounts.firstOrNull { it.id == selectedId }?.email ?: "统一收件箱"

private fun toggle(values: Set<String>, id: String): Set<String> = if (id in values) values - id else values + id
