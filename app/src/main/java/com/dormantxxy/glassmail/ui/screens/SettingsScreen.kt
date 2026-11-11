package com.dormantxxy.glassmail.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dormantxxy.glassmail.BuildConfig
import com.dormantxxy.glassmail.data.ThemeMode
import com.dormantxxy.glassmail.ui.AppViewModel
import com.dormantxxy.glassmail.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
    onAccount: (String) -> Unit
) {
    val accounts by viewModel.accounts.collectAsState()
    val prefs by viewModel.preferences.collectAsState()
    val context = LocalContext.current
    var privacy by remember { mutableStateOf(false) }

    AuroraBackground(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("设置") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .82f))
                )
            }
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 38.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { SectionLabel("邮件账户") }
                item {
                    GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                        Column {
                            accounts.forEachIndexed { index, account ->
                                SettingRow(
                                    icon = if (account.isDemo) Icons.Rounded.Science else Icons.Rounded.AccountCircle,
                                    title = account.displayName,
                                    subtitle = account.email,
                                    trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    onClick = { onAccount(account.id) }
                                )
                                if (index != accounts.lastIndex) HorizontalDivider(Modifier.padding(start = 62.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .16f))
                            }
                            if (accounts.isNotEmpty()) HorizontalDivider(Modifier.padding(start = 62.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .16f))
                            SettingRow(Icons.Rounded.AddCircle, "添加账户", "IMAP、SMTP 或 XOAUTH2", onClick = onAddAccount)
                        }
                    }
                }
                item { SectionLabel("外观") }
                item {
                    GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                        Column {
                            SettingRow(Icons.Rounded.DarkMode, "主题", trailing = {
                                SingleChoiceSegmentedButtonRow {
                                    ThemeMode.entries.forEachIndexed { index, mode ->
                                        SegmentedButton(
                                            selected = prefs.themeMode == mode,
                                            onClick = { viewModel.setTheme(mode) },
                                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                                            label = { Text(when (mode) { ThemeMode.SYSTEM -> "自动"; ThemeMode.LIGHT -> "浅色"; ThemeMode.DARK -> "深色" }) }
                                        )
                                    }
                                }
                            })
                            SettingDivider()
                            SettingRow(Icons.Rounded.ViewAgenda, "紧凑邮件列表", "在一屏中显示更多邮件", trailing = { Switch(prefs.compactRows, viewModel::setCompactRows) })
                            SettingDivider()
                            SettingRow(Icons.Rounded.Category, "智能分类", "主要、交易、动态与推广", trailing = { Switch(prefs.categoriesEnabled, viewModel::setCategories) })
                        }
                    }
                }
                item { SectionLabel("隐私与操作") }
                item {
                    GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                        Column {
                            SettingRow(Icons.Rounded.Image, "显示远程图片", "关闭可阻止邮件追踪像素", trailing = { Switch(prefs.remoteImagesEnabled, viewModel::setRemoteImages) })
                            SettingDivider()
                            SettingRow(Icons.Rounded.Delete, "删除前确认", trailing = { Switch(prefs.confirmDelete, viewModel::setConfirmDelete) })
                            SettingDivider()
                            SettingRow(Icons.Rounded.Undo, "撤销发送", "发送后保留 ${prefs.undoSendSeconds} 秒")
                            Slider(
                                value = prefs.undoSendSeconds.toFloat(),
                                onValueChange = { viewModel.setUndoSend(it.toInt()) },
                                valueRange = 0f..15f,
                                steps = 14,
                                modifier = Modifier.padding(horizontal = 18.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
                item { SectionLabel("关于") }
                item {
                    GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                        Column {
                            SettingRow(Icons.Rounded.Security, "隐私说明", "无广告、无遥测、无中转服务器", onClick = { privacy = true })
                            SettingDivider()
                            SettingRow(Icons.Rounded.Code, "GitHub 源码", BuildConfig.RELEASE_REPOSITORY, trailing = { Icon(Icons.Rounded.OpenInNew, null) }) {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.RELEASE_REPOSITORY}"))) }
                            }
                            SettingDivider()
                            SettingRow(Icons.Rounded.Info, "澄邮 GlassMail", "版本 ${BuildConfig.VERSION_NAME}")
                        }
                    }
                }
                item {
                    Text(
                        "视觉灵感来自现代玻璃质感界面。本项目与 Apple Inc. 无关联，也不包含其代码或素材。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }

    if (privacy) {
        AlertDialog(
            onDismissRequest = { privacy = false },
            icon = { Icon(Icons.Rounded.PrivacyTip, null) },
            title = { Text("隐私优先") },
            text = { Text("凭据由 Android Keystore 加密。邮件只在本机与所配置的邮件服务器之间传输。HTML 脚本和文件访问始终禁用，远程图片默认关闭。删除账户会一并删除对应本地缓存和凭据。") },
            confirmButton = { TextButton(onClick = { privacy = false }) { Text("知道了") } }
        )
    }
}

@Composable
private fun SectionLabel(value: String) {
    Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 7.dp))
}

@Composable
private fun SettingDivider() {
    HorizontalDivider(Modifier.padding(start = 62.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .16f))
}
