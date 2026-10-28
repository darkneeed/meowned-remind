package com.meowned.remind.core

import com.meowned.remind.data.model.ForwardMode
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class ParsedSetupData(
    val mode: ForwardMode,
    val token: String = "",
    val chatId: String = "",
    val topicId: String = "",
    val msgId: Long? = null,
    val serverUrl: String = "",
    val serverApiKey: String = "",
    val deviceName: String = ""
)

sealed class QrParseResult {
    data class Success(val data: ParsedSetupData, val message: String) : QrParseResult()
    data class Error(val message: String) : QrParseResult()
}

object QrCodeParser {

    fun parse(rawData: String?): QrParseResult {
        if (rawData.isNullOrBlank()) {
            return QrParseResult.Error("QR-код пуст")
        }

        val trimmed = rawData.trim()

        // 1. Try JSON format
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return parseJson(trimmed)
        }

        // 2. Try Pipe format (token|chat or server|url or url|key)
        if (trimmed.contains("|")) {
            return parsePipe(trimmed)
        }

        // 3. Try URL / Deep Link format
        if (isUrlOrDeepLink(trimmed)) {
            return parseUrl(trimmed)
        }

        return QrParseResult.Error("Не удалось распознать данные подключения в QR-коде")
    }

    private fun isUrlOrDeepLink(text: String): Boolean {
        val lower = text.lowercase()
        return lower.startsWith("http://") ||
                lower.startsWith("https://") ||
                lower.startsWith("meowned-remind://") ||
                lower.startsWith("meowned://")
    }

    private fun extractJsonString(json: String, key: String): String {
        val pattern = Regex(""""$key"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")
        val match = pattern.find(json) ?: return ""
        val rawVal = match.groupValues[1]
        return unescapeJson(rawVal)
    }

    private fun extractJsonLong(json: String, key: String): Long? {
        val pattern = Regex(""""$key"\s*:\s*([0-9]+)""")
        return pattern.find(json)?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun unescapeJson(text: String): String {
        return text
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\/", "/")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }

    private fun parseJson(json: String): QrParseResult {
        val modeStr = extractJsonString(json, "mode").lowercase()
        val serverUrl = extractJsonString(json, "server_url")
            .ifBlank { extractJsonString(json, "serverUrl") }
            .ifBlank { extractJsonString(json, "server") }
            .ifBlank { extractJsonString(json, "url") }

        val serverApiKey = extractJsonString(json, "api_key")
            .ifBlank { extractJsonString(json, "apiKey") }
            .ifBlank { extractJsonString(json, "secret") }
            .ifBlank { extractJsonString(json, "key") }

        val deviceName = extractJsonString(json, "device_name")
            .ifBlank { extractJsonString(json, "deviceName") }
            .ifBlank { extractJsonString(json, "device") }

        val token = extractJsonString(json, "token")
            .ifBlank { extractJsonString(json, "bot_token") }
        val chatId = extractJsonString(json, "chat_id")
            .ifBlank { extractJsonString(json, "chatId") }
        val topicId = extractJsonString(json, "topic_id")
            .ifBlank { extractJsonString(json, "topicId") }
        val msgId = extractJsonLong(json, "msg_id") ?: extractJsonLong(json, "message_id")

        if (modeStr == "server" || serverUrl.isNotBlank()) {
            if (serverUrl.isBlank()) {
                return QrParseResult.Error("В QR-коде не указан URL сервера")
            }
            return QrParseResult.Success(
                ParsedSetupData(
                    mode = ForwardMode.WITH_SERVER,
                    serverUrl = serverUrl.trim(),
                    serverApiKey = serverApiKey.trim(),
                    deviceName = deviceName.trim()
                ),
                "Данные сервера получены. Подтвердите настройку."
            )
        }

        if (token.isNotBlank() && chatId.isNotBlank()) {
            return QrParseResult.Success(
                ParsedSetupData(
                    mode = ForwardMode.WITHOUT_SERVER,
                    token = token.trim(),
                    chatId = chatId.trim(),
                    topicId = topicId.trim(),
                    msgId = msgId
                ),
                "Данные Telegram получены. Подтвердите настройку."
            )
        }

        return QrParseResult.Error("Не удалось распознать необходимые поля в JSON")
    }

    private fun parsePipe(text: String): QrParseResult {
        val parts = text.split("|").map { it.trim() }
        if (parts.isEmpty()) return QrParseResult.Error("Некорректный формат")

        val first = parts[0]
        if (first.equals("server", ignoreCase = true)) {
            val url = parts.getOrNull(1) ?: ""
            if (url.isBlank()) return QrParseResult.Error("URL сервера не указан")
            val apiKey = parts.getOrNull(2) ?: ""
            val device = parts.getOrNull(3) ?: ""
            return QrParseResult.Success(
                ParsedSetupData(
                    mode = ForwardMode.WITH_SERVER,
                    serverUrl = url,
                    serverApiKey = apiKey,
                    deviceName = device
                ),
                "Данные сервера получены. Подтвердите настройку."
            )
        }

        if (first.startsWith("http://", ignoreCase = true) || first.startsWith("https://", ignoreCase = true)) {
            val apiKey = parts.getOrNull(1) ?: ""
            val device = parts.getOrNull(2) ?: ""
            return QrParseResult.Success(
                ParsedSetupData(
                    mode = ForwardMode.WITH_SERVER,
                    serverUrl = first,
                    serverApiKey = apiKey,
                    deviceName = device
                ),
                "Данные сервера получены. Подтвердите настройку."
            )
        }

        // Telegram: token|chatId|topicId|msgId
        if (parts.size >= 2) {
            val token = parts[0]
            val chatId = parts[1]
            val topicId = parts.getOrNull(2) ?: ""
            val msgId = parts.getOrNull(3)?.toLongOrNull()
            if (token.isNotBlank() && chatId.isNotBlank()) {
                return QrParseResult.Success(
                    ParsedSetupData(
                        mode = ForwardMode.WITHOUT_SERVER,
                        token = token,
                        chatId = chatId,
                        topicId = topicId,
                        msgId = msgId
                    ),
                    "Данные Telegram получены. Подтвердите настройку."
                )
            }
        }

        return QrParseResult.Error("Не удалось распознать данные в строке с разделителями")
    }

    private fun parseUrl(urlStr: String): QrParseResult {
        val basePart = urlStr.substringBefore("?")
        val queryPart = if (urlStr.contains("?")) urlStr.substringAfter("?") else ""

        val queryParams = mutableMapOf<String, String>()
        if (queryPart.isNotBlank()) {
            for (pair in queryPart.split("&")) {
                val idx = pair.indexOf('=')
                if (idx != -1) {
                    val key = pair.substring(0, idx).trim().lowercase()
                    val rawVal = pair.substring(idx + 1).trim()
                    val decodedVal = try {
                        URLDecoder.decode(rawVal, StandardCharsets.UTF_8.name())
                    } catch (e: Exception) {
                        rawVal
                    }
                    queryParams[key] = decodedVal
                }
            }
        }

        val modeParam = queryParams["mode"]?.lowercase()
        val explicitServerUrl = queryParams["server_url"]
            ?: queryParams["serverurl"]
            ?: queryParams["server"]
            ?: queryParams["url"]

        val apiKey = queryParams["api_key"]
            ?: queryParams["apikey"]
            ?: queryParams["secret"]
            ?: queryParams["key"]
            ?: ""

        val deviceName = queryParams["device_name"]
            ?: queryParams["devicename"]
            ?: queryParams["device"]
            ?: ""

        val token = queryParams["token"]
            ?: queryParams["bot_token"]
            ?: queryParams["bottoken"]
            ?: ""

        val chatId = queryParams["chat_id"]
            ?: queryParams["chatid"]
            ?: ""

        val topicId = queryParams["topic_id"]
            ?: queryParams["topicid"]
            ?: queryParams["thread_id"]
            ?: ""

        val msgId = queryParams["msg_id"]?.toLongOrNull()
            ?: queryParams["message_id"]?.toLongOrNull()

        // Telegram mode
        if (token.isNotBlank() && chatId.isNotBlank()) {
            return QrParseResult.Success(
                ParsedSetupData(
                    mode = ForwardMode.WITHOUT_SERVER,
                    token = token,
                    chatId = chatId,
                    topicId = topicId,
                    msgId = msgId
                ),
                "Данные Telegram получены. Подтвердите настройку."
            )
        }

        // Server mode: explicit server url param
        if (!explicitServerUrl.isNullOrBlank()) {
            return QrParseResult.Success(
                ParsedSetupData(
                    mode = ForwardMode.WITH_SERVER,
                    serverUrl = explicitServerUrl,
                    serverApiKey = apiKey,
                    deviceName = deviceName
                ),
                "Данные сервера получены. Подтвердите настройку."
            )
        }

        // Server mode: HTTP / HTTPS direct endpoint URL
        val lowerBase = basePart.lowercase()
        if ((lowerBase.startsWith("http://") || lowerBase.startsWith("https://")) && !lowerBase.contains("t.me")) {
            return QrParseResult.Success(
                ParsedSetupData(
                    mode = ForwardMode.WITH_SERVER,
                    serverUrl = basePart,
                    serverApiKey = apiKey,
                    deviceName = deviceName
                ),
                "Данные сервера получены. Подтвердите настройку."
            )
        }

        if (modeParam == "server") {
            return QrParseResult.Error("В ссылке указан режим 'server', но отсутствует URL сервера")
        }

        return QrParseResult.Error("Не удалось распознать данные подключения в ссылке")
    }
}
