package com.example.faces

import android.content.Context
import android.graphics.Bitmap
import com.example.data.local.FaceProfileEntity
import com.example.data.local.SecurityProfileDao
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.math.sqrt

data class FaceMatchResult(
    val facesDetected: Int,
    val matchedProfile: FaceProfileEntity?,
    val similarity: Float,
    val message: String
)

class FaceRecognitionEngine(
    private val context: Context,
    private val securityDao: SecurityProfileDao
) {
    private val appContext = context.applicationContext
    val profiles: Flow<List<FaceProfileEntity>> = securityDao.observeFaceProfiles()

    private val detector by lazy {
        val opts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
        FaceDetection.getClient(opts)
    }

    fun hasExternalYunetOrSfaceModels(): Boolean {
        val dir = File(appContext.filesDir, "models/faces")
        return dir.exists() && dir.listFiles()?.any { it.extension in setOf("onnx", "tflite") } == true
    }

    suspend fun enrollFace(
        bitmap: Bitmap,
        name: String,
        relationship: String,
        consentGiven: Boolean
    ): Result<FaceProfileEntity> {
        if (!consentGiven) {
            return Result.failure(SecurityException("Explicit consent is required before storing any face profile."))
        }
        val cleanName = name.trim()
        if (cleanName.isBlank()) return Result.failure(IllegalArgumentException("Name is required."))

        val faces = detector.process(InputImage.fromBitmap(bitmap, 0)).await()
        val face = faces.firstOrNull()
            ?: return Result.failure(IllegalStateException("No face detected in photo. Ensure clear front lighting."))

        val descriptor = computeGeometricDescriptor(face)
            ?: return Result.failure(IllegalStateException("Facial landmarks incomplete; hold camera straight and retry."))

        val file = File(appContext.filesDir, "faces").apply { mkdirs() }
            .let { File(it, "face_${System.currentTimeMillis()}.jpg") }
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }

        val entity = FaceProfileEntity(
            name = cleanName,
            relationship = relationship.ifBlank { "Friend" },
            embeddingJson = JSONArray().apply { descriptor.forEach { put(it.toDouble()) } }.toString(),
            consentGiven = true,
            photoPath = file.absolutePath
        )
        val id = securityDao.insertFaceProfile(entity)
        return Result.success(entity.copy(id = id))
    }

    suspend fun recognizeFace(bitmap: Bitmap): Result<FaceMatchResult> {
        return try {
            val faces = detector.process(InputImage.fromBitmap(bitmap, 0)).await()
            if (faces.isEmpty()) {
                return Result.success(FaceMatchResult(0, null, 0f, "No face detected in frame."))
            }
            val probe = computeGeometricDescriptor(faces.first())
                ?: return Result.success(FaceMatchResult(faces.size, null, 0f, "Face detected (${faces.size}), landmarks obscured."))

            val consented = securityDao.getConsentedFacesSnapshot()
            if (consented.isEmpty()) {
                return Result.success(FaceMatchResult(faces.size, null, 0f, "Face detected, no consented gallery profiles enrolled."))
            }

            var best: FaceProfileEntity? = null
            var bestScore = -1f
            for (p in consented) {
                val ref = parseDescriptor(p.embeddingJson)
                val sim = cosine(probe, ref)
                if (sim > bestScore) {
                    bestScore = sim
                    best = p
                }
            }
            val match = if (bestScore >= 0.94f) best else null
            val msg = if (match != null) {
                "Matched ${match.name} (${match.relationship}) at ${(bestScore * 100).toInt()}% landmark similarity"
            } else {
                "Unknown face detected (${(bestScore * 100).toInt()}%)"
            }
            Result.success(FaceMatchResult(faces.size, match, bestScore.coerceIn(0f, 1f), msg))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteProfile(profile: FaceProfileEntity) {
        if (profile.photoPath.isNotBlank()) {
            runCatching { File(profile.photoPath).delete() }
        }
        securityDao.deleteFaceProfile(profile.id)
    }

    suspend fun wipeAllFaceData() {
        securityDao.wipeAllFaces()
        File(appContext.filesDir, "faces").deleteRecursively()
    }

    private fun computeGeometricDescriptor(face: Face): FloatArray? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position ?: return null
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position ?: return null
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position ?: return null
        val box = face.boundingBox
        val interOcular = hypot((rightEye.x - leftEye.x).toDouble(), (rightEye.y - leftEye.y).toDouble()).toFloat().coerceAtLeast(1f)

        val vec = floatArrayOf(
            box.width().toFloat() / box.height().coerceAtLeast(1),
            hypot((nose.x - leftEye.x).toDouble(), (nose.y - leftEye.y).toDouble()).toFloat() / interOcular,
            hypot((nose.x - rightEye.x).toDouble(), (nose.y - rightEye.y).toDouble()).toFloat() / interOcular,
            hypot((mouthRight.x - mouthLeft.x).toDouble(), (mouthRight.y - mouthLeft.y).toDouble()).toFloat() / interOcular,
            hypot((mouthLeft.x - leftEye.x).toDouble(), (mouthLeft.y - leftEye.y).toDouble()).toFloat() / interOcular,
            hypot((mouthRight.x - rightEye.x).toDouble(), (mouthRight.y - rightEye.y).toDouble()).toFloat() / interOcular,
            hypot((nose.x - (mouthLeft.x + mouthRight.x) / 2f).toDouble(), (nose.y - (mouthLeft.y + mouthRight.y) / 2f).toDouble()).toFloat() / interOcular
        )
        var norm = 0f
        for (v in vec) norm += v * v
        val mag = sqrt(norm)
        if (mag > 0f) for (i in vec.indices) vec[i] /= mag
        return vec
    }

    private fun parseDescriptor(json: String): FloatArray {
        return try {
            val arr = JSONArray(json)
            FloatArray(arr.length()) { i -> arr.optDouble(i, 0.0).toFloat() }
        } catch (_: Exception) {
            FloatArray(7)
        }
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        for (i in 0 until minOf(a.size, b.size)) dot += a[i] * b[i]
        return dot
    }
}
