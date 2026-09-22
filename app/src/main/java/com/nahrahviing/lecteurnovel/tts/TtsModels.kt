package com.nahrahviing.lecteurnovel.tts

import kotlinx.serialization.Serializable

/**
 * Modèle représentant une voix disponible pour la lecture vocale.
 */
data class VoiceOption(
    val id: String,
    val name: String,
    val description: String,
    val isNovelFranceVoice: Boolean = false,
    val isNeuralOrHighQuality: Boolean = true
)

/**
 * Alignement mot à mot pour la synchronisation audio.
 */
@Serializable
data class AlignmentData(
    val version: Int = 1,
    val durationSec: Double = 0.0,
    val paragraphs: List<AlignmentParagraph> = emptyList(),
    val spans: List<AlignmentSpan> = emptyList()
)

@Serializable
data class AlignmentParagraph(
    val id: String,
    val html: String
)

@Serializable
data class AlignmentSpan(
    val p: String,
    val from: Int,
    val to: Int,
    val start: Double,
    val end: Double
)

/**
 * Réponse du statut TTS d'un chapitre sur Novel France
 */
@Serializable
data class NovelFranceChapterTtsResponse(
    val voiceId: String? = null,
    val state: String = "ABSENT", // READY, GENERATING, ABSENT, FAILED
    val audioUrl: String? = null,
    val alignmentUrl: String? = null,
    val durationSec: Double? = null
)

/**
 * État de lecture vocale
 */
enum class TtsPlayState {
    IDLE,
    LOADING,
    PREPARING,
    PLAYING,
    PAUSED,
    ERROR
}

data class ActiveTtsHighlight(
    val paragraphIndex: Int = -1,
    val charStart: Int = -1,
    val charEnd: Int = -1,
    val wordText: String = ""
)
