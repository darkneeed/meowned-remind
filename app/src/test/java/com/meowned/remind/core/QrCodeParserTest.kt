package com.meowned.remind.core

import com.meowned.remind.data.model.ForwardMode
import org.junit.Assert.*
import org.junit.Test

class QrCodeParserTest {

    @Test
    fun `parse empty or blank string returns error`() {
        val r1 = QrCodeParser.parse(null)
        assertTrue(r1 is QrParseResult.Error)
        assertEquals("QR-код пуст", (r1 as QrParseResult.Error).message)

        val r2 = QrCodeParser.parse("")
        assertTrue(r2 is QrParseResult.Error)

        val r3 = QrCodeParser.parse("   ")
        assertTrue(r3 is QrParseResult.Error)
    }

    @Test
    fun `parse direct webhook URL without parameters defaults to with server mode`() {
        val result = QrCodeParser.parse("https://my-webhook.site/api/sms")
        assertTrue(result is QrParseResult.Success)
        val data = (result as QrParseResult.Success).data
        assertEquals(ForwardMode.WITH_SERVER, data.mode)
        assertEquals("https://my-webhook.site/api/sms", data.serverUrl)
        assertEquals("", data.serverApiKey)
        assertEquals("", data.deviceName)
    }

    @Test
    fun `parse direct webhook URL with query parameters extracts api key and device`() {
        val result = QrCodeParser.parse("http://192.168.1.50:8080/sms?api_key=secret_token_42&device_name=OfficePhone")
        assertTrue(result is QrParseResult.Success)
        val data = (result as QrParseResult.Success).data
        assertEquals(ForwardMode.WITH_SERVER, data.mode)
        assertEquals("http://192.168.1.50:8080/sms", data.serverUrl)
        assertEquals("secret_token_42", data.serverApiKey)
        assertEquals("OfficePhone", data.deviceName)
    }

    @Test
    fun `parse deep link with server_url query parameter`() {
        val url = "meowned-remind://config?server_url=https%3A%2F%2Fexample.com%2Fapi%2Fsms&api_key=my_key"
        val result = QrCodeParser.parse(url)
        assertTrue(result is QrParseResult.Success)
        val data = (result as QrParseResult.Success).data
        assertEquals(ForwardMode.WITH_SERVER, data.mode)
        assertEquals("https://example.com/api/sms", data.serverUrl)
        assertEquals("my_key", data.serverApiKey)
    }

    @Test
    fun `parse server pipe format with keyword`() {
        val pipe = "server|https://my-server.com/webhook|secret_key|Pixel 7"
        val result = QrCodeParser.parse(pipe)
        assertTrue(result is QrParseResult.Success)
        val data = (result as QrParseResult.Success).data
        assertEquals(ForwardMode.WITH_SERVER, data.mode)
        assertEquals("https://my-server.com/webhook", data.serverUrl)
        assertEquals("secret_key", data.serverApiKey)
        assertEquals("Pixel 7", data.deviceName)
    }

    @Test
    fun `parse server pipe format starting with url`() {
        val pipe = "https://my-server.com/webhook|secret_123"
        val result = QrCodeParser.parse(pipe)
        assertTrue(result is QrParseResult.Success)
        val data = (result as QrParseResult.Success).data
        assertEquals(ForwardMode.WITH_SERVER, data.mode)
        assertEquals("https://my-server.com/webhook", data.serverUrl)
        assertEquals("secret_123", data.serverApiKey)
    }

    @Test
    fun `parse server JSON format`() {
        val json = """{"server_url":"https://api.domain.com/v1/sms","api_key":"sec42","device_name":"HomeGateway"}"""
        val result = QrCodeParser.parse(json)
        assertTrue(result is QrParseResult.Success)
        val data = (result as QrParseResult.Success).data
        assertEquals(ForwardMode.WITH_SERVER, data.mode)
        assertEquals("https://api.domain.com/v1/sms", data.serverUrl)
        assertEquals("sec42", data.serverApiKey)
        assertEquals("HomeGateway", data.deviceName)
    }

    @Test
    fun `parse Telegram pipe format with all fields`() {
        val pipe = "123456:ABC-DEF1234ghIkl|987654321|42|1001"
        val result = QrCodeParser.parse(pipe)
        assertTrue(result is QrParseResult.Success)
        val data = (result as QrParseResult.Success).data
        assertEquals(ForwardMode.WITHOUT_SERVER, data.mode)
        assertEquals("123456:ABC-DEF1234ghIkl", data.token)
        assertEquals("987654321", data.chatId)
        assertEquals("42", data.topicId)
        assertEquals(1001L, data.msgId)
    }

    @Test
    fun `parse Telegram deep link with query parameters`() {
        val deepLink = "meowned-remind://telegram?token=123:ABC&chat_id=456&topic_id=789"
        val result = QrCodeParser.parse(deepLink)
        assertTrue(result is QrParseResult.Success)
        val data = (result as QrParseResult.Success).data
        assertEquals(ForwardMode.WITHOUT_SERVER, data.mode)
        assertEquals("123:ABC", data.token)
        assertEquals("456", data.chatId)
        assertEquals("789", data.topicId)
    }

    @Test
    fun `parse Telegram JSON format`() {
        val json = """{"token":"123:bot_token","chat_id":"-1001234567","topic_id":"5","msg_id":25}"""
        val result = QrCodeParser.parse(json)
        assertTrue(result is QrParseResult.Success)
        val data = (result as QrParseResult.Success).data
        assertEquals(ForwardMode.WITHOUT_SERVER, data.mode)
        assertEquals("123:bot_token", data.token)
        assertEquals("-1001234567", data.chatId)
        assertEquals("5", data.topicId)
        assertEquals(25L, data.msgId)
    }

    @Test
    fun `parse invalid input returns descriptive error`() {
        val result = QrCodeParser.parse("random meaningless string")
        assertTrue(result is QrParseResult.Error)
        val error = result as QrParseResult.Error
        assertTrue(error.message.contains("Не удалось распознать"))
    }
}
