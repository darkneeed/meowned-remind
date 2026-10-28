package com.meowned.remind.network

import android.util.Log
import com.meowned.remind.data.model.SmsMessageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class ServerResult {
    data class Success(val responseBody: String = "") : ServerResult()
    data class Error(val message: String, val statusCode: Int = -1) : ServerResult()
}

object ServerSender {

    private val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""
        return if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }
    }

    fun escapeJson(text: String): String {
        val sb = StringBuilder()
        for (c in text) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c <= '\u001F') {
                        sb.append(String.format(java.util.Locale.US, "\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        return sb.toString()
    }

    fun buildSmsJson(
        sms: SmsMessageItem,
        renderedText: String? = null,
        deviceName: String? = null
    ): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"event\":\"sms_received\",")
        sb.append("\"sender\":\"").append(escapeJson(sms.sender)).append("\",")
        sb.append("\"body\":\"").append(escapeJson(sms.body)).append("\",")
        sb.append("\"timestamp\":").append(sms.timestamp).append(",")
        sb.append("\"sim_slot\":").append(sms.simSlot)
        if (!deviceName.isNullOrBlank()) {
            sb.append(",\"device_name\":\"").append(escapeJson(deviceName)).append("\"")
        }
        if (!renderedText.isNullOrBlank()) {
            sb.append(",\"text\":\"").append(escapeJson(renderedText)).append("\"")
        }
        sb.append("}")
        return sb.toString()
    }

    fun buildTestJson(deviceName: String? = null): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"event\":\"test_connection\",")
        sb.append("\"sender\":\"MEOWNED_TEST\",")
        sb.append("\"body\":\"Тестовое подключение meowned ремайнд\",")
        sb.append("\"timestamp\":").append(System.currentTimeMillis()).append(",")
        sb.append("\"sim_slot\":1")
        if (!deviceName.isNullOrBlank()) {
            sb.append(",\"device_name\":\"").append(escapeJson(deviceName)).append("\"")
        }
        sb.append(",\"text\":\"Тестовое подключение успешно проверено\"")
        sb.append("}")
        return sb.toString()
    }

    suspend fun sendSms(
        serverUrl: String,
        apiKey: String?,
        sms: SmsMessageItem,
        renderedText: String? = null,
        deviceName: String? = null
    ): ServerResult = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeUrl(serverUrl)
        if (normalizedUrl.isBlank()) {
            return@withContext ServerResult.Error("URL сервера не указан")
        }

        val jsonPayload = buildSmsJson(sms, renderedText, deviceName)
        executePost(normalizedUrl, apiKey, jsonPayload)
    }

    suspend fun testConnection(
        serverUrl: String,
        apiKey: String?,
        deviceName: String? = null
    ): ServerResult = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeUrl(serverUrl)
        if (normalizedUrl.isBlank()) {
            return@withContext ServerResult.Error("URL сервера не указан")
        }

        val jsonPayload = buildTestJson(deviceName)
        executePost(normalizedUrl, apiKey, jsonPayload)
    }

    private fun executePost(
        url: String,
        apiKey: String?,
        jsonPayload: String
    ): ServerResult {
        val request = try {
            val requestBody = jsonPayload.toRequestBody(JSON_MEDIA_TYPE)
            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("User-Agent", "meowned-remind/1.0")

            if (!apiKey.isNullOrBlank()) {
                val key = apiKey.trim()
                val sanitizedKey = key.filter { it.code in 32..126 }
                if (sanitizedKey.isNotEmpty()) {
                    requestBuilder.addHeader("Authorization", "Bearer $sanitizedKey")
                    requestBuilder.addHeader("X-API-Key", sanitizedKey)
                }
            }

            requestBuilder.build()
        } catch (e: IllegalArgumentException) {
            return ServerResult.Error("Некорректный URL или заголовок: ${e.localizedMessage ?: "Синтаксическая ошибка"}")
        } catch (e: Exception) {
            return ServerResult.Error("Ошибка формирования запроса: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            val statusCode = response.code

            try {
                Log.d("ServerSender", "Response [$statusCode]: $responseBody")
            } catch (e: Throwable) {
                // Catch any Android Log stub invocation in unit tests
            }

            if (response.isSuccessful) {
                ServerResult.Success(responseBody)
            } else {
                val errorDetails = if (responseBody.isNotBlank() && responseBody.length <= 200) {
                    ": $responseBody"
                } else ""
                ServerResult.Error("HTTP $statusCode (${response.message})$errorDetails", statusCode)
            }
        } catch (e: IOException) {
            try {
                Log.e("ServerSender", "Network error", e)
            } catch (ignored: Throwable) {}
            ServerResult.Error("Сетевая ошибка: ${e.localizedMessage ?: "Нет соединения с сервером"}")
        } catch (e: Exception) {
            try {
                Log.e("ServerSender", "General error", e)
            } catch (ignored: Throwable) {}
            ServerResult.Error("Ошибка: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }
}
