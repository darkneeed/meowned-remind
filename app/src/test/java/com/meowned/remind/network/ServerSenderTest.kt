package com.meowned.remind.network

import com.meowned.remind.data.model.SmsMessageItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.ServerSocket

class ServerSenderTest {

    private class SimpleTestServer(
        private val responseCode: Int = 200,
        private val responseBody: String = """{"status":"ok"}"""
    ) : AutoCloseable {
        private val serverSocket = ServerSocket(0)
        val port: Int = serverSocket.localPort
        val receivedHeaders = mutableListOf<String>()
        var receivedBody: String = ""
        private var thread: Thread? = null

        fun start() {
            thread = Thread {
                try {
                    val socket = serverSocket.accept()
                    val inputStream = socket.getInputStream()

                    // Read header bytes until \r\n\r\n
                    val headerStream = ByteArrayOutputStream()
                    var b = inputStream.read()
                    while (b != -1) {
                        headerStream.write(b)
                        val bytes = headerStream.toByteArray()
                        val len = bytes.size
                        if (len >= 4 &&
                            bytes[len - 4] == '\r'.code.toByte() &&
                            bytes[len - 3] == '\n'.code.toByte() &&
                            bytes[len - 2] == '\r'.code.toByte() &&
                            bytes[len - 1] == '\n'.code.toByte()
                        ) {
                            break
                        }
                        b = inputStream.read()
                    }

                    val headerText = headerStream.toString(Charsets.UTF_8.name())
                    var contentLength = 0
                    for (line in headerText.lines()) {
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty()) {
                            receivedHeaders.add(trimmed)
                            if (trimmed.lowercase().startsWith("content-length:")) {
                                contentLength = trimmed.substringAfter(":").trim().toIntOrNull() ?: 0
                            }
                        }
                    }

                    if (contentLength > 0) {
                        val bodyBytes = ByteArray(contentLength)
                        var read = 0
                        while (read < contentLength) {
                            val count = inputStream.read(bodyBytes, read, contentLength - read)
                            if (count == -1) break
                            read += count
                        }
                        receivedBody = String(bodyBytes, 0, read, Charsets.UTF_8)
                    }

                    val statusText = when (responseCode) {
                        200 -> "OK"
                        401 -> "Unauthorized"
                        500 -> "Internal Server Error"
                        else -> "Status"
                    }
                    val responseBytes = responseBody.toByteArray(Charsets.UTF_8)
                    val outputStream = socket.getOutputStream()
                    val header = "HTTP/1.1 $responseCode $statusText\r\n" +
                            "Content-Type: application/json; charset=utf-8\r\n" +
                            "Content-Length: ${responseBytes.size}\r\n" +
                            "Connection: close\r\n\r\n"
                    outputStream.write(header.toByteArray(Charsets.UTF_8))
                    outputStream.write(responseBytes)
                    outputStream.flush()
                    socket.close()
                } catch (e: Exception) {
                    // Socket closed
                }
            }.apply {
                isDaemon = true
                start()
            }
        }

