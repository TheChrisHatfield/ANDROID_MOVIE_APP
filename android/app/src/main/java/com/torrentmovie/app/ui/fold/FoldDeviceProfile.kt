package com.torrentmovie.app.ui.fold

import android.content.Context
import android.os.Build

/**
 * Samsung Galaxy Z Fold detection, locked in on first app launch on the device.
 * One APK: non-Fold phones never enable two-pane; Z Fold models persist the flag at install/first run.
 */
object FoldDeviceProfile {
    private const val PREFS = "device_profile"
    private const val KEY_Z_FOLD_TWO_PANE = "samsung_z_fold_two_pane"

    /** Minimum inner-screen width (dp) before showing search + detail side by side. */
    const val TWO_PANE_MIN_WIDTH_DP = 600

    fun twoPaneSearchDetailEnabled(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_Z_FOLD_TWO_PANE)) {
            val detected = isSamsungGalaxyZFold()
            prefs.edit().putBoolean(KEY_Z_FOLD_TWO_PANE, detected).apply()
            return detected
        }
        return prefs.getBoolean(KEY_Z_FOLD_TWO_PANE, false)
    }

    fun isSamsungGalaxyZFold(): Boolean = isSamsungGalaxyZFoldModel(
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        device = Build.DEVICE,
    )

    internal fun isSamsungGalaxyZFoldModel(
        manufacturer: String,
        model: String,
        device: String,
    ): Boolean {
        if (!manufacturer.equals("samsung", ignoreCase = true)) return false
        val normalizedModel = model.uppercase()
        if (normalizedModel.startsWith("SM-F")) return true
        // Samsung internal device names for Z Fold line (e.g. q2q, q2q_usa).
        val normalizedDevice = device.lowercase()
        return normalizedDevice.startsWith("q2") || normalizedDevice.contains("fold")
    }
}
