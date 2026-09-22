package com.nahrahviing.lecteurnovel.tts

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Lecteur audio unifié pour la synthèse vocale (TTS) et l'écoute de chapitres.
 * 
 * Fonctionnalités clés :
 * - Double moteur : support des flux audio distants (NovelFrance audio IA pré-généré)
 *   et du moteur TextToSpeech natif Android (voix neuronales Google HD en français).
 * - Synchronisation dynamique mot par mot (karaoké) grâce aux callbacks UtteranceProgressListener.
 * - Contrôle de vitesse de lecture dynamique (0.5x à 2.0x).
 * - Navigation par saut de temps (+10s, -10s) et sélection de paragraphe.
 */
class UnifiedNovelTtsPlayer(private val context: Context) : TextToSpeech.OnInitListener {

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Émetteurs d'état
    private val _playState = MutableStateFlow(TtsPlayState.IDLE)
    val playState: StateFlow<TtsPlayState> = _playState.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val availableVoices: StateFlow<List<VoiceOption>> = _availableVoices.asStateFlow()

    private val _currentVoice = MutableStateFlow(
        VoiceOption("neural_default", "Voix Neuronale Haute Définition", "Voix naturelle optimisée", false, true)
    )
    val currentVoice: StateFlow<VoiceOption> = _currentVoice.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _progressMs = MutableStateFlow(0L)
    val progressMs: StateFlow<Long> = _progressMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _activeHighlight = MutableStateFlow<ActiveTtsHighlight?>(null)
    val activeHighlight: StateFlow<ActiveTtsHighlight?> = _activeHighlight.asStateFlow()

    private val _statusMessage = MutableStateFlow("Prêt pour la lecture vocale")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // Moteurs audio
    private var mediaPlayer: MediaPlayer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    // Données de lecture en cours
    private var currentChapterUrl: String? = null
    private var currentParagraphs: List<String> = emptyList()
    private var currentParagraphIndex: Int = 0
    private var alignmentData: AlignmentData? = null
    private var progressTrackingJob: Job? = null

    // Mode actuel (NOVEL_FRANCE_STREAM ou NATIVE_TTS)
    private var isPlayingRemoteStream = false

