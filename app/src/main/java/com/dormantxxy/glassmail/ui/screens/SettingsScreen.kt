package com.dormantxxy.glassmail.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dormantxxy.glassmail.ui.AppViewModel
import com.dormantxxy.glassmail.ui.components.IOSLargeTitle
import com.dormantxxy.glassmail.ui.components.IOSSectionLabel
import com.dormantxxy.glassmail.ui.components.LiquidCircleButton
import com.dormantxxy.glassmail.ui.theme.IOSBlue

@Composable
fun SettingsScreen(viewModel: AppViewModel, onBack: () -> Unit, onAddAccount: () -> Unit, onAccount: (String) -> Unit) {
    val accounts by viewModel.accounts.collectAsState()
    val prefs by viewModel.preferences.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Spacer(Modifier.statusBarsPadding())
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp)) {
                LiquidCircleButton(Icons.Rounded.ChevronLeft, "返回", onBack)
            }
            IOSLargeTitle("邮件", modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        }
        item {
            IOSSectionLabel("邮件账户")
            IOSSettingsGroup {
                accounts.forEachIndexed { index, account ->
                    SettingsLine(
                        title = account.displayName,
                        subtitle = account.email,
                        onClick = { onAccount(account.id) },
                        trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    )
                    if (index != accounts.lastIndex) SettingsDivider()
                }
                if (accounts.isNotEmpty()) SettingsDivider()
                SettingsLine("添加账户", color = IOSBlue, onClick = onAddAccount, trailing = { Icon(Icons.Rounded.Add, null, tint = IOSBlue) })
            }
        }
        item {
            IOSSectionLabel("邮件列表")
            IOSSettingsGroup {
                SettingsLine("预览", if (prefs.compactRows) "1 行" else "2 行", onClick = { viewModel.setCompactRows(!prefs.compactRows) }, trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) })
                SettingsDivider()
                SettingsSwitch("按类别显示", prefs.categoriesEnabled, viewModel::setCategories)
                SettingsDivider()
                SettingsSwitch("删除前询问", prefs.confirmDelete, viewModel::setConfirmDelete)
            }
        }
        item {
            IOSSectionLabel("邮件")
            IOSSettingsGroup {
                SettingsSwitch("载入远程图像", prefs.remoteImagesEnabled, viewModel::setRemoteImages)
            }
            Text("关闭远程图像可减少发件人跟踪邮件打开情况。", Modifier.padding(horizontal = 32.dp, vertical = 7.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            IOSSectionLabel("发送")
            IOSSettingsGroup {
                SettingsLine("撤销发送延迟", "${prefs.undoSendSeconds} 秒", trailing = {})
                Slider(
                    value = prefs.undoSendSeconds.toFloat(),
                    onValueChange = { viewModel.setUndoSend(it.toInt()) },
                    valueRange = 0f..15f,
                    steps = 14,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(5.dp))
            }
        }
    }
}

@Composable
private fun IOSSettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)), content = content)
}

@Composable
private fun SettingsLine(
    title: String,
    subtitle: String = "",
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun SettingsSwitch(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(value, onChange)
    }
}

@Composable private fun SettingsDivider() { HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant) }
