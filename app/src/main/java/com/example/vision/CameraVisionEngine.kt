package com.example.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class CameraVisionEngine(private val context: Context) {
    private val appContext = context.applicationContext
    private val barcodeScanner by lazy { BarcodeScanning.getClient() }
    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun extractOcrText(bitmap: Bitmap): Result<String> {
        return try {
            val result = textRecognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            Result.success(result.text.ifBlank { "No text detected in image." })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun scanBarcodes(bitmap: Bitmap): Result<List<String>> {
        return try {
            val barcodes = barcodeScanner.process(InputImage.fromBitmap(bitmap, 0)).await()
            val values = barcodes.mapNotNull { it.rawValue ?: it.displayValue }
            Result.success(values)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun analyzeImageWithGemini(
        bitmap: Bitmap,
        prompt: String = "Describe what you see clearly, extract any key text or objects, and answer the user's question."
    ): Result<String> {
        return try {
            val scaled = scaleFor4GbDevice(bitmap)
            val model = Firebase.ai.generativeModel("gemini-3.8-flash")
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        image(scaled)
                        text(prompt)
                    }
                )
            }
            val text = response.text?.trim()
                ?: return Result.failure(IllegalStateException("Gemini returned an empty vision response."))
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun decodeUriToBitmap(uri: Uri): Bitmap? {
        return try {
            appContext.contentResolver.openInputStream(uri)?.use { stream ->
                val raw = BitmapFactory.decodeStream(stream) ?: return null
                scaleFor4GbDevice(raw)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleFor4GbDevice(bitmap: Bitmap): Bitmap {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        if (maxDim <= 1024) return bitmap
        val ratio = 1024f / maxDim
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt(),
            (bitmap.height * ratio).toInt(),
            true
        )
    }
}
