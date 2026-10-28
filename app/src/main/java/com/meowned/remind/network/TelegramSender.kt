package com.meowned.remind.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class TelegramResult {
    data class Success(val messageId: Long) : TelegramResult()
    data class Error(val message: String, val statusCode: Int = -1) : TelegramResult()
}

object TelegramSender {

    private val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> {
                val addresses = Dns.SYSTEM.lookup(hostname)
                return addresses.sortedBy { if (it is java.net.Inet4Address) 0 else 1 }
            }
        })
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    fun chunkMessage(text: String, maxLen: Int = 4000): List<String> {
        if (text.length <= maxLen) return listOf(text)

        var totalChunks = (text.length + maxLen - 1) / maxLen
        while (true) {
            val chunks = splitIntoChunks(text, totalChunks, maxLen)
            if (chunks.size <= totalChunks) {
                val finalTotal = chunks.size
                return chunks.mapIndexed { index, chunk ->
                    "[${index + 1}/$finalTotal]\n$chunk"
                }
            }
            totalChunks = chunks.size
        }
    }

    private fun splitIntoChunks(text: String, estimatedTotal: Int, maxLen: Int): List<String> {
        val result = mutableListOf<String>()
        var startIndex = 0
        var currentChunkIndex = 1

        while (startIndex < text.length) {
            val header = "[$currentChunkIndex/$estimatedTotal]\n"
            val available = maxLen - header.length
            if (available <= 0) {
                result.add(text.substring(startIndex, startIndex + 1))
                startIndex += 1
                currentChunkIndex++
                continue
            }

            var endIndex = (startIndex + available).coerceAtMost(text.length)
            if (endIndex < text.length && Character.isHighSurrogate(text[endIndex - 1])) {
                endIndex--
            }

            result.add(text.substring(startIndex, endIndex))
            startIndex = endIndex
            currentChunkIndex++
        }

        return result
    }

    suspend fun sendMessage(
        botToken: String,
        chatId: String,
        topicId: String? = null,
        text: String,
        parseMode: String? = "HTML"
    ): TelegramResult = withContext(Dispatchers.IO) {
        if (botToken.isBlank()) {
            return@withContext TelegramResult.Error("Bot token не указан")
        }
        if (chatId.isBlank()) {
            return@withContext TelegramResult.Error("Chat ID не указан")
        }

        val chunks = chunkMessage(text, 4000)
        var lastSuccessId = 0L

        for (chunk in chunks) {
            var res = sendSingleMessage(botToken, chatId, topicId, chunk, parseMode)

            // Fallback: If Telegram returns entity parse error, retry without parseMode
            if (res is TelegramResult.Error && parseMode != null && isEntityParseError(res.message)) {
                Log.w("TelegramSender", "HTML parse error ('${res.message}'), retrying as plain text")
                res = sendSingleMessage(botToken, chatId, topicId, chunk, null)
            }

            when (res) {
                is TelegramResult.Success -> lastSuccessId = res.messageId
                is TelegramResult.Error -> return@withContext res
            }
        }

        TelegramResult.Success(lastSuccessId)
    }

    internal fun isEntityParseError(errorMessage: String): Boolean {
        val lower = errorMessage.lowercase()
        return lower.contains("can't parse entities") ||
                lower.contains("failed to parse entities") ||
                (lower.contains("entity") && (lower.contains("parse") || lower.contains("tag") || lower.contains("close") || lower.contains("find"))) ||
                lower.contains("closing tag") ||
                lower.contains("unclosed tag")
    }

    private fun sendSingleMessage(
        botToken: String,
        chatId: String,
        topicId: String?,
        text: String,
        parseMode: String?
    ): TelegramResult {
        val url = "https://api.telegram.org/bot${botToken.trim()}/sendMessage"
        val jsonPayload = JSONObject().apply {
            put("chat_id", chatId.trim())
            put("text", text)
            if (parseMode != null) {
                put("parse_mode", parseMode)
            }
            put("disable_web_page_preview", true)
            if (!topicId.isNullOrBlank()) {
                try {
                    put("message_thread_id", topicId.trim().toLong())
                } catch (e: NumberFormatException) {
                    // Ignore
                }
            }
        }
        return executePost(url, jsonPayload)
    }

    suspend fun editMessageCaption(
        botToken: String,
        chatId: String,
        messageId: Long,
        newCaption: String,
        parseMode: String = "HTML"
    ): TelegramResult = withContext(Dispatchers.IO) {
        val url = "https://api.telegram.org/bot${botToken.trim()}/editMessageCaption"
        val jsonPayload = JSONObject().apply {
            put("chat_id", chatId.trim())
            put("message_id", messageId)
            put("caption", newCaption)
            put("parse_mode", parseMode)
        }
        executePost(url, jsonPayload)
    }

    suspend fun setMessageReaction(
        botToken: String,
        chatId: String,
        messageId: Long,
        emoji: String = "🎉"
    ): TelegramResult = withContext(Dispatchers.IO) {
        val url = "https://api.telegram.org/bot${botToken.trim()}/setMessageReaction"
        val jsonPayload = JSONObject().apply {
            put("chat_id", chatId.trim())
            put("message_id", messageId)
            put("reaction", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "emoji")
                    put("emoji", emoji)
                })
            })
        }
        executePost(url, jsonPayload)
    }

    private fun executePost(url: String, jsonPayload: JSONObject): TelegramResult {
        val request = try {
            val body = jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE)
            Request.Builder()
                .url(url)
                .post(body)
                .build()
        } catch (e: IllegalArgumentException) {
            return TelegramResult.Error("Некорректный URL запроса: ${e.localizedMessage ?: "Синтаксическая ошибка"}")
        } catch (e: Exception) {
            return TelegramResult.Error("Ошибка формирования запроса: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            val statusCode = response.code

            Log.d("TelegramSender", "Response [$statusCode]: $responseBody")

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                if (json.optBoolean("ok", false)) {
                    val resultObj = json.optJSONObject("result")
                    val messageId = resultObj?.optLong("message_id", 0L) ?: 0L
                    TelegramResult.Success(messageId)
                } else {
                    val desc = json.optString("description", "Неизвестная ошибка Telegram API")
                    TelegramResult.Error(desc, statusCode)
                }
            } else {
                val desc = try {
                    val json = JSONObject(responseBody)
                    json.optString("description", "HTTP $statusCode")
                } catch (e: Exception) {
                    "HTTP $statusCode: ${response.message}"
                }
                TelegramResult.Error(desc, statusCode)
            }
        } catch (e: IOException) {
            Log.e("TelegramSender", "Network error", e)
            TelegramResult.Error("Сетевая ошибка: ${e.localizedMessage ?: "Нет соединения"}")
        } catch (e: Exception) {
            Log.e("TelegramSender", "General error", e)
            TelegramResult.Error("Ошибка: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    suspend fun testConnection(
        botToken: String,
        chatId: String,
        topicId: String? = null
    ): TelegramResult {
        val testMessage = "✅ <b>meowned ремайнд</b>\n\n" +
                "Тестовое сообщение успешно доставлено!\n" +
                "Бот настроен корректно и готов к пересылке SMS."
        return sendMessage(botToken, chatId, topicId, testMessage)
    }
}
