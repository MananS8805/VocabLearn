package com.example.walllearn.model

/** A glossary entry shipped with a model essay: a term the essay uses well. */
data class GlossaryEntry(val term: String, val definition: String)

/**
 * A justified point about an essay. [quote] is a verbatim sentence from the essay
 * (the build tool refuses to emit one that isn't), [why] is the explanation taken
 * from the source's own structure notes, and may be empty.
 */
data class EssayPoint(val kind: String, val quote: String, val why: String)

data class Essay(
    val id: String,
    val theme: String,
    val title: String,
    val instructionType: String,
    val prompt: String,
    val paragraphs: List<String>,
    val glossary: List<GlossaryEntry> = emptyList(),
    val structureNote: String = "",
    val points: List<EssayPoint> = emptyList(),
    /** True for essays the user pasted in; false for the bundled model essays. */
    val isUser: Boolean = false
) {
    val wordCount: Int get() = paragraphs.sumOf { p -> p.split(Regex("\\s+")).count { it.isNotEmpty() } }
}
