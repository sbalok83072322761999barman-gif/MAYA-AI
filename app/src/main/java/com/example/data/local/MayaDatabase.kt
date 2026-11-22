package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MemoryEntity::class,
        ChatMessageEntity::class,
        ReminderEntity::class,
        MacroRuleEntity::class,
        MissionEntity::class,
        BusinessProfileEntity::class,
        MarketingLeadEntity::class,
        WebsiteProjectEntity::class,
        SmartDeviceEntity::class,
        CapturedNotificationEntity::class,
        FaceProfileEntity::class,
        VoiceProfileEntity::class,
        DiagnosticLogEntity::class,
        UsageRecordEntity::class,
        MediaHistoryEntity::class,
        GeneratedImageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MayaDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun chatDao(): ChatDao
    abstract fun reminderDao(): ReminderDao
    abstract fun macroRuleDao(): MacroRuleDao
    abstract fun missionDao(): MissionDao
    abstract fun marketingDao(): MarketingDao
    abstract fun websiteDao(): WebsiteDao
    abstract fun smartHomeDao(): SmartHomeDao
    abstract fun notificationDao(): NotificationDao
    abstract fun securityProfileDao(): SecurityProfileDao
    abstract fun diagnosticsDao(): DiagnosticsDao
    abstract fun mediaDao(): MediaDao

    companion object {
        @Volatile
        private var INSTANCE: MayaDatabase? = null

        fun getInstance(context: Context): MayaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MayaDatabase::class.java,
                    "maya_ai_database.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
