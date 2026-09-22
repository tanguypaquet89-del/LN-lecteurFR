package com.nahrahviing.lecteurnovel.tts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

object NovelFranceTtsService {

    private const val BASE_URL = "https://novelfrance.fr"
    private val json = Json { ignoreUnknownKeys = true }

    // Liste des voix officielles Novel France
    val NOVEL_FRANCE_VOICES = listOf(
        VoiceOption(
            id = "hugo",
            name = "Ruby (Novel France)",
            description = "Voix naturelle féminine par défaut",
            isNovelFranceVoice = true,
            isNeuralOrHighQuality = true
        ),
        VoiceOption(
            id = "narrateur",
            name = "Narrateur HXH (Novel France)",
            description = "Voix narrative masculine profonde et immersive",
            isNovelFranceVoice = true,
            isNeuralOrHighQuality = true
        ),
        VoiceOption(
            id = "naruto",
            name = "Narrateur Naruto (Novel France)",
            description = "Voix masculine dynamique et animée",
            isNovelFranceVoice = true,
            isNeuralOrHighQuality = true
        ),
        VoiceOption(
            id = "myko",
            name = "Clara (Novel France)",
            description = "Voix féminine posée et chaleureuse",
            isNovelFranceVoice = true,
            isNeuralOrHighQuality = true
        )
    )

    /**
     * Tente d'extraire l'identifiant interne de chapitre Novel France depuis son URL ou son HTML.
     */
    suspend fun resolveNovelFranceChapterId(chapterUrl: String): String? = withContext(Dispatchers.IO) {
        if (!chapterUrl.contains("novelfrance.fr")) return@withContext null

        try {
            val doc = Jsoup.connect(chapterUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .timeout(10000)
                .get()

            val html = doc.html()
            val pattern = Pattern.compile("""initialChapter.*?id\\*":\\*"([a-zA-Z0-9]{15,})\\*"""")
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                return@withContext matcher.group(1)
            }
        } catch (_: Exception) {}

        null
    }

    /**
     * Vérifie si le fichier audio d'un chapitre est disponible sur Novel France.
     */
    suspend fun getChapterTtsStatus(chapterId: String, voiceId: String): NovelFranceChapterTtsResponse? = withContext(Dispatchers.IO) {
        try {
            val endpoint = "$BASE_URL/api/tts/chapters/$chapterId?voice=$voiceId"
            val url = URL(endpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val body = reader.readText()
                reader.close()
                return@withContext json.decodeFromString<NovelFranceChapterTtsResponse>(body)
            }
        } catch (_: Exception) {}

        null
    }

    /**
     * Récupère le fichier JSON d'alignement mot à mot (spans temporels)
     */
    suspend fun fetchAlignmentData(alignmentUrl: String): AlignmentData? = withContext(Dispatchers.IO) {
        try {
            val fullUrl = if (alignmentUrl.startsWith("http")) alignmentUrl else "$BASE_URL$alignmentUrl"
            val url = URL(fullUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val body = reader.readText()
                reader.close()
                return@withContext json.decodeFromString<AlignmentData>(body)
            }
        } catch (_: Exception) {}

        null
    }
}
