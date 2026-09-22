package com.nahrahviing.lecteurnovel.data.epub

import android.content.Context
import com.nahrahviing.lecteurnovel.data.model.NovelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Générateur de fichiers texte brut (TXT) encodés en UTF-8 pour les romans complets.
 * Idéal pour les liseuses basiques, les scripts de synthèse vocale externes ou l'archivage léger.
 */
object TxtGenerator {
    /**
     * Génère un fichier TXT contenant le titre, l'auteur et la succession de tous les chapitres.
     * @param context Contexte Android pour accéder au stockage externe de l'application
     * @param novel Informations complètes du roman et chapitres
     * @param destinationFile Fichier cible optionnel (si null, créé dans le dossier 'txts' de l'app)
     */
    suspend fun generateTxt(
        context: Context,
        novel: NovelInfo,
        destinationFile: File? = null
    ): File = withContext(Dispatchers.IO) {
        val outDir = File(context.getExternalFilesDir(null), "txts")
        if (!outDir.exists()) outDir.mkdirs()

        val safeTitle = novel.title.replace(Regex("[^a-zA-Z0-9.-]"), "_").take(50)
        val txtFile = destinationFile ?: File(outDir, "${safeTitle}.txt")

        OutputStreamWriter(FileOutputStream(txtFile), StandardCharsets.UTF_8).use { writer ->
            writer.write("${novel.title}\n")
            writer.write("Auteur : ${novel.author}\n\n")
            
            novel.chapters.forEach { chapter ->
                writer.write("========== ${chapter.title} ==========\n\n")
                val cleanContent = com.nahrahviing.lecteurnovel.data.util.NovelContentCleaner.cleanToPlainText(chapter.content, novel.title)
                writer.write(cleanContent)
                writer.write("\n\n\n")
            }
        }
        
        txtFile
    }
}
