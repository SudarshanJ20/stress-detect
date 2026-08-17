package com.stressdetect.ui.content

/**
 * Four ordinary things, the same four for everybody, every time.
 *
 * **Nothing here is derived from anything.** Not from the score, not from the phone, not
 * from the week. That is the whole design of this section: the rows above it are specific
 * because they are measured, and these are general because they are not. An app that
 * reordered this list by score would be implying it had worked something out about the
 * person, and it has not.
 *
 * They are also not advice about anyone's health, which the caption beneath them says in as
 * many words. Going outside and breathing slowly are things people do; recommending them is
 * not a clinical act, and nothing here is presented as treatment for anything.
 */
object ThingsThatHelp {

    val ITEMS: List<String> = listOf(
        "Ten minutes outside, ideally in daylight",
        "Slow breathing for a couple of minutes: longer out-breath than in-breath",
        "Moving your body, even a short walk",
        "Talking to someone you trust",
    )

    /**
     * One extra line at the top band only.
     *
     * Someone reporting a hard month is the one reader for whom a list of small self-help
     * suggestions could land badly — as though a walk were being offered as an answer. This
     * says plainly that it is not, and points past the app. It is a pointer to ordinary
     * support, not a clinical referral, and it never appears as an alarm.
     */
    fun extraFor(band: Band): String? = when (band) {
        Band.HIGH ->
            "If this has been going on a while, talking to someone — a friend, or your " +
                "college counsellor — can help more than anything on this screen."
        else -> null
    }

    /** Exposed for the copy test: everything a user can read from this file. */
    internal fun allCopyStrings(): List<String> = ITEMS + Band.entries.mapNotNull { extraFor(it) }
}
