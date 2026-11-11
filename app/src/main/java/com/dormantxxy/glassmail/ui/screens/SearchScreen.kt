package com.dormantxxy.glassmail.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dormantxxy.glassmail.ui.AppViewModel
import com.dormantxxy.glassmail.ui.components.*
import com.dormantxxy.glassmail.ui.theme.IOSBlue

@Composable
fun SearchScreen(viewModel: AppViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val results by viewModel.searchResults.collectAsState()
    val prefs by viewModel.preferences.collectAsState()
    var query by remember { mutableStateOf("") }
    var unreadOnly by remember { mutableStateOf(false) }
    var starredOnly by remember { mutableStateOf(false) }
    var attachmentsOnly by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val filtered = remember(results, unreadOnly, starredOnly, attachmentsOnly) {
        results.filter { (!unreadOnly || !it.isRead) && (!starredOnly || it.isStarred) && (!attachmentsOnly || it.hasAttachments) }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            Spacer(Modifier.statusBarsPadding())
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp)) {
                LiquidCircleButton(Icons.Rounded.ChevronLeft, "返回", onBack)
                Spacer(Modifier.weight(1f))
            }
            IOSLargeTitle("搜索", modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(7.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it; viewModel.setSearchQuery(it) },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    cursorBrush = SolidColor(IOSBlue),
                    decorationBox = { inner ->
                        if (query.isBlank()) Text("搜索", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        inner()
                    }
                )
                if (query.isNotBlank()) IconButton(onClick = { query = ""; viewModel.setSearchQuery("") }, Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.Cancel, "清除", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyRow(
                Modifier.fillMaxWidth().padding(vertical = 9.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { IOSFilter("未读", unreadOnly) { unreadOnly = !unreadOnly } }
                item { IOSFilter("有旗标", starredOnly) { starredOnly = !starredOnly } }
                item { IOSFilter("附件", attachmentsOnly) { attachmentsOnly = !attachmentsOnly } }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        when {
            query.isBlank() -> item { EmptyPane("搜索邮件", "输入发件人、主题或邮件内容", Modifier.fillParentMaxHeight(.72f), Icons.Rounded.Search) }
            filtered.isEmpty() -> item { EmptyPane("未找到邮件", "请尝试其他关键词", Modifier.fillParentMaxHeight(.72f), Icons.Rounded.Search) }
            else -> {
                item { Text("找到 ${filtered.size} 封邮件", Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .62f)).padding(horizontal = 15.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(filtered, key = { it.id }) { message ->
                    MailMessageRow(message, prefs.compactRows, onClick = { onOpen(message.id) }, onLongClick = { onOpen(message.id) }, onToggleStar = { viewModel.toggleStar(message) })
                    HorizontalDivider(Modifier.padding(start = 65.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun IOSFilter(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) IOSBlue else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface,
        shape = CircleShape
    ) { Text(label, Modifier.padding(horizontal = 14.dp, vertical = 7.dp), style = MaterialTheme.typography.labelLarge) }
}
