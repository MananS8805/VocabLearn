package com.example.walllearn.model

/**
 * One of the five voices offered in the essay reader. They are the phone's own
 * Google text-to-speech voices (installed, offline, instant), picked by region.
 *
 * [names] lists specific Google voice names to prefer, in order; if none is
 * installed the first installed local voice for [country] is used instead.
 * Google doesn't publish a gender for these voices, so the picker plays a sample
 * when you choose one, and swapping a voice is a one-line edit to [names].
 */
data class EssayVoice(
    val id: String,
    val label: String,
    val accent: String,
    val country: String,
    val names: List<String>
)

object EssayVoices {

    const val DEFAULT_ID = "us1"

    val all: List<EssayVoice> = listOf(
        EssayVoice("us1", "American 1", "American", "US", listOf("en-us-x-tpf-local", "en-us-x-sfg-local")),
        EssayVoice("uk1", "British 1", "British", "GB", listOf("en-gb-x-gba-local", "en-gb-x-gbc-local")),
        EssayVoice("in1", "Indian 1", "Indian", "IN", listOf("en-in-x-ena-local", "en-in-x-end-local")),
        EssayVoice("us2", "American 2", "American", "US", listOf("en-us-x-iob-local", "en-us-x-tpc-local")),
        EssayVoice("uk2", "British 2", "British", "GB", listOf("en-gb-x-gbg-local", "en-gb-x-rjs-local"))
    )

    fun byId(id: String?): EssayVoice = all.firstOrNull { it.id == id } ?: all.first()

    const val SAMPLE_TEXT = "Education should prepare students for a world that is not yet fully known."
}
