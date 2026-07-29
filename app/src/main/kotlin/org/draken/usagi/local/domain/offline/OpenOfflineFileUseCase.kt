package org.draken.usagi.local.domain.offline

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.draken.usagi.core.util.ext.printStackTraceDebug
import org.draken.usagi.local.data.pdf.OfflineFile
import org.draken.usagi.local.data.pdf.seriesKey
import tsuki.model.Manga
import tsuki.util.runCatchingCancellable
import javax.inject.Inject

/** Result of opening a scanned file: the whole series as a manga, and which chapter to land on. */
data class OpenOfflineResult(
	val manga: Manga,
	val chapterId: Long,
)

/**
 * Resolves a tapped [OfflineFile] into an [OpenOfflineResult] ready for the reader, by
 * building (via [OfflineSeriesResolver]) the full multi-chapter manga for its series and
 * finding which chapter corresponds to the tapped file. Returns null (never throws) if the
 * tapped file itself can't be opened.
 */
class OpenOfflineFileUseCase @Inject constructor(
	private val seriesResolver: OfflineSeriesResolver,
) {

	suspend operator fun invoke(file: OfflineFile, rootUri: Uri): OpenOfflineResult? = withContext(Dispatchers.IO) {
		runCatchingCancellable {
			val manga = seriesResolver.buildManga(rootUri, file.seriesKey()) ?: return@runCatchingCancellable null
			val chapterUrl = with(seriesResolver) { file.toChapterUrlOrNull() } ?: return@runCatchingCancellable null
			val chapter = manga.chapters?.firstOrNull { it.url == chapterUrl } ?: return@runCatchingCancellable null
			OpenOfflineResult(manga, chapter.id)
		}.onFailure { e ->
			e.printStackTraceDebug()
		}.getOrNull()
	}
}
