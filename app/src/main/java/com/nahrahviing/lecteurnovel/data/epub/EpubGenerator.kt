package com.nahrahviing.lecteurnovel.data.epub

import android.content.Context
import com.nahrahviing.lecteurnovel.data.model.NovelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object EpubGenerator {
    fun getSafeTitle(title: String): String {
        return title.replace(Regex("[^a-zA-Z0-9.-]"), "_").take(50).trim('_')
    }

    suspend fun generateEpub(context: Context, novel: NovelInfo, destinationFile: File? = null): File = withContext(Dispatchers.IO) {
        val outDir = File(context.getExternalFilesDir(null), "epubs")
        if (!outDir.exists()) outDir.mkdirs()

        val safeTitle = getSafeTitle(novel.title)
        val epubFile = destinationFile ?: File(outDir, "${safeTitle}_ChiReads.epub")

        ZipOutputStream(FileOutputStream(epubFile)).use { zip ->
            // 1. mimetype (doit être non compressé en premier)
            val mimetypeEntry = ZipEntry("mimetype")
            mimetypeEntry.method = ZipEntry.STORED
            val mimetypeBytes = "application/epub+zip".toByteArray()
            mimetypeEntry.size = mimetypeBytes.size.toLong()
            mimetypeEntry.crc = java.util.zip.CRC32().apply { update(mimetypeBytes) }.value
            zip.putNextEntry(mimetypeEntry)
            zip.write(mimetypeBytes)
            zip.closeEntry()

            // 2. container.xml
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            val containerXml = """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
   <rootfiles>
      <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
   </rootfiles>
</container>"""
            zip.write(containerXml.toByteArray())
            zip.closeEntry()

            // 3. Chapters
            val chapterManifest = StringBuilder()
            val chapterSpine = StringBuilder()

            novel.chapters.forEachIndexed { index, chapter ->
                val chapId = "chap_${index + 1}"
                val chapFileName = "chapter_${index + 1}.xhtml"
                chapterManifest.append("""    <item id="$chapId" href="$chapFileName" media-type="application/xhtml+xml"/>${"\n"}""")
                chapterSpine.append("""    <itemref idref="$chapId"/>${"\n"}""")

                zip.putNextEntry(ZipEntry("OEBPS/$chapFileName"))
                val paragraphs = com.nahrahviing.lecteurnovel.data.util.NovelContentCleaner.formatToParagraphs(chapter.content, novel.title)
                val contentLines = paragraphs.joinToString("\n") { p ->
                    "<p>${escapeXml(p)}</p>"
                }
                val xhtml = """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>${escapeXml(chapter.title)}</title>
  <style>body { font-family: serif; line-height: 1.6; padding: 1em; } h2 { text-align: center; color: #333; } p { text-indent: 1.5em; margin: 0.5em 0; }</style>
</head>
<body>
  <h2>${escapeXml(chapter.title)}</h2>
  $contentLines
</body>
</html>"""
                zip.write(xhtml.toByteArray())
                zip.closeEntry()
            }

            // 4. content.opf
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            val opf = """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookID" version="2.0">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>${escapeXml(novel.title)}</dc:title>
    <dc:creator>${escapeXml(novel.author)}</dc:creator>
    <dc:identifier id="BookID">${escapeXml(novel.originalUrl)}</dc:identifier>
    <dc:language>fr</dc:language>
  </metadata>
  <manifest>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
$chapterManifest
  </manifest>
  <spine toc="ncx">
$chapterSpine
  </spine>
</package>"""
            zip.write(opf.toByteArray())
            zip.closeEntry()

            // 5. toc.ncx
            zip.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
            val navPoints = StringBuilder()
            novel.chapters.forEachIndexed { index, chapter ->
                navPoints.append("""    <navPoint id="navPoint-${index + 1}" playOrder="${index + 1}">
      <navLabel><text>${escapeXml(chapter.title)}</text></navLabel>
      <content src="chapter_${index + 1}.xhtml"/>
    </navPoint>${"\n"}""")
            }
            val ncx = """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head>
    <meta name="dtb:uid" content="${escapeXml(novel.originalUrl)}"/>
    <meta name="dtb:depth" content="1"/>
  </head>
  <docTitle><text>${escapeXml(novel.title)}</text></docTitle>
  <navMap>
$navPoints
  </navMap>
</ncx>"""
            zip.write(ncx.toByteArray())
            zip.closeEntry()
        }

        epubFile
    }

    suspend fun generateSingleChapterEpub(
        context: Context,
        novel: NovelInfo,
        chapter: com.nahrahviing.lecteurnovel.data.model.Chapter
    ): File = withContext(Dispatchers.IO) {
        val safeTitle = getSafeTitle(novel.title)
        val novelDir = File(File(context.getExternalFilesDir(null), "epubs"), safeTitle)
        if (!novelDir.exists()) novelDir.mkdirs()

        val paddedIndex = chapter.index.toString().padStart(3, '0')
        val epubFile = File(novelDir, "${safeTitle}_Chap_${paddedIndex}.epub")

        val singleChapterNovel = novel.copy(chapters = listOf(chapter))
        generateEpub(context, singleChapterNovel, destinationFile = epubFile)
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
