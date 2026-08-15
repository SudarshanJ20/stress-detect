package com.stressdetect.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guard: the Kotlin [SpecConstants.SPEC_VERSION] must equal the version declared in
 * `docs/feature-spec.md`.
 *
 * Kotlin mirror of `ml/tests/test_spec_version.py`. Version drift is the exact failure the
 * SPEC_VERSION field exists to catch: a code/doc/model mismatch means the Kotlin extractor
 * and the trained ONNX model disagree SILENTLY, producing confident nonsense. So it is a
 * loud test, not a convention.
 */
class SpecVersionTest {

    private val versionPattern = Regex("""SPEC_VERSION:\s*(v\d+\.\d+\.\d+)""")

    @Test
    fun `kotlin spec version matches docs feature-spec md`() {
        val declared = documentSpecVersion()
        assertEquals(
            "SPEC_VERSION DRIFT — Kotlin (features/SpecConstants.kt) = " +
                "${SpecConstants.SPEC_VERSION} but docs/feature-spec.md declares $declared. " +
                "Bump ALL of: the doc, ml/src/features/spec_constants.py, this constant, and " +
                "the ONNX export metadata. See feature-spec.md 'Versioning'.",
            declared,
            SpecConstants.SPEC_VERSION,
        )
    }

    @Test
    fun `kotlin spec version is well-formed`() {
        assertTrue(
            "SPEC_VERSION '${SpecConstants.SPEC_VERSION}' is not vMAJOR.MINOR.PATCH",
            versionPattern.matches("SPEC_VERSION: ${SpecConstants.SPEC_VERSION}"),
        )
    }

    @Test
    fun `kotlin spec version matches the python constant`() {
        // Reading the Python source directly closes the last gap: doc, Python and Kotlin
        // must ALL agree, and this catches the case where only the doc was bumped.
        val python = repoRoot().resolve("ml/src/features/spec_constants.py").readText()
        val match = Regex("""SPEC_VERSION\s*=\s*"(v\d+\.\d+\.\d+)"""").find(python)
        assertNotNull("no SPEC_VERSION found in ml/src/features/spec_constants.py", match)
        assertEquals(
            "Kotlin and Python SPEC_VERSION disagree — the extractors are out of sync.",
            match!!.groupValues[1],
            SpecConstants.SPEC_VERSION,
        )
    }

    private fun documentSpecVersion(): String {
        val spec = repoRoot().resolve("docs/feature-spec.md")
        assertTrue("cannot find ${spec.path}", spec.isFile)
        // The header block is the first — and authoritative — declaration.
        val match = versionPattern.find(spec.readText())
        assertNotNull("no 'SPEC_VERSION: vX.Y.Z' declaration found in ${spec.path}", match)
        return match!!.groupValues[1]
    }

    /** Walks up from the Gradle module dir until the repo's `docs/feature-spec.md` appears. */
    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "docs/feature-spec.md").isFile) return dir
            dir = dir.parentFile
        }
        throw AssertionError(
            "could not locate the repo root (no ancestor of ${System.getProperty("user.dir")} " +
                "contains docs/feature-spec.md)"
        )
    }
}
