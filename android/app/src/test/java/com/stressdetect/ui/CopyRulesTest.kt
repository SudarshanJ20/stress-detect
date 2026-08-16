package com.stressdetect.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The research vocabulary is confined to About.
 *
 * A rule like "use everyday words" survives exactly as long as someone remembers it. This
 * reads the screen sources and fails if the jargon reappears, because the failure mode is
 * gradual: one "validated" slips into a caption, then a "spec version" into a footer, and
 * the app is a lab readout again.
 *
 * About and `Factors` are exempt — About is *supposed* to carry the full technical account,
 * and `Factors` holds the labels for the model read-out shown there.
 */
class CopyRulesTest {

    private val bannedOutsideAbout = listOf(
        "questionnaire", "validated", "attribution", "construct", "percentile",
        "baseline", "correlation", "occlusion", "extractor",
    )

    /**
     * "model" and "spec" need word-boundary matching: "spec" appears inside "specific" and
     * "especially", and "model" inside "modelling" — a naive substring check would ban
     * ordinary English.
     */
    private val bannedWholeWords = listOf("model", "models", "spec", "onnx", "pss-4", "pss4")

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "docs/feature-spec.md").isFile) return dir
            dir = dir.parentFile
        }
        throw AssertionError("could not locate the repo root")
    }

    private fun userFacingSources(): List<File> {
        val screens = repoRoot().resolve("android/app/src/main/java/com/stressdetect/ui")
        assertTrue("cannot find ${screens.path}", screens.isDirectory)
        return screens.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.name == "AboutScreen.kt" }   // the one place jargon belongs
            .filterNot { it.name == "Factors.kt" }       // labels for About's model read-out
            .toList()
    }

    /**
     * Only the literal strings a user can read — not comments, not identifiers. The code
     * may legitimately talk about the model; the SCREEN may not.
     */
    private fun userVisibleStrings(source: String): List<String> {
        val withoutBlockComments = source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        val withoutLineComments = withoutBlockComments.lines()
            .joinToString("\n") { it.substringBefore("//") }
        return Regex(""""([^"\\]*(?:\\.[^"\\]*)*)"""")
            .findAll(withoutLineComments)
            .map { it.groupValues[1] }
            // A ${'$'}{...} interpolation is an EXPRESSION, not words on screen: the identifier
            // Pss4.ITEMS.size is not something a user reads.
            .map { it.replace(Regex("""\${'$'}\{[^}]*\}"""), "") }
            .filter { it.length > 3 && it.any { char -> char == ' ' } }   // prose, not keys
            .toList()
    }

    @Test
    fun `no research vocabulary outside About`() {
        val violations = mutableListOf<String>()
        for (file in userFacingSources()) {
            for (text in userVisibleStrings(file.readText())) {
                val lower = text.lowercase()
                for (word in bannedOutsideAbout) {
                    if (lower.contains(word)) violations += "${file.name}: '$word' in \"$text\""
                }
                for (word in bannedWholeWords) {
                    if (Regex("""\b${Regex.escape(word)}\b""").containsMatchIn(lower)) {
                        violations += "${file.name}: '$word' in \"$text\""
                    }
                }
            }
        }
        assertTrue(
            "research vocabulary leaked out of About:\n  " + violations.joinToString("\n  "),
            violations.isEmpty(),
        )
    }

    @Test
    fun `no clinical vocabulary outside About`() {
        // About is exempt because it has to DISCLAIM these things — "not a medical device",
        // "does not diagnose anything" — which is the opposite of making a clinical claim.
        val clinical = listOf(
            "diagnos", "disorder", "symptom", "patient", "illness", "mental health condition",
        )
        val violations = mutableListOf<String>()
        for (file in userFacingSources()) {
            for (text in userVisibleStrings(file.readText())) {
                val lower = text.lowercase()
                for (word in clinical) {
                    if (lower.contains(word)) violations += "${file.name}: '$word' in \"$text\""
                }
            }
        }
        assertTrue(
            "clinical vocabulary outside About:\n  " + violations.joinToString("\n  "),
            violations.isEmpty(),
        )
    }

    @Test
    fun `no alarming language anywhere, including About`() {
        // This one has NO exemption. Nothing in this app, on any screen, tells someone
        // their state is severe, dangerous, abnormal, or a risk.
        val alarming = listOf(
            "severe", "dangerous", "abnormal", "you are highly stressed", "at risk",
            "concerning", "worrying level", "critical",
        )
        val screens = repoRoot().resolve("android/app/src/main/java/com/stressdetect/ui")
        val violations = mutableListOf<String>()
        for (file in screens.walkTopDown().filter { it.isFile && it.extension == "kt" }) {
            for (text in userVisibleStrings(file.readText())) {
                val lower = text.lowercase()
                for (word in alarming) {
                    if (lower.contains(word)) violations += "${file.name}: '$word' in \"$text\""
                }
            }
        }
        assertTrue(
            "alarming language in the UI:\n  " + violations.joinToString("\n  "),
            violations.isEmpty(),
        )
    }

    @Test
    fun `About still carries the honest evaluation account`() {
        // The redesign moved this content; it must not have quietly lost it. These are the
        // specific claims that make the account honest rather than a vague disclaimer.
        val about = repoRoot()
            .resolve("android/app/src/main/java/com/stressdetect/ui/screens/AboutScreen.kt")
            .readText()
        for (required in listOf(
            "1157",          // the evaluation n
            "20.8",          // the model's error
            "16.9",          // the per-person mean it lost to
            "19.9",          // the global mean it also lost to
            "−0.12",         // the negative rank correlation
            "no internet permission",
            "no official cut-offs",
            "7 days",
        )) {
            assertTrue("About no longer mentions '$required'", about.contains(required))
        }
    }
}