        override fun close() {
            try {
                serverSocket.close()
            } catch (e: Exception) {}
            thread?.interrupt()
        }
    }

    @Test
    fun `normalizeUrl prepends https when scheme is missing`() {
        val result = ServerSender.normalizeUrl("api.example.com/sms")
        assertEquals("https://api.example.com/sms", result)
    }

    @Test
    fun `normalizeUrl preserves existing http and https`() {
        assertEquals("http://localhost:8080/sms", ServerSender.normalizeUrl("http://localhost:8080/sms"))
        assertEquals("https://my-server.com/api", ServerSender.normalizeUrl("https://my-server.com/api"))
    }

    @Test
    fun `normalizeUrl handles whitespace and empty input`() {
        assertEquals("", ServerSender.normalizeUrl(""))
        assertEquals("", ServerSender.normalizeUrl("   "))
        assertEquals("https://api.test.com", ServerSender.normalizeUrl("  api.test.com  "))
    }

    @Test
    fun `escapeJson correctly escapes quotes, newlines and backslashes`() {
        val raw = "Hello \"World\"\nLine 2\tTabbed \\ Backslash"
        val escaped = ServerSender.escapeJson(raw)
        assertEquals("Hello \\\"World\\\"\\nLine 2\\tTabbed \\\\ Backslash", escaped)
    }

    @Test
    fun `buildSmsJson produces valid JSON string with all fields`() {
        val sms = SmsMessageItem(
            sender = "+79991234567",
            body = "Code: 1234",
            timestamp = 1700000000000L,
            simSlot = 2
        )
        val json = ServerSender.buildSmsJson(sms, "Rendered SMS", "Pixel 7")
        assertTrue(json.contains(""""event":"sms_received""""))
        assertTrue(json.contains(""""sender":"+79991234567""""))
        assertTrue(json.contains(""""body":"Code: 1234""""))
        assertTrue(json.contains(""""timestamp":1700000000000"""))
        assertTrue(json.contains(""""sim_slot":2"""))
        assertTrue(json.contains(""""device_name":"Pixel 7""""))
        assertTrue(json.contains(""""text":"Rendered SMS""""))
    }

    @Test
    fun `buildTestJson produces valid test event payload`() {
        val json = ServerSender.buildTestJson("Tester Device")
        assertTrue(json.contains(""""event":"test_connection""""))
        assertTrue(json.contains(""""sender":"MEOWNED_TEST""""))
        assertTrue(json.contains(""""device_name":"Tester Device""""))
    }

    @Test
    fun `sendSms returns error when serverUrl is blank`() = runBlocking {
        val sms = SmsMessageItem(sender = "+123456", body = "Test message")
        val result = ServerSender.sendSms("", null, sms)
        assertTrue(result is ServerResult.Error)
        assertEquals("URL сервера не указан", (result as ServerResult.Error).message)
    }

    @Test
    fun `testConnection returns error when serverUrl is blank`() = runBlocking {
        val result = ServerSender.testConnection("   ", null)
        assertTrue(result is ServerResult.Error)
        assertEquals("URL сервера не указан", (result as ServerResult.Error).message)
    }

    @Test
    fun `sendSms sends valid JSON payload and headers and receives 200 OK`() = runBlocking {
        SimpleTestServer(200, """{"status":"ok","id":123}""").use { server ->
            server.start()

            val sms = SmsMessageItem(
                sender = "+79991234567",
                body = "Код подтверждения: 9988",
                timestamp = 1700000000000L,
                simSlot = 2
            )

            val url = "http://127.0.0.1:${server.port}/sms"
            val result = ServerSender.sendSms(
                serverUrl = url,
                apiKey = "secret_api_token_42",
                sms = sms,
                renderedText = "Formatted text",
                deviceName = "Pixel Test"
            )

            assertTrue("Result should be Success but was: $result", result is ServerResult.Success)
            val success = result as ServerResult.Success
            assertTrue(success.responseBody.contains(""""status":"ok""""))

            // Verify headers
            val authHeader = server.receivedHeaders.find { it.startsWith("Authorization:", ignoreCase = true) }
            val apiKeyHeader = server.receivedHeaders.find { it.startsWith("X-API-Key:", ignoreCase = true) }
            val userAgentHeader = server.receivedHeaders.find { it.startsWith("User-Agent:", ignoreCase = true) }

            assertEquals("Authorization: Bearer secret_api_token_42", authHeader)
            assertEquals("X-API-Key: secret_api_token_42", apiKeyHeader)
            assertEquals("User-Agent: meowned-remind/1.0", userAgentHeader)

            // Verify JSON body
            assertTrue(server.receivedBody.contains(""""event":"sms_received""""))
            assertTrue(server.receivedBody.contains(""""sender":"+79991234567""""))
            assertTrue(server.receivedBody.contains(""""body":"Код подтверждения: 9988""""))
            assertTrue(server.receivedBody.contains(""""timestamp":1700000000000"""))
            assertTrue(server.receivedBody.contains(""""sim_slot":2"""))
            assertTrue(server.receivedBody.contains(""""device_name":"Pixel Test""""))
            assertTrue(server.receivedBody.contains(""""text":"Formatted text""""))
        }
    }

    @Test
    fun `sendSms handles HTTP 401 Unauthorized`() = runBlocking {
        SimpleTestServer(401, """{"error":"Invalid API key"}""").use { server ->
            server.start()

            val sms = SmsMessageItem(sender = "Bank", body = "Code 1234")
            val result = ServerSender.sendSms(
                serverUrl = "http://127.0.0.1:${server.port}/sms-auth",
                apiKey = "wrong_key",
                sms = sms
            )

            assertTrue(result is ServerResult.Error)
            val error = result as ServerResult.Error
            assertEquals(401, error.statusCode)
            assertTrue(error.message.contains("401"))
            assertTrue(error.message.contains("Invalid API key"))
        }
    }

    @Test
    fun `testConnection sends test payload and handles 200 OK`() = runBlocking {
        SimpleTestServer(200, "pong").use { server ->
            server.start()

            val result = ServerSender.testConnection(
                serverUrl = "http://127.0.0.1:${server.port}/ping",
                apiKey = "test_key",
                deviceName = "Tester Device"
            )

            assertTrue("Result should be Success but was: $result", result is ServerResult.Success)
            assertTrue(server.receivedBody.contains(""""event":"test_connection""""))
            assertTrue(server.receivedBody.contains(""""sender":"MEOWNED_TEST""""))
            assertTrue(server.receivedBody.contains(""""device_name":"Tester Device""""))
        }
    }

    @Test
    fun `sendSms returns Error on network connection refused`() = runBlocking {
        val dummySocket = ServerSocket(0)
        val closedPort = dummySocket.localPort
        dummySocket.close()

        val sms = SmsMessageItem(sender = "Bank", body = "Test")
        val result = ServerSender.sendSms("http://127.0.0.1:$closedPort/nowhere", null, sms)

        assertTrue(result is ServerResult.Error)
        val error = result as ServerResult.Error
        assertTrue(error.message.contains("Сетевая ошибка"))
    }

    @Test
    fun `sendSms handles invalid URL with spaces gracefully without crashing`() = runBlocking {
        val sms = SmsMessageItem(sender = "Bank", body = "Test")
        val result = ServerSender.sendSms("http://invalid host with spaces/api", null, sms)

        assertTrue(result is ServerResult.Error)
        val error = result as ServerResult.Error
        assertTrue(error.message.contains("Некорректный URL"))
    }

    @Test
    fun `buildSmsJson preserves unicode emojis and surrogate pairs properly`() {
        val sms = SmsMessageItem(
            sender = "+79991234567",
            body = "Привет! Код: 1234 👋🎉🚀",
            timestamp = 1700000000000L,
            simSlot = 1
        )
        val json = ServerSender.buildSmsJson(sms, "Rendered 👋", "Phone")
        assertTrue(json.contains("Привет! Код: 1234 👋🎉🚀"))
        assertTrue(json.contains("Rendered 👋"))
    }

    @Test
    fun `escapeJson handles unicode control characters`() {
        val raw = "Test\u0001Control\u001FEnd"
        val escaped = ServerSender.escapeJson(raw)
        assertEquals("Test\\u0001Control\\u001fEnd", escaped)
    }
}

