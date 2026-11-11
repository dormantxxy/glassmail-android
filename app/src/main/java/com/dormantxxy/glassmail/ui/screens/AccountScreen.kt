package com.dormantxxy.glassmail.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.dormantxxy.glassmail.ui.AppViewModel
import com.dormantxxy.glassmail.ui.components.AuroraBackground
import com.dormantxxy.glassmail.ui.components.GlassSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(accountId: String, viewModel: AppViewModel, onBack: () -> Unit) {
    val accounts by viewModel.accounts.collectAsState()
    val source = accounts.firstOrNull { it.id == accountId }
    var displayName by remember(source) { mutableStateOf(source?.displayName.orEmpty()) }
    var signature by remember(source) { mutableStateOf(source?.signature.orEmpty()) }
    var interval by remember(source) { mutableFloatStateOf((source?.syncIntervalMinutes ?: 15).toFloat()) }
    var enabled by remember(source) { mutableStateOf(source?.enabled ?: true) }
    var deleting by remember { mutableStateOf(false) }

    AuroraBackground(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("账户设置") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") } },
                    actions = {
                        TextButton(
                            onClick = {
                                source?.let { viewModel.updateAccount(it.copy(displayName = displayName.trim().ifBlank { it.displayName }, signature = signature, syncIntervalMinutes = interval.toInt(), enabled = enabled)) }
                                onBack()
                            },
                            enabled = source != null
                        ) { Text("保存") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .82f))
                )
            }
        ) { padding ->
            if (source == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("账户不存在") }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 40.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 22.dp, contentPadding = PaddingValues(16.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(source.email, style = MaterialTheme.typography.titleLarge)
                                Text(source.provider.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedTextField(displayName, { displayName = it }, Modifier.fillMaxWidth(), label = { Text("显示名称") }, leadingIcon = { Icon(Icons.Rounded.Person, null) }, singleLine = true)
                                OutlinedTextField(signature, { signature = it }, Modifier.fillMaxWidth().heightIn(min = 110.dp), label = { Text("邮件签名") }, leadingIcon = { Icon(Icons.Rounded.Draw, null) })
                            }
                        }
                    }
                    item {
                        GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 22.dp, contentPadding = PaddingValues(16.dp)) {
                            Column {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { Text("启用后台同步"); Text("关闭后仍可手动刷新", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    Switch(enabled, { enabled = it })
                                }
                                Spacer(Modifier.height(16.dp))
                                Text("同步间隔：${interval.toInt()} 分钟")
                                Slider(interval, { interval = it }, valueRange = 15f..120f, steps = 6, enabled = enabled && !source.isDemo)
                            }
                        }
                    }
                    item {
                        GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 22.dp, contentPadding = PaddingValues(16.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("服务器", style = MaterialTheme.typography.titleMedium)
                                ServerValue("IMAP", "${source.imapHost}:${source.imapPort} · ${source.imapSecurity.name}")
                                ServerValue("SMTP", "${source.smtpHost}:${source.smtpPort} · ${source.smtpSecurity.name}")
                                ServerValue("登录名", source.username)
                                Text("密码和令牌已由 Android Keystore 加密，无法在此显示。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = { deleting = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Icon(Icons.Rounded.DeleteForever, null); Spacer(Modifier.width(8.dp)); Text("删除此账户") }
                    }
                }
            }
        }
    }

    if (source != null && deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            icon = { Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("删除 ${source.email}？") },
            text = { Text("将删除此账户的本地邮件缓存和加密凭据，不会删除服务器上的邮件。") },
            confirmButton = { Button(onClick = { viewModel.deleteAccount(source.id); deleting = false; onBack() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("删除账户") } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun ServerValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.width(62.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f))
    }
}
