package org.draken.usagi.local.data.output

import androidx.core.net.toFile
import androidx.core.net.toUri
import org.draken.usagi.core.model.isLocal
import org.draken.usagi.local.data.pdf.PdfMangaParser
import tsuki.model.Manga

class LocalMangaUtil(
	private val manga: Manga,
) {

	init {
		require(manga.isLocal) { "Expected LOCAL source but ${manga.source} found" }
	}

	suspend fun deleteChapters(ids: Set<Long>) {
		val uri = manga.url.toUri()
		check(!PdfMangaParser.isPdfDocUri(uri)) {
			"Deleting individual chapters isn't supported for PDF-backed manga"
		}
		val file = uri.toFile()
		if (file.isDirectory) {
			LocalMangaDirOutput(file, manga).use { output ->
				output.deleteChapters(ids)
				output.finish()
			}
		} else {
			LocalMangaZipOutput.filterChapters(file, manga, ids)
		}
	}
}
