package com.nahrahviing.lecteurnovel.data.epub

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.nahrahviing.lecteurnovel.data.model.NovelInfo
import com.nahrahviing.lecteurnovel.data.util.NovelContentCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfGenerator {

    // Dimensions A4 standard en points (72 points par pouce) : 595 x 842
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_LEFT = 48
    private const val MARGIN_RIGHT = 48
    private const val MARGIN_TOP = 54
    private const val MARGIN_BOTTOM = 54
    private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT
    private const val CONTENT_HEIGHT = PAGE_HEIGHT - MARGIN_TOP - MARGIN_BOTTOM

    suspend fun generatePdf(
        context: Context,
        novel: NovelInfo,
        destinationFile: File? = null
    ): File = withContext(Dispatchers.IO) {
        val outDir = File(context.getExternalFilesDir(null), "pdfs")
        if (!outDir.exists()) outDir.mkdirs()

        val safeTitle = EpubGenerator.getSafeTitle(novel.title)
        val targetFile = destinationFile ?: File(outDir, "${safeTitle}.pdf")

        val document = PdfDocument()
        try {
            var pageNumber = 1

        val titlePaint = TextPaint().apply {
            color = Color.rgb(20, 20, 20)
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val subtitlePaint = TextPaint().apply {
            color = Color.rgb(80, 80, 80)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val chapterHeadingPaint = TextPaint().apply {
            color = Color.rgb(30, 30, 30)
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val bodyPaint = TextPaint().apply {
            color = Color.rgb(35, 35, 35)
            textSize = 11f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }

        val footerPaint = Paint().apply {
            color = Color.rgb(130, 130, 130)
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val linePaint = Paint().apply {
            color = Color.rgb(210, 210, 210)
            strokeWidth = 1f
        }

        // ==========================================
        // Page 1 : Page de Titre & Couverture textuelle
        // ==========================================
        val coverPageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        val coverPage = document.startPage(coverPageInfo)
        val coverCanvas = coverPage.canvas

        var coverY = MARGIN_TOP + 40f

        // Titre du Roman
        val titleLayout = StaticLayout.Builder.obtain(novel.title, 0, novel.title.length, titlePaint, CONTENT_WIDTH)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(4f, 1.15f)
            .build()
        coverCanvas.save()
        coverCanvas.translate(MARGIN_LEFT.toFloat(), coverY)
        titleLayout.draw(coverCanvas)
        coverCanvas.restore()
        coverY += titleLayout.height + 24f

        // Auteur
        if (novel.author.isNotBlank()) {
            val authorText = "Auteur : ${novel.author}"
            val authorLayout = StaticLayout.Builder.obtain(authorText, 0, authorText.length, subtitlePaint, CONTENT_WIDTH)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()
            coverCanvas.save()
            coverCanvas.translate(MARGIN_LEFT.toFloat(), coverY)
            authorLayout.draw(coverCanvas)
            coverCanvas.restore()
            coverY += authorLayout.height + 16f
        }

        // Ligne de séparation
        coverCanvas.drawLine(
            (MARGIN_LEFT + 60).toFloat(), coverY,
            (PAGE_WIDTH - MARGIN_RIGHT - 60).toFloat(), coverY,
            linePaint
        )
        coverY += 24f

        // Synopsis
        if (novel.synopsis.isNotBlank()) {
            val cleanSynopsis = NovelContentCleaner.cleanToPlainText(novel.synopsis, novel.title)
            val synopsisHeader = "Synopsis :"
            coverCanvas.drawText(synopsisHeader, MARGIN_LEFT.toFloat(), coverY, chapterHeadingPaint)
            coverY += 20f

            val synopsisLayout = StaticLayout.Builder.obtain(cleanSynopsis, 0, cleanSynopsis.length, bodyPaint, CONTENT_WIDTH)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(3f, 1.2f)
                .build()
            coverCanvas.save()
            coverCanvas.translate(MARGIN_LEFT.toFloat(), coverY)
            synopsisLayout.draw(coverCanvas)
            coverCanvas.restore()
            coverY += synopsisLayout.height + 24f
        }

        // Métadonnées
        val metaText = "Nombre de chapitres : ${novel.chapters.size}\nSource : ${novel.originalUrl}"
        val metaLayout = StaticLayout.Builder.obtain(metaText, 0, metaText.length, subtitlePaint, CONTENT_WIDTH)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
        coverCanvas.save()
        coverCanvas.translate(MARGIN_LEFT.toFloat(), (PAGE_HEIGHT - MARGIN_BOTTOM - metaLayout.height - 20).toFloat())
        metaLayout.draw(coverCanvas)
        coverCanvas.restore()

        document.finishPage(coverPage)
        pageNumber++

        // ==========================================
        // Chapitres et Contenu
        // ==========================================
        var currentPage: PdfDocument.Page? = null
        var currentCanvas = coverCanvas // placeholder
        var currentY = MARGIN_TOP.toFloat()

        fun startNewPage() {
            currentPage?.let {
                // Numérotation de page en bas
                currentCanvas.drawText(
                    "— $pageNumber —",
                    (PAGE_WIDTH / 2).toFloat(),
                    (PAGE_HEIGHT - MARGIN_BOTTOM + 24).toFloat(),
                    footerPaint
                )
                document.finishPage(it)
                pageNumber++
            }
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val newPage = document.startPage(pageInfo)
            currentPage = newPage
            currentCanvas = newPage.canvas
            currentY = MARGIN_TOP.toFloat()
        }

        for (chapter in novel.chapters) {
            // Chaque nouveau chapitre commence sur une nouvelle page
            startNewPage()

            // Titre du chapitre
            val chapHeading = chapter.title.ifBlank { "Chapitre ${chapter.index}" }
            val chapHeadingLayout = StaticLayout.Builder.obtain(chapHeading, 0, chapHeading.length, chapterHeadingPaint, CONTENT_WIDTH)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()

            currentCanvas.save()
            currentCanvas.translate(MARGIN_LEFT.toFloat(), currentY)
            chapHeadingLayout.draw(currentCanvas)
            currentCanvas.restore()
            currentY += chapHeadingLayout.height + 12f

            // Ligne sous le titre du chapitre
            currentCanvas.drawLine(
                MARGIN_LEFT.toFloat(), currentY,
                (PAGE_WIDTH - MARGIN_RIGHT).toFloat(), currentY,
                linePaint
            )
            currentY += 16f

            // Paragraphes du chapitre
            val cleanContent = NovelContentCleaner.cleanToPlainText(chapter.content, novel.title)
            val paragraphs = cleanContent.split("\n\n").filter { it.isNotBlank() }

            for (p in paragraphs) {
                val pText = p.trim()
                if (pText.isBlank()) continue

                val pLayout = StaticLayout.Builder.obtain(pText, 0, pText.length, bodyPaint, CONTENT_WIDTH)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(3f, 1.25f)
                    .build()

                // Si le paragraphe ne rentre pas dans la page courante
                if (currentY + pLayout.height > PAGE_HEIGHT - MARGIN_BOTTOM) {
                    startNewPage()
                }

                currentCanvas.save()
                currentCanvas.translate(MARGIN_LEFT.toFloat(), currentY)
                pLayout.draw(currentCanvas)
                currentCanvas.restore()
                currentY += pLayout.height + 8f
            }
        }

        // Fermeture de la dernière page
        currentPage?.let {
            currentCanvas.drawText(
                "— $pageNumber —",
                (PAGE_WIDTH / 2).toFloat(),
                (PAGE_HEIGHT - MARGIN_BOTTOM + 24).toFloat(),
                footerPaint
            )
            document.finishPage(it)
        }

            // Écriture du fichier PDF
            FileOutputStream(targetFile).use { out ->
                document.writeTo(out)
            }
            targetFile
        } finally {
            document.close()
        }
    }

    suspend fun generateSingleChapterPdf(
        context: Context,
        novel: NovelInfo,
        chapter: com.nahrahviing.lecteurnovel.data.model.Chapter
    ): File = withContext(Dispatchers.IO) {
        val safeTitle = EpubGenerator.getSafeTitle(novel.title)
        val novelDir = File(File(context.getExternalFilesDir(null), "pdfs"), safeTitle)
        if (!novelDir.exists()) novelDir.mkdirs()

        val paddedIndex = chapter.index.toString().padStart(3, '0')
        val file = File(novelDir, "${safeTitle}_Chap_${paddedIndex}.pdf")

        val dummyNovel = novel.copy(chapters = listOf(chapter))
        generatePdf(context, dummyNovel, destinationFile = file)
    }
}
