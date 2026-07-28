package org.draken.usagi.local.domain.offline

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.draken.usagi.core.util.ext.printStackTraceDebug
import org.draken.usagi.local.data.input.LocalMangaParser
import org.draken.usagi.local.data.pdf.OfflineFile
import org.draken.usagi.local.data.pdf.PdfMangaParser
import org.draken.usagi.local.data.pdf.PdfPageRenderer
import org.draken.usagi.local.data.pdf.toRealFileOrNull
import tsuki.model.Manga
import tsuki.util.runCatchingCancellable
import javax.inject.Inject

/**
 * Resolves a file found by [org.draken.usagi.local.data.pdf.OfflineFolderScanner] into a
 * [Manga] with its chapters, ready to be handed to the existing reader navigation.
 *
 * Returns null (never throws) when a file can't be opened — a corrupt PDF, or a CBZ whose
 * SAF uri can't be resolved back to a real path (see [toRealFileOrNull]) — so the caller can
 * skip it (e.g. show a "couldn't open this file" toast) instead of crashing.
 */
class OpenOfflineFileUseCase @Inject constructor(
	@ApplicationContext private val context: Context,
	private val pdfPageRenderer: PdfPageRenderer,
) {

	suspend operator fun invoke(file: OfflineFile): Manga? = withContext(Dispatchers.IO) {
		runCatchingCancellable {
			when (file) {
				is OfflineFile.Pdf -> PdfMangaParser(file.docUri, file.displayName, pdfPageRenderer)
					.getManga(withDetails = true)
					?.manga

				is OfflineFile.Cbz -> {
					val realFile = file.docUri.toRealFileOrNull(context) ?: return@runCatchingCancellable null
					LocalMangaParser.getOrNull(realFile)?.getManga(withDetails = true)?.manga
				}
			}
		}.onFailure { e ->
			e.printStackTraceDebug()
		}.getOrNull()
	}
}
