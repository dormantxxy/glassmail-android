package com.dormantxxy.glassmail.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dormantxxy.glassmail.data.model.MailMessage
import com.dormantxxy.glassmail.ui.theme.IOSBlue
import com.dormantxxy.glassmail.ui.theme.IOSOrange
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
    val emphasis = if (message.isRead) FontWeight.Normal else FontWeight.SemiBold
    Row(
        modifier
            .fillMaxWidth()
            .background(if (selected) IOSBlue.copy(alpha = .12f) else MaterialTheme.colorScheme.background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 12.dp, end = 12.dp, top = if (compact) 9.dp else 11.dp, bottom = if (compact) 9.dp else 11.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.padding(top = 1.dp)) {
            SenderAvatar(message.from.name, message.from.address, size = if (compact) 38.dp else 42.dp)
            if (!message.isRead) {
                Box(
                    Modifier.size(10.dp).align(Alignment.TopEnd).background(MaterialTheme.colorScheme.background, CircleShape).padding(2.dp).background(IOSBlue, CircleShape)
                )
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    message.from.name.ifBlank { message.from.address },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = emphasis,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(formatMailDate(message.receivedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(1.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(message.subject, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = emphasis, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (message.hasAttachments) Icon(Icons.Rounded.AttachFile, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (message.isStarred) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Rounded.Flag,
                        "取消旗标",
                        Modifier.size(16.dp).clickable(onClick = onToggleStar),
                        tint = IOSOrange
                    )
                }
            }
            if (!compact) {
                Spacer(Modifier.height(1.dp))
                Text(
                    message.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
