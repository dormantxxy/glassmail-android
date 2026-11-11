package com.dormantxxy.glassmail.data.local

import android.util.Base64
import com.dormantxxy.glassmail.data.model.LocalAttachment
import com.dormantxxy.glassmail.data.model.MailAddress

internal object ValueCodec {
    private const val ITEM = "\u001e"
    private const val FIELD = "\u001f"

    private fun encodePart(value: String): String = Base64.encodeToString(
        value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE
    )

    private fun decodePart(value: String): String = runCatching {
        String(Base64.decode(value, Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8)
    }.getOrDefault("")

    fun addresses(values: List<MailAddress>): String = values.joinToString(ITEM) {
        encodePart(it.name) + FIELD + encodePart(it.address)
    }

    fun readAddresses(value: String): List<MailAddress> = if (value.isBlank()) emptyList() else value
        .split(ITEM)
        .mapNotNull { item ->
            val parts = item.split(FIELD, limit = 2)
            if (parts.size != 2) null else MailAddress(decodePart(parts[0]), decodePart(parts[1]))
        }

    fun strings(values: List<String>): String = values.joinToString(ITEM, transform = ::encodePart)
    fun readStrings(value: String): List<String> = if (value.isBlank()) emptyList() else value.split(ITEM).map(::decodePart)

    fun attachments(values: List<LocalAttachment>): String = values.joinToString(ITEM) {
        listOf(it.uri, it.name, it.mimeType, it.sizeBytes.toString()).joinToString(FIELD, transform = ::encodePart)
    }

    fun readAttachments(value: String): List<LocalAttachment> = if (value.isBlank()) emptyList() else value
        .split(ITEM)
        .mapNotNull { item ->
            val p = item.split(FIELD).map(::decodePart)
            if (p.size < 4) null else LocalAttachment(p[0], p[1], p[2], p[3].toLongOrNull() ?: 0L)
        }
}
