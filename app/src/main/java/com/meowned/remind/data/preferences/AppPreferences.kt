package com.meowned.remind.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.meowned.remind.data.model.FilterMode
import com.meowned.remind.data.model.ForwardMode
import com.meowned.remind.data.model.SimSlotFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "meowned_remind_prefs")

class AppPreferences(private val context: Context) {

    companion object {
        val KEY_IS_ENABLED = booleanPreferencesKey("is_enabled")
        val KEY_FORWARD_MODE = stringPreferencesKey("forward_mode")
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_SERVER_API_KEY = stringPreferencesKey("server_api_key")
        val KEY_SERVER_DEVICE_NAME = stringPreferencesKey("server_device_name")
        val KEY_IS_INITIAL_SETUP_DONE = booleanPreferencesKey("is_initial_setup_done")
        val KEY_TELEGRAM_BOT_TOKEN = stringPreferencesKey("telegram_bot_token")
        val KEY_TELEGRAM_CHAT_ID = stringPreferencesKey("telegram_chat_id")
        val KEY_TELEGRAM_TOPIC_ID = stringPreferencesKey("telegram_topic_id")
        val KEY_MESSAGE_TEMPLATE = stringPreferencesKey("message_template")
        val KEY_FILTER_MODE = stringPreferencesKey("filter_mode")
        val KEY_SENDER_RULES = stringPreferencesKey("sender_rules")
        val KEY_KEYWORD_WHITELIST = stringPreferencesKey("keyword_whitelist")
        val KEY_KEYWORD_BLACKLIST = stringPreferencesKey("keyword_blacklist")
        val KEY_SIM_SLOT_FILTER = stringPreferencesKey("sim_slot_filter")

        const val DEFAULT_TEMPLATE = "📬 <b>Входящее SMS</b>\n" +
                "👤 <b>От:</b> <code>{sender}</code>\n" +
                "📱 <b>SIM:</b> SIM {sim}\n" +
                "🕒 <b>Время:</b> {time}\n\n" +
                "💬 <b>Текст:</b>\n{body}"
    }

    val isEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IS_ENABLED] ?: false
    }

    val forwardModeFlow: Flow<ForwardMode> = context.dataStore.data.map { prefs ->
        val name = prefs[KEY_FORWARD_MODE]
        if (name != null) {
            try {
                ForwardMode.valueOf(name)
            } catch (e: Exception) {
                ForwardMode.WITHOUT_SERVER
            }
        } else {
            val sUrl = prefs[KEY_SERVER_URL] ?: ""
            val token = prefs[KEY_TELEGRAM_BOT_TOKEN] ?: ""
            if (sUrl.isNotBlank() && token.isBlank()) {
                ForwardMode.WITH_SERVER
            } else {
                ForwardMode.WITHOUT_SERVER
            }
        }
    }

    val serverUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SERVER_URL] ?: ""
    }

    val serverApiKeyFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SERVER_API_KEY] ?: ""
    }

    val serverDeviceNameFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SERVER_DEVICE_NAME] ?: (android.os.Build.MODEL ?: "Android Device")
    }

    val isInitialSetupDoneFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        val explicitDone = prefs[KEY_IS_INITIAL_SETUP_DONE]
        if (explicitDone != null) {
            explicitDone
        } else {
            val token = prefs[KEY_TELEGRAM_BOT_TOKEN] ?: ""
            val chat = prefs[KEY_TELEGRAM_CHAT_ID] ?: ""
            val sUrl = prefs[KEY_SERVER_URL] ?: ""
            (token.isNotBlank() && chat.isNotBlank()) || sUrl.isNotBlank()
        }
    }

    val telegramBotTokenFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_TELEGRAM_BOT_TOKEN] ?: ""
    }

    val telegramChatIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_TELEGRAM_CHAT_ID] ?: ""
    }

    val telegramTopicIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_TELEGRAM_TOPIC_ID] ?: ""
    }

    val messageTemplateFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_MESSAGE_TEMPLATE] ?: DEFAULT_TEMPLATE
    }

    val filterModeFlow: Flow<FilterMode> = context.dataStore.data.map { prefs ->
        val name = prefs[KEY_FILTER_MODE] ?: FilterMode.ALLOW_ALL.name
        try {
            FilterMode.valueOf(name)
        } catch (e: Exception) {
            FilterMode.ALLOW_ALL
        }
    }

    val senderRulesFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SENDER_RULES] ?: ""
    }

    val keywordWhitelistFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_KEYWORD_WHITELIST] ?: ""
    }

    val keywordBlacklistFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_KEYWORD_BLACKLIST] ?: ""
    }

    val simSlotFilterFlow: Flow<SimSlotFilter> = context.dataStore.data.map { prefs ->
        val name = prefs[KEY_SIM_SLOT_FILTER] ?: SimSlotFilter.ALL.name
        try {
            SimSlotFilter.valueOf(name)
        } catch (e: Exception) {
            SimSlotFilter.ALL
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_IS_ENABLED] = enabled }
    }

    suspend fun setTelegramBotToken(token: String) {
        context.dataStore.edit { it[KEY_TELEGRAM_BOT_TOKEN] = token.trim() }
    }

    suspend fun setTelegramChatId(chatId: String) {
        context.dataStore.edit { it[KEY_TELEGRAM_CHAT_ID] = chatId.trim() }
    }

    suspend fun setTelegramTopicId(topicId: String) {
        context.dataStore.edit { it[KEY_TELEGRAM_TOPIC_ID] = topicId.trim() }
    }

    suspend fun setMessageTemplate(template: String) {
        context.dataStore.edit { it[KEY_MESSAGE_TEMPLATE] = template }
    }

    suspend fun setFilterMode(mode: FilterMode) {
        context.dataStore.edit { it[KEY_FILTER_MODE] = mode.name }
    }

    suspend fun setSenderRules(rules: String) {
        context.dataStore.edit { it[KEY_SENDER_RULES] = rules }
    }

    suspend fun setKeywordWhitelist(keywords: String) {
        context.dataStore.edit { it[KEY_KEYWORD_WHITELIST] = keywords }
    }

    suspend fun setKeywordBlacklist(keywords: String) {
        context.dataStore.edit { it[KEY_KEYWORD_BLACKLIST] = keywords }
    }

    suspend fun setSimSlotFilter(filter: SimSlotFilter) {
        context.dataStore.edit { it[KEY_SIM_SLOT_FILTER] = filter.name }
    }

    suspend fun setForwardMode(mode: ForwardMode) {
        context.dataStore.edit { it[KEY_FORWARD_MODE] = mode.name }
    }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[KEY_SERVER_URL] = url.trim() }
    }

    suspend fun setServerApiKey(key: String) {
        context.dataStore.edit { it[KEY_SERVER_API_KEY] = key.trim() }
    }

    suspend fun setServerDeviceName(name: String) {
        context.dataStore.edit { it[KEY_SERVER_DEVICE_NAME] = name.trim() }
    }

    suspend fun setInitialSetupDone(done: Boolean) {
        context.dataStore.edit { it[KEY_IS_INITIAL_SETUP_DONE] = done }
    }
}
