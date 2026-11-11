package com.dormantxxy.glassmail.ui.screens

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import java.text.DateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(
    messageId: String,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onCompose: () -> Unit
) {
    val detail by viewModel.detailState.collectAsState()
    val mailboxes by viewModel.mailboxes.collectAsState()
    val prefs by viewModel.preferences.collectAsState()
    var showMove by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(messageId) { viewModel.openMessage(messageId) }
    val message = detail.selected

    AuroraBackground(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(message?.subject ?: "邮件", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") } },
                    actions = {
                        if (message != null) {
                            IconButton(onClick = { viewModel.archive(message); onBack() }) { Icon(Icons.Rounded.Archive, "归档") }
                            IconButton(onClick = { showDelete = true }) { Icon(Icons.Rounded.Delete, "删除") }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreHoriz, "更多") }
                                DropdownMenu(menu, { menu = false }) {
                                    DropdownMenuItem({ Text(if (message.isRead) "标为未读" else "标为已读") }, { viewModel.toggleRead(message); menu = false }, leadingIcon = { Icon(Icons.Rounded.MarkEmailRead, null) })
                                    DropdownMenuItem({ Text(if (message.isStarred) "取消星标" else "添加星标") }, { viewModel.toggleStar(message); menu = false }, leadingIcon = { Icon(Icons.Rounded.Star, null) })
                                    DropdownMenuItem({ Text("移动到…") }, { showMove = true; menu = false }, leadingIcon = { Icon(Icons.Rounded.DriveFileMove, null) })
                                    DropdownMenuItem({ Text("转发") }, { viewModel.forward(message); menu = false; onCompose() }, leadingIcon = { Icon(Icons.Rounded.ForwardToInbox, null) })
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .82f))
                )
            },
            bottomBar = {
                if (message != null) ReplyBar(
                    starred = message.isStarred,
                    onReply = { viewModel.reply(message); onCompose() },
                    onReplyAll = { viewModel.reply(message, true); onCompose() },
                    onForward = { viewModel.forward(message); onCompose() },
                    onStar = { viewModel.toggleStar(message) }
                )
            }
        ) { padding ->
            when {
                detail.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                message == null -> EmptyPane("无法显示邮件", "它可能已被移动或删除", Modifier.fillMaxSize().padding(padding), actionLabel = "返回", onAction = onBack)
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 26.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    item {
                        Text(message.subject, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp))
                    }
                    items(detail.thread, key = { it.id }) { item ->
                        MessageCard(
                            message = item,
                            attachments = detail.attachments[item.id].orEmpty(),
                            allowRemoteImages = prefs.remoteImagesEnabled,
                            onEnableImages = { viewModel.setRemoteImages(true) }
                        )
                    }
                }
            }
        }
    }

    if (message != null && showMove) {
        val targets = mailboxes.filter { it.accountId == message.accountId && it.id != message.mailboxId && it.kind != MailboxKind.INBOX }
        AlertDialog(
            onDismissRequest = { showMove = false },
            title = { Text("移动邮件") },
            text = {
                if (targets.isEmpty()) Text("没有其他可用文件夹") else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(targets, key = { it.id }) { box ->
                        ListItem(
                            headlineContent = { Text(box.displayName) },
                            leadingContent = { Icon(Icons.Rounded.Folder, null) },
                            modifier = Modifier.clickable { viewModel.move(message, box); showMove = false; onBack() }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMove = false }) { Text("取消") } }
        )
    }

    if (message != null && showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text("移到废纸篓？") },
            text = { Text("邮件会被移动到当前账户的废纸篓。") },
            confirmButton = { Button(onClick = { viewModel.trash(message); showDelete = false; onBack() }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun MessageCard(message: MailMessage, attachments: List<MailAttachment>, allowRemoteImages: Boolean, onEnableImages: () -> Unit) {
    var expanded by remember(message.id) { mutableStateOf(true) }
    GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 23.dp) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SenderAvatar(message.from.name, message.from.address)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(message.from.name.ifBlank { message.from.address }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("发给 ${message.to.joinToString { it.name.ifBlank { it.address } }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(message.sentAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
                if (message.bodyHtml.isNotBlank()) {
                    if (!allowRemoteImages && Regex("(?i)<img\\s").containsMatchIn(message.bodyHtml)) {
                        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ImageNotSupported, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp)); Text("已保护隐私并阻止远程图片", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = onEnableImages) { Text("显示") }
                        }
                    }
                    SafeHtml(message.bodyHtml, allowRemoteImages)
                } else {
                    Text(message.bodyText.ifBlank { message.preview }, Modifier.padding(17.dp), style = MaterialTheme.typography.bodyLarge, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.18f)
                }
                if (attachments.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("附件 (${attachments.size})", style = MaterialTheme.typography.labelLarge)
                        attachments.forEach { attachment ->
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f), shape = MaterialTheme.shapes.medium) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.InsertDriveFile, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) {
                                        Text(attachment.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(formatBytes(attachment.sizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Rounded.Download, "下载", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SafeHtml(html: String, allowRemoteImages: Boolean) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.red < .2f
    val foreground = if (dark) "#F2F4FA" else "#17181C"
    val link = if (dark) "#5EB0FF" else "#007AFF"
    val wrapped = remember(html, dark) {
        """<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><style>
            body{font-family:sans-serif;color:$foreground;background:transparent;padding:8px;line-height:1.55;overflow-wrap:anywhere}img{max-width:100%;height:auto}a{color:$link}pre{white-space:pre-wrap}blockquote{border-left:3px solid #8a8a8a;padding-left:10px;color:#777}
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
                        if (uri.scheme == "http" || uri.scheme == "https" || uri.scheme == "mailto") {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        }
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
        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 650.dp)
    )
}

@Composable
private fun ReplyBar(starred: Boolean, onReply: () -> Unit, onReplyAll: () -> Unit, onForward: () -> Unit, onStar: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .9f), tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
            IconButton(onClick = onReply) { Icon(Icons.Rounded.Reply, "回复") }
            IconButton(onClick = onReplyAll) { Icon(Icons.Rounded.ReplyAll, "全部回复") }
            IconButton(onClick = onForward) { Icon(Icons.Rounded.Forward, "转发") }
            IconButton(onClick = onStar) { Icon(if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder, "星标", tint = if (starred) Color(0xFFFFB000) else LocalContentColor.current) }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "大小未知"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}
