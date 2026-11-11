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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dormantxxy.glassmail.data.model.LocalAttachment
import com.dormantxxy.glassmail.ui.AppViewModel
import com.dormantxxy.glassmail.ui.components.LiquidCircleButton
import com.dormantxxy.glassmail.ui.components.LiquidPillButton
import com.dormantxxy.glassmail.ui.theme.IOSBlue
import com.dormantxxy.glassmail.ui.theme.IOSRed
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
    var accountSheet by remember { mutableStateOf(false) }
    var scheduleSheet by remember { mutableStateOf(false) }
    var cancelSheet by remember { mutableStateOf(false) }
    val attachments = remember { mutableStateListOf<LocalAttachment>() }

    LaunchedEffect(seed) {
        accountId = seed.accountId.ifBlank { accounts.firstOrNull()?.id.orEmpty() }
        to = seed.to
        cc = seed.cc
        subject = seed.subject
        body = seed.body
        showCc = seed.cc.isNotBlank()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val (name, size) = queryAttachment(context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null))
            attachments += LocalAttachment(uri.toString(), name, context.contentResolver.getType(uri) ?: "application/octet-stream", size)
        }
    }

    val hasContent = to.isNotBlank() || cc.isNotBlank() || subject.isNotBlank() || body.isNotBlank() || attachments.isNotEmpty()
    fun cancel() { if (hasContent) cancelSheet = true else onBack() }
    fun send(at: Long = 0L) { viewModel.send(accountId, to, cc, bcc, subject, body, attachments.toList(), at, onBack) }
    BackHandler(onBack = ::cancel)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        Spacer(Modifier.statusBarsPadding())
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            LiquidPillButton("取消", ::cancel, tint = IOSBlue)
            Text("新邮件", Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            LiquidCircleButton(Icons.Rounded.ArrowUpward, "发送", { send() }, enabled = to.isNotBlank() && accountId.isNotBlank(), tint = IOSBlue)
            Box {
                IconButton(onClick = { scheduleSheet = true }, Modifier.size(28.dp)) { Icon(Icons.Rounded.ArrowDropDown, "发送选项", tint = IOSBlue) }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        ComposeHeaderRow("发件人", accounts.firstOrNull { it.id == accountId }?.email.orEmpty(), onClick = { accountSheet = true }, trailing = {
            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        })
        ComposeDivider()
        ComposeFieldRow("收件人", to, { to = it }, KeyboardType.Email) {
            Text("抄送/密送", Modifier.clickable { showCc = !showCc }.padding(8.dp), style = MaterialTheme.typography.labelMedium, color = IOSBlue)
        }
        if (showCc) {
            ComposeDivider(); ComposeFieldRow("抄送", cc, { cc = it }, KeyboardType.Email)
            ComposeDivider(); ComposeFieldRow("密送", bcc, { bcc = it }, KeyboardType.Email)
        }
        ComposeDivider()
        ComposeFieldRow("主题", subject, { subject = it }, KeyboardType.Text)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (attachments.isNotEmpty()) {
            LazyRow(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(attachments, key = { it.uri }) { attachment ->
                    Row(
                        Modifier.height(38.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).padding(start = 11.dp, end = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.InsertDriveFile, null, Modifier.size(17.dp), tint = IOSBlue)
                        Spacer(Modifier.width(6.dp)); Text(attachment.name, Modifier.widthIn(max = 160.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { attachments.remove(attachment) }, Modifier.size(28.dp)) { Icon(Icons.Rounded.Close, "移除", Modifier.size(16.dp)) }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        BasicTextField(
            value = body,
            onValueChange = { body = it },
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 14.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            cursorBrush = SolidColor(IOSBlue),
            decorationBox = { inner ->
                if (body.isBlank()) Text("", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                inner()
            }
        )

        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(50.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .94f)).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { picker.launch(arrayOf("image/*")) }) { Icon(Icons.Rounded.Image, "照片", tint = IOSBlue) }
            IconButton(onClick = { picker.launch(arrayOf("*/*")) }) { Icon(Icons.Rounded.AttachFile, "附件", tint = IOSBlue) }
            IconButton(onClick = { body += "\n" }) { Text("Aa", color = IOSBlue, style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (accountSheet) {
        ModalBottomSheet(onDismissRequest = { accountSheet = false }, containerColor = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
                Text("发件账户", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(8.dp))
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))) {
                    accounts.forEachIndexed { index, account ->
                        Row(Modifier.fillMaxWidth().clickable { accountId = account.id; accountSheet = false }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(account.displayName); Text(account.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (account.id == accountId) Icon(Icons.Rounded.Check, null, tint = IOSBlue)
                        }
                        if (index != accounts.lastIndex) HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    if (scheduleSheet) {
        ModalBottomSheet(onDismissRequest = { scheduleSheet = false }, containerColor = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
                Text("发送时间", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(8.dp))
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))) {
                    ScheduleRow("现在发送", Icons.Rounded.Send) { scheduleSheet = false; send() }
                    ComposeDivider(); ScheduleRow("一小时后发送", Icons.Rounded.Schedule) { scheduleSheet = false; send(System.currentTimeMillis() + 60 * 60 * 1000L) }
                    ComposeDivider(); ScheduleRow("明天上午 8:00", Icons.Rounded.WbSunny) { scheduleSheet = false; send(tomorrowMorning()) }
                }
            }
        }
    }

    if (cancelSheet) {
        ModalBottomSheet(onDismissRequest = { cancelSheet = false }, containerColor = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))) {
                    Row(Modifier.fillMaxWidth().clickable { cancelSheet = false }.padding(16.dp), horizontalArrangement = Arrangement.Center) { Text("继续编辑", color = IOSBlue) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().clickable(onClick = onBack).padding(16.dp), horizontalArrangement = Arrangement.Center) { Text("删除草稿", color = IOSRed) }
                }
                TextButton(onClick = { cancelSheet = false }, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))) { Text("取消", color = IOSBlue) }
            }
        }
    }
}

@Composable
private fun ComposeHeaderRow(label: String, value: String, onClick: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(70.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

@Composable
private fun ComposeFieldRow(label: String, value: String, onValue: (String) -> Unit, keyboardType: KeyboardType, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.padding(start = 14.dp).width(70.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        BasicTextField(
            value,
            onValue,
            Modifier.weight(1f).padding(vertical = 12.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            singleLine = keyboardType != KeyboardType.Text,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            cursorBrush = SolidColor(IOSBlue)
        )
        trailing?.invoke()
    }
}

@Composable private fun ComposeDivider() { HorizontalDivider(Modifier.padding(start = 84.dp), color = MaterialTheme.colorScheme.outlineVariant) }

@Composable
private fun ScheduleRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge); Icon(icon, null, tint = IOSBlue)
    }
}

private fun queryAttachment(cursor: Cursor?): Pair<String, Long> {
    if (cursor == null) return "附件" to 0L
    return cursor.use {
        if (!it.moveToFirst()) return@use "附件" to 0L
        val name = it.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { i -> i >= 0 }?.let(it::getString) ?: "附件"
        val size = it.getColumnIndex(OpenableColumns.SIZE).takeIf { i -> i >= 0 && !it.isNull(i) }?.let(it::getLong) ?: 0L
        name to size
    }
}

private fun tomorrowMorning(): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 8); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
