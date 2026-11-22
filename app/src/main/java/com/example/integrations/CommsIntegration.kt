package com.example.integrations

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ContactEntry(
    val id: String,
    val name: String,
    val phone: String
)

class CommsIntegration(private val context: Context) {
    private val appContext = context.applicationContext

    private val _callStateLabel = MutableStateFlow("Idle")
    val callStateLabel: StateFlow<String> = _callStateLabel.asStateFlow()

    fun startCallStateObserver() {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            tm.registerTelephonyCallback(
                appContext.mainExecutor,
                object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        _callStateLabel.value = mapCallState(state)
                    }
                }
            )
        } else {
            @Suppress("DEPRECATION")
            tm.listen(object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    _callStateLabel.value = mapCallState(state)
                }
            }, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun mapCallState(state: Int): String = when (state) {
        TelephonyManager.CALL_STATE_RINGING -> "Incoming Ringing"
        TelephonyManager.CALL_STATE_OFFHOOK -> "Call Active"
        else -> "Idle"
    }

    fun searchContacts(query: String): List<ContactEntry> {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val results = mutableListOf<ContactEntry>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = if (query.isBlank()) null else "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        val args = if (query.isBlank()) null else arrayOf("%$query%", "%$query%")

        appContext.contentResolver.query(uri, projection, selection, args, "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC")?.use { cursor ->
            val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext() && results.size < 40) {
                val id = if (idIdx >= 0) cursor.getString(idIdx).orEmpty() else ""
                val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                val num = if (numIdx >= 0) cursor.getString(numIdx).orEmpty() else ""
                if (name.isNotBlank() && num.isNotBlank()) {
                    results.add(ContactEntry(id, name, num))
                }
            }
        }
        return results
    }

    fun placeCall(number: String, directCall: Boolean): Result<String> {
        val clean = number.trim()
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Phone number is required."))
        val hasCallPerm = ContextCompat.checkSelfPermission(appContext, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val action = if (directCall && hasCallPerm) Intent.ACTION_CALL else Intent.ACTION_DIAL
        return try {
            val intent = Intent(action, Uri.parse("tel:${Uri.encode(clean)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            Result.success(if (action == Intent.ACTION_CALL) "Calling $clean..." else "Opened dialer for $clean (grant CALL_PHONE for direct dialing).")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openSmsComposer(number: String, body: String): Result<String> {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number.trim())}")).apply {
                putExtra("sms_body", body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            Result.success("Opened SMS composer draft to $number.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sendSmsDirect(number: String, body: String): Result<String> {
        val clean = number.trim()
        if (clean.isBlank() || body.isBlank()) {
            return Result.failure(IllegalArgumentException("Phone number and message body are required."))
        }
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return openSmsComposer(clean, body)
        }
        return try {
            val smsManager = appContext.getSystemService(android.telephony.SmsManager::class.java)
            smsManager.sendTextMessage(clean, null, body, null, null)
            Result.success("SMS sent via carrier to $clean.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openWhatsAppMessage(phoneOrContact: String, message: String): Result<String> {
        val digits = phoneOrContact.filter { it.isDigit() }
        return try {
            val intent = if (digits.length >= 8) {
                Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=${Uri.encode(message)}"))
            } else {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    setPackage("com.whatsapp")
                    putExtra(Intent.EXTRA_TEXT, message)
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            Result.success("Opened WhatsApp draft for '$phoneOrContact'. Confirm send inside WhatsApp.")
        } catch (e: Exception) {
            Result.failure(IllegalStateException("WhatsApp unavailable: ${e.localizedMessage}"))
        }
    }

    fun launchAppByName(appName: String): Boolean {
        val query = appName.trim().lowercase()
        if (query.isBlank()) return false
        val pm = appContext.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = pm.queryIntentActivities(mainIntent, 0)
        val match = activities.firstOrNull { info ->
            val label = info.loadLabel(pm).toString().lowercase()
            label == query || label.contains(query)
        } ?: return false
        val launch = pm.getLaunchIntentForPackage(match.activityInfo.packageName) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(launch)
        return true
    }
}
