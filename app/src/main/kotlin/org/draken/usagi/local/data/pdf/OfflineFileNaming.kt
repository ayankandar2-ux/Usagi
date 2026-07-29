package org.draken.usagi.local.data.pdf

import android.net.Uri

private val CHAPTER_TOKEN_REGEXES = listOf(
	Regex("""\[Ch]\[(\d+)]""", RegexOption.IGNORE_CASE),
	Regex("""\bChapter\s*-?\s*(\d+)\b""", RegexOption.IGNORE_CASE),
	Regex("""\bCh\.?\s*-?\s*(\d+)\b""", RegexOption.IGNORE_CASE),
)
private val ANY_NUMBER_REGEX = Regex("""\d+""")
private val WHITESPACE_REGEX = Regex("""\s+""")

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

/**
 * A grouping key for "which series does this file belong to": the display name with the
 * matched chapter-number token and file extension removed, then normalized for case and
 * whitespace. Files from the same channel/series that only differ by chapter number end
 * up sharing this key.
 */
fun OfflineFile.seriesKey(): String {
	var name = displayName.substringBeforeLast('.')
	for (regex in CHAPTER_TOKEN_REGEXES) {
		val match = regex.find(name) ?: continue
		name = name.removeRange(match.range)
		break
	}
	return name.trim().replace(WHITESPACE_REGEX, " ").lowercase()
}

/** Turns a series key back into a human-readable title for display. */
fun String.seriesKeyToTitle(): String = split(' ')
	.joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
	.trim()

/** Sorts scanned files by chapter number (unknown numbers last), then by name as a tiebreaker. */
val offlineFileChapterOrder: Comparator<OfflineFile> = compareBy(
	{ it.chapterNumber() ?: Int.MAX_VALUE },
	{ it.displayName },
)

private const val URI_SCHEME_OFFLINE_SERIES = "usagi-offline-series"

/**
 * A stable, made-up uri identifying a whole grouped series (not any single file) — encodes
 * both the scanned folder root and the series key, so the chapter list can be rebuilt from
 * scratch by re-scanning [rootUri] and filtering to [seriesKey]. This matters because the
 * app doesn't always hand [org.draken.usagi.local.data.LocalMangaRepository.getDetails] the
 * fully-populated Manga we originally built — e.g. after it round-trips through history/
 * favorites storage — so getDetails must be able to reconstruct the chapters on its own
 * rather than merely returning whatever (possibly chapter-less) Manga it was given.
 */
fun buildOfflineSeriesUri(rootUri: Uri, seriesKey: String): Uri = Uri.Builder()
	.scheme(URI_SCHEME_OFFLINE_SERIES)
	.authority("series")
	.appendQueryParameter("root", rootUri.toString())
	.appendQueryParameter("key", seriesKey)
	.build()

fun isOfflineSeriesUri(uri: Uri): Boolean = uri.scheme == URI_SCHEME_OFFLINE_SERIES

/** Recovers (root folder uri, series key) from a wrapped series uri, or null if malformed. */
fun parseOfflineSeriesUri(uri: Uri): Pair<Uri, String>? {
	val root = uri.getQueryParameter("root")?.let(Uri::parse) ?: return null
	val key = uri.getQueryParameter("key") ?: return null
	return root to key
}