    init {
        textToSpeech = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            textToSpeech?.language = Locale.FRENCH
            detectAvailableVoices()
            setupTtsListener()
        }
    }

    private fun detectAvailableVoices() {
        val tts = textToSpeech ?: return
        val detected = mutableListOf<VoiceOption>()

        // 1. Voix exclusives Novel France (IA)
        detected.addAll(NovelFranceTtsService.NOVEL_FRANCE_VOICES)

        // 2. Détection des voix locales Android / Google Speech Services en français
        try {
            val systemVoices = tts.voices ?: emptySet()
            val frenchVoices = systemVoices.filter { 
                it.locale.language.equals("fr", ignoreCase = true) 
            }.sortedByDescending { it.quality }

            for (voice in frenchVoices) {
                val isNeural = voice.name.contains("network", ignoreCase = true) || 
                               voice.name.contains("neural", ignoreCase = true) ||
                               voice.quality >= Voice.QUALITY_HIGH
                val label = when {
                    voice.name.contains("fr-fr-x-fra", ignoreCase = true) -> "Google Français Naturel (Femme)"
                    voice.name.contains("fr-fr-x-frb", ignoreCase = true) -> "Google Français Posé (Homme)"
                    voice.name.contains("fr-fr-x-frc", ignoreCase = true) -> "Google Français Expressif (Femme)"
                    voice.name.contains("fr-fr-x-frd", ignoreCase = true) -> "Google Français Profond (Homme)"
                    else -> "Voix Système (${voice.name.takeLast(10)})"
                }

                detected.add(
                    VoiceOption(
                        id = voice.name,
                        name = label,
                        description = if (isNeural) "Synthèse neuronale HD Google" else "Voix standard appareil",
                        isNovelFranceVoice = false,
                        isNeuralOrHighQuality = isNeural
                    )
                )
            }
        } catch (_: Exception) {}

        if (detected.none { !it.isNovelFranceVoice }) {
            detected.add(
                VoiceOption("system_default", "Voix Française Standard", "Moteur vocal Android", false, false)
            )
        }

        _availableVoices.value = detected

        // Sélectionner par défaut une voix haute fidélité
        val defaultVoice = detected.firstOrNull { it.isNovelFranceVoice } 
            ?: detected.firstOrNull { it.isNeuralOrHighQuality } 
            ?: detected.first()
        _currentVoice.value = defaultVoice
    }

    private fun setupTtsListener() {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _playState.value = TtsPlayState.PLAYING
                _statusMessage.value = "En lecture..."
            }

            override fun onDone(utteranceId: String?) {
                coroutineScope.launch {
                    playNextNativeSentence()
                }
            }

            override fun onError(utteranceId: String?) {
                _playState.value = TtsPlayState.ERROR
                _statusMessage.value = "Erreur de lecture vocale"
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (start in 0..end) {
                    val pIndex = currentParagraphIndex
                    val text = currentParagraphs.getOrNull(pIndex) ?: ""
                    val word = if (end <= text.length) text.substring(start, end) else ""
                    _activeHighlight.value = ActiveTtsHighlight(
                        paragraphIndex = pIndex,
                        charStart = start,
                        charEnd = end,
                        wordText = word
                    )
                }
            }
        })
    }

    /**
     * Démarre la lecture vocale intelligente pour un chapitre donné.
     * Si le chapitre vient de Novel France et qu'une piste audio/alignement existe,
     * elle est jouée directement. Sinon, le moteur haute fidélité local prend le relais.
     */
    fun startChapterReading(
        chapterUrl: String,
        paragraphs: List<String>,
        startParagraphIndex: Int = 0
    ) {
        stop()
        currentChapterUrl = chapterUrl
        currentParagraphs = paragraphs
        currentParagraphIndex = startParagraphIndex.coerceAtLeast(0)

        _playState.value = TtsPlayState.LOADING
        _statusMessage.value = "Préparation de la lecture audio..."

        coroutineScope.launch {
            val selectedVoice = _currentVoice.value

            // 1. Tenter la lecture audio directe Novel France si c'est un lien Novel France
            if (chapterUrl.contains("novelfrance.fr")) {
                val chapterId = NovelFranceTtsService.resolveNovelFranceChapterId(chapterUrl)
                if (chapterId != null) {
                    val voiceId = if (selectedVoice.isNovelFranceVoice) selectedVoice.id else "hugo"
                    val status = NovelFranceTtsService.getChapterTtsStatus(chapterId, voiceId)

                    if (status != null && status.state == "READY" && !status.audioUrl.isNullOrBlank()) {
                        val fullAudioUrl = if (status.audioUrl.startsWith("http")) status.audioUrl else "https://novelfrance.fr${status.audioUrl}"
                        val align = status.alignmentUrl?.let { NovelFranceTtsService.fetchAlignmentData(it) }

                        if (align != null) {
                            alignmentData = align
                            playRemoteAudioStream(fullAudioUrl, status.durationSec ?: 0.0)
                            return@launch
                        }
                    }
                }
            }

            // 2. Si non disponible en distant ou autre source, basculer sur le moteur neuronal local
            playWithNativeTts()
        }
    }

    /**
     * Lecture d'un flux audio haute fidélité distant (Novel France)
     */
    private fun playRemoteAudioStream(audioUrl: String, durationSec: Double) {
        isPlayingRemoteStream = true
        _playState.value = TtsPlayState.PREPARING
        _statusMessage.value = "Connexion au flux vocal Novel France..."

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioUrl)
                setOnPreparedListener { mp ->
                    _playState.value = TtsPlayState.PLAYING
                    _statusMessage.value = "En lecture (Voix Novel France)"
                    _durationMs.value = mp.duration.toLong().coerceAtLeast((durationSec * 1000).toLong())
                    applySpeedToMediaPlayer()
                    mp.start()
                    startProgressTracker()
                }
                setOnCompletionListener {
                    _playState.value = TtsPlayState.IDLE
                    _statusMessage.value = "Lecture terminée"
                    _activeHighlight.value = null
                    stopProgressTracker()
                }
                setOnErrorListener { _, _, _ ->
                    _playState.value = TtsPlayState.ERROR
                    _statusMessage.value = "Erreur de lecture du flux audio"
                    stopProgressTracker()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            _playState.value = TtsPlayState.ERROR
            _statusMessage.value = "Impossible de charger le flux audio"
        }
    }

    /**
     * Lecture avec le moteur TextToSpeech Android natif de haute qualité
     */
    private fun playWithNativeTts() {
        isPlayingRemoteStream = false
        val tts = textToSpeech
        if (tts == null || !isTtsInitialized) {
            _playState.value = TtsPlayState.ERROR
            _statusMessage.value = "Moteur vocal non initialisé"
            return
        }

        // Configurer la voix système
        val selected = _currentVoice.value
        if (!selected.isNovelFranceVoice) {
            try {
                val voiceObj = tts.voices?.find { it.name == selected.id }
                if (voiceObj != null) {
                    tts.voice = voiceObj
                }
            } catch (_: Exception) {}
        }

        tts.setSpeechRate(_playbackSpeed.value)
        tts.setPitch(1.0f)

        _playState.value = TtsPlayState.PLAYING
        _statusMessage.value = "En lecture (${selected.name})"

        // Estimer une durée globale basée sur le nombre total de mots (~150 mots/min)
        val totalWords = currentParagraphs.sumOf { it.split(Regex("\\s+")).size }
        val estimatedSec = (totalWords / 2.5) / _playbackSpeed.value
        _durationMs.value = (estimatedSec * 1000).toLong()

        startProgressTracker()
        speakCurrentParagraph()
    }

    private fun speakCurrentParagraph() {
        val tts = textToSpeech ?: return
        if (currentParagraphIndex !in currentParagraphs.indices) {
            _playState.value = TtsPlayState.IDLE
            _statusMessage.value = "Fin du chapitre"
            _activeHighlight.value = null
            stopProgressTracker()
            return
        }

        val paragraphText = currentParagraphs[currentParagraphIndex].trim()
        if (paragraphText.isBlank()) {
            currentParagraphIndex++
            speakCurrentParagraph()
            return
        }

        val utteranceId = "para_$currentParagraphIndex"
        tts.speak(paragraphText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun playNextNativeSentence() {
        if (_playState.value != TtsPlayState.PLAYING) return
        currentParagraphIndex++
        speakCurrentParagraph()
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressTrackingJob = coroutineScope.launch {
            while (isActive && _playState.value == TtsPlayState.PLAYING) {
                if (isPlayingRemoteStream) {
                    val mp = mediaPlayer
                    if (mp != null && mp.isPlaying) {
                        val currentMs = mp.currentPosition.toLong()
                        _progressMs.value = currentMs

                        // Synchronisation du surlignage mot par mot via spans JSON
                        alignmentData?.let { align ->
                            val currentSec = currentMs / 1000.0
                            val activeSpan = align.spans.find { currentSec in it.start..it.end }
                            if (activeSpan != null) {
                                val pIndex = align.paragraphs.indexOfFirst { it.id == activeSpan.p }
                                _activeHighlight.value = ActiveTtsHighlight(
                                    paragraphIndex = pIndex,
                                    charStart = activeSpan.from,
                                    charEnd = activeSpan.to
                                )
                            }
                        }
                    }
                } else {
                    _progressMs.value = (_progressMs.value + 200).coerceAtMost(_durationMs.value)
                }
                delay(200)
            }
        }
    }

    private fun stopProgressTracker() {
        progressTrackingJob?.cancel()
        progressTrackingJob = null
    }

    fun pause() {
        if (isPlayingRemoteStream) {
            mediaPlayer?.pause()
        } else {
            textToSpeech?.stop()
        }
        _playState.value = TtsPlayState.PAUSED
        _statusMessage.value = "En pause"
        stopProgressTracker()
    }

    fun resume() {
        if (isPlayingRemoteStream) {
            mediaPlayer?.start()
            _playState.value = TtsPlayState.PLAYING
            _statusMessage.value = "En lecture"
            startProgressTracker()
        } else {
            _playState.value = TtsPlayState.PLAYING
            speakCurrentParagraph()
            startProgressTracker()
        }
    }

    fun stop() {
        stopProgressTracker()
        if (isPlayingRemoteStream) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } else {
            textToSpeech?.stop()
        }
        _playState.value = TtsPlayState.IDLE
        _statusMessage.value = "Lecture arrêtée"
        _progressMs.value = 0L
        _activeHighlight.value = null
    }

    fun seekTo(positionMs: Long) {
        if (isPlayingRemoteStream) {
            mediaPlayer?.seekTo(positionMs.toInt())
            _progressMs.value = positionMs
        } else {
            // Dans le moteur local, calculer le paragraphe proportionnel
            val ratio = if (_durationMs.value > 0) positionMs.toFloat() / _durationMs.value else 0f
            currentParagraphIndex = (ratio * currentParagraphs.size).toInt().coerceIn(0, currentParagraphs.size - 1)
            if (_playState.value == TtsPlayState.PLAYING) {
                speakCurrentParagraph()
            }
        }
    }

    fun skipForward10s() {
        val target = (_progressMs.value + 10000).coerceAtMost(_durationMs.value)
        seekTo(target)
    }

    fun skipBackward10s() {
        val target = (_progressMs.value - 10000).coerceAtLeast(0)
        seekTo(target)
    }

    fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        if (isPlayingRemoteStream) {
            applySpeedToMediaPlayer()
        } else {
            textToSpeech?.setSpeechRate(speed)
        }
    }

    private fun applySpeedToMediaPlayer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mediaPlayer?.let { mp ->
                    val params = mp.playbackParams ?: PlaybackParams()
                    params.speed = _playbackSpeed.value
                    mp.playbackParams = params
                }
            } catch (_: Exception) {}
        }
    }

    fun setVoice(voice: VoiceOption) {
        _currentVoice.value = voice
        // Si en cours de lecture, redémarrer avec la nouvelle voix
        if (_playState.value == TtsPlayState.PLAYING) {
            val url = currentChapterUrl
            if (url != null) {
                startChapterReading(url, currentParagraphs, currentParagraphIndex)
            }
        }
    }

    fun destroy() {
        stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        coroutineScope.cancel()
    }
}
