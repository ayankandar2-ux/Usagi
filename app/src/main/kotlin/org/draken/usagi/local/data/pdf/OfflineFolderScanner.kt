package org.draken.usagi.local.data.pdf

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.draken.usagi.core.util.ext.printStackTraceDebug
import tsuki.util.runCatchingCancellable
import javax.inject.Inject
import javax.inject.Singleton

/** One file found while scanning an "Offline Reader" folder. */
sealed interface OfflineFile {
	val docUri: Uri
	val displayName: String

	data class Cbz(override val docUri: Uri, override val displayName: String) : OfflineFile
	data class Pdf(override val docUri: Uri, override val displayName: String) : OfflineFile
}

/**
 * Scans a folder picked via `ACTION_OPEN_DOCUMENT_TREE` for CBZ/ZIP and PDF files.
 *
 * Runs entirely on [Dispatchers.IO]. Every entry is inspected independently and wrapped
 * in its own try/catch (via [runCatchingCancellable]) so one corrupt/unreadable file or
 * one unreadable subfolder is skipped rather than aborting or crashing the whole scan.
 */
@Singleton
class OfflineFolderScanner @Inject constructor(
	@ApplicationContext private val context: Context,
) {

	suspend fun scan(treeUri: Uri, recursive: Boolean = true): List<OfflineFile> = withContext(Dispatchers.IO) {
		val root = runCatchingCancellable {
			DocumentFile.fromTreeUri(context, treeUri)
		}.onFailure { e ->
			e.printStackTraceDebug()
		}.getOrNull() ?: return@withContext emptyList()

		val result = ArrayList<OfflineFile>()
		scanInto(root, recursive, result)
		result.sortWith(offlineFileChapterOrder)
		result
	}

	private fun scanInto(dir: DocumentFile, recursive: Boolean, out: MutableList<OfflineFile>) {
		val children = runCatchingCancellable {
			dir.listFiles()
		}.onFailure { e ->
			e.printStackTraceDebug()
		}.getOrNull() ?: return

		for (entry in children) {
			runCatchingCancellable {
				when {
					entry.isDirectory && recursive -> scanInto(entry, recursive, out)
					!entry.isFile -> Unit
					!entry.canRead() -> Unit
					entry.isCbz() -> out += OfflineFile.Cbz(entry.uri, entry.nameOrFallback())
					entry.isPdf() -> out += OfflineFile.Pdf(entry.uri, entry.nameOrFallback())
					else -> Unit
				}
			}.onFailure { e ->
				// A single unreadable/corrupt entry (bad permissions, mid-scan removal,
				// malformed provider metadata, etc.) must never abort the whole scan.
				e.printStackTraceDebug()
			}
		}
	}

	private fun DocumentFile.isCbz(): Boolean {
		val name = name.orEmpty().lowercase()
		return name.endsWith(".cbz") || name.endsWith(".zip") ||
			type == "application/vnd.comicbook+zip" || type == "application/zip"
	}

	private fun DocumentFile.isPdf(): Boolean {
		val name = name.orEmpty().lowercase()
		return name.endsWith(".pdf") || type == "application/pdf"
	}

	private fun DocumentFile.nameOrFallback(): String = name.orEmpty().ifEmpty { uri.lastPathSegment.orEmpty() }
}
