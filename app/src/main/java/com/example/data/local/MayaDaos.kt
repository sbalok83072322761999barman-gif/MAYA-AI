package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY importance DESC, updatedAt DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    suspend fun getAllSnapshot(): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MemoryEntity): Long

    @Update
    suspend fun update(entity: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM memories")
    suspend fun clearAll()
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeSession(sessionId: String = "default"): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 40): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(msg: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: String = "default")
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY isCompleted ASC, triggerAtMillis ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface MacroRuleDao {
    @Query("SELECT * FROM macro_rules ORDER BY isBuiltIn DESC, id ASC")
    fun observeAll(): Flow<List<MacroRuleEntity>>

    @Query("SELECT COUNT(*) FROM macro_rules")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: MacroRuleEntity): Long

    @Update
    suspend fun update(rule: MacroRuleEntity)

    @Query("DELETE FROM macro_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface MissionDao {
    @Query("SELECT * FROM missions ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MissionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mission: MissionEntity): Long

    @Update
    suspend fun update(mission: MissionEntity)

    @Query("DELETE FROM missions WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface MarketingDao {
    @Query("SELECT * FROM business_profiles ORDER BY id DESC")
    fun observeBusinesses(): Flow<List<BusinessProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBusiness(profile: BusinessProfileEntity): Long

    @Query("DELETE FROM business_profiles WHERE id = :id")
    suspend fun deleteBusiness(id: Long)

    @Query("SELECT * FROM marketing_leads ORDER BY id DESC")
    fun observeLeads(): Flow<List<MarketingLeadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLead(lead: MarketingLeadEntity): Long

    @Update
    suspend fun updateLead(lead: MarketingLeadEntity)

    @Query("DELETE FROM marketing_leads WHERE id = :id")
    suspend fun deleteLead(id: Long)
}

@Dao
interface WebsiteDao {
    @Query("SELECT * FROM website_projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<WebsiteProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: WebsiteProjectEntity): Long

    @Update
    suspend fun update(project: WebsiteProjectEntity)

    @Query("DELETE FROM website_projects WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface SmartHomeDao {
    @Query("SELECT * FROM smart_devices ORDER BY id DESC")
    fun observeDevices(): Flow<List<SmartDeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: SmartDeviceEntity): Long

    @Update
    suspend fun update(device: SmartDeviceEntity)

    @Query("DELETE FROM smart_devices WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM captured_notifications ORDER BY timestamp DESC LIMIT 100")
    fun observeRecent(): Flow<List<CapturedNotificationEntity>>

    @Query("SELECT * FROM captured_notifications WHERE notificationKey = :key AND text = :text LIMIT 1")
    suspend fun findDuplicate(key: String, text: String): CapturedNotificationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: CapturedNotificationEntity): Long

    @Query("DELETE FROM captured_notifications")
    suspend fun clearAll()
}

@Dao
interface SecurityProfileDao {
    @Query("SELECT * FROM voice_profiles ORDER BY enrolledAt DESC")
    fun observeVoiceProfiles(): Flow<List<VoiceProfileEntity>>

    @Query("SELECT * FROM voice_profiles")
    suspend fun getVoiceProfilesSnapshot(): List<VoiceProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoiceProfile(profile: VoiceProfileEntity): Long

    @Query("DELETE FROM voice_profiles WHERE id = :id")
    suspend fun deleteVoiceProfile(id: Long)

    @Query("SELECT * FROM face_profiles ORDER BY enrolledAt DESC")
    fun observeFaceProfiles(): Flow<List<FaceProfileEntity>>

    @Query("SELECT * FROM face_profiles WHERE consentGiven = 1")
    suspend fun getConsentedFacesSnapshot(): List<FaceProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFaceProfile(profile: FaceProfileEntity): Long

    @Query("DELETE FROM face_profiles WHERE id = :id")
    suspend fun deleteFaceProfile(id: Long)

    @Query("DELETE FROM face_profiles")
    suspend fun wipeAllFaces()
}

@Dao
interface DiagnosticsDao {
    @Query("SELECT * FROM diagnostic_logs ORDER BY timestamp DESC LIMIT 200")
    fun observeLogs(): Flow<List<DiagnosticLogEntity>>

    @Insert
    suspend fun insertLog(log: DiagnosticLogEntity)

    @Query("DELETE FROM diagnostic_logs")
    suspend fun clearLogs()

    @Query("SELECT * FROM usage_records ORDER BY timestamp DESC LIMIT 500")
    fun observeUsage(): Flow<List<UsageRecordEntity>>

    @Insert
    suspend fun insertUsage(record: UsageRecordEntity)

    @Query("DELETE FROM usage_records")
    suspend fun clearUsage()
}

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_history ORDER BY playedAt DESC LIMIT 100")
    fun observeHistory(): Flow<List<MediaHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: MediaHistoryEntity): Long

    @Update
    suspend fun updateHistory(item: MediaHistoryEntity)

    @Query("SELECT * FROM generated_images ORDER BY createdAt DESC LIMIT 50")
    fun observeGeneratedImages(): Flow<List<GeneratedImageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeneratedImage(image: GeneratedImageEntity): Long
}
