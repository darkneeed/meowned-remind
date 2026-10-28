package com.meowned.remind.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.meowned.remind.data.model.ForwardStatus

@Entity(
    tableName = "sms_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["status"])
    ]
)
data class SmsLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val simSlot: Int,
    val status: ForwardStatus,
    val errorMessage: String? = null,
    val retryCount: Int = 0
)
