package com.meowned.remind.core

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import com.meowned.remind.data.model.SmsMessageItem

object SmsParser {

    fun parseSmsIntent(context: Context, intent: Intent): SmsMessageItem? {
        val messages: Array<SmsMessage> = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            null
        } ?: return null

        if (messages.isEmpty()) return null

        val firstMsg = messages[0]
        val sender = firstMsg.displayOriginatingAddress ?: firstMsg.originatingAddress ?: "Неизвестный"
        val timestamp = firstMsg.timestampMillis

        // Concatenate multipart SMS
        val bodyBuilder = StringBuilder()
        for (msg in messages) {
            val body = msg.displayMessageBody ?: msg.messageBody
            if (body != null) {
                bodyBuilder.append(body)
            }
        }
        val fullBody = bodyBuilder.toString()

        // Extract SIM slot / subscription ID
        val slotExtra = intent.getIntExtra("slot", -1)
            .takeIf { it != -1 }
            ?: intent.getIntExtra("simSlot", -1)
            .takeIf { it != -1 }

        val subId = intent.getIntExtra("subscription", -1)
            .takeIf { it != -1 }
            ?: intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1)
            .takeIf { it != -1 }

        val simSlot = determineSimSlot(context, subId ?: -1, slotExtra)

        return SmsMessageItem(
            sender = sender,
            body = fullBody,
            timestamp = if (timestamp > 0) timestamp else System.currentTimeMillis(),
            simSlot = simSlot,
            subId = subId ?: -1
        )
    }

    fun determineSimSlot(context: Context?, subId: Int, slotExtra: Int? = null): Int {
        if (slotExtra != null && slotExtra >= 0) {
            return mapSlotIndexToSlotNumber(slotExtra)
        }

        if (context != null && subId >= 0) {
            try {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val subInfo = subManager?.getActiveSubscriptionInfo(subId)
                if (subInfo != null) {
                    return mapSlotIndexToSlotNumber(subInfo.simSlotIndex)
                }
            } catch (e: SecurityException) {
                // Permission not granted
            } catch (e: Exception) {
                // Fallback
            }
        }
        return 1
    }

    fun mapSlotIndexToSlotNumber(simSlotIndex: Int): Int {
        return if (simSlotIndex >= 0) simSlotIndex + 1 else 1
    }
}
