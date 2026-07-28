package org.draken.usagi.local.data.pdf

import android.net.Uri
import org.draken.usagi.core.model.LocalMangaSource
import org.draken.usagi.core.util.ext.printStackTraceDebug
import org.draken.usagi.local.domain.model.LocalManga
import tsuki.model.Manga
import tsuki.model.MangaChapter
import tsuki.model.MangaPage
import tsuki.util.longHashCode
import tsuki.util.runCatchingCancellable
import java.io.File

/**
 * Builds the manga/chapter/page model for a single PDF file, mirroring what
 * [org.draken.usagi.local.data.input.LocalMangaParser] does for CBZ/ZIP archives.
 *
 * A PDF is treated as a single-chapter manga: one file, one chapter, N pages.
 * Each page's [MangaPage.url] is a synthetic `usagi-pdf-page://` URI (see
 * [PdfPageRenderer.buildPageUri]) rather than a real file/zip entry — the actual
 * bitmap is only rendered lazily, on demand, when the reader asks for that page.
 */
class PdfMangaParser(
	private val docUri: Uri,
	private val displayName: String,
	private val renderer: PdfPageRenderer,
) {

	/**
	 * Returns the manga info for this PDF, or null if the file could not be opened
	 * or parsed (corrupt/unsupported PDF) — callers should skip it, not crash.
	 */
	suspend fun getManga(withDetails: Boolean): LocalManga? = runCatchingCancellable {
		val pageCount = renderer.getPageCount(docUri) ?: return@runCatchingCancellable null
		if (pageCount <= 0) return@runCatchingCancellable null

		val title = displayName.fileNameToTitle()
		val wrappedUrl = buildDocUri(docUri).toString()
		val chapterId = "$docUri:chapter".longHashCode()
		val manga = Manga(
			id = docUri.toString().longHashCode(),
			title = title,
			url = wrappedUrl,
			publicUrl = wrappedUrl,
			source = LocalMangaSource,
			coverUrl = null, // first page can be wired in as the cover once rendered once
			largeCoverUrl = null,
			chapters = if (withDetails) {
				listOf(
					MangaChapter(
						id = chapterId,
						title = title,
						number = 1f,
						volume = 0,
						source = LocalMangaSource,
						uploadDate = 0L,
						url = wrappedUrl,
						scanlator = null,
						branch = null,
					),
				)
			} else {
				null
			},
			altTitles = emptySet(),
			rating = -1f,
			contentRating = null,
			tags = emptySet(),
			state = null,
			authors = emptySet(),
			description = null,
		)
		// LocalManga's File field is only used for things like "delete from disk" /
		// size display elsewhere; safe to pass a best-effort File even for a
		// content:// tree Uri, since PDFs opened via SAF may not have a real path.
		LocalManga(manga, docUri.toFileOrPlaceholder())
	}.onFailure { e ->
		e.printStackTraceDebug()
	}.getOrNull()

	/** Returns the page list for this PDF's single chapter. */
	suspend fun getPages(chapter: MangaChapter): List<MangaPage> = runCatchingCancellable {
		val pageCount = renderer.getPageCount(docUri) ?: return@runCatchingCancellable emptyList()
		(0 until pageCount).map { index ->
			val pageUri = PdfPageRenderer.buildPageUri(docUri, index)
			MangaPage(
				id = pageUri.toString().longHashCode(),
				url = pageUri.toString(),
				preview = null,
				source = LocalMangaSource,
			)
		}
	}.onFailure { e ->
		e.printStackTraceDebug()
	}.getOrDefault(emptyList())

	private fun Uri.toFileOrPlaceholder(): File = path?.let { File(it) } ?: File(displayName)

	private fun String.fileNameToTitle() = substringBeforeLast('.')
		.replace('_', ' ')
		.replaceFirstChar { it.uppercase() }

	companion object {

		private const val URI_SCHEME_PDF_DOC = "usagi-pdf-doc"

		/**
		 * Wraps a PDF's real content:// uri so [org.draken.usagi.local.data.LocalMangaRepository]
		 * can tell "this manga/chapter is backed by a PDF" apart from a normal CBZ/dir, purely by
		 * looking at [tsuki.model.Manga.url] / [MangaChapter.url] — without needing a new
		 * [tsuki.model.MangaSource].
		 */
		fun buildDocUri(pdfUri: Uri): Uri = Uri.Builder()
			.scheme(URI_SCHEME_PDF_DOC)
			.authority("doc")
			.appendQueryParameter("src", pdfUri.toString())
			.build()

		fun isPdfDocUri(uri: Uri): Boolean = uri.scheme == URI_SCHEME_PDF_DOC

		/** Recovers the real content:// uri of the PDF from a wrapped manga/chapter url. */
		fun parseDocUri(uri: Uri): Uri? = uri.getQueryParameter("src")?.let(Uri::parse)
	}
}
