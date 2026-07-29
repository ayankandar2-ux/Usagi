package org.draken.usagi.local.domain.offline

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.draken.usagi.core.model.LocalMangaSource
import org.draken.usagi.core.util.ext.printStackTraceDebug
import org.draken.usagi.local.data.pdf.OfflineFile
import org.draken.usagi.local.data.pdf.PdfMangaParser
import org.draken.usagi.local.data.pdf.PdfPageRenderer
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

/** Result of opening a scanned file: the whole series as a manga, and which chapter to land on. */
data class OpenOfflineResult(
	val manga: Manga,
	val chapterId: Long,
)

/**
 * Resolves a tapped [OfflineFile] into an [OpenOfflineResult] ready for the reader.
 *
 * All scanned files sharing the same series (see [seriesKey]) are grouped into a single
 * [Manga] with one [MangaChapter] per file, sorted by chapter number -- this is what lets
 * the reader's existing "next chapter" behavior carry across files, since it operates on
 * a manga's chapter list, not on individual files. Returns null (never throws) if the
 * tapped file itself can't be opened (corrupt PDF, or a CBZ whose uri can't be resolved
 * back to a real path -- see [toRealFileOrNull]) -- the caller should skip it.
 */
class OpenOfflineFileUseCase @Inject constructor(
	@ApplicationContext private val context: Context,
	private val pdfPageRenderer: PdfPageRenderer,
) {

	suspend operator fun invoke(file: OfflineFile, allScannedFiles: List<OfflineFile>): OpenOfflineResult? =
		withContext(Dispatchers.IO) {
			runCatchingCancellable {
				val seriesKey = file.seriesKey()
				val siblings = allScannedFiles
					.filter { it.seriesKey() == seriesKey }
					.sortedWith(offlineFileChapterOrder)

				val chapters = ArrayList<MangaChapter>(siblings.size)
				var targetChapterId: Long? = null

				for ((index, sibling) in siblings.withIndex()) {
					val chapterUrl = sibling.toChapterUrlOrNull() ?: continue
					val chapterId = chapterUrl.longHashCode()
					val number = sibling.chapterNumber() ?: (index + 1)
					chapters += MangaChapter(
						id = chapterId,
						title = "Chapter $number",
						number = number.toFloat(),
						volume = 0,
						source = LocalMangaSource,
						uploadDate = 0L,
						url = chapterUrl,
						scanlator = null,
						branch = null,
					)
					if (sibling.docUri == file.docUri) {
						targetChapterId = chapterId
					}
				}
				if (chapters.isEmpty() || targetChapterId == null) {
					return@runCatchingCancellable null
				}

				val seriesUrl = buildOfflineSeriesUri(seriesKey).toString()
				val manga = Manga(
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
				OpenOfflineResult(manga, targetChapterId)
			}.onFailure { e ->
				e.printStackTraceDebug()
			}.getOrNull()
		}

	/** The url a [MangaChapter] backed by this file should use, or null if unreadable. */
	private fun OfflineFile.toChapterUrlOrNull(): String? = when (this) {
		is OfflineFile.Pdf -> PdfMangaParser.buildDocUri(docUri).toString()
		is OfflineFile.Cbz -> docUri.toRealFileOrNull(context)?.toUri()?.toString()
	}
}
