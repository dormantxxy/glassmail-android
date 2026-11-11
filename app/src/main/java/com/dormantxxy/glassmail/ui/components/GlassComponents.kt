package com.dormantxxy.glassmail.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dormantxxy.glassmail.ui.theme.MailBlue

@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .3f
    val base = MaterialTheme.colorScheme.background
    val blue = if (dark) Color(0xFF102B45) else Color(0xFFDDF2FF)
    val purple = if (dark) Color(0xFF251A3D) else Color(0xFFF0E7FF)
    Box(
        modifier.background(
            Brush.linearGradient(listOf(blue, base, purple, base))
        ),
        content = content
    )
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .3f
    val shape = RoundedCornerShape(cornerRadius)
    val surface = if (dark) Color(0xCC171C25) else Color(0xCFFFFFFF)
    val edge = if (dark) Color.White.copy(alpha = .12f) else Color.White.copy(alpha = .92f)
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(surface.copy(alpha = .96f), surface.copy(alpha = .76f))))
            .border(1.dp, edge, shape)
            .then(clickable)
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun CircleGlassButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    val background = MaterialTheme.colorScheme.surface.copy(alpha = .78f)
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .22f), CircleShape)
    ) { Icon(icon, description, tint = tint) }
}

@Composable
fun SenderAvatar(name: String, address: String, color: Color = MailBlue, size: Dp = 44.dp) {
    val label = name.trim().takeIf { it.isNotBlank() }?.take(1) ?: address.take(1).uppercase()
    val hue = ((name + address).hashCode().toUInt().toLong() % 5).toInt()
    val colors = listOf(Color(0xFF0A84FF), Color(0xFF5E5CE6), Color(0xFF30B0C7), Color(0xFFFF9F0A), Color(0xFFBF5AF2))
    Box(
        Modifier.size(size).clip(CircleShape).background(
            Brush.linearGradient(listOf(colors[hue].copy(alpha = .88f), color.copy(alpha = .72f)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CategoryPill(label: String, selected: Boolean, count: Int = 0, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .86f) else MaterialTheme.colorScheme.surface.copy(alpha = .54f)
    Surface(
        color = background,
        contentColor = color,
        shape = CircleShape,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            if (count > 0) {
                Spacer(Modifier.width(6.dp))
                Text(count.coerceAtMost(999).toString(), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun EmptyPane(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(7.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    trailing: @Composable RowScope.() -> Unit = {},
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        trailing()
    }
}

