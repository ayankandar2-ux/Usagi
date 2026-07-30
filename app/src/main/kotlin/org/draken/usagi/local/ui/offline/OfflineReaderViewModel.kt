package org.draken.usagi.local.ui.offline

import android.net.Uri
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.draken.usagi.core.ui.BaseViewModel
import org.draken.usagi.core.util.ext.MutableEventFlow
import org.draken.usagi.core.util.ext.call
import org.draken.usagi.local.data.LocalStorageManager
import org.draken.usagi.local.domain.offline.OfflineFolderMangaBuilder
import tsuki.model.Manga
import javax.inject.Inject

@HiltViewModel
class OfflineReaderViewModel @Inject constructor(
	private val mangaBuilder: OfflineFolderMangaBuilder,
	private val storageManager: LocalStorageManager,
	private val libraryPrefs: OfflineLibraryPrefs,
) : BaseViewModel() {

	private val _library = MutableStateFlow(emptyList<Manga>())
	val library = _library.asStateFlow()

	private val _hasLoadedOnce = MutableStateFlow(false)
	val hasLoadedOnce = _hasLoadedOnce.asStateFlow()

	private val _onMangaReady = MutableEventFlow<Manga>()
	val onMangaReady get() = _onMangaReady

	init {
		reloadLibrary()
	}

	/** Called after the user picks a folder via ACTION_OPEN_DOCUMENT_TREE. */
	fun onFolderPicked(treeUri: Uri) {
		launchLoadingJob(Dispatchers.Default) {
			// Persist the grant so the folder can be re-scanned across app restarts
			// without asking the user to pick it again.
			storageManager.takePermissions(treeUri)
			libraryPrefs.addFolder(treeUri)
			reloadLibraryInternal()
		}
	}

	fun onLibraryItemClick(manga: Manga) {
		_onMangaReady.call(manga)
	}

	private fun reloadLibrary() {
		launchLoadingJob(Dispatchers.Default) {
			reloadLibraryInternal()
		}
	}

	private suspend fun reloadLibraryInternal() {
		val mangaList = libraryPrefs.folders().mapNotNull { folderUri -> mangaBuilder.buildManga(folderUri) }
		_library.value = mangaList
		_hasLoadedOnce.value = true
	}
}
