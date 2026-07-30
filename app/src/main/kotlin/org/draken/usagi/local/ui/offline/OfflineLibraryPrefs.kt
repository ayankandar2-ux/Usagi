package org.draken.usagi.local.ui.offline

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/** Remembers every folder added to the Offline Reader library, so they don't need re-picking. */
@Singleton
class OfflineLibraryPrefs @Inject constructor(
	@ApplicationContext context: Context,
) {

	private val prefs = context.getSharedPreferences("offline_reader", Context.MODE_PRIVATE)

	fun folders(): List<Uri> {
		val raw = prefs.getString(KEY_FOLDERS, null) ?: return emptyList()
		return runCatching {
			val array = JSONArray(raw)
			(0 until array.length()).mapNotNull { i -> array.optString(i, null)?.let(Uri::parse) }
		}.getOrDefault(emptyList())
	}

	fun addFolder(uri: Uri) {
		val current = folders().toMutableList()
		if (uri !in current) {
			current += uri
			save(current)
		}
	}

	fun removeFolder(uri: Uri) {
		save(folders().filterNot { it == uri })
	}

	private fun save(folders: List<Uri>) {
		val array = JSONArray()
		folders.forEach { array.put(it.toString()) }
		prefs.edit { putString(KEY_FOLDERS, array.toString()) }
	}

	private companion object {
		const val KEY_FOLDERS = "library_folders"
	}
}
