package com.dormantxxy.glassmail

import com.dormantxxy.glassmail.data.model.MailCategory
import com.dormantxxy.glassmail.data.model.MailRules
import org.junit.Assert.assertEquals
import org.junit.Test

class MailRulesTest {
    @Test fun stripsReplyPrefixesFromThreadKey() {
        assertEquals("项目进度", MailRules.threadKey("Re: 回复: 项目进度"))
    }

    @Test fun recognizesTransactionMail() {
        assertEquals(MailCategory.TRANSACTIONS, MailRules.categorize("您的订单已发货", "shop@example.com"))
    }

    @Test fun promotionHeaderWins() {
        assertEquals(MailCategory.PROMOTIONS, MailRules.categorize("本周资讯", "news@example.com", "List-Unsubscribe: yes"))
    }
}
