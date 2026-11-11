package com.dormantxxy.glassmail.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dormantxxy.glassmail.data.model.*
import com.dormantxxy.glassmail.ui.AppViewModel
import com.dormantxxy.glassmail.ui.components.IOSSectionLabel
import com.dormantxxy.glassmail.ui.components.LiquidCircleButton
import com.dormantxxy.glassmail.ui.theme.IOSBlue

@Composable
fun OnboardingScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf<MailProvider?>(null) }
    if (selected == null) {
        WelcomeToMail(modifier) { selected = it }
    } else {
        AccountSetupScreen(
            viewModel = viewModel,
            initialProvider = selected,
            onBack = { selected = null }
        )
    }
}

@Composable
private fun WelcomeToMail(modifier: Modifier, onProvider: (MailProvider) -> Unit) {
    val choices = listOf(
        MailProvider.ICLOUD to "iCloud",
        MailProvider.OUTLOOK to "Microsoft Exchange / Outlook",
        MailProvider.GMAIL to "Google",
        MailProvider.YAHOO to "Yahoo!",
        MailProvider.QQ to "QQ 邮箱",
        MailProvider.NETEASE to "网易邮箱",
        MailProvider.CUSTOM to "其他"
    )
    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(54.dp))
        Box(Modifier.size(78.dp).clip(RoundedCornerShape(18.dp)).background(IOSBlue), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Mail, null, Modifier.size(48.dp), tint = Color.White)
        }
        Spacer(Modifier.height(24.dp))
        Text("欢迎使用“邮件”", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(32.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            choices.forEachIndexed { index, (provider, label) ->
                Row(
                    Modifier.fillMaxWidth().clickable { onProvider(provider) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProviderGlyph(provider)
                    Spacer(Modifier.width(13.dp))
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (index != choices.lastIndex) HorizontalDivider(Modifier.padding(start = 58.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        Spacer(Modifier.weight(1f))
        Text("邮件账户信息仅保存在此设备上", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ProviderGlyph(provider: MailProvider) {
    val (icon, color) = when (provider) {
        MailProvider.ICLOUD -> Icons.Rounded.Cloud to Color(0xFF0A84FF)
        MailProvider.OUTLOOK -> Icons.Rounded.Business to Color(0xFF0078D4)
        MailProvider.GMAIL -> Icons.Rounded.Mail to Color(0xFFEA4335)
        MailProvider.YAHOO -> Icons.Rounded.AlternateEmail to Color(0xFF7B3FE4)
        MailProvider.QQ -> Icons.Rounded.Chat to Color(0xFF12B7F5)
        MailProvider.NETEASE -> Icons.Rounded.Email to Color(0xFFD33A2C)
        MailProvider.CUSTOM -> Icons.Rounded.MoreHoriz to Color(0xFF8E8E93)
    }
    Box(Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(19.dp), tint = Color.White)
    }
}

@Composable
fun AccountSetupScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onDone: (() -> Unit)? = null,
    initialProvider: MailProvider? = null
) {
    val busy by viewModel.setupBusy.collectAsState()
    var provider by rememberSaveable { mutableStateOf(initialProvider) }
    if (provider == null) {
        WelcomeToMail(Modifier) { provider = it }
        return
    }
    AccountForm(
        provider = provider!!,
        busy = busy,
        onBack = { if (initialProvider == null) provider = null else onBack() },
        onSubmit = { setup -> viewModel.addAccount(setup) { (onDone ?: onBack).invoke() } }
    )
}

@Composable
private fun AccountForm(provider: MailProvider, busy: Boolean, onBack: () -> Unit, onSubmit: (AccountSetup) -> Unit) {
    val firstPreset = remember(provider) { ProviderPresets.forProvider(provider) }
    var email by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var usernameEdited by rememberSaveable { mutableStateOf(false) }
    var secret by rememberSaveable { mutableStateOf("") }
    var showSecret by rememberSaveable { mutableStateOf(false) }
    var auth by rememberSaveable { mutableStateOf(AuthType.PASSWORD) }
    var advanced by rememberSaveable { mutableStateOf(provider == MailProvider.CUSTOM) }
    var imapHost by rememberSaveable { mutableStateOf(firstPreset.imapHost) }
    var imapPort by rememberSaveable { mutableStateOf(firstPreset.imapPort.toString()) }
    var imapSecurity by rememberSaveable { mutableStateOf(firstPreset.imapSecurity) }
    var smtpHost by rememberSaveable { mutableStateOf(firstPreset.smtpHost) }
    var smtpPort by rememberSaveable { mutableStateOf(firstPreset.smtpPort.toString()) }
    var smtpSecurity by rememberSaveable { mutableStateOf(firstPreset.smtpSecurity) }
    var validation by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(email) {
        if (provider == MailProvider.NETEASE) {
            val p = ProviderPresets.forProvider(provider, email)
            imapHost = p.imapHost
            smtpHost = p.smtpHost
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        LazyColumn(
            Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(top = 112.dp, bottom = 44.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Text("新账户", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(3.dp))
                Text(provider.label, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                IOSSectionLabel("账户信息")
                SettingsGroup {
                    IOSInputRow("名称", name, { name = it }, "你的姓名")
                    GroupDivider()
                    IOSInputRow("电子邮件", email, {
                        email = it
                        if (!usernameEdited) username = it
                    }, "name@example.com", KeyboardType.Email)
                    GroupDivider()
                    IOSInputRow(
                        if (auth == AuthType.PASSWORD) "密码" else "令牌",
                        secret,
                        { secret = it },
                        if (auth == AuthType.PASSWORD) "必填" else "OAuth2 访问令牌",
                        KeyboardType.Password,
                        if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                        trailing = {
                            IconButton(onClick = { showSecret = !showSecret }, Modifier.size(32.dp)) {
                                Icon(if (showSecret) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                }
                Text(firstPreset.help, Modifier.padding(horizontal = 36.dp, vertical = 7.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                IOSSectionLabel("认证")
                SettingsGroup {
                    ChoiceRow("密码 / 应用专用密码", auth == AuthType.PASSWORD) { auth = AuthType.PASSWORD }
                    GroupDivider()
                    ChoiceRow("XOAUTH2", auth == AuthType.XOAUTH2) { auth = AuthType.XOAUTH2 }
                }
            }
            item {
                SettingsGroup {
                    Row(
                        Modifier.fillMaxWidth().clickable { advanced = !advanced }.padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("高级", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = IOSBlue)
                        Icon(if (advanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (advanced) {
                item {
                    IOSSectionLabel("收件服务器")
                    SettingsGroup {
                        IOSInputRow("主机名", imapHost, { imapHost = it }, "imap.example.com")
                        GroupDivider(); IOSInputRow("用户名", username, { username = it; usernameEdited = true }, email)
                        GroupDivider(); IOSInputRow("端口", imapPort, { imapPort = it.filter(Char::isDigit) }, "993", KeyboardType.Number)
                        GroupDivider(); SecurityRow(imapSecurity) { imapSecurity = it }
                    }
                }
                item {
                    IOSSectionLabel("发件服务器")
                    SettingsGroup {
                        IOSInputRow("主机名", smtpHost, { smtpHost = it }, "smtp.example.com")
                        GroupDivider(); IOSInputRow("端口", smtpPort, { smtpPort = it.filter(Char::isDigit) }, "587", KeyboardType.Number)
                        GroupDivider(); SecurityRow(smtpSecurity) { smtpSecurity = it }
                    }
                }
            }
            if (validation.isNotBlank()) item {
                Text(validation, Modifier.padding(horizontal = 22.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            item {
                Button(
                    onClick = {
                        validation = when {
                            !email.contains('@') -> "请输入有效的电子邮件地址。"
                            secret.isBlank() -> "请输入密码、应用专用密码或令牌。"
                            imapHost.isBlank() || smtpHost.isBlank() -> "服务器地址不能为空。"
                            imapPort.toIntOrNull() == null || smtpPort.toIntOrNull() == null -> "端口不正确。"
                            else -> ""
                        }
                        if (validation.isBlank()) onSubmit(
                            AccountSetup(provider, email, name, username.ifBlank { email }, secret, auth, imapHost, imapPort.toInt(), imapSecurity, smtpHost, smtpPort.toInt(), smtpSecurity)
                        )
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(50.dp),
                    shape = CircleShape
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("下一步", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiquidCircleButton(Icons.Rounded.ArrowBack, "返回", onBack)
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface),
        content = content
    )
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun IOSInputRow(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    transformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(92.dp), style = MaterialTheme.typography.bodyLarge)
        BasicTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = transformation,
            cursorBrush = SolidColor(IOSBlue),
            decorationBox = { inner ->
                if (value.isBlank()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f))
                inner()
            }
        )
        trailing?.invoke()
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (selected) Icon(Icons.Rounded.Check, null, tint = IOSBlue)
    }
}

@Composable
private fun SecurityRow(value: SecurityType, onValue: (SecurityType) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("安全性", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(2.dp)) {
            listOf(SecurityType.SSL_TLS to "SSL/TLS", SecurityType.STARTTLS to "STARTTLS").forEach { (type, label) ->
                Text(
                    label,
                    Modifier.clip(CircleShape).background(if (value == type) MaterialTheme.colorScheme.surface else Color.Transparent).clickable { onValue(type) }.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
