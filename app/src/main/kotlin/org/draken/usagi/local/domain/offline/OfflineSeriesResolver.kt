package org.draken.usagi.local.domain.offline

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.draken.usagi.core.model.LocalMangaSource
import org.draken.usagi.core.util.ext.printStackTraceDebug
import org.draken.usagi.local.data.pdf.OfflineFile
import org.draken.usagi.local.data.pdf.OfflineFolderScanner
import org.draken.usagi.local.data.pdf.PdfMangaParser
import org.draken.usagi.local.data.pdf.buildOfflineSeriesUri
import org.draken.usagi.local.data.pdf.chapterNumber
import org.draken.usagi.local.data.pdf.offlineFileChapterOrder
import org.draken.usagi.local.data.pdf.seriesKey
import org.draken.usagi.local.data.pdf.seriesKeyToTitle
import org.draken.usagi.local.data.pdf.toRealFileOrNull
import tsuki.model.Manga
import tsuki.model.MangaChapter
import tsuki.util.longHashCode
import tsuki.util.runCatchingCancellable
import javax.inject.Inject

/**
 * Rebuilds a grouped-series [Manga] (one file = one chapter, sorted by chapter number) by
 * re-scanning [rootUri] and filtering to files matching [seriesKey]. Stateless by design: the
 * folder is the only source of truth, so this can be called fresh every time — both right
 * after the user taps a file, and later when the reader asks for updated chapter details.
 */
class OfflineSeriesResolver @Inject constructor(
	@ApplicationContext private val context: Context,
	private val scanner: OfflineFolderScanner,
) {

	suspend fun buildManga(rootUri: Uri, seriesKey: String): Manga? = withContext(Dispatchers.IO) {
		runCatchingCancellable {
			val siblings = scanner.scan(rootUri)
				.filter { it.seriesKey() == seriesKey }
				.sortedWith(offlineFileChapterOrder)
			if (siblings.isEmpty()) return@runCatchingCancellable null

			val chapters = ArrayList<MangaChapter>(siblings.size)
			for ((index, sibling) in siblings.withIndex()) {
				val chapterUrl = sibling.toChapterUrlOrNull() ?: continue
				val number = sibling.chapterNumber() ?: (index + 1)
				chapters += MangaChapter(
					id = chapterUrl.longHashCode(),
					title = "Chapter $number",
					number = number.toFloat(),
					volume = 0,
					source = LocalMangaSource,
					uploadDate = 0L,
					url = chapterUrl,
					scanlator = null,
					branch = null,
				)
			}
			if (chapters.isEmpty()) return@runCatchingCancellable null

			val seriesUrl = buildOfflineSeriesUri(rootUri, seriesKey).toString()
			Manga(
				id = seriesUrl.longHashCode(),
				title = seriesKey.seriesKeyToTitle(),
				url = seriesUrl,
				publicUrl = seriesUrl,
				source = LocalMangaSource,
				coverUrl = null,
				largeCoverUrl = null,
				chapters = chapters,
				altTitles = emptySet(),
				rating = -1f,
				contentRating = null,
				tags = emptySet(),
				state = null,
				authors = emptySet(),
				description = null,
			)
		}.onFailure { e ->
			e.printStackTraceDebug()
		}.getOrNull()
	}

	/** The url a [MangaChapter] backed by this file should use, or null if unreadable. */
	fun OfflineFile.toChapterUrlOrNull(): String? = when (this) {
		is OfflineFile.Pdf -> PdfMangaParser.buildDocUri(docUri).toString()
		is OfflineFile.Cbz -> docUri.toRealFileOrNull(context)?.toUri()?.toString()
	}
}
