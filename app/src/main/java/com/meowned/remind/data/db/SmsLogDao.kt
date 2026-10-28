package com.meowned.remind.data.db

import androidx.room.*
import com.meowned.remind.data.model.ForwardStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsLogDao {
    @Query("SELECT * FROM sms_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<SmsLogEntity>>

    @Query("SELECT * FROM sms_logs WHERE (:status IS NULL OR status = :status) ORDER BY timestamp DESC LIMIT :limit")
    fun getLogs(status: ForwardStatus? = null, limit: Int = 100): Flow<List<SmsLogEntity>>

    @Query("SELECT * FROM sms_logs WHERE id = :id LIMIT 1")
    suspend fun getLogById(id: Long): SmsLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SmsLogEntity): Long

    @Update
    suspend fun updateLog(log: SmsLogEntity)

    @Query("DELETE FROM sms_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sms_logs")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM sms_logs WHERE status = 'SUCCESS'")
    fun getSuccessCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sms_logs WHERE status = 'FAILED'")
    fun getFailedCount(): Flow<Int>
}
