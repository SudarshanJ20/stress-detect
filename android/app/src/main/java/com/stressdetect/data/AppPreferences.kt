package com.stressdetect.data

import android.content.Context

/**
 * Small local settings store. SharedPreferences rather than DataStore — two booleans and an
 * enum do not justify another dependency, and nothing here is participant data.
 */
class AppPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("stress_detect_prefs", Context.MODE_PRIVATE)

    /** SYSTEM / LIGHT / DARK — defaults to following the system. */
    var themeChoice: String
        get() = prefs.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    /**
     * Demo mode replays the committed fixture instead of reading the phone. It is sticky
     * across launches on purpose: someone rehearsing a demo should not have to re-enable it,
     * and the persistent on-screen chip makes it impossible to forget it is on.
     */
    var demoMode: Boolean
        get() = prefs.getBoolean(KEY_DEMO, false)
        set(value) = prefs.edit().putBoolean(KEY_DEMO, value).apply()

    private companion object {
        const val KEY_THEME = "theme_choice"
        const val KEY_DEMO = "demo_mode"
        const val THEME_SYSTEM = "SYSTEM"
    }
}
