package com.meowned.remind.core

import com.meowned.remind.data.model.SmsMessageItem
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class TemplateEngineTest {

    private val fixedZone = ZoneId.of("UTC")
    // 1700000000000 ms is 2023-11-14T22:13:20Z
    private val sampleSms = SmsMessageItem(
        sender = "Bank <Alert>",
        body = "Code: 1234 & secret > nothing",
        timestamp = 1700000000000L,
        simSlot = 2
    )

    @Test
    fun `render substitutes all placeholders correctly`() {
        val template = "From: {sender}\nText: {body}\nTime: {time}\nDate: {date}\nFull: {datetime}\nSIM: {sim}"
        val result = TemplateEngine.render(template, sampleSms, fixedZone)

        assertTrue("Should substitute sender with HTML escaping", result.contains("From: Bank &lt;Alert&gt;"))
        assertTrue("Should substitute body with HTML escaping", result.contains("Text: Code: 1234 &amp; secret &gt; nothing"))
        assertTrue("Should substitute time", result.contains("Time: 22:13:20"))
        assertTrue("Should substitute date", result.contains("Date: 14.11.2023"))
        assertTrue("Should substitute datetime", result.contains("Full: 14.11.2023 22:13:20"))
        assertTrue("Should substitute SIM", result.contains("SIM: 2"))
    }

    @Test
    fun `escapeHtml escapes special characters`() {
        val input = "Hello <script>alert('xss')</script> & \"more\" > 5"
        val escaped = TemplateEngine.escapeHtml(input)

        assertFalse(escaped.contains("<"))
        assertFalse(escaped.contains(">"))
        assertTrue(escaped.contains("&lt;"))
        assertTrue(escaped.contains("&gt;"))
        assertTrue(escaped.contains("&amp;"))
    }

    @Test
    fun `render works with empty body and sender`() {
        val emptySms = SmsMessageItem(
            sender = "",
            body = "",
            timestamp = 1700000000000L,
            simSlot = 1
        )
        val template = "{sender}:{body}"
        val result = TemplateEngine.render(template, emptySms, fixedZone)
        assertEquals(":", result)
    }

    @Test
    fun `render handles repeated placeholders`() {
        val template = "{sim}-{sim}-{sim}"
        val result = TemplateEngine.render(template, sampleSms, fixedZone)
        assertEquals("2-2-2", result)
    }
}
