package com.dormantxxy.glassmail.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.dormantxxy.glassmail.ui.AppViewModel
import com.dormantxxy.glassmail.ui.components.IOSLargeTitle
import com.dormantxxy.glassmail.ui.components.IOSSectionLabel
import com.dormantxxy.glassmail.ui.components.LiquidCircleButton
import com.dormantxxy.glassmail.ui.components.LiquidPillButton
import com.dormantxxy.glassmail.ui.theme.IOSBlue
import com.dormantxxy.glassmail.ui.theme.IOSRed

@Composable
fun AccountScreen(accountId: String, viewModel: AppViewModel, onBack: () -> Unit) {
    val accounts by viewModel.accounts.collectAsState()
    val account = accounts.firstOrNull { it.id == accountId }
    var displayName by remember(account) { mutableStateOf(account?.displayName.orEmpty()) }
    var signature by remember(account) { mutableStateOf(account?.signature.orEmpty()) }
    var interval by remember(account) { mutableFloatStateOf((account?.syncIntervalMinutes ?: 15).toFloat()) }
    var enabled by remember(account) { mutableStateOf(account?.enabled ?: true) }
    var deleting by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        contentPadding = PaddingValues(bottom = 42.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Spacer(Modifier.statusBarsPadding())
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                LiquidCircleButton(Icons.Rounded.ChevronLeft, "返回", onBack)
                Spacer(Modifier.weight(1f))
                LiquidPillButton("完成", {
                    account?.let { viewModel.updateAccount(it.copy(displayName = displayName.trim().ifBlank { it.displayName }, signature = signature, syncIntervalMinutes = interval.toInt(), enabled = enabled)) }
                    onBack()
                }, tint = IOSBlue)
            }
            IOSLargeTitle("账户", account?.email.orEmpty(), Modifier.padding(top = 8.dp, bottom = 8.dp))
        }
        if (account == null) {
            item { Text("账户不存在", Modifier.padding(20.dp)) }
        } else {
            item {
                IOSSectionLabel("账户信息")
                AccountGroup {
                    AccountField("名称", displayName, { displayName = it })
                    AccountDivider()
                    AccountValue("电子邮件", account.email)
                    AccountDivider()
                    AccountValue("描述", account.provider.label)
                }
            }
            item {
                IOSSectionLabel("发件设置")
                AccountGroup {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                        Text("签名", Modifier.width(88.dp))
                        BasicTextField(
                            signature,
                            { signature = it },
                            Modifier.weight(1f).heightIn(min = 76.dp),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(IOSBlue)
                        )
                    }
                }
            }
            item {
                IOSSectionLabel("同步")
                AccountGroup {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("启用此账户", Modifier.weight(1f)); Switch(enabled, { enabled = it })
                    }
                    AccountDivider()
                    Text("获取新数据：每 ${interval.toInt()} 分钟", Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                    Slider(interval, { interval = it }, Modifier.padding(horizontal = 16.dp), enabled = enabled && !account.isDemo, valueRange = 15f..120f, steps = 6)
                    Spacer(Modifier.height(4.dp))
                }
            }
            item {
                IOSSectionLabel("服务器")
                AccountGroup {
                    AccountValue("IMAP", "${account.imapHost}:${account.imapPort}")
                    AccountDivider(); AccountValue("SMTP", "${account.smtpHost}:${account.smtpPort}")
                    AccountDivider(); AccountValue("用户名", account.username)
                }
            }
            item {
                AccountGroup {
                    Row(Modifier.fillMaxWidth().clickable { deleting = true }.padding(15.dp), horizontalArrangement = Arrangement.Center) {
                        Text("删除账户", color = IOSRed, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    if (account != null && deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            icon = { Icon(Icons.Rounded.Warning, null, tint = IOSRed) },
            title = { Text("删除账户？") },
            text = { Text("将从此设备移除 ${account.email} 的邮件和账户信息。服务器上的邮件不会被删除。") },
            confirmButton = { TextButton(onClick = { viewModel.deleteAccount(account.id); deleting = false; onBack() }) { Text("删除", color = IOSRed) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } }
        )
    }
}

@Composable private fun AccountGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)), content = content)
}

@Composable private fun AccountDivider() { HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant) }

@Composable private fun AccountValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)) {
        Text(label, Modifier.width(88.dp)); Text(value, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun AccountField(label: String, value: String, onValue: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(88.dp))
        BasicTextField(value, onValue, Modifier.weight(1f), textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(IOSBlue))
    }
}
