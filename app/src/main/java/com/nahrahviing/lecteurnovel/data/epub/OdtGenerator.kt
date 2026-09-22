package com.nahrahviing.lecteurnovel.data.epub

import android.content.Context
import com.nahrahviing.lecteurnovel.data.model.NovelInfo
import com.nahrahviing.lecteurnovel.data.util.NovelContentCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object OdtGenerator {

    suspend fun generateOdt(
        context: Context,
        novel: NovelInfo,
        destinationFile: File? = null
    ): File = withContext(Dispatchers.IO) {
        val outDir = File(context.getExternalFilesDir(null), "odts")
        if (!outDir.exists()) outDir.mkdirs()

        val safeTitle = EpubGenerator.getSafeTitle(novel.title)
        val targetFile = destinationFile ?: File(outDir, "${safeTitle}.odt")

        ZipOutputStream(FileOutputStream(targetFile)).use { zipOut ->
            // 1. mimetype (doit être non-compressé selon la spécification ODF)
            val mimetypeBytes = "application/vnd.oasis.opendocument.text".toByteArray(Charsets.US_ASCII)
            val mimeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimetypeBytes.size.toLong()
                compressedSize = mimetypeBytes.size.toLong()
                crc = CRC32().apply { update(mimetypeBytes) }.value
            }
            zipOut.putNextEntry(mimeEntry)
            zipOut.write(mimetypeBytes)
            zipOut.closeEntry()

            // 2. META-INF/manifest.xml
            zipOut.putNextEntry(ZipEntry("META-INF/manifest.xml"))
            zipOut.write(generateManifestXml().toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // 3. meta.xml
            zipOut.putNextEntry(ZipEntry("meta.xml"))
            zipOut.write(generateMetaXml(novel).toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // 4. styles.xml
            zipOut.putNextEntry(ZipEntry("styles.xml"))
            zipOut.write(generateStylesXml().toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // 5. content.xml
            zipOut.putNextEntry(ZipEntry("content.xml"))
            zipOut.write(generateContentXml(novel).toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            zipOut.finish()
        }

        targetFile
    }

    suspend fun generateSingleChapterOdt(
        context: Context,
        novel: NovelInfo,
        chapter: com.nahrahviing.lecteurnovel.data.model.Chapter
    ): File = withContext(Dispatchers.IO) {
        val safeTitle = EpubGenerator.getSafeTitle(novel.title)
        val novelDir = File(File(context.getExternalFilesDir(null), "odts"), safeTitle)
        if (!novelDir.exists()) novelDir.mkdirs()

        val paddedIndex = chapter.index.toString().padStart(3, '0')
        val file = File(novelDir, "${safeTitle}_Chap_${paddedIndex}.odt")

        val dummyNovel = novel.copy(chapters = listOf(chapter))
        generateOdt(context, dummyNovel, destinationFile = file)
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun generateManifestXml(): String {
        return """<?xml version="1.0" encoding="UTF-8"?>
<manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.2">
  <manifest:file-entry manifest:full-path="/" manifest:version="1.2" manifest:media-type="application/vnd.oasis.opendocument.text"/>
  <manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>
  <manifest:file-entry manifest:full-path="styles.xml" manifest:media-type="text/xml"/>
  <manifest:file-entry manifest:full-path="meta.xml" manifest:media-type="text/xml"/>
</manifest:manifest>"""
    }

    private fun generateMetaXml(novel: NovelInfo): String {
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
        return """<?xml version="1.0" encoding="UTF-8"?>
<office:document-meta xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                      xmlns:meta="urn:oasis:names:tc:opendocument:xmlns:meta:1.0"
                      xmlns:dc="http://purl.org/dc/elements/1.1/"
                      office:version="1.2">
  <office:meta>
    <dc:title>${escapeXml(novel.title)}</dc:title>
    <dc:creator>${escapeXml(novel.author)}</dc:creator>
    <dc:date>$now</dc:date>
    <meta:generator>LN lecteurFR</meta:generator>
  </office:meta>
</office:document-meta>"""
    }

    private fun generateStylesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8"?>
<office:document-styles xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                        xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
                        xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
                        office:version="1.2">
  <office:styles>
    <style:default-style style:family="paragraph">
      <style:paragraph-properties fo:line-height="130%" fo:margin-bottom="0.2cm"/>
      <style:text-properties fo:font-size="11pt" fo:font-family="Liberation Serif, Times New Roman, serif"/>
    </style:default-style>
    <style:style style:name="Title" style:family="paragraph">
      <style:paragraph-properties fo:text-align="center" fo:margin-bottom="0.5cm"/>
      <style:text-properties fo:font-size="22pt" fo:font-weight="bold"/>
    </style:style>
    <style:style style:name="Subtitle" style:family="paragraph">
      <style:paragraph-properties fo:text-align="center" fo:margin-bottom="1.0cm"/>
      <style:text-properties fo:font-size="13pt" fo:font-style="italic" fo:color="#555555"/>
    </style:style>
    <style:style style:name="Heading_1" style:family="paragraph">
      <style:paragraph-properties fo:margin-top="0.8cm" fo:margin-bottom="0.3cm" fo:keep-with-next="always"/>
      <style:text-properties fo:font-size="16pt" fo:font-weight="bold" fo:color="#1a1a1a"/>
    </style:style>
  </office:styles>
</office:document-styles>"""
    }

    private fun generateContentXml(novel: NovelInfo): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>
<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                         xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
                         xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
                         xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
                         office:version="1.2">
  <office:body>
    <office:text>
""")

        // Titre du Roman
        sb.append("      <text:h text:style-name=\"Title\" text:outline-level=\"1\">")
        sb.append(escapeXml(novel.title))
        sb.append("</text:h>\n")

        // Auteur
        if (novel.author.isNotBlank()) {
            sb.append("      <text:p text:style-name=\"Subtitle\">")
            sb.append("Auteur : ").append(escapeXml(novel.author))
            sb.append("</text:p>\n")
        }

        // Synopsis
        if (novel.synopsis.isNotBlank()) {
            sb.append("      <text:h text:style-name=\"Heading_1\" text:outline-level=\"2\">Synopsis</text:h>\n")
            val cleanSynopsis = NovelContentCleaner.cleanToPlainText(novel.synopsis, novel.title)
            for (line in cleanSynopsis.split("\n\n").filter { it.isNotBlank() }) {
                sb.append("      <text:p>").append(escapeXml(line.trim())).append("</text:p>\n")
            }
        }

        // Chapitres
        for (chapter in novel.chapters) {
            val chapTitle = chapter.title.ifBlank { "Chapitre ${chapter.index}" }
            sb.append("      <text:h text:style-name=\"Heading_1\" text:outline-level=\"2\">")
            sb.append(escapeXml(chapTitle))
            sb.append("</text:h>\n")

            val cleanContent = NovelContentCleaner.cleanToPlainText(chapter.content, novel.title)
            for (para in cleanContent.split("\n\n").filter { it.isNotBlank() }) {
                sb.append("      <text:p>").append(escapeXml(para.trim())).append("</text:p>\n")
            }
        }

        sb.append("""    </office:text>
  </office:body>
</office:document-content>""")

        return sb.toString()
    }
}
