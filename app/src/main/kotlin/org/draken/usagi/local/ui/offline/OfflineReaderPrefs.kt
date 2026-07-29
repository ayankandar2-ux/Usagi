package org.draken.usagi.local.ui.offline

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Remembers the last folder picked for Offline Reader, so it doesn't need to be re-picked every time. */
@Singleton
class OfflineReaderPrefs @Inject constructor(
	@ApplicationContext context: Context,
) {

	private val prefs = context.getSharedPreferences("offline_reader", Context.MODE_PRIVATE)

	var lastFolderUri: Uri?
		get() = prefs.getString(KEY_LAST_FOLDER, null)?.let(Uri::parse)
		set(value) = prefs.edit { putString(KEY_LAST_FOLDER, value?.toString()) }

	private companion object {
		const val KEY_LAST_FOLDER = "last_folder_uri"
	}
}
