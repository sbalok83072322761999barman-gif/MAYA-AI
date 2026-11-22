package com.example.data.remote

import android.content.Context
import androidx.annotation.Keep
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

@Keep
@IgnoreExtraProperties
data class CloudUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val activeProvider: String = "GEMINI_FIREBASE",
    val activeModel: String = "gemini-3.8-flash",
    val activeFramework: String = "ANDROID_COMPOSE",
    val activePersona: String = "MAYA",
    val customInstructions: String? = null,
    val customInstructionsEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudMemoryRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = "general",
    val content: String = "",
    val importance: Long = 3L,
    val source: String? = "manual",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudChatBackupRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sessionId: String = "default",
    val role: String = "user",
    val text: String = "",
    val modelUsed: String? = null,
    val providerUsed: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

class FirestoreSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeCloudMemories(): Flow<List<CloudMemoryRecord>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/memories"
        emitAll(
            db.collection("users").document(uid).collection("memories")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        CloudMemoryRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertUserProfile(
        displayName: String,
        email: String?,
        activeProvider: String,
        activeModel: String,
        activeFramework: String,
        activePersona: String,
        customInstructions: String?,
        customInstructionsEnabled: Boolean
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            val existing = docRef.get().await()
            val safeName = displayName.trim().ifBlank { "Maya User" }.take(100)
            val safeInstructions = customInstructions?.take(4000)

            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to email?.take(200),
                    "activeProvider" to activeProvider.take(50),
                    "activeModel" to activeModel.take(120),
                    "activeFramework" to activeFramework.take(60),
                    "activePersona" to activePersona.take(40),
                    "customInstructions" to safeInstructions,
                    "customInstructionsEnabled" to customInstructionsEnabled,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                val updatePayload = mapOf(
                    "displayName" to safeName,
                    "email" to email?.take(200),
                    "activeProvider" to activeProvider.take(50),
                    "activeModel" to activeModel.take(120),
                    "activeFramework" to activeFramework.take(60),
                    "activePersona" to activePersona.take(40),
                    "customInstructions" to safeInstructions,
                    "customInstructionsEnabled" to customInstructionsEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun syncMemoryToCloud(
        docId: String?,
        category: String,
        content: String,
        importance: Int,
        source: String?
    ): Result<String> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/memories"
        return try {
            val uid = requireUserId()
            require(content.isNotBlank() && content.length <= 4000) {
                "Memory content must be between 1 and 4000 characters."
            }
            val safeImp = importance.coerceIn(1, 5)
            val colRef = db.collection("users").document(uid).collection("memories")
            val targetRef = if (!docId.isNullOrBlank()) colRef.document(docId) else colRef.document()
            path = targetRef.path
            val existing = if (!docId.isNullOrBlank()) targetRef.get().await() else null

            if (existing == null || !existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "category" to category.take(50).ifBlank { "general" },
                    "content" to content.take(4000),
                    "importance" to safeImp,
                    "source" to source?.take(60),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                targetRef.set(payload).await()
            } else {
                val updatePayload = mapOf(
                    "category" to category.take(50).ifBlank { "general" },
                    "content" to content.take(4000),
                    "importance" to safeImp,
                    "source" to source?.take(60),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                targetRef.update(updatePayload).await()
            }
            Result.success(targetRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteCloudMemory(memoryId: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/memories/$memoryId"
        return try {
            val uid = requireUserId()
            require(memoryId.isNotBlank()) { "Memory ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("memories").document(memoryId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun backupChatMessage(
        sessionId: String,
        role: String,
        text: String,
        modelUsed: String?,
        providerUsed: String?
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/chats"
        return try {
            val uid = requireUserId()
            val normalizedRole = if (role in setOf("user", "model", "tool")) role else "user"
            val docRef = db.collection("users").document(uid).collection("chats").document()
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "sessionId" to sessionId.take(100).ifBlank { "default" },
                "role" to normalizedRole,
                "text" to text.take(12000).ifBlank { "(empty)" },
                "modelUsed" to modelUsed?.take(120),
                "providerUsed" to providerUsed?.take(60),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }
}
