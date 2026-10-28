package com.meowned.remind.core

import android.content.Context
import com.meowned.remind.MeownedRemindApp
import com.meowned.remind.data.db.SmsLogEntity
import com.meowned.remind.data.model.ForwardMode
import com.meowned.remind.data.model.ForwardStatus
import com.meowned.remind.data.model.SmsMessageItem
import com.meowned.remind.network.ServerResult
import com.meowned.remind.network.ServerSender
import com.meowned.remind.network.TelegramResult
import com.meowned.remind.network.TelegramSender
import kotlinx.coroutines.flow.first

object SmsForwardManager {

    suspend fun processSms(context: Context, sms: SmsMessageItem): ForwardStatus {
        val app = context.applicationContext as? MeownedRemindApp ?: MeownedRemindApp.instance
        val prefs = app.preferences
        val dao = app.database.smsLogDao()

        val isEnabled = prefs.isEnabledFlow.first()
        if (!isEnabled) {
            return ForwardStatus.SKIPPED_BY_FILTER
        }

        val simFilter = prefs.simSlotFilterFlow.first()
        val filterMode = prefs.filterModeFlow.first()
        val senderRules = prefs.senderRulesFlow.first()
        val kwWhitelist = prefs.keywordWhitelistFlow.first()
        val kwBlacklist = prefs.keywordBlacklistFlow.first()

        // 1. Filter evaluation
        val (shouldForward, filterReason) = FilterEngine.shouldForward(
            sms = sms,
            simFilter = simFilter,
            filterMode = filterMode,
            senderRules = senderRules,
            keywordWhitelist = kwWhitelist,
            keywordBlacklist = kwBlacklist
        )

        if (!shouldForward) {
            dao.insertLog(
                SmsLogEntity(
                    sender = sms.sender,
                    body = sms.body,
                    timestamp = sms.timestamp,
                    simSlot = sms.simSlot,
                    status = ForwardStatus.SKIPPED_BY_FILTER,
                    errorMessage = filterReason
                )
            )
            return ForwardStatus.SKIPPED_BY_FILTER
        }

        // 2. Format message
        val template = prefs.messageTemplateFlow.first()
        val renderedText = TemplateEngine.render(template, sms)

        // 3. Send message according to active forward mode
        val forwardMode = prefs.forwardModeFlow.first()
        val (finalStatus, errorMsg) = when (forwardMode) {
            ForwardMode.WITH_SERVER -> {
                val serverUrl = prefs.serverUrlFlow.first()
                val apiKey = prefs.serverApiKeyFlow.first()
                val deviceName = prefs.serverDeviceNameFlow.first()
                val res = ServerSender.sendSms(
                    serverUrl = serverUrl,
                    apiKey = apiKey,
                    sms = sms,
                    renderedText = renderedText,
                    deviceName = deviceName
                )
                when (res) {
                    is ServerResult.Success -> Pair(ForwardStatus.SUCCESS, null)
                    is ServerResult.Error -> Pair(ForwardStatus.FAILED, res.message)
                }
            }
            ForwardMode.WITHOUT_SERVER -> {
                val botToken = prefs.telegramBotTokenFlow.first()
                val chatId = prefs.telegramChatIdFlow.first()
                val topicId = prefs.telegramTopicIdFlow.first().takeIf { it.isNotBlank() }
                val res = TelegramSender.sendMessage(
                    botToken = botToken,
                    chatId = chatId,
                    topicId = topicId,
                    text = renderedText,
                    parseMode = "HTML"
                )
                when (res) {
                    is TelegramResult.Success -> Pair(ForwardStatus.SUCCESS, null)
                    is TelegramResult.Error -> Pair(ForwardStatus.FAILED, res.message)
                }
            }
        }

        // 4. Record to Database
        dao.insertLog(
            SmsLogEntity(
                sender = sms.sender,
                body = sms.body,
                timestamp = sms.timestamp,
                simSlot = sms.simSlot,
                status = finalStatus,
                errorMessage = errorMsg
            )
        )

        return finalStatus
    }

    suspend fun retryLog(context: Context, logId: Long): Boolean {
        val app = context.applicationContext as? MeownedRemindApp ?: MeownedRemindApp.instance
        val prefs = app.preferences
        val dao = app.database.smsLogDao()

        val log = dao.getLogById(logId) ?: return false

        val template = prefs.messageTemplateFlow.first()
        val sms = SmsMessageItem(
            sender = log.sender,
            body = log.body,
            timestamp = log.timestamp,
            simSlot = log.simSlot
        )
        val renderedText = TemplateEngine.render(template, sms)

        val forwardMode = prefs.forwardModeFlow.first()
        val (isSuccess, updatedLog) = when (forwardMode) {
            ForwardMode.WITH_SERVER -> {
                val serverUrl = prefs.serverUrlFlow.first()
                val apiKey = prefs.serverApiKeyFlow.first()
                val deviceName = prefs.serverDeviceNameFlow.first()
                val res = ServerSender.sendSms(
                    serverUrl = serverUrl,
                    apiKey = apiKey,
                    sms = sms,
                    renderedText = renderedText,
                    deviceName = deviceName
                )
                when (res) {
                    is ServerResult.Success -> Pair(
                        true,
                        log.copy(
                            status = ForwardStatus.SUCCESS,
                            errorMessage = null,
                            retryCount = log.retryCount + 1
                        )
                    )
                    is ServerResult.Error -> Pair(
                        false,
                        log.copy(
                            status = ForwardStatus.FAILED,
                            errorMessage = res.message,
                            retryCount = log.retryCount + 1
                        )
                    )
                }
            }
            ForwardMode.WITHOUT_SERVER -> {
                val botToken = prefs.telegramBotTokenFlow.first()
                val chatId = prefs.telegramChatIdFlow.first()
                val topicId = prefs.telegramTopicIdFlow.first().takeIf { it.isNotBlank() }
                val res = TelegramSender.sendMessage(
                    botToken = botToken,
                    chatId = chatId,
                    topicId = topicId,
                    text = renderedText,
                    parseMode = "HTML"
                )
                when (res) {
                    is TelegramResult.Success -> Pair(
                        true,
                        log.copy(
                            status = ForwardStatus.SUCCESS,
                            errorMessage = null,
                            retryCount = log.retryCount + 1
                        )
                    )
                    is TelegramResult.Error -> Pair(
                        false,
                        log.copy(
                            status = ForwardStatus.FAILED,
                            errorMessage = res.message,
                            retryCount = log.retryCount + 1
                        )
                    )
                }
            }
        }

        dao.updateLog(updatedLog)
        return isSuccess
    }
}
