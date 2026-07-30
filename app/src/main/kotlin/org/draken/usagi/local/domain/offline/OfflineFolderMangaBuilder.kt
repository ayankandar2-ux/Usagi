package org.draken.usagi.local.domain.offline

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.draken.usagi.core.model.LocalMangaSource
import org.draken.usagi.core.util.ext.printStackTraceDebug
import org.draken.usagi.local.data.PageCache
import org.draken.usagi.local.data.LocalStorageCache
import org.draken.usagi.local.data.pdf.OfflineFile
import org.draken.usagi.local.data.pdf.OfflineFolderScanner
import org.draken.usagi.local.data.pdf.PdfMangaParser
import org.draken.usagi.local.data.pdf.PdfPageRenderer
import org.draken.usagi.local.data.pdf.buildOfflineFolderUri
import org.draken.usagi.local.data.pdf.chapterNumber
import org.draken.usagi.local.data.pdf.offlineFileChapterOrder
import org.draken.usagi.local.data.pdf.toRealFileOrNull
import tsuki.model.Manga
import tsuki.model.MangaChapter
import tsuki.util.longHashCode
import tsuki.util.runCatchingCancellable
import javax.inject.Inject

/**
 * Builds a [Manga] representing one whole picked folder: every CBZ/PDF file directly inside
 * it becomes one chapter, sorted by chapter number; the folder's own display name is the
 * title; the cover is rendered from the first page of the first chapter (PDF chapters only —
 * a CBZ-first-chapter folder just gets no cover rather than a crash).
 *
 * Stateless and re-runnable: nothing here is cached across calls except the underlying page
 * render cache, so calling this again after files were added/removed/renamed just reflects
 * the folder's current contents.
 */
class OfflineFolderMangaBuilder @Inject constructor(
	@ApplicationContext private val context: Context,
	private val scanner: OfflineFolderScanner,
	private val pdfPageRenderer: PdfPageRenderer,
	@PageCache private val cache: LocalStorageCache,
) {

	suspend fun buildManga(rootUri: Uri): Manga? = withContext(Dispatchers.IO) {
		runCatchingCancellable {
			val files = scanner.scan(rootUri).sortedWith(offlineFileChapterOrder)
			if (files.isEmpty()) return@runCatchingCancellable null

			val chapters = ArrayList<MangaChapter>(files.size)
			for ((index, file) in files.withIndex()) {
				val chapterUrl = file.toChapterUrlOrNull() ?: continue
				val number = file.chapterNumber() ?: (index + 1)
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

			val title = rootUri.folderDisplayName()
			val seriesUrl = buildOfflineFolderUri(rootUri).toString()
			val coverUrl = runCatchingCancellable { buildCoverUrl(files.first()) }
				.onFailure { it.printStackTraceDebug() }
				.getOrNull()

			Manga(
				id = seriesUrl.longHashCode(),
				title = title,
				url = seriesUrl,
				publicUrl = seriesUrl,
				source = LocalMangaSource,
				coverUrl = coverUrl,
				largeCoverUrl = coverUrl,
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

	/** Renders (or reuses an already-rendered) page 0 of [firstFile] as this folder's cover. */
	private suspend fun buildCoverUrl(firstFile: OfflineFile): String? {
		if (firstFile !is OfflineFile.Pdf) return null // CBZ cover extraction not implemented yet
		val pageUri = PdfPageRenderer.buildPageUri(firstFile.docUri, pageIndex = 0)
		val cacheKey = pageUri.toString()
		val existing = cache[cacheKey]
		if (existing != null) return existing.toUri().toString()
		val rendered = pdfPageRenderer.renderPage(firstFile.docUri, pageIndex = 0, cacheKey = cacheKey, cache = cache)
		return rendered?.toUri()?.toString()
	}

	private fun Uri.folderDisplayName(): String =
		DocumentFile.fromTreeUri(context, this)?.name?.takeIf { it.isNotBlank() }
			?: lastPathSegment?.substringAfterLast('/')
			?: "Offline folder"

	/** The url a [MangaChapter] backed by this file should use, or null if unreadable. */
	private fun OfflineFile.toChapterUrlOrNull(): String? = when (this) {
		is OfflineFile.Pdf -> PdfMangaParser.buildDocUri(docUri).toString()
		is OfflineFile.Cbz -> docUri.toRealFileOrNull(context)?.toUri()?.toString()
	}
}
