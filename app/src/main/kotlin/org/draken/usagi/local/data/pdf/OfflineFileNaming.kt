package org.draken.usagi.local.data.pdf

import android.net.Uri

private val CHAPTER_TOKEN_REGEXES = listOf(
	Regex("""\[Ch]\[(\d+)]""", RegexOption.IGNORE_CASE),
	Regex("""\bChapter\s*-?\s*(\d+)\b""", RegexOption.IGNORE_CASE),
	Regex("""\bCh\.?\s*-?\s*(\d+)\b""", RegexOption.IGNORE_CASE),
)
private val ANY_NUMBER_REGEX = Regex("""\d+""")

/**
 * Extracts a chapter number from a scanned file's display name, trying a few common
 * naming conventions (`[Ch][153]`, `Ch - 106`, `Chapter 12`, ...), and finally falling
 * back to the first standalone number anywhere in the name. Returns null only if the
 * name has no number at all.
 */
fun OfflineFile.chapterNumber(): Int? {
	for (regex in CHAPTER_TOKEN_REGEXES) {
		regex.find(displayName)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
	}
	return ANY_NUMBER_REGEX.find(displayName)?.value?.toIntOrNull()
}

/** Sorts scanned files by chapter number (unknown numbers last), then by name as a tiebreaker. */
val offlineFileChapterOrder: Comparator<OfflineFile> = compareBy(
	{ it.chapterNumber() ?: Int.MAX_VALUE },
	{ it.displayName },
)

private const val URI_SCHEME_OFFLINE_FOLDER = "usagi-offline-folder"

/**
 * A stable, made-up uri identifying a whole folder-as-manga (not any single file) — encodes
 * the scanned folder's tree uri, so the chapter list can be rebuilt from scratch by
 * re-scanning it. There's no persistent database backing this: the folder itself is the
 * source of truth, so [org.draken.usagi.local.data.LocalMangaRepository.getDetails] just
 * re-scans it every time rather than depending on stored state.
 */
fun buildOfflineFolderUri(rootUri: Uri): Uri = Uri.Builder()
	.scheme(URI_SCHEME_OFFLINE_FOLDER)
	.authority("folder")
	.appendQueryParameter("root", rootUri.toString())
	.build()

fun isOfflineFolderUri(uri: Uri): Boolean = uri.scheme == URI_SCHEME_OFFLINE_FOLDER

/** Recovers the folder tree uri from a wrapped folder-manga uri, or null if malformed. */
fun parseOfflineFolderUri(uri: Uri): Uri? = uri.getQueryParameter("root")?.let(Uri::parse)
