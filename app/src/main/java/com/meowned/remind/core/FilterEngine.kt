package com.meowned.remind.core

import com.meowned.remind.data.model.FilterMode
import com.meowned.remind.data.model.SimSlotFilter
import com.meowned.remind.data.model.SmsMessageItem

object FilterEngine {

    fun shouldForward(
        sms: SmsMessageItem,
        simFilter: SimSlotFilter,
        filterMode: FilterMode,
        senderRules: String,
        keywordWhitelist: String,
        keywordBlacklist: String
    ): Pair<Boolean, String?> {

        // 1. Check SIM slot
        when (simFilter) {
            SimSlotFilter.SIM_1 -> if (sms.simSlot != 1) return Pair(false, "Отклонено: требуется SIM 1, сообщение с SIM ${sms.simSlot}")
            SimSlotFilter.SIM_2 -> if (sms.simSlot != 2) return Pair(false, "Отклонено: требуется SIM 2, сообщение с SIM ${sms.simSlot}")
            SimSlotFilter.ALL -> {}
        }

        // 2. Check Sender rules
        val senderRulesList = parseRulesList(senderRules)
        if (filterMode == FilterMode.WHITELIST && senderRulesList.isEmpty()) {
            return Pair(false, "Отклонено: белый список пуст")
        }

        if (senderRulesList.isNotEmpty()) {
            val matchesSender = senderRulesList.any { rule ->
                matchesRule(sms.sender, rule)
            }

            when (filterMode) {
                FilterMode.WHITELIST -> {
                    if (!matchesSender) {
                        return Pair(false, "Отклонено: отправитель ${sms.sender} отсутствует в белом списке")
                    }
                }
                FilterMode.BLACKLIST -> {
                    if (matchesSender) {
                        return Pair(false, "Отклонено: отправитель ${sms.sender} находится в черном списке")
                    }
                }
                FilterMode.ALLOW_ALL -> {}
            }
        }

        // 3. Check Keyword Whitelist (if non-empty, must contain at least one keyword)
        val whitelistKeywords = parseRulesList(keywordWhitelist)
        if (whitelistKeywords.isNotEmpty()) {
            val containsKeyword = whitelistKeywords.any { kw ->
                sms.body.contains(kw, ignoreCase = true)
            }
            if (!containsKeyword) {
                return Pair(false, "Отклонено: текст не содержит ключевых слов из белого списка")
            }
        }

        // 4. Check Keyword Blacklist (if non-empty, must NOT contain any keyword)
        val blacklistKeywords = parseRulesList(keywordBlacklist)
        if (blacklistKeywords.isNotEmpty()) {
            val containsBlacklisted = blacklistKeywords.firstOrNull { kw ->
                sms.body.contains(kw, ignoreCase = true)
            }
            if (containsBlacklisted != null) {
                return Pair(false, "Отклонено: текст содержит запрещенное ключевое слово '$containsBlacklisted'")
            }
        }

        return Pair(true, null)
    }

    fun parseRulesList(raw: String): List<String> {
        return raw.split(",", "\n", ";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun matchesRule(sender: String, rule: String): Boolean {
        val trimmedSender = sender.trim()
        val trimmedRule = rule.trim()
        if (trimmedRule.isEmpty()) return false

        // 1. Explicit regex prefix: "regex:..."
        if (trimmedRule.startsWith("regex:", ignoreCase = true)) {
            val pattern = trimmedRule.substring(6).trim()
            return try {
                Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(trimmedSender)
            } catch (e: Exception) {
                false
            }
        }

        // 2. Standard regex syntax: begins with ^ or ends with $
        if (trimmedRule.startsWith("^") || trimmedRule.endsWith("$")) {
            return try {
                Regex(trimmedRule, RegexOption.IGNORE_CASE).containsMatchIn(trimmedSender)
            } catch (e: Exception) {
                false
            }
        }

        // 3. Wildcard matching with * or ?
        if (trimmedRule.contains("*") || trimmedRule.contains("?")) {
            return try {
                val regexPattern = buildWildcardRegex(trimmedRule)
                Regex(regexPattern, RegexOption.IGNORE_CASE).matches(trimmedSender)
            } catch (e: Exception) {
                false
            }
        }

        // 4. Exact match (case insensitive)
        if (trimmedSender.equals(trimmedRule, ignoreCase = true)) {
            return true
        }

        // 5. Phone number normalized matching
        val senderDigits = extractDigits(trimmedSender)
        val ruleDigits = extractDigits(trimmedRule)
        if (isPotentialPhoneNumber(trimmedSender) && isPotentialPhoneNumber(trimmedRule)) {
            if (senderDigits.isNotEmpty() && senderDigits == ruleDigits) {
                return true
            }
            // In Russia, normalize +7 and 8 (11 digits starting with 7 or 8)
            if (normalizeRuPhone(senderDigits) == normalizeRuPhone(ruleDigits) && normalizeRuPhone(ruleDigits).isNotEmpty()) {
                return true
            }
        }

        return false
    }

    private fun buildWildcardRegex(rule: String): String {
        val sb = StringBuilder("^")
        for (char in rule) {
            when (char) {
                '*' -> sb.append(".*")
                '?' -> sb.append(".")
                in "\\.[]{}()+^$|" -> sb.append("\\").append(char)
                else -> sb.append(char)
            }
        }
        sb.append("$")
        return sb.toString()
    }

    private fun extractDigits(text: String): String {
        return text.filter { it.isDigit() }
    }

    private fun isPotentialPhoneNumber(text: String): Boolean {
        return text.all { it.isDigit() || it == '+' || it == '-' || it == '(' || it == ')' || it == ' ' } &&
                text.any { it.isDigit() }
    }

    private fun normalizeRuPhone(digits: String): String {
        if (digits.length == 11 && (digits.startsWith("7") || digits.startsWith("8"))) {
            return digits.substring(1)
        }
        return digits
    }
}
