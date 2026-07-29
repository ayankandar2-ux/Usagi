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
 * A stable, made-up uri identifying a whole grouped series (not any single file). Used as
 * [tsuki.model.Manga.url] for a manga built by grouping several chapter files together —
 * see [org.draken.usagi.local.domain.offline.OpenOfflineFileUseCase]. There's no real
 * resource behind it: the manga's chapter list is already fully known at scan time, so
 * [org.draken.usagi.local.data.LocalMangaRepository.getDetails] just returns it unchanged.
 */
fun buildOfflineSeriesUri(seriesKey: String): Uri = Uri.Builder()
	.scheme(URI_SCHEME_OFFLINE_SERIES)
	.authority("series")
	.appendQueryParameter("key", seriesKey)
	.build()

fun isOfflineSeriesUri(uri: Uri): Boolean = uri.scheme == URI_SCHEME_OFFLINE_SERIES
