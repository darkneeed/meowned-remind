package com.meowned.remind.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meowned.remind.MeownedRemindApp
import com.meowned.remind.core.SmsForwardManager
import com.meowned.remind.data.db.SmsLogEntity
import com.meowned.remind.data.model.FilterMode
import com.meowned.remind.data.model.ForwardMode
import com.meowned.remind.data.model.ForwardStatus
import com.meowned.remind.data.model.SimSlotFilter
import com.meowned.remind.data.model.SmsMessageItem
import com.meowned.remind.network.ServerResult
import com.meowned.remind.network.ServerSender
import com.meowned.remind.network.TelegramResult
import com.meowned.remind.network.TelegramSender
import com.meowned.remind.service.SmsForwarderService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UiTestState(
    val isLoading: Boolean = false,
    val resultMessage: String? = null,
    val isSuccess: Boolean = false
)

data class SetupData(
    val mode: ForwardMode = ForwardMode.WITHOUT_SERVER,
    val token: String = "",
    val chatId: String = "",
    val topicId: String = "",
    val msgId: Long? = null,
    val serverUrl: String = "",
    val serverApiKey: String = "",
    val deviceName: String = ""
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MeownedRemindApp
    private val prefs = app.preferences
    private val dao = app.database.smsLogDao()

    val isEnabled = prefs.isEnabledFlow.stateIn(viewModelScope, SharingStarted.Lazily, false)

    private val _forwardModeOverride = MutableStateFlow<ForwardMode?>(null)
    val forwardMode: StateFlow<ForwardMode> = combine(_forwardModeOverride, prefs.forwardModeFlow) { override, pref ->
        override ?: pref
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ForwardMode.WITHOUT_SERVER)

    val serverUrl = prefs.serverUrlFlow.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val serverApiKey = prefs.serverApiKeyFlow.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val serverDeviceName = prefs.serverDeviceNameFlow.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val isInitialSetupDone: StateFlow<Boolean?> = prefs.isInitialSetupDoneFlow.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val botToken = prefs.telegramBotTokenFlow.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val chatId = prefs.telegramChatIdFlow.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val topicId = prefs.telegramTopicIdFlow.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val template = prefs.messageTemplateFlow.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val filterMode = prefs.filterModeFlow.stateIn(viewModelScope, SharingStarted.Lazily, FilterMode.ALLOW_ALL)
    val senderRules = prefs.senderRulesFlow.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val keywordWhitelist = prefs.keywordWhitelistFlow.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val keywordBlacklist = prefs.keywordBlacklistFlow.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val simSlotFilter = prefs.simSlotFilterFlow.stateIn(viewModelScope, SharingStarted.Lazily, SimSlotFilter.ALL)

    val isConfigured: StateFlow<Boolean> = combine(
        forwardMode,
        serverUrl,
        botToken,
        chatId
    ) { mode, sUrl, token, chat ->
        when (mode) {
            ForwardMode.WITH_SERVER -> sUrl.isNotBlank()
            ForwardMode.WITHOUT_SERVER -> token.isNotBlank() && chat.isNotBlank()
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    private val _logFilterStatus = MutableStateFlow<ForwardStatus?>(null)
    val logFilterStatus: StateFlow<ForwardStatus?> = _logFilterStatus.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val logs = _logFilterStatus.flatMapLatest { status ->
        dao.getLogs(status = status, limit = 200)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val successCount = dao.getSuccessCount().stateIn(viewModelScope, SharingStarted.Lazily, 0)
    val failedCount = dao.getFailedCount().stateIn(viewModelScope, SharingStarted.Lazily, 0)

    fun setLogFilterStatus(status: ForwardStatus?) {
        _logFilterStatus.value = status
    }

    private val _testState = MutableStateFlow(UiTestState())
    val testState: StateFlow<UiTestState> = _testState.asStateFlow()

    private val _serverTestState = MutableStateFlow(UiTestState())
    val serverTestState: StateFlow<UiTestState> = _serverTestState.asStateFlow()

    private val _pendingSetupData = MutableStateFlow<SetupData?>(null)
    val pendingSetupData: StateFlow<SetupData?> = _pendingSetupData.asStateFlow()

    fun toggleService(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setEnabled(enabled)
            if (enabled) {
                SmsForwarderService.startService(app)
            } else {
                SmsForwarderService.stopService(app)
            }
        }
    }

    fun setForwardMode(mode: ForwardMode) {
        _forwardModeOverride.value = mode
        viewModelScope.launch { prefs.setForwardMode(mode) }
    }
    fun setServerUrl(url: String) = viewModelScope.launch { prefs.setServerUrl(url) }
    fun setServerApiKey(key: String) = viewModelScope.launch { prefs.setServerApiKey(key) }
    fun setServerDeviceName(name: String) = viewModelScope.launch { prefs.setServerDeviceName(name) }
    fun setInitialSetupDone(done: Boolean) = viewModelScope.launch { prefs.setInitialSetupDone(done) }

    fun setBotToken(token: String) = viewModelScope.launch { prefs.setTelegramBotToken(token) }
    fun setChatId(id: String) = viewModelScope.launch { prefs.setTelegramChatId(id) }
    fun setTopicId(id: String) = viewModelScope.launch { prefs.setTelegramTopicId(id) }
    fun setTemplate(text: String) = viewModelScope.launch { prefs.setMessageTemplate(text) }
    fun setFilterMode(mode: FilterMode) = viewModelScope.launch { prefs.setFilterMode(mode) }
    fun setSenderRules(rules: String) = viewModelScope.launch { prefs.setSenderRules(rules) }
    fun setKeywordWhitelist(kw: String) = viewModelScope.launch { prefs.setKeywordWhitelist(kw) }
    fun setKeywordBlacklist(kw: String) = viewModelScope.launch { prefs.setKeywordBlacklist(kw) }
    fun setSimSlotFilter(filter: SimSlotFilter) = viewModelScope.launch { prefs.setSimSlotFilter(filter) }

    fun testServer(url: String? = null, apiKey: String? = null, deviceName: String? = null) {
        viewModelScope.launch {
            _serverTestState.value = UiTestState(isLoading = true)
            val targetUrl = (url ?: serverUrl.value).trim()
            val targetKey = (apiKey ?: serverApiKey.value).trim()
            val targetDevice = (deviceName ?: serverDeviceName.value).trim()

            if (targetUrl.isBlank()) {
                _serverTestState.value = UiTestState(
                    isLoading = false,
                    resultMessage = "Заполните URL сервера!",
                    isSuccess = false
                )
                return@launch
            }

            prefs.setServerUrl(targetUrl)
            if (apiKey != null) prefs.setServerApiKey(targetKey)
            if (deviceName != null) prefs.setServerDeviceName(targetDevice)

            val res = ServerSender.testConnection(targetUrl, targetKey, targetDevice)
            when (res) {
                is ServerResult.Success -> {
                    val msg = if (res.responseBody.isNotBlank() && res.responseBody.length <= 100) {
                        "Подключение успешно! Ответ: ${res.responseBody}"
                    } else {
                        "Подключение к серверу успешно проверено (HTTP 200 OK)!"
                    }
                    _serverTestState.value = UiTestState(
                        isLoading = false,
                        resultMessage = msg,
                        isSuccess = true
                    )
                }
                is ServerResult.Error -> {
                    _serverTestState.value = UiTestState(
                        isLoading = false,
                        resultMessage = "Ошибка подключения: ${res.message}",
                        isSuccess = false
                    )
                }
            }
        }
    }

    fun resetServerTestState() {
        _serverTestState.value = UiTestState()
    }

    fun testTelegram(token: String? = null, chatId: String? = null, topicId: String? = null) {
        viewModelScope.launch {
            _testState.value = UiTestState(isLoading = true)
            val targetToken = (token ?: botToken.value).trim()
            val targetChat = (chatId ?: this@MainViewModel.chatId.value).trim()
            val targetTopic = (topicId ?: this@MainViewModel.topicId.value).trim().takeIf { it.isNotBlank() }

            if (targetToken.isBlank() || targetChat.isBlank()) {
                _testState.value = UiTestState(
                    isLoading = false,
                    resultMessage = "Заполните Bot Token и Chat ID!",
                    isSuccess = false
                )
                return@launch
            }

            prefs.setTelegramBotToken(targetToken)
            prefs.setTelegramChatId(targetChat)
            if (topicId != null) prefs.setTelegramTopicId(targetTopic ?: "")

            val res = TelegramSender.testConnection(targetToken, targetChat, targetTopic)
            when (res) {
                is TelegramResult.Success -> {
                    _testState.value = UiTestState(
                        isLoading = false,
                        resultMessage = "Тестовое сообщение успешно отправлено в Telegram!",
                        isSuccess = true
                    )
                }
                is TelegramResult.Error -> {
                    _testState.value = UiTestState(
                        isLoading = false,
                        resultMessage = "Ошибка отправки: ${res.message}",
                        isSuccess = false
                    )
                }
            }
        }
    }

    fun resetTestState() {
        _testState.value = UiTestState()
    }

    fun retryLog(logId: Long) {
        viewModelScope.launch {
            SmsForwardManager.retryLog(app, logId)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            dao.clearAll()
        }
    }

    fun processQrCode(rawData: String, onResult: (Boolean, String) -> Unit) {
        when (val result = com.meowned.remind.core.QrCodeParser.parse(rawData)) {
            is com.meowned.remind.core.QrParseResult.Success -> {
                val d = result.data
                _pendingSetupData.value = SetupData(
                    mode = d.mode,
                    token = d.token,
                    chatId = d.chatId,
                    topicId = d.topicId,
                    msgId = d.msgId,
                    serverUrl = d.serverUrl,
                    serverApiKey = d.serverApiKey,
                    deviceName = d.deviceName
                )
                onResult(true, result.message)
            }
            is com.meowned.remind.core.QrParseResult.Error -> {
                onResult(false, result.message)
            }
        }
    }

    fun confirmSetup(onComplete: (Boolean, String) -> Unit) {
        val setup = _pendingSetupData.value ?: return
        viewModelScope.launch {
            try {
                setForwardMode(setup.mode)
                prefs.setInitialSetupDone(true)
                prefs.setEnabled(true)

                if (setup.mode == ForwardMode.WITH_SERVER) {
                    prefs.setServerUrl(setup.serverUrl)
                    prefs.setServerApiKey(setup.serverApiKey)
                    if (setup.deviceName.isNotBlank()) {
                        prefs.setServerDeviceName(setup.deviceName)
                    }
                    SmsForwarderService.startService(app)
                    _pendingSetupData.value = null
                    onComplete(true, "Устройство успешно настроено в режиме 'С сервером'!")
                } else {
                    prefs.setTelegramBotToken(setup.token)
                    prefs.setTelegramChatId(setup.chatId)
                    prefs.setTelegramTopicId(setup.topicId)
                    SmsForwarderService.startService(app)

                    val successCaption = "✅ <b>Настройки успешно применены!</b> 🎉\n\n" +
                            "📱 <b>Телефон:</b> подключен\n" +
                            "🛡️ <b>Служба SMS Forwarder:</b> активна 24/7\n" +
                            "💬 Все входящие SMS будут пересылаться в этот чат."

                    var editedSuccessfully = false
                    if (setup.msgId != null && setup.msgId > 0) {
                        val editRes = TelegramSender.editMessageCaption(setup.token, setup.chatId, setup.msgId, successCaption)
                        TelegramSender.setMessageReaction(setup.token, setup.chatId, setup.msgId, "🎉")
                        editedSuccessfully = editRes is TelegramResult.Success
                    }

                    if (!editedSuccessfully) {
                        TelegramSender.sendMessage(setup.token, setup.chatId, setup.topicId.takeIf { it.isNotBlank() }, successCaption)
                    }

                    _pendingSetupData.value = null
                    onComplete(true, "Устройство успешно подключено к Telegram!")
                }
            } catch (e: Exception) {
                _pendingSetupData.value = null
                onComplete(false, "Ошибка применения настроек: ${e.localizedMessage ?: "Неизвестная ошибка"}")
            }
        }
    }

    fun dismissSetup() {
        _pendingSetupData.value = null
    }

    fun sendMockSms() {
        viewModelScope.launch {
            val mockSms = SmsMessageItem(
                sender = "+79991234567",
                body = "Код подтверждения: 749213. Не сообщайте никому.",
                timestamp = System.currentTimeMillis(),
                simSlot = 1
            )
            SmsForwardManager.processSms(app, mockSms)
        }
    }
}
