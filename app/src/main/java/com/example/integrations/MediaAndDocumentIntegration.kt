package com.example.integrations

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.vision.CameraVisionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ExtractedDocumentResult(
    val fileName: String,
    val pageCount: Int,
    val text: String
)

class MediaAndDocumentIntegration(
    private val context: Context,
    private val visionEngine: CameraVisionEngine
) {
    private val appContext = context.applicationContext

    suspend fun extractDocumentText(uri: Uri): Result<ExtractedDocumentResult> = withContext(Dispatchers.IO) {
        val fileName = queryDisplayName(uri) ?: "document"
        val mime = appContext.contentResolver.getType(uri).orEmpty()
        return@withContext try {
            if (mime == "application/pdf" || fileName.endsWith(".pdf", ignoreCase = true)) {
                appContext.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        val pages = renderer.pageCount.coerceAtMost(8)
                        val sb = StringBuilder()
                        for (i in 0 until pages) {
                            renderer.openPage(i).use { page ->
                                val bmp = Bitmap.createBitmap(
                                    (page.width * 1.5f).toInt().coerceAtMost(1200),
                                    (page.height * 1.5f).toInt().coerceAtMost(1600),
                                    Bitmap.Config.ARGB_8888
                                )
                                bmp.eraseColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val pageText = visionEngine.extractOcrText(bmp).getOrDefault("")
                                sb.appendLine("--- Page ${i + 1} ---").appendLine(pageText)
                                bmp.recycle()
                            }
                        }
                        Result.success(ExtractedDocumentResult(fileName, renderer.pageCount, sb.toString().trim()))
                    }
                } ?: Result.failure(IllegalStateException("Unable to open PDF descriptor."))
            } else {
                val text = appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                    it.readText().take(24000)
                }.orEmpty()
                Result.success(ExtractedDocumentResult(fileName, 1, text.ifBlank { "(Empty text document)" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openYouTubeSearch(query: String): Result<String> {
        return try {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            Result.success("Opened YouTube search for '$query'.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openSpotifySearch(query: String): Result<String> {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/${Uri.encode(query)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            Result.success("Opened Spotify search for '$query'.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareTextContent(title: String, body: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        val chooser = Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(chooser)
    }

    suspend fun saveBitmapToGallery(bitmap: Bitmap, titlePrefix: String = "MayaAI"): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val filename = "${titlePrefix}_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MayaAI")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = appContext.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext Result.failure(IllegalStateException("MediaStore insert returned null."))
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            // Also cache a lightweight local path for history
            val cacheFile = File(appContext.filesDir, "generated_images").apply { mkdirs() }
                .let { File(it, filename) }
            FileOutputStream(cacheFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
            Result.success(cacheFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return uri.lastPathSegment
    }
}
