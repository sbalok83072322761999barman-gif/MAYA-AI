package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cloudId: String = "",
    val category: String,
    val content: String,
    val importance: Int = 3,
    val source: String = "manual",
    val embeddingJson: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncedToCloud: Boolean = false
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String = "default",
    val role: String, // "user", "model", "tool"
    val text: String,
    val modelUsed: String = "",
    val providerUsed: String = "",
    val imageUri: String? = null,
    val toolCallsJson: String? = null,
    val tokensUsed: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val triggerAtMillis: Long,
    val repeatDaily: Boolean = false,
    val isAlarm: Boolean = false,
    val isFavorite: Boolean = false,
    val isCompleted: Boolean = false,
    val kind: String = "REMINDER" // REMINDER, ALARM, NUDGE, WATCH, MISSION
)

@Entity(tableName = "macro_rules")
data class MacroRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val triggerType: String,
    val actionJson: String,
    val isBuiltIn: Boolean = false,
    val isEnabled: Boolean = true
)

@Entity(tableName = "missions")
data class MissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val goal: String,
    val status: String = "PENDING", // PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    val stepsJson: String = "[]",
    val currentStep: Int = 0,
    val resultSummary: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "business_profiles")
data class BusinessProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val location: String,
    val services: String,
    val targetAudience: String,
    val websiteUrl: String = "",
    val notes: String = ""
)

@Entity(tableName = "marketing_leads")
data class MarketingLeadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long = 0,
    val name: String,
    val contact: String,
    val status: String = "NEW", // NEW, CONTACTED, QUALIFIED, WON
    val followUpNote: String = "",
    val nextFollowUpAt: Long = 0L,
    val kpiValue: Double = 0.0
)

@Entity(tableName = "website_projects")
data class WebsiteProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val htmlContent: String,
    val cssContent: String = "",
    val jsContent: String = "",
    val status: String = "DRAFT",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "smart_devices")
data class SmartDeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String = "LIGHT", // LIGHT, SWITCH, SENSOR
    val endpointUrl: String,
    val mqttTopic: String = "",
    val isOn: Boolean = false,
    val lastStatus: String = "Not tested",
    val lastCheckedAt: Long = 0L
)

@Entity(tableName = "captured_notifications")
data class CapturedNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notificationKey: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val isDuplicate: Boolean = false,
    val summary: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "face_profiles")
data class FaceProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val relationship: String,
    val embeddingJson: String,
    val consentGiven: Boolean = false,
    val photoPath: String = "",
    val enrolledAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "voice_profiles")
data class VoiceProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val role: String = "BOSS", // BOSS, FAMILY, FRIEND, UNKNOWN
    val embeddingJson: String,
    val enrolledAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "diagnostic_logs")
data class DiagnosticLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val level: String = "INFO", // INFO, WARN, ERROR
    val tag: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "usage_records")
data class UsageRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val provider: String,
    val model: String,
    val success: Boolean,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val errorType: String? = null,
    val latencyMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "media_history")
data class MediaHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val title: String,
    val uriOrUrl: String,
    val source: String = "YOUTUBE",
    val isFavorite: Boolean = false,
    val playedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "generated_images")
data class GeneratedImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prompt: String,
    val model: String,
    val filePath: String,
    val createdAt: Long = System.currentTimeMillis()
)
