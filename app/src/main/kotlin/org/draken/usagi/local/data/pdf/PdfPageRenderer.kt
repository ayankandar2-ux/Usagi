package org.draken.usagi.local.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.draken.usagi.core.util.ext.printStackTraceDebug
import org.draken.usagi.local.data.LocalStorageCache
import tsuki.util.runCatchingCancellable
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders individual pages of a PDF file to bitmap files, on demand, one page at a time.
 *
 * Design constraints (required for low-end Android devices):
 * - Every render happens under [Dispatchers.IO]; nothing here ever touches the main thread.
 * - At most one decoded page [Bitmap] exists at a time — it is written to the disk cache and
 *   recycled immediately after, before the next page is touched. Full PDFs are never loaded
 *   into memory at once; only [PdfRenderer] keeps a lightweight handle to the file itself.
 * - Every file/PDF operation is wrapped so a single corrupt or unreadable PDF page returns
 *   null instead of crashing the caller.
 */
@Singleton
class PdfPageRenderer @Inject constructor(
	@ApplicationContext private val context: Context,
) {

	// PdfRenderer (and PdfRenderer.Page) are not thread-safe, and only one Page may be
	// open on a given PdfRenderer at a time. This mutex serializes all access.
	private val mutex = Mutex()

	/**
	 * Renders [pageIndex] (0-based) of the PDF at [pdfUri] into the shared [cache] and
	 * returns the resulting file, or null if the PDF/page could not be read for any reason.
	 * Never throws.
	 */
	suspend fun renderPage(
		pdfUri: Uri,
		pageIndex: Int,
		cacheKey: String,
		cache: LocalStorageCache,
	): File? = mutex.withLock {
		withContext(Dispatchers.IO) {
			runCatchingCancellable {
				openRenderer(pdfUri)?.use { renderer ->
					if (pageIndex !in 0 until renderer.pageCount) {
						return@use null
					}
					renderPageInternal(renderer, pageIndex, cacheKey, cache)
				}
			}.onFailure { e ->
				e.printStackTraceDebug()
			}.getOrNull()
		}
	}

	/** Returns the page count of the PDF, or null if it cannot be opened/parsed. */
	suspend fun getPageCount(pdfUri: Uri): Int? = withContext(Dispatchers.IO) {
		runCatchingCancellable {
			openRenderer(pdfUri)?.use { it.pageCount }
		}.onFailure { e ->
			e.printStackTraceDebug()
		}.getOrNull()
	}

	private suspend fun renderPageInternal(
		renderer: PdfRenderer,
		pageIndex: Int,
		cacheKey: String,
		cache: LocalStorageCache,
	): File? = runCatchingCancellable {
		renderer.openPage(pageIndex).use { page ->
			// One bitmap, sized to this page only. RGB_565 halves memory vs ARGB_8888,
			// which matters on the low-end devices this app targets; PDF pages don't need
			// an alpha channel.
			// PdfRenderer.Page.render() requires an ARGB_8888 destination bitmap; other
			// configs (e.g. RGB_565, which would have halved memory use) throw here.
			val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
			try {
				bitmap.eraseColor(Color.WHITE)
				page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
				cache.set(cacheKey, bitmap)
			} finally {
				bitmap.recycle()
			}
		}
	}.onFailure { e ->
		e.printStackTraceDebug()
	}.getOrNull()

	private fun openRenderer(pdfUri: Uri): PdfRenderer? = runCatchingCancellable {
		val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(pdfUri, "r")
			?: return@runCatchingCancellable null
		try {
			PdfRenderer(pfd)
		} catch (e: Throwable) {
			// PdfRenderer() takes ownership of the fd only on success; close it ourselves
			// on failure (e.g. corrupt PDF) so it isn't leaked.
			pfd.close()
			throw e
		}
	}.onFailure { e ->
		e.printStackTraceDebug()
	}.getOrNull()

	companion object {

		const val URI_SCHEME_PDF_PAGE = "usagi-pdf-page"

		/** Builds the synthetic page URL stored in [tsuki.model.MangaPage.url]. */
		fun buildPageUri(pdfUri: Uri, pageIndex: Int): Uri = Uri.Builder()
			.scheme(URI_SCHEME_PDF_PAGE)
			.authority("page")
			.appendQueryParameter("src", pdfUri.toString())
			.appendQueryParameter("index", pageIndex.toString())
			.build()

		fun isPdfPageUri(uri: Uri): Boolean = uri.scheme == URI_SCHEME_PDF_PAGE

		fun parsePdfUri(uri: Uri): Uri? = uri.getQueryParameter("src")?.let(Uri::parse)

		fun parsePageIndex(uri: Uri): Int? = uri.getQueryParameter("index")?.toIntOrNull()
	}
}
