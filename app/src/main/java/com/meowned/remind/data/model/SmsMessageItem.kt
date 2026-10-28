package com.meowned.remind.data.model

enum class ForwardMode {
    WITH_SERVER,
    WITHOUT_SERVER
}

enum class ForwardStatus {
    SUCCESS,
    FAILED,
    SKIPPED_BY_FILTER
}

enum class SimSlotFilter {
    ALL,
    SIM_1,
    SIM_2
}

enum class FilterMode {
    ALLOW_ALL,
    WHITELIST,
    BLACKLIST
}

data class SmsMessageItem(
    val sender: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val simSlot: Int = 1,
    val subId: Int = -1
)
