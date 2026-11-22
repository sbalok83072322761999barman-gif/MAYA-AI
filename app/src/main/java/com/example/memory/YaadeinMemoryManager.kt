package com.example.memory

import com.example.data.local.MemoryDao
import com.example.data.local.MemoryEntity
import com.example.data.remote.FirestoreSyncRepository
import com.example.security.KeystoreVault
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

/**
 * Yaadein Long-Term Memory Engine:
 * - Room persistence + lightweight 64-dim character-trigram local vector embedding for semantic recall
 * - Credential/OTP/PIN/CVV sanitizer (never stores secrets)
 * - Atomic JSON backup/restore
 * - Optional Firestore cloud sync when authenticated with Google
 */
class YaadeinMemoryManager(
    private val memoryDao: MemoryDao,
    private val firestoreSync: FirestoreSyncRepository
) {
    val allMemories: Flow<List<MemoryEntity>> = memoryDao.observeAll()

    suspend fun rememberFact(
        content: String,
        category: String = "general",
        importance: Int = 3,
        source: String = "manual",
        syncToCloud: Boolean = true
    ): Result<Long> {
        val clean = content.trim()
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Memory content cannot be empty."))
        if (KeystoreVault.containsSensitiveCredential(clean)) {
            return Result.failure(
                SecurityException("Refused to save sensitive credential (password/OTP/PIN/CVV/API key) to Yaadein memory.")
            )
        }

        // Reconcile near-duplicate memories before inserting
        val existing = memoryDao.getAllSnapshot()
        val vec = computeLightweightEmbedding(clean)
        val duplicate = existing.firstOrNull { mem ->
            mem.content.equals(clean, ignoreCase = true) ||
                cosineSimilarity(vec, parseEmbedding(mem.embeddingJson)) > 0.93f
        }

        val now = System.currentTimeMillis()
        val id = if (duplicate != null) {
            val updated = duplicate.copy(
                content = clean,
                category = category.ifBlank { duplicate.category },
                importance = maxOf(duplicate.importance, importance.coerceIn(1, 5)),
                updatedAt = now,
                syncedToCloud = false
            )
            memoryDao.update(updated)
            updated.id
        } else {
            val entity = MemoryEntity(
                category = category.ifBlank { "general" },
                content = clean,
                importance = importance.coerceIn(1, 5),
                source = source,
                embeddingJson = serializeEmbedding(vec),
                createdAt = now,
                updatedAt = now
            )
            memoryDao.insert(entity)
        }

        if (syncToCloud && Firebase.auth.currentUser != null) {
            val target = memoryDao.getAllSnapshot().firstOrNull { it.id == id }
            if (target != null) {
                val res = firestoreSync.syncMemoryToCloud(
                    docId = target.cloudId.ifBlank { null },
                    category = target.category,
                    content = target.content,
                    importance = target.importance,
                    source = target.source
                )
                res.getOrNull()?.let { cloudId ->
                    memoryDao.update(target.copy(cloudId = cloudId, syncedToCloud = true))
                }
            }
        }
        return Result.success(id)
    }

    suspend fun recallMemory(query: String, limit: Int = 6): List<MemoryEntity> {
        val all = memoryDao.getAllSnapshot()
        if (query.isBlank()) return all.take(limit)
        val qVec = computeLightweightEmbedding(query)
        val qTokens = query.lowercase().split(Regex("\\W+")).filter { it.length >= 2 }.toSet()

        return all.map { mem ->
            val semantic = cosineSimilarity(qVec, parseEmbedding(mem.embeddingJson))
            val lexical = if (qTokens.isEmpty()) 0f else {
                val hits = qTokens.count { mem.content.contains(it, ignoreCase = true) || mem.category.contains(it, ignoreCase = true) }
                hits.toFloat() / qTokens.size
            }
            val importanceBoost = mem.importance * 0.04f
            mem to (semantic * 0.55f + lexical * 0.45f + importanceBoost)
        }
            .filter { it.second > 0.16f }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    suspend fun forgetMemory(id: Long): Result<Unit> {
        val existing = memoryDao.getAllSnapshot().firstOrNull { it.id == id }
        memoryDao.deleteById(id)
        if (existing != null && existing.cloudId.isNotBlank() && Firebase.auth.currentUser != null) {
            firestoreSync.deleteCloudMemory(existing.cloudId)
        }
        return Result.success(Unit)
    }

    suspend fun exportBackupJson(): String {
        val all = memoryDao.getAllSnapshot()
        val arr = JSONArray()
        all.forEach { mem ->
            arr.put(
                JSONObject()
                    .put("category", mem.category)
                    .put("content", mem.content)
                    .put("importance", mem.importance)
                    .put("source", mem.source)
                    .put("createdAt", mem.createdAt)
            )
        }
        return JSONObject()
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("memories", arr)
            .toString(2)
    }

    suspend fun importBackupJson(jsonString: String): Result<Int> {
        return try {
            val root = JSONObject(jsonString)
            val arr = root.getJSONArray("memories")
            var count = 0
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val content = obj.optString("content")
                if (content.isNotBlank() && !KeystoreVault.containsSensitiveCredential(content)) {
                    rememberFact(
                        content = content,
                        category = obj.optString("category", "restored"),
                        importance = obj.optInt("importance", 3),
                        source = "restore",
                        syncToCloud = false
                    )
                    count++
                }
            }
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun computeLightweightEmbedding(text: String): FloatArray {
        val dim = 64
        val vec = FloatArray(dim)
        val normText = " ${text.lowercase().trim()} "
        if (normText.length < 3) return vec
        for (i in 0..normText.length - 3) {
            val h = ((normText[i].code * 31 + normText[i + 1].code) * 31 + normText[i + 2].code)
            val idx = (h and 0x7FFFFFFF) % dim
            vec[idx] += 1f
        }
        var norm = 0f
        for (v in vec) norm += v * v
        val mag = sqrt(norm)
        if (mag > 0f) {
            for (i in vec.indices) vec[i] /= mag
        }
        return vec
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }

    private fun serializeEmbedding(vec: FloatArray): String {
        val arr = JSONArray()
        vec.forEach { arr.put(it.toDouble()) }
        return arr.toString()
    }

    private fun parseEmbedding(json: String): FloatArray {
        if (json.isBlank()) return FloatArray(64)
        return try {
            val arr = JSONArray(json)
            FloatArray(64) { i -> arr.optDouble(i, 0.0).toFloat() }
        } catch (_: Exception) {
            FloatArray(64)
        }
    }
}
