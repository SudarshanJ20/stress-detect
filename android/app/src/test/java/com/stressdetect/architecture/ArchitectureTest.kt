package com.stressdetect.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/**
 * Konsist architecture tests — the reason this app is ONE Gradle module with packages
 * rather than several modules. Without a compiler-enforced boundary these rules are what
 * keep the layering real, so they are listed as hard constraints in android/CLAUDE.md.
 * Do not delete or weaken them; if a rule blocks you, the design changed and that is a
 * conversation, not a test edit.
 */
class ArchitectureTest {

    private val scope = Konsist.scopeFromProject()

    /**
     * `ui` must not touch `sensing`.
     *
     * Screens have no business holding raw sensor readers: it is how a UI callback ends up
     * querying the CallLog on the main thread, and how raw data leaks into a screenshot or
     * a log. The UI goes through `data` (see `ExtractionGateway`).
     */
    @Test
    fun `ui does not depend on sensing`() {
        scope.files
            .withPackage("com.stressdetect.ui..")
            .assertFalse(testName = "ui must not import sensing") { file ->
                file.imports.any { it.name.startsWith("com.stressdetect.sensing") }
            }
    }

    /**
     * Raw platform sensor/provider types stay inside `sensing`.
     *
     * `sensing` normalizes them into plain domain types ([com.stressdetect.sensing.RawUsageEvent],
     * epoch-second lists). Once a `Cursor` or a `UsageEvents.Event` escapes that package, the
     * privacy surface stops being reviewable in one place — which is exactly what the
     * typing-privacy and data-egress constraints require.
     */
    @Test
    fun `raw sensor types are confined to sensing`() {
        val rawSensorTypes = listOf(
            "android.app.usage.UsageEvents",
            "android.app.usage.UsageStats",
            "android.app.usage.UsageStatsManager",
            "android.provider.CallLog",
            "android.provider.Telephony",
            "android.database.Cursor",
            "android.hardware.Sensor",
            "android.hardware.SensorEvent",
            "android.hardware.SensorManager",
            "android.view.accessibility.AccessibilityEvent",
            "android.location.Location",
        )
        scope.files
            .filter { file -> file.packagee?.name?.startsWith("com.stressdetect.sensing") != true }
            .assertFalse(testName = "raw sensor types must not be referenced outside sensing") { file ->
                file.imports.any { import -> rawSensorTypes.any { import.name.startsWith(it) } }
            }
    }

    /**
     * `inference` may depend on `features`, never the reverse.
     *
     * Feature extraction must stay a pure, model-independent mirror of `ml/src/features`.
     * If it could reach into `inference`, feature values could start depending on which
     * model is loaded — and parity with the Python extractor would be unprovable.
     */
    @Test
    fun `features does not depend on inference`() {
        scope.files
            .withPackage("com.stressdetect.features..")
            .assertFalse(testName = "features must not import inference") { file ->
                file.imports.any { it.name.startsWith("com.stressdetect.inference") }
            }
    }

    /**
     * Corollary of the rule above: `features` is a pure-JVM mirror of the Python extractor,
     * so it must not import Android at all. That is what lets the parity test run as a plain
     * JVM unit test — no emulator, no Robolectric, nothing that could diverge from what the
     * Python side computes.
     */
    @Test
    fun `features is free of android dependencies`() {
        scope.files
            .withPackage("com.stressdetect.features..")
            .assertFalse(testName = "features must not import android.*") { file ->
                file.imports.any { it.name.startsWith("android.") || it.name.startsWith("androidx.") }
            }
    }

    /**
     * `ui` must not hold the model either.
     *
     * Screens render a result; they do not run inference. Routing through `data` is what
     * keeps the "spec_version mismatch → refuse to run" check in one place instead of
     * something a screen could skip.
     */
    @Test
    fun `ui does not depend on inference`() {
        scope.files
            .withPackage("com.stressdetect.ui..")
            .assertFalse(testName = "ui must not import inference") { file ->
                file.imports.any { it.name.startsWith("com.stressdetect.inference") }
            }
    }

    /**
     * `survey` is the PSS-4 instrument: pure Kotlin, no Android, no dependencies on our own
     * packages. It has to stay that way so the published wording and the reverse-scoring
     * can be unit-tested directly, and so nothing in the app can quietly make the
     * questionnaire depend on phone data.
     */
    @Test
    fun `survey is a pure domain package`() {
        scope.files
            .withPackage("com.stressdetect.survey..")
            .assertFalse(testName = "survey must not import android or other app packages") { file ->
                file.imports.any {
                    it.name.startsWith("android.") || it.name.startsWith("androidx.") ||
                        it.name.startsWith("com.stressdetect.")
                }
            }
    }

    /** `sensing` must not depend on `ui` or `data` — it is a leaf that others read from. */
    @Test
    fun `sensing depends on neither ui nor data`() {
        scope.files
            .withPackage("com.stressdetect.sensing..")
            .assertFalse(testName = "sensing must not import ui or data") { file ->
                file.imports.any {
                    it.name.startsWith("com.stressdetect.ui") ||
                        it.name.startsWith("com.stressdetect.data")
                }
            }
    }

    /**
     * Nothing may name a typed character, key code, or message body. The typing collector
     * must be architecturally incapable of receiving text (root CLAUDE.md constraint 1),
     * and the comms sources read a DATE column and nothing else — this catches the moment
     * someone adds a field that would hold content.
     */
    @Test
    fun `no property names suggest captured content`() {
        val forbidden = listOf(
            "keyChar", "typedText", "messageBody", "smsBody", "phoneNumber",
            "contactName", "callerName", "textContent",
        )
        scope.properties()
            .assertFalse(testName = "no property may hold typed/message content") { property ->
                forbidden.any { property.name.equals(it, ignoreCase = true) }
            }
    }

    /** Every Room DAO stays in `data`, so all persistence is reviewable in one package. */
    @Test
    fun `room daos live in the data package`() {
        scope.interfaces()
            .filter { it.hasAnnotationWithName("androidx.room.Dao", "Dao") }
            .assertTrue(testName = "@Dao interfaces belong in com.stressdetect.data") {
                it.resideInPackage("com.stressdetect.data..")
            }
    }
}
