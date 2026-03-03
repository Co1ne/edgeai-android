package com.edgeaivoice.policy

import android.content.Context
import android.os.Build
import android.os.PowerManager

object ThermalStatusReader {
    fun readStatus(context: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return 0
        return pm.currentThermalStatus
    }

    fun label(status: Int): String {
        return when (status) {
            0 -> "NONE"
            1 -> "LIGHT"
            2 -> "MODERATE"
            3 -> "SEVERE"
            4 -> "CRITICAL"
            5 -> "EMERGENCY"
            6 -> "SHUTDOWN"
            else -> "UNKNOWN($status)"
        }
    }
}
