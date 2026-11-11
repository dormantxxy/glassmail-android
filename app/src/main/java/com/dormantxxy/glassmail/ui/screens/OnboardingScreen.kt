package com.dormantxxy.glassmail.ui.screens

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dormantxxy.glassmail.data.model.*
import com.dormantxxy.glassmail.ui.AppViewModel
import com.dormantxxy.glassmail.ui.components.AuroraBackground
import com.dormantxxy.glassmail.ui.components.GlassSurface

@Composable
fun OnboardingScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    var setup by rememberSaveable { mutableStateOf(false) }
    AnimatedContent(setup, label = "onboarding") { showingSetup ->
        if (showingSetup) AccountSetupScreen(viewModel = viewModel, onBack = { setup = false })
        else WelcomeScreen(onAdd = { setup = true }, onDemo = viewModel::addDemo, modifier = modifier)
    }
}

@Composable
fun WelcomeScreen(onAdd: () -> Unit, onDemo: () -> Unit, modifier: Modifier = Modifier) {
    AuroraBackground(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(106.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)).background(
                    Brush.linearGradient(listOf(Color(0xFF73C8FF), Color(0xFF007AFF), Color(0xFF5E5CE6)))
                ),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Mail, null, Modifier.size(58.dp), tint = Color.White) }
            Spacer(Modifier.height(28.dp))
            Text("澄邮", style = MaterialTheme.typography.displaySmall)
            Text("GlassMail", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(14.dp))
            Text(
                "让每一封重要邮件都清晰可见。\n账户数据只保存在你的设备上。",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(36.dp))
            Button(onClick = onAdd, Modifier.fillMaxWidth().height(54.dp), shape = CircleShape) {
                Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("添加邮件账户")
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onDemo, Modifier.fillMaxWidth()) { Text("先体验演示模式") }
            Spacer(Modifier.height(20.dp))
            Text("支持 IMAP / SMTP · 无广告 · 无遥测", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSetupScreen(viewModel: AppViewModel, onBack: () -> Unit, onDone: (() -> Unit)? = null) {
    val busy by viewModel.setupBusy.collectAsState()
    var selected by rememberSaveable { mutableStateOf<MailProvider?>(null) }
    AuroraBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopAppBar(
                title = { Text(if (selected == null) "选择邮件服务" else selected!!.label) },
                navigationIcon = { IconButton(onClick = { if (selected == null) onBack() else selected = null }) { Icon(Icons.Rounded.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
            if (selected == null) ProviderPicker(onSelect = { selected = it })
            else AccountForm(
                provider = selected!!,
                busy = busy,
                onSubmit = { setup -> viewModel.addAccount(setup) { (onDone ?: onBack).invoke() } }
            )
        }
    }
}

@Composable
private fun ProviderPicker(onSelect: (MailProvider) -> Unit) {
    val providerIcons = mapOf(
        MailProvider.GMAIL to Icons.Rounded.MarkEmailUnread,
        MailProvider.OUTLOOK to Icons.Rounded.Business,
        MailProvider.YAHOO to Icons.Rounded.AlternateEmail,
        MailProvider.ICLOUD to Icons.Rounded.Cloud,
        MailProvider.QQ to Icons.Rounded.Chat,
        MailProvider.NETEASE to Icons.Rounded.Email,
        MailProvider.CUSTOM to Icons.Rounded.Dns
    )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 40.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item {
            Text("使用应用专用密码或授权码登录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        }
        items(MailProvider.entries) { provider ->
            GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 20.dp, onClick = { onSelect(provider) }) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProviderIcon(providerIcons.getValue(provider), provider.ordinal)
                    Spacer(Modifier.width(15.dp))
                    Column(Modifier.weight(1f)) {
                        Text(provider.label, style = MaterialTheme.typography.titleMedium)
                        Text(providerSubtitle(provider), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ProviderIcon(icon: ImageVector, index: Int) {
    val colors = listOf(Color(0xFFEA4335), Color(0xFF0078D4), Color(0xFF7B3FE4), Color(0xFF4FA7F8), Color(0xFF12B7F5), Color(0xFFD33A2C), Color(0xFF64748B))
    Box(Modifier.size(46.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(13.dp)).background(colors[index % colors.size]), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White)
    }
}

@Composable
private fun AccountForm(provider: MailProvider, busy: Boolean, onSubmit: (AccountSetup) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var usernameEdited by rememberSaveable { mutableStateOf(false) }
    var secret by rememberSaveable { mutableStateOf("") }
    var showSecret by rememberSaveable { mutableStateOf(false) }
    var auth by rememberSaveable { mutableStateOf(AuthType.PASSWORD) }
    var advanced by rememberSaveable { mutableStateOf(provider == MailProvider.CUSTOM) }
    var imapHost by rememberSaveable { mutableStateOf(ProviderPresets.forProvider(provider).imapHost) }
    var imapPort by rememberSaveable { mutableStateOf(ProviderPresets.forProvider(provider).imapPort.toString()) }
    var imapSecurity by rememberSaveable { mutableStateOf(ProviderPresets.forProvider(provider).imapSecurity) }
    var smtpHost by rememberSaveable { mutableStateOf(ProviderPresets.forProvider(provider).smtpHost) }
    var smtpPort by rememberSaveable { mutableStateOf(ProviderPresets.forProvider(provider).smtpPort.toString()) }
    var smtpSecurity by rememberSaveable { mutableStateOf(ProviderPresets.forProvider(provider).smtpSecurity) }
    var validation by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(email) {
        if (provider == MailProvider.NETEASE) {
            val preset = ProviderPresets.forProvider(provider, email)
            imapHost = preset.imapHost; smtpHost = preset.smtpHost
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 48.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            val help = ProviderPresets.forProvider(provider, email).help
            GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 17.dp, contentPadding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { MailField(email, { email = it; if (!usernameEdited) username = it }, "邮箱地址", Icons.Rounded.AlternateEmail, false) }
        item { MailField(name, { name = it }, "显示名称", Icons.Rounded.Person, false) }
        item { MailField(username, { username = it; usernameEdited = true }, "登录用户名", Icons.Rounded.AccountCircle, false) }
        item {
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (auth == AuthType.PASSWORD) "应用专用密码 / 授权码" else "OAuth2 访问令牌") },
                leadingIcon = { Icon(Icons.Rounded.Key, null) },
                trailingIcon = { IconButton(onClick = { showSecret = !showSecret }) { Icon(if (showSecret) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null) } },
                visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(auth == AuthType.PASSWORD, { auth = AuthType.PASSWORD }, { Text("密码/授权码") })
                FilterChip(auth == AuthType.XOAUTH2, { auth = AuthType.XOAUTH2 }, { Text("XOAUTH2") })
            }
        }
        item {
            TextButton(onClick = { advanced = !advanced }) {
                Icon(if (advanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                Text(if (advanced) "收起服务器设置" else "高级服务器设置")
            }
        }
        if (advanced) {
            item { ServerSection("接收服务器 (IMAP)", imapHost, { imapHost = it }, imapPort, { imapPort = it }, imapSecurity, { imapSecurity = it }) }
            item { ServerSection("发送服务器 (SMTP)", smtpHost, { smtpHost = it }, smtpPort, { smtpPort = it }, smtpSecurity, { smtpSecurity = it }) }
        }
        if (validation.isNotBlank()) item { Text(validation, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        item {
            Button(
                onClick = {
                    validation = when {
                        !email.contains('@') -> "请输入有效邮箱地址"
                        secret.isBlank() -> "请输入应用专用密码、授权码或令牌"
                        imapHost.isBlank() || smtpHost.isBlank() -> "请填写服务器地址"
                        imapPort.toIntOrNull() == null || smtpPort.toIntOrNull() == null -> "端口格式不正确"
                        else -> ""
                    }
                    if (validation.isBlank()) onSubmit(AccountSetup(provider, email, name, username.ifBlank { email }, secret, auth, imapHost, imapPort.toInt(), imapSecurity, smtpHost, smtpPort.toInt(), smtpSecurity))
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = CircleShape
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else { Icon(Icons.Rounded.Lock, null); Spacer(Modifier.width(8.dp)); Text("安全连接并添加") }
            }
        }
    }
}

@Composable
private fun MailField(value: String, onValue: (String) -> Unit, label: String, icon: ImageVector, secret: Boolean) {
    OutlinedTextField(
        value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth(), label = { Text(label) },
        leadingIcon = { Icon(icon, null) }, singleLine = true, shape = MaterialTheme.shapes.medium,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None
    )
}

@Composable
private fun ServerSection(title: String, host: String, onHost: (String) -> Unit, port: String, onPort: (String) -> Unit, security: SecurityType, onSecurity: (SecurityType) -> Unit) {
    GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 20.dp, contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            OutlinedTextField(host, onHost, Modifier.fillMaxWidth(), label = { Text("主机名") }, singleLine = true)
            OutlinedTextField(port, onPort, Modifier.fillMaxWidth(), label = { Text("端口") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(security == SecurityType.SSL_TLS, { onSecurity(SecurityType.SSL_TLS) }, { Text("SSL/TLS") })
                FilterChip(security == SecurityType.STARTTLS, { onSecurity(SecurityType.STARTTLS) }, { Text("STARTTLS") })
            }
        }
    }
}

private fun providerSubtitle(provider: MailProvider) = when (provider) {
    MailProvider.GMAIL -> "Gmail 与 Google Workspace"
    MailProvider.OUTLOOK -> "Outlook、Hotmail 与 Microsoft 365"
    MailProvider.YAHOO -> "Yahoo Mail"
    MailProvider.ICLOUD -> "iCloud Mail 与 me.com"
    MailProvider.QQ -> "QQ 邮箱授权码登录"
    MailProvider.NETEASE -> "163、126 与 yeah.net"
    MailProvider.CUSTOM -> "手动填写 IMAP / SMTP"
}
