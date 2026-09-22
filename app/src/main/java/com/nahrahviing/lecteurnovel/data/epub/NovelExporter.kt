package com.nahrahviing.lecteurnovel.data.epub

import android.content.Context
import com.nahrahviing.lecteurnovel.data.model.NovelInfo
import java.io.File

enum class ExportFormat(
    val id: String,
    val extension: String,
    val mimeType: String,
    val label: String,
    val description: String
) {
    EPUB(
        id = "EPUB",
        extension = "epub",
        mimeType = "application/epub+zip",
        label = "EPUB",
        description = "Format standard pour liseuses et applications de lecture"
    ),
    PDF(
        id = "PDF",
        extension = "pdf",
        mimeType = "application/pdf",
        label = "PDF",
        description = "Mise en page fixe avec pagination, titre et chapitres"
    ),
    ODT(
        id = "ODT",
        extension = "odt",
        mimeType = "application/vnd.oasis.opendocument.text",
        label = "ODT",
        description = "Document modifiable pour LibreOffice, Word ou Docs"
    ),
    TXT(
        id = "TXT",
        extension = "txt",
        mimeType = "text/plain",
        label = "TXT",
        description = "Fichier texte léger sans mise en forme"
    );

    companion object {
        fun fromString(value: String): ExportFormat {
            return entries.find { it.name.equals(value, ignoreCase = true) || it.extension.equals(value, ignoreCase = true) }
                ?: EPUB
        }
    }
}

/**
 * Gestionnaire centralisé pour l'exportation multi-formats (EPUB, PDF, ODT, TXT).
 * Délègue aux générateurs spécialisés tout en unifiant la signature d'appel.
 */
object NovelExporter {

    suspend fun exportNovel(
        context: Context,
        novel: NovelInfo,
        format: ExportFormat,
        destinationFile: File? = null
    ): File {
        return when (format) {
            ExportFormat.EPUB -> EpubGenerator.generateEpub(context, novel, destinationFile)
            ExportFormat.PDF -> PdfGenerator.generatePdf(context, novel, destinationFile)
            ExportFormat.ODT -> OdtGenerator.generateOdt(context, novel, destinationFile)
            ExportFormat.TXT -> TxtGenerator.generateTxt(context, novel, destinationFile)
        }
    }

    suspend fun exportSingleChapter(
        context: Context,
        novel: NovelInfo,
        chapter: com.nahrahviing.lecteurnovel.data.model.Chapter,
        format: ExportFormat
    ): File {
        return when (format) {
            ExportFormat.EPUB -> EpubGenerator.generateSingleChapterEpub(context, novel, chapter)
            ExportFormat.PDF -> PdfGenerator.generateSingleChapterPdf(context, novel, chapter)
            ExportFormat.ODT -> OdtGenerator.generateSingleChapterOdt(context, novel, chapter)
            ExportFormat.TXT -> {
                val safeTitle = EpubGenerator.getSafeTitle(novel.title)
                val novelDir = File(File(context.getExternalFilesDir(null), "txts"), safeTitle)
                if (!novelDir.exists()) novelDir.mkdirs()
                val paddedIndex = chapter.index.toString().padStart(3, '0')
                val txtFile = File(novelDir, "${safeTitle}_Chap_${paddedIndex}.txt")
                txtFile.writeText("${novel.title}\n${chapter.title}\n\n${chapter.content}")
                txtFile
            }
        }
    }
}
