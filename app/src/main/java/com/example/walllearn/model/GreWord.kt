package com.example.walllearn.model

/**
 * A single GRE vocabulary entry: the word itself, its part of speech,
 * a concise definition, and one natural example sentence that uses it.
 */
data class GreWord(
    val word: String,
    val pos: String,
    val meaning: String,
    val example: String
)
