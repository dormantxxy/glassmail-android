package com.dormantxxy.glassmail.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dormantxxy.glassmail.data.model.MailMessage
import com.dormantxxy.glassmail.ui.theme.MailBlue
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MailMessageRow(
    message: MailMessage,
    compact: Boolean,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleStar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val weight = if (message.isRead) FontWeight.Normal else FontWeight.Bold
    val background = when {
        selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = .72f)
        !message.isRead -> MaterialTheme.colorScheme.surface.copy(alpha = .8f)
        else -> Color.Transparent
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = if (compact) 9.dp else 13.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box {
            SenderAvatar(message.from.name, message.from.address, size = if (compact) 38.dp else 44.dp)
            if (!message.isRead) Box(Modifier.size(11.dp).align(Alignment.TopEnd).background(MailBlue, androidx.compose.foundation.shape.CircleShape))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    message.from.name.ifBlank { message.from.address },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = weight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(formatMailDate(message.receivedAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (message.importance > 0) {
                    Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.width(5.dp))
                }
                Text(message.subject, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (message.hasAttachments) Icon(Icons.Rounded.AttachFile, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!compact) {
                Spacer(Modifier.height(2.dp))
                Text(message.preview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onToggleStar, Modifier.size(36.dp)) {
            Icon(if (message.isStarred) Icons.Rounded.Star else Icons.Rounded.StarBorder, "星标", tint = if (message.isStarred) Color(0xFFFFB000) else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun formatMailDate(time: Long): String {
    val date = Date(time)
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { this.time = date }
    return when {
        now.get(Calendar.YEAR) == target.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR) -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        now.get(Calendar.YEAR) == target.get(Calendar.YEAR) -> SimpleDateFormat("M月d日", Locale.getDefault()).format(date)
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(date)
    }
}
