package com.dormantxxy.glassmail.data.model

import java.util.Locale

object MailRules {
    private val transactionWords = listOf(
        "receipt", "invoice", "order", "payment", "paid", "bank", "账单", "订单", "支付", "收据", "物流", "快递"
    )
    private val updateWords = listOf(
        "notification", "alert", "security", "verify", "code", "newsletter", "通知", "提醒", "验证码", "安全", "周报"
    )
    private val promotionWords = listOf(
        "sale", "deal", "offer", "discount", "coupon", "marketing", "促销", "优惠", "折扣", "活动", "推广"
    )

    fun categorize(subject: String, sender: String, headers: String = ""): MailCategory {
        val value = "$subject $sender $headers".lowercase(Locale.ROOT)
        return when {
            promotionWords.any(value::contains) || "list-unsubscribe" in value -> MailCategory.PROMOTIONS
            transactionWords.any(value::contains) -> MailCategory.TRANSACTIONS
            updateWords.any(value::contains) || "auto-submitted" in value -> MailCategory.UPDATES
            else -> MailCategory.PRIMARY
        }
    }

    fun threadKey(subject: String): String {
        var normalized = subject.trim().lowercase(Locale.ROOT)
        val prefixes = listOf("re:", "fw:", "fwd:", "回复:", "转发:", "答复:")
        var changed: Boolean
        do {
            changed = false
            prefixes.forEach { prefix ->
                if (normalized.startsWith(prefix)) {
                    normalized = normalized.removePrefix(prefix).trim()
                    changed = true
                }
            }
        } while (changed)
        return normalized.ifBlank { "(no subject)" }
    }

    fun cleanPreview(value: String, maxLength: Int = 180): String = value
        .replace(Regex("\\s+"), " ")
        .trim()
        .let { if (it.length <= maxLength) it else it.take(maxLength).trimEnd() + "…" }
}
