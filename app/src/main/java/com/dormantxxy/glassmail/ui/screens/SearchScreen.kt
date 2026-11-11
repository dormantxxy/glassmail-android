package com.dormantxxy.glassmail.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dormantxxy.glassmail.ui.AppViewModel
import com.dormantxxy.glassmail.ui.components.AuroraBackground
import com.dormantxxy.glassmail.ui.components.EmptyPane
import com.dormantxxy.glassmail.ui.components.MailMessageRow

@OptIn(ExperimentalMaterial3Api::class)
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

    AuroraBackground(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        TextField(
                            value = query,
                            onValueChange = { query = it; viewModel.setSearchQuery(it) },
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            placeholder = { Text("搜索发件人、主题或正文") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            trailingIcon = if (query.isNotBlank()) ({ IconButton(onClick = { query = ""; viewModel.setSearchQuery("") }) { Icon(Icons.Rounded.Clear, "清除") } }) else null,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = MaterialTheme.shapes.extraLarge
                        )
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .85f))
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                LazyRow(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { FilterChip(unreadOnly, { unreadOnly = !unreadOnly }, { Text("未读") }) }
                    item { FilterChip(starredOnly, { starredOnly = !starredOnly }, { Text("星标") }) }
                    item { FilterChip(attachmentsOnly, { attachmentsOnly = !attachmentsOnly }, { Text("有附件") }) }
                }
                when {
                    query.isBlank() -> EmptyPane("搜索邮件", "输入关键词可搜索已同步到本机的邮件", Modifier.fillMaxSize(), icon = Icons.Rounded.Search)
                    filtered.isEmpty() -> EmptyPane("没有找到结果", "尝试缩短关键词或关闭筛选条件", Modifier.fillMaxSize(), icon = Icons.Rounded.Search)
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
                        item { Text("${filtered.size} 个结果", Modifier.padding(horizontal = 16.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(filtered, key = { it.id }) { message ->
                            MailMessageRow(
                                message = message,
                                compact = prefs.compactRows,
                                onClick = { onOpen(message.id) },
                                onLongClick = { onOpen(message.id) },
                                onToggleStar = { viewModel.toggleStar(message) }
                            )
                            HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .16f))
                        }
                    }
                }
            }
        }
    }
}
