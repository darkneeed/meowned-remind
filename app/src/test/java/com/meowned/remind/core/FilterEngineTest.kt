package com.meowned.remind.core

import com.meowned.remind.data.model.FilterMode
import com.meowned.remind.data.model.SimSlotFilter
import com.meowned.remind.data.model.SmsMessageItem
import org.junit.Assert.*
import org.junit.Test

class FilterEngineTest {

    private fun createSms(
        sender: String = "+79991234567",
        body: String = "Test message content",
        simSlot: Int = 1
    ) = SmsMessageItem(
        sender = sender,
        body = body,
        timestamp = 1700000000000L,
        simSlot = simSlot
    )

    @Test
    fun `whitelist with empty rules must block all messages`() {
        val sms = createSms()
        val (shouldForward, reason) = FilterEngine.shouldForward(
            sms = sms,
            simFilter = SimSlotFilter.ALL,
            filterMode = FilterMode.WHITELIST,
            senderRules = "",
            keywordWhitelist = "",
            keywordBlacklist = ""
        )

        assertFalse(shouldForward)
        assertEquals("Отклонено: белый список пуст", reason)
    }

    @Test
    fun `whitelist with sender rules allows matching sender`() {
        val sms = createSms(sender = "900")
        val (shouldForward, reason) = FilterEngine.shouldForward(
            sms = sms,
            simFilter = SimSlotFilter.ALL,
            filterMode = FilterMode.WHITELIST,
            senderRules = "900, Tinkoff",
            keywordWhitelist = "",
            keywordBlacklist = ""
        )

        assertTrue(shouldForward)
        assertNull(reason)
    }

    @Test
    fun `whitelist with sender rules blocks non-matching sender`() {
        val sms = createSms(sender = "SpamBot")
        val (shouldForward, reason) = FilterEngine.shouldForward(
            sms = sms,
            simFilter = SimSlotFilter.ALL,
            filterMode = FilterMode.WHITELIST,
            senderRules = "900, Tinkoff",
            keywordWhitelist = "",
            keywordBlacklist = ""
        )

        assertFalse(shouldForward)
        assertTrue(reason?.contains("отсутствует в белом списке") == true)
    }

    @Test
    fun `rule 900 does not match long phone number containing 900`() {
        // Prevents regression of false positive phone number substring match
        val matches = FilterEngine.matchesRule("+79001234567", "900")
        assertFalse("Phone number +79001234567 should not match rule '900'", matches)

        val exactMatch = FilterEngine.matchesRule("900", "900")
        assertTrue("Exact sender '900' should match rule '900'", exactMatch)
    }

    @Test
    fun `normalized phone numbers match regardless of formatting`() {
        assertTrue(FilterEngine.matchesRule("+7 (999) 123-45-67", "89991234567"))
        assertTrue(FilterEngine.matchesRule("89991234567", "+79991234567"))
        assertFalse(FilterEngine.matchesRule("+79991234567", "+79990000000"))
    }

    @Test
    fun `wildcard matching works for senders`() {
        assertTrue(FilterEngine.matchesRule("+79001234567", "+7900*"))
        assertFalse(FilterEngine.matchesRule("+79011234567", "+7900*"))
        assertTrue(FilterEngine.matchesRule("Sberbank", "*bank*"))
        assertTrue(FilterEngine.matchesRule("TinkoffBank", "*bank"))
        assertFalse(FilterEngine.matchesRule("TaxiService", "*bank*"))
    }

    @Test
    fun `regex matching works for senders`() {
        assertTrue(FilterEngine.matchesRule("900", "regex:^9\\d{2}$"))
        assertFalse(FilterEngine.matchesRule("9000", "regex:^9\\d{2}$"))
        assertTrue(FilterEngine.matchesRule("Bank123", "^[A-Za-z]+[0-9]+$"))
    }

    @Test
    fun `blacklist blocks matched sender and allows others`() {
        val blockedSms = createSms(sender = "Spammer")
        val (blockedForward, blockedReason) = FilterEngine.shouldForward(
            sms = blockedSms,
            simFilter = SimSlotFilter.ALL,
            filterMode = FilterMode.BLACKLIST,
            senderRules = "Spammer, 1234",
            keywordWhitelist = "",
            keywordBlacklist = ""
        )
        assertFalse(blockedForward)
        assertTrue(blockedReason?.contains("находится в черном списке") == true)

        val allowedSms = createSms(sender = "Friend")
        val (allowedForward, _) = FilterEngine.shouldForward(
            sms = allowedSms,
            simFilter = SimSlotFilter.ALL,
            filterMode = FilterMode.BLACKLIST,
            senderRules = "Spammer, 1234",
            keywordWhitelist = "",
            keywordBlacklist = ""
        )
        assertTrue(allowedForward)
    }

