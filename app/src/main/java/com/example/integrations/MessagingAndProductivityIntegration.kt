package com.example.integrations

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.core.SettingsDataStore
import com.example.data.local.SmartDeviceEntity
import com.example.security.KeystoreVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class MessagingAndProductivityIntegration(
    private val context: Context,
    private val vault: KeystoreVault,
    private val settingsStore: SettingsDataStore
) {
    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun toggleSmartDevice(device: SmartDeviceEntity, turnOn: Boolean): Result<String> = withContext(Dispatchers.IO) {
        val url = device.endpointUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return@withContext Result.failure(IllegalArgumentException("Smart device endpoint must start with http:// or https://"))
        }
        try {
            val payload = JSONObject()
                .put("device", device.name)
                .put("state", if (turnOn) "ON" else "OFF")
                .put("on", turnOn)
                .toString()
            val req = Request.Builder()
                .url(url)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${resp.code} from ${device.name}"))
                }
                Result.success("Confirmed HTTP ${resp.code}: ${device.name} -> ${if (turnOn) "ON" else "OFF"}")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendTelegramMessage(text: String): Result<String> = withContext(Dispatchers.IO) {
        val token = vault.getSecret(KeystoreVault.KEY_TELEGRAM_BOT)
            ?: return@withContext Result.failure(IllegalStateException("Telegram Bot token not configured."))
        val chatId = settingsStore.settingsFlow.first().telegramChatId
        if (chatId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Telegram Chat ID is not configured."))
        }
        try {
            val body = JSONObject().put("chat_id", chatId).put("text", text).toString()
            val req = Request.Builder()
                .url("https://api.telegram.org/bot$token/sendMessage")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Telegram HTTP ${resp.code}: ${raw.take(150)}"))
                }
                Result.success("Telegram message delivered to chat $chatId.")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchNotionWorkspace(query: String): Result<String> = withContext(Dispatchers.IO) {
        val token = vault.getSecret(KeystoreVault.KEY_NOTION_TOKEN)
            ?: return@withContext Result.failure(IllegalStateException("Notion API integration token not configured."))
        try {
            val body = JSONObject().put("query", query).put("page_size", 8).toString()
            val req = Request.Builder()
                .url("https://api.notion.com/v1/search")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Notion-Version", "2022-06-28")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Notion HTTP ${resp.code}: ${raw.take(150)}"))
                }
                val results = JSONObject(raw).optJSONArray("results") ?: JSONArray()
                Result.success("Notion returned ${results.length()} matching object(s).")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchGitHubRepoSummary(ownerRepo: String): Result<String> = withContext(Dispatchers.IO) {
        val token = vault.getSecret(KeystoreVault.KEY_GITHUB_TOKEN)
        val clean = ownerRepo.trim().trim('/')
        if (!clean.contains("/")) return@withContext Result.failure(IllegalArgumentException("Use owner/repo format."))
        try {
            val reqB = Request.Builder().url("https://api.github.com/repos/$clean")
                .addHeader("Accept", "application/vnd.github+json")
            if (!token.isNullOrBlank()) reqB.addHeader("Authorization", "Bearer $token")
            client.newCall(reqB.build()).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext Result.failure(IllegalStateException("GitHub HTTP ${resp.code}"))
                val j = JSONObject(raw)
                Result.success("${j.optString("full_name")} | ★ ${j.optInt("stargazers_count")} | Issues: ${j.optInt("open_issues_count")} | Default branch: ${j.optString("default_branch")}")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testTlsMailServer(host: String, port: Int): Result<String> = withContext(Dispatchers.IO) {
        if (host.isBlank()) return@withContext Result.failure(IllegalArgumentException("Mail host is required."))
        try {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            (factory.createSocket(host.trim(), port) as SSLSocket).use { socket ->
                socket.soTimeout = 8000
                socket.startHandshake()
                val reader = BufferedReader(InputStreamReader(socket.inputStream))
                val banner = reader.readLine().orEmpty()
                val cipher = socket.session.cipherSuite
                Result.success("TLS verified ($cipher): $banner")
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("TLS connection failed: ${e.localizedMessage}"))
        }
    }

    fun openEmailDraft(to: String, subject: String, body: String): Result<String> {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(to)}")).apply {
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            Result.success("Opened email composer draft.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Optional authenticated TLS SMTP check/send for port 465 when user explicitly configures credentials in KeystoreVault.
     */
    suspend fun sendSmtpTlsEmail(
        host: String,
        port: Int,
        fromEmail: String,
        toEmail: String,
        subject: String,
        body: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val password = vault.getSecret(KeystoreVault.KEY_SMTP_PASSWORD)
            ?: return@withContext Result.failure(IllegalStateException("SMTP password not set in Keystore Vault."))
        try {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            (factory.createSocket(host.trim(), port) as SSLSocket).use { socket ->
                socket.soTimeout = 10000
                socket.startHandshake()
                val reader = BufferedReader(InputStreamReader(socket.inputStream))
                val writer = OutputStreamWriter(socket.outputStream)
                fun sendLine(cmd: String): String {
                    writer.write("$cmd\r\n")
                    writer.flush()
                    return reader.readLine().orEmpty()
                }
                val greet = reader.readLine().orEmpty()
                if (!greet.startsWith("220")) throw IllegalStateException("SMTP banner error: $greet")
                sendLine("EHLO maya.android.local")
                val authResp = sendLine("AUTH LOGIN")
                if (!authResp.startsWith("334")) throw IllegalStateException("SMTP AUTH rejected: $authResp")
                sendLine(android.util.Base64.encodeToString(fromEmail.toByteArray(), android.util.Base64.NO_WRAP))
                val passResp = sendLine(android.util.Base64.encodeToString(password.toByteArray(), android.util.Base64.NO_WRAP))
                if (!passResp.startsWith("235")) throw IllegalStateException("SMTP login failed: $passResp")
                sendLine("MAIL FROM:<$fromEmail>")
                sendLine("RCPT TO:<$toEmail>")
                sendLine("DATA")
                val msg = "From: $fromEmail\r\nTo: $toEmail\r\nSubject: $subject\r\n\r\n$body\r\n."
                val dataResp = sendLine(msg)
                sendLine("QUIT")
                if (!dataResp.startsWith("250")) throw IllegalStateException("SMTP send failed: $dataResp")
                Result.success("Email sent via TLS SMTP to $toEmail.")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
