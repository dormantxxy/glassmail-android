package com.dormantxxy.glassmail.ui.screens

import android.content.Intent
import android.database.Cursor
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dormantxxy.glassmail.data.model.LocalAttachment
import com.dormantxxy.glassmail.ui.AppViewModel
import com.dormantxxy.glassmail.ui.components.AuroraBackground
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val accounts by viewModel.accounts.collectAsState()
    val seed by viewModel.composeSeed.collectAsState()
    val context = LocalContext.current
    var accountId by rememberSaveable { mutableStateOf(seed.accountId) }
    var to by rememberSaveable { mutableStateOf(seed.to) }
    var cc by rememberSaveable { mutableStateOf(seed.cc) }
    var bcc by rememberSaveable { mutableStateOf("") }
    var subject by rememberSaveable { mutableStateOf(seed.subject) }
    var body by rememberSaveable { mutableStateOf(seed.body) }
    var showCc by rememberSaveable { mutableStateOf(seed.cc.isNotBlank()) }
    var accountMenu by remember { mutableStateOf(false) }
    var sendMenu by remember { mutableStateOf(false) }
    var discardDialog by remember { mutableStateOf(false) }
    val attachments = remember { mutableStateListOf<LocalAttachment>() }

    LaunchedEffect(seed) {
        accountId = seed.accountId.ifBlank { accounts.firstOrNull()?.id.orEmpty() }
        to = seed.to; cc = seed.cc; subject = seed.subject; body = seed.body
        showCc = seed.cc.isNotBlank()
    }

    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val meta = queryAttachment(context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null))
            attachments += LocalAttachment(uri.toString(), meta.first, context.contentResolver.getType(uri) ?: "application/octet-stream", meta.second)
        }
    }

    fun leave() {
        if (to.isNotBlank() || subject.isNotBlank() || body.isNotBlank() || attachments.isNotEmpty()) discardDialog = true else onBack()
    }
    fun queue(at: Long = 0L) {
        viewModel.send(accountId, to, cc, bcc, subject, body, attachments.toList(), at, onBack)
    }

    BackHandler(onBack = ::leave)

    AuroraBackground(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("新邮件") },
                    navigationIcon = { TextButton(onClick = ::leave) { Text("取消") } },
                    actions = {
                        IconButton(onClick = { attachmentPicker.launch(arrayOf("*/*")) }) { Icon(Icons.Rounded.AttachFile, "添加附件") }
                        IconButton(onClick = { queue() }, enabled = to.isNotBlank() && accountId.isNotBlank()) { Icon(Icons.Rounded.Send, "发送", tint = MaterialTheme.colorScheme.primary) }
                        Box {
                            IconButton(onClick = { sendMenu = true }) { Icon(Icons.Rounded.ArrowDropDown, "发送选项") }
                            DropdownMenu(sendMenu, { sendMenu = false }) {
                                DropdownMenuItem({ Text("立即发送") }, { sendMenu = false; queue() }, leadingIcon = { Icon(Icons.Rounded.Send, null) })
                                DropdownMenuItem({ Text("1 小时后发送") }, { sendMenu = false; queue(System.currentTimeMillis() + 60 * 60 * 1000L) }, leadingIcon = { Icon(Icons.Rounded.Schedule, null) })
                                DropdownMenuItem({ Text("明早 8:00 发送") }, { sendMenu = false; queue(tomorrowMorning()) }, leadingIcon = { Icon(Icons.Rounded.WbSunny, null) })
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .82f))
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding().background(MaterialTheme.colorScheme.surface.copy(alpha = .68f))) {
                Box {
                    ComposeLine(label = "发件人") {
                        Text(
                            accounts.firstOrNull { it.id == accountId }?.email ?: "选择账户",
                            Modifier.weight(1f).clickable { accountMenu = true },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(Icons.Rounded.UnfoldMore, null, Modifier.clickable { accountMenu = true }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(accountMenu, { accountMenu = false }) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Column { Text(account.displayName); Text(account.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                                onClick = { accountId = account.id; accountMenu = false },
                                leadingIcon = { Icon(if (account.id == accountId) Icons.Rounded.CheckCircle else Icons.Rounded.AccountCircle, null) }
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
                ComposeRecipientField("收件人", to, { to = it }, trailing = {
                    TextButton(onClick = { showCc = !showCc }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("抄送/密送", style = MaterialTheme.typography.labelMedium) }
                })
                if (showCc) {
                    HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
                    ComposeRecipientField("抄送", cc, { cc = it })
                    HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
                    ComposeRecipientField("密送", bcc, { bcc = it })
                }
                HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
                ComposeRecipientField("主题", subject, { subject = it }, keyboardType = KeyboardType.Text)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
                if (attachments.isNotEmpty()) {
                    LazyRow(
                        Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(attachments, key = { it.uri }) { attachment ->
                            InputChip(
                                selected = false,
                                onClick = {},
                                label = { Text(attachment.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = { Icon(Icons.Rounded.InsertDriveFile, null, Modifier.size(18.dp)) },
                                trailingIcon = { Icon(Icons.Rounded.Close, "移除", Modifier.size(18.dp).clickable { attachments.remove(attachment) }) }
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
                }
                TextField(
                    value = body,
                    onValueChange = { body = it },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    placeholder = { Text("写下内容…") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .88f)) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { attachmentPicker.launch(arrayOf("image/*")) }) { Icon(Icons.Rounded.Image, "图片") }
                        IconButton(onClick = { attachmentPicker.launch(arrayOf("*/*")) }) { Icon(Icons.Rounded.AttachFile, "文件") }
                        IconButton(onClick = { body += "\n• " }) { Icon(Icons.Rounded.FormatListBulleted, "列表") }
                        Spacer(Modifier.weight(1f))
                        Text("${body.length} 字", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (discardDialog) {
        AlertDialog(
            onDismissRequest = { discardDialog = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, null) },
            title = { Text("放弃这封邮件？") },
            text = { Text("当前内容尚未发送。") },
            confirmButton = { Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("放弃") } },
            dismissButton = { TextButton(onClick = { discardDialog = false }) { Text("继续编辑") } }
        )
    }
}

@Composable
private fun ComposeLine(label: String, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(62.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        content()
    }
}

@Composable
private fun ComposeRecipientField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Email,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.padding(start = 14.dp).width(62.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        BasicComposeField(value, onValue, Modifier.weight(1f), keyboardType)
        trailing?.invoke()
    }
}

@Composable
private fun BasicComposeField(value: String, onValue: (String) -> Unit, modifier: Modifier, keyboardType: KeyboardType) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValue,
        modifier = modifier.padding(vertical = 12.dp, horizontal = 4.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = keyboardType != KeyboardType.Text,
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
    )
}

private fun queryAttachment(cursor: Cursor?): Pair<String, Long> {
    if (cursor == null) return "附件" to 0L
    return cursor.use {
        if (!it.moveToFirst()) return@use "附件" to 0L
        val name = it.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { index -> index >= 0 }?.let(it::getString) ?: "附件"
        val size = it.getColumnIndex(OpenableColumns.SIZE).takeIf { index -> index >= 0 && !it.isNull(index) }?.let(it::getLong) ?: 0L
        name to size
    }
}

private fun tomorrowMorning(): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, 1)
    set(Calendar.HOUR_OF_DAY, 8)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