    @Test
    fun `keyword whitelist filtering requires at least one keyword`() {
        val validSms = createSms(body = "Ваш код подтверждения: 123456")
        val (forwardValid, _) = FilterEngine.shouldForward(
            sms = validSms,
            simFilter = SimSlotFilter.ALL,
            filterMode = FilterMode.ALLOW_ALL,
            senderRules = "",
            keywordWhitelist = "код, пароль, auth",
            keywordBlacklist = ""
        )
        assertTrue(forwardValid)

        val invalidSms = createSms(body = "Привет, как дела?")
        val (forwardInvalid, invalidReason) = FilterEngine.shouldForward(
            sms = invalidSms,
            simFilter = SimSlotFilter.ALL,
            filterMode = FilterMode.ALLOW_ALL,
            senderRules = "",
            keywordWhitelist = "код, пароль, auth",
            keywordBlacklist = ""
        )
        assertFalse(forwardInvalid)
        assertTrue(invalidReason?.contains("не содержит ключевых слов") == true)
    }

    @Test
    fun `keyword blacklist filtering blocks spam words`() {
        val spamSms = createSms(body = "Срочно оформите займ под 0%!")
        val (forwardSpam, spamReason) = FilterEngine.shouldForward(
            sms = spamSms,
            simFilter = SimSlotFilter.ALL,
            filterMode = FilterMode.ALLOW_ALL,
            senderRules = "",
            keywordWhitelist = "",
            keywordBlacklist = "займ, кредит, акция"
        )
        assertFalse(forwardSpam)
        assertTrue(spamReason?.contains("содержит запрещенное ключевое слово") == true)
    }

    @Test
    fun `sim slot filter enforces correct sim card`() {
        val sim1Sms = createSms(simSlot = 1)
        val sim2Sms = createSms(simSlot = 2)

        val (sim1OnSim1, _) = FilterEngine.shouldForward(
            sms = sim1Sms,
            simFilter = SimSlotFilter.SIM_1,
            filterMode = FilterMode.ALLOW_ALL,
            senderRules = "",
            keywordWhitelist = "",
            keywordBlacklist = ""
        )
        assertTrue(sim1OnSim1)

        val (sim2OnSim1, reasonSim2On1) = FilterEngine.shouldForward(
            sms = sim2Sms,
            simFilter = SimSlotFilter.SIM_1,
            filterMode = FilterMode.ALLOW_ALL,
            senderRules = "",
            keywordWhitelist = "",
            keywordBlacklist = ""
        )
        assertFalse(sim2OnSim1)
        assertTrue(reasonSim2On1?.contains("требуется SIM 1") == true)

        val (sim2OnSim2, _) = FilterEngine.shouldForward(
            sms = sim2Sms,
            simFilter = SimSlotFilter.SIM_2,
            filterMode = FilterMode.ALLOW_ALL,
            senderRules = "",
            keywordWhitelist = "",
            keywordBlacklist = ""
        )
        assertTrue(sim2OnSim2)
    }

    @Test
    fun `empty blacklist allows all messages`() {
        val sms = createSms(sender = "RandomSender")
        val (shouldForward, reason) = FilterEngine.shouldForward(
            sms = sms,
            simFilter = SimSlotFilter.ALL,
            filterMode = FilterMode.BLACKLIST,
            senderRules = "   ",
            keywordWhitelist = "",
            keywordBlacklist = ""
        )
        assertTrue(shouldForward)
        assertNull(reason)
    }

    @Test
    fun `parseRulesList handles mixed commas, semicolons, newlines, and trims spaces`() {
        val raw = "  900 , Tinkoff ;\n Sberbank \n; 88005553535  "
        val parsed = FilterEngine.parseRulesList(raw)
        assertEquals(listOf("900", "Tinkoff", "Sberbank", "88005553535"), parsed)
    }

    @Test
    fun `invalid regex in rule does not crash and returns false`() {
        val sms = createSms(sender = "Bank")
        assertFalse(FilterEngine.matchesRule(sms.sender, "regex:[unclosed"))
        assertFalse(FilterEngine.matchesRule(sms.sender, "^[unclosed$"))
    }
}
