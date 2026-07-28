package org.draken.usagi.local.domain.model

import android.net.Uri
import androidx.core.net.toFile
import androidx.core.net.toUri
import org.draken.usagi.core.util.ext.contains
import org.draken.usagi.core.util.ext.creationTime
import tsuki.model.Manga
import tsuki.model.MangaTag
import java.io.File

data class LocalManga(
	val manga: Manga,
	val file: File = manga.url.toUri().toFileOrFallback(),
) {

	var createdAt: Long = -1L
		private set
		get() {
			if (field == -1L) {
				field = runCatching { file.creationTime }.getOrDefault(0L)
			}
			return field
		}

	fun toUri(): Uri = manga.url.toUri()

	fun isMatchesQuery(query: String): Boolean {
		return manga.title.contains(query, ignoreCase = true) ||
			manga.altTitles.contains(query, ignoreCase = true) ||
			manga.authors.contains(query, ignoreCase = true)
	}

	fun containsTags(tags: Collection<String>): Boolean {
		return tags.all { tag -> tag in manga.tags }
	}

	fun containsAnyTag(tags: Collection<String>): Boolean {
		return tags.any { tag -> tag in manga.tags }
	}

	private operator fun Collection<MangaTag>.contains(title: String): Boolean {
		return any { it.title.equals(title, ignoreCase = true) }
	}

	override fun toString(): String {
		return "LocalManga(${file.path}: ${manga.title})"
	}
}

/**
 * [Uri.toFile] throws for any non-`file://` uri. [LocalManga]'s default [LocalManga.file] is
 * derived from [tsuki.model.Manga.url] wherever callers construct `LocalManga(manga)` without
 * an explicit file — which includes call sites outside this feature (e.g. the details screen)
 * that assume every local-sourced manga has a real filesystem path. That assumption doesn't
 * hold for manga backed by a SAF `content://` PDF, so this falls back to a synthetic [File]
 * (used only for things like [LocalManga.toString]/[LocalManga.createdAt] display) instead of
 * crashing every such call site.
 */
private fun Uri.toFileOrFallback(): File = runCatching { toFile() }.getOrElse { File(toString()) }
