package com.dormantxxy.glassmail.ui.screens

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dormantxxy.glassmail.data.model.MailAttachment
import com.dormantxxy.glassmail.data.model.MailMessage
import com.dormantxxy.glassmail.data.model.MailboxKind
import com.dormantxxy.glassmail.ui.AppViewModel
import com.dormantxxy.glassmail.ui.components.*
import com.dormantxxy.glassmail.ui.theme.IOSBlue
import com.dormantxxy.glassmail.ui.theme.IOSOrange
import com.dormantxxy.glassmail.ui.theme.IOSRed
import java.text.DateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(messageId: String, viewModel: AppViewModel, onBack: () -> Unit, onCompose: () -> Unit) {
    val detail by viewModel.detailState.collectAsState()
    val mailboxes by viewModel.mailboxes.collectAsState()
    val prefs by viewModel.preferences.collectAsState()
    var showMove by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showReply by remember { mutableStateOf(false) }

    LaunchedEffect(messageId) { viewModel.openMessage(messageId) }
    val message = detail.selected

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            detail.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
            message == null -> EmptyPane("无法显示邮件", "邮件可能已被移动或删除", Modifier.fillMaxSize(), actionLabel = "返回", onAction = onBack)
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 104.dp)
            ) {
                item {
                    Spacer(Modifier.statusBarsPadding())
                    ReaderControls(onBack = onBack, onMore = { showMore = true })
                    Text(
                        message.subject,
                        Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(detail.thread, key = { it.id }) { threadMessage ->
                    ThreadMessage(
                        message = threadMessage,
                        attachments = detail.attachments[threadMessage.id].orEmpty(),
                        allowRemoteImages = prefs.remoteImagesEnabled,
                        onEnableImages = { viewModel.setRemoteImages(true) },
                        onDownload = { attachment, uri -> viewModel.downloadAttachment(threadMessage.id, attachment, uri) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

        if (message != null) {
            ReaderBottomBar(
                onArchive = { viewModel.archive(message); onBack() },
                onTrash = { showDelete = true },
                onMove = { showMove = true },
                onReply = { showReply = true },
                onCompose = { viewModel.startNewMessage(); onCompose() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (message != null && showReply) {
        ActionSheet(
            title = "回复",
            content = {
                ActionSheetRow(Icons.Rounded.Reply, "回复") { showReply = false; viewModel.reply(message); onCompose() }
                SheetDivider()
                ActionSheetRow(Icons.Rounded.ReplyAll, "全部回复") { showReply = false; viewModel.reply(message, true); onCompose() }
                SheetDivider()
                ActionSheetRow(Icons.Rounded.Forward, "转发") { showReply = false; viewModel.forward(message); onCompose() }
            },
            onDismiss = { showReply = false }
        )
    }

    if (message != null && showMore) {
        ActionSheet(
            title = "更多",
            content = {
                ActionSheetRow(Icons.Rounded.MarkEmailRead, if (message.isRead) "标记为未读" else "标记为已读") { showMore = false; viewModel.toggleRead(message) }
                SheetDivider()
                ActionSheetRow(Icons.Rounded.Flag, if (message.isStarred) "取消旗标" else "旗标", IOSOrange) { showMore = false; viewModel.toggleStar(message) }
                SheetDivider()
                ActionSheetRow(Icons.Rounded.DriveFileMove, "移动邮件") { showMore = false; showMove = true }
                SheetDivider()
                ActionSheetRow(Icons.Rounded.Delete, "移到废纸篓", IOSRed) { showMore = false; showDelete = true }
            },
            onDismiss = { showMore = false }
        )
    }

    if (message != null && showMove) {
        val targets = mailboxes.filter { it.accountId == message.accountId && it.id != message.mailboxId && it.kind != MailboxKind.INBOX }
        ModalBottomSheet(onDismissRequest = { showMove = false }, containerColor = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
                Text("移动邮件", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(8.dp))
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))) {
                    targets.forEachIndexed { index, box ->
                        Row(
                            Modifier.fillMaxWidth().clickable { viewModel.move(message, box); showMove = false; onBack() }.padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Folder, null, tint = IOSBlue)
                            Spacer(Modifier.width(12.dp)); Text(box.displayName, Modifier.weight(1f)); Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (index != targets.lastIndex) HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    if (message != null && showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("移到废纸篓？") },
            text = { Text("这封邮件将被移到废纸篓。") },
            confirmButton = { TextButton(onClick = { viewModel.trash(message); showDelete = false; onBack() }) { Text("删除", color = IOSRed) } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun ReaderControls(onBack: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        LiquidCircleButton(Icons.Rounded.ChevronLeft, "返回", onBack)
        Spacer(Modifier.weight(1f))
        LiquidCircleButton(Icons.Rounded.KeyboardArrowUp, "上一封", {})
        Spacer(Modifier.width(8.dp))
        LiquidCircleButton(Icons.Rounded.KeyboardArrowDown, "下一封", {})
        Spacer(Modifier.width(8.dp))
        LiquidCircleButton(Icons.Rounded.MoreHoriz, "更多", onMore)
    }
}

@Composable
private fun ThreadMessage(
    message: MailMessage,
    attachments: List<MailAttachment>,
    allowRemoteImages: Boolean,
    onEnableImages: () -> Unit,
    onDownload: (MailAttachment, Uri) -> Unit
) {
    var expanded by remember(message.id) { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SenderAvatar(message.from.name, message.from.address, size = 44.dp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(message.from.name.ifBlank { message.from.address }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("发给 ${message.to.joinToString { it.name.ifBlank { it.address } }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(message.sentAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!expanded) return@Column
        HorizontalDivider(Modifier.padding(start = 70.dp), color = MaterialTheme.colorScheme.outlineVariant)
        if (message.bodyHtml.isNotBlank()) {
            if (!allowRemoteImages && Regex("(?i)<img\\s").containsMatchIn(message.bodyHtml)) {
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ImageNotSupported, null, Modifier.size(19.dp), tint = IOSBlue)
                    Spacer(Modifier.width(8.dp)); Text("远程图片已隐藏", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onEnableImages) { Text("载入所有图片", color = IOSBlue) }
                }
            }
            SafeHtml(message.bodyHtml, allowRemoteImages)
        } else {
            Text(
                message.bodyText.ifBlank { message.preview },
                Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        if (attachments.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${attachments.size} 个附件", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                attachments.forEach { attachment -> AttachmentDownloadRow(attachment) { onDownload(attachment, it) } }
            }
        }
    }
}

@Composable
private fun AttachmentDownloadRow(attachment: MailAttachment, onDestination: (Uri) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(attachment.mimeType)) { uri -> if (uri != null) onDestination(uri) }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).clickable { launcher.launch(attachment.fileName) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.InsertDriveFile, null, tint = IOSBlue)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(attachment.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatBytes(attachment.sizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.Download, "下载", tint = IOSBlue)
    }
}

@Composable
private fun SafeHtml(html: String, allowRemoteImages: Boolean) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.red < .2f
    val foreground = if (dark) "#FFFFFF" else "#000000"
    val link = if (dark) "#0A84FF" else "#007AFF"
    val wrapped = remember(html, dark) {
        """<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><style>
        body{font-family:-apple-system,sans-serif;color:$foreground;background:transparent;padding:10px;line-height:1.5;overflow-wrap:anywhere;font-size:16px}img{max-width:100%;height:auto}a{color:$link}pre{white-space:pre-wrap}blockquote{border-left:3px solid #8e8e93;padding-left:10px;color:#8e8e93}
        </style></head><body>$html</body></html>"""
    }
    AndroidView(
        factory = {
            WebView(it).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.blockNetworkImage = !allowRemoteImages
                settings.blockNetworkLoads = !allowRemoteImages
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val uri = request?.url ?: return true
                        if (uri.scheme in listOf("http", "https", "mailto")) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        return true
                    }
                }
                loadDataWithBaseURL(null, wrapped, "text/html", "UTF-8", null)
            }
        },
        update = {
            it.settings.blockNetworkImage = !allowRemoteImages
            it.settings.blockNetworkLoads = !allowRemoteImages
            it.loadDataWithBaseURL(null, wrapped, "text/html", "UTF-8", null)
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 700.dp)
    )
}

@Composable
private fun ReaderBottomBar(
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    onMove: () -> Unit,
    onReply: () -> Unit,
    onCompose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.height(50.dp).weight(1f).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .96f), CircleShape).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            IconButton(onClick = onArchive) { Icon(Icons.Rounded.Archive, "归档") }
            IconButton(onClick = onTrash) { Icon(Icons.Rounded.Delete, "删除") }
            IconButton(onClick = onMove) { Icon(Icons.Rounded.Folder, "移动") }
            IconButton(onClick = onReply) { Icon(Icons.Rounded.Reply, "回复") }
        }
        Spacer(Modifier.width(9.dp))
        LiquidCircleButton(Icons.Rounded.Edit, "新邮件", onCompose, tint = IOSBlue)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionSheet(title: String, content: @Composable ColumnScope.() -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp))
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)), content = content)
        }
    }
}

@Composable
private fun ActionSheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = tint, style = MaterialTheme.typography.bodyLarge)
        Icon(icon, null, tint = tint)
    }
}

@Composable private fun SheetDivider() { HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant) }

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "大小未知"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}
