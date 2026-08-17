package com.stressdetect.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether this app may animate.
 *
 * One source of truth for every animation in the app — the breath, the greeting's entry, the
 * hero count-up. A per-animation check is how one of them ends up ignoring the setting.
 *
 * Verified against the SDK source rather than assumed: `ANIMATOR_DURATION_SCALE` is
 * "Scaling factor for Animator-based animations… Setting to 0.0f will cause animations to
 * end immediately", and it is `@Readable`. Someone who turned animations off has asked for
 * stillness; they should not get a breathing face and a number that counts itself up.
 *
 * @return false when the user has asked the system to remove animations.
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) != 0f
        }.getOrDefault(true)
    }
}
