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
import org.draken.usagi.local.data.pdf.OfflineFile
import org.draken.usagi.local.data.pdf.OfflineFolderScanner
import org.draken.usagi.local.domain.offline.OpenOfflineFileUseCase
import org.draken.usagi.local.domain.offline.OpenOfflineResult
import javax.inject.Inject

@HiltViewModel
class OfflineReaderViewModel @Inject constructor(
	private val scanner: OfflineFolderScanner,
	private val openOfflineFile: OpenOfflineFileUseCase,
	private val storageManager: LocalStorageManager,
) : BaseViewModel() {

	private val _items = MutableStateFlow(emptyList<OfflineFile>())
	val items = _items.asStateFlow()

	private val _hasScannedOnce = MutableStateFlow(false)
	val hasScannedOnce = _hasScannedOnce.asStateFlow()

	private var lastTreeUri: Uri? = null

	private val _onMangaReady = MutableEventFlow<OpenOfflineResult>()
	val onMangaReady get() = _onMangaReady

	/** Called after the user picks a folder via ACTION_OPEN_DOCUMENT_TREE. */
	fun onFolderPicked(treeUri: Uri) {
		launchLoadingJob(Dispatchers.Default) {
			// Persist the grant so the folder can be re-scanned across app restarts
			// without asking the user to pick it again.
			storageManager.takePermissions(treeUri)
			lastTreeUri = treeUri
			_items.value = scanner.scan(treeUri)
			_hasScannedOnce.value = true
		}
	}

	fun onFileClick(file: OfflineFile) {
		val rootUri = lastTreeUri ?: return
		launchLoadingJob(Dispatchers.Default) {
			val result = openOfflineFile(file, rootUri)
			if (result != null) {
				_onMangaReady.call(result)
			} else {
				errorEvent.call(IllegalStateException("Could not open ${file.displayName}"))
			}
		}
	}
}
