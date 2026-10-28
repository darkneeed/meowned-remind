package com.meowned.remind.core

import com.meowned.remind.data.model.SmsMessageItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TemplateEngine {

    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)
    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.US)
    private val fullDateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale.US)

    fun render(template: String, sms: SmsMessageItem, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val zonedDateTime = Instant.ofEpochMilli(sms.timestamp).atZone(zoneId)
        val timeStr = timeFormat.format(zonedDateTime)
        val dateStr = dateFormat.format(zonedDateTime)
        val fullDateTimeStr = fullDateTimeFormat.format(zonedDateTime)

        val escapedSender = escapeHtml(sms.sender)
        val escapedBody = escapeHtml(sms.body)

        return template
            .replace("{sender}", escapedSender)
            .replace("{body}", escapedBody)
            .replace("{time}", timeStr)
            .replace("{date}", dateStr)
            .replace("{datetime}", fullDateTimeStr)
            .replace("{sim}", sms.simSlot.toString())
    }

    fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
