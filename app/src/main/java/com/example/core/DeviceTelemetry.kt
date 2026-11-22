package com.example.core

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

data class DeviceHealthSnapshot(
    val deviceModel: String,
    val androidVersion: String,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val isLowRamState: Boolean,
    val appHeapUsedMb: Long,
    val appHeapMaxMb: Long,
    val batteryPercent: Int,
    val isPowerSaveMode: Boolean
)

class DeviceTelemetry(private val context: Context) {
    private val appContext = context.applicationContext

    fun captureSnapshot(): DeviceHealthSnapshot {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val runtime = Runtime.getRuntime()
        val heapUsed = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val heapMax = runtime.maxMemory() / (1024 * 1024)

        val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val powerSave = pm?.isPowerSaveMode ?: false

        return DeviceHealthSnapshot(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            totalRamMb = memInfo.totalMem / (1024 * 1024),
            availableRamMb = memInfo.availMem / (1024 * 1024),
            isLowRamState = memInfo.lowMemory,
            appHeapUsedMb = heapUsed,
            appHeapMaxMb = heapMax,
            batteryPercent = batteryPct,
            isPowerSaveMode = powerSave
        )
    }
}
