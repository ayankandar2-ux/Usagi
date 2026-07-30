package org.draken.usagi.local.ui.offline

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.draken.usagi.R
import org.draken.usagi.core.exceptions.resolve.SnackbarErrorObserver
import org.draken.usagi.core.nav.router
import org.draken.usagi.core.os.OpenDocumentTreeHelper
import org.draken.usagi.core.ui.BaseActivity
import org.draken.usagi.core.util.ext.consumeAllSystemBarsInsets
import org.draken.usagi.core.util.ext.observe
import org.draken.usagi.core.util.ext.observeEvent
import org.draken.usagi.core.util.ext.tryLaunch
import org.draken.usagi.databinding.ActivityOfflineReaderBinding

@AndroidEntryPoint
class OfflineReaderActivity : BaseActivity<ActivityOfflineReaderBinding>() {

	private val viewModel: OfflineReaderViewModel by viewModels()

	private val pickFolderLauncher = OpenDocumentTreeHelper(
		activityResultCaller = this,
		flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
	) { uri ->
		if (uri != null) viewModel.onFolderPicked(uri)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityOfflineReaderBinding.inflate(layoutInflater))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		title = getString(R.string.offline_reader)

		val adapter = OfflineFolderAdapter { manga -> viewModel.onLibraryItemClick(manga) }
		viewBinding.recyclerView.adapter = adapter

		viewBinding.fabSelectFolder.setOnClickListener {
			if (!pickFolderLauncher.tryLaunch(null)) {
				Snackbar.make(viewBinding.root, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
			}
		}

		viewModel.library.observe(this) { library ->
			adapter.submitList(library)
			viewBinding.textViewEmpty.isVisible = library.isEmpty() && viewModel.hasLoadedOnce.value
		}
		viewModel.isLoading.observe(this) { viewBinding.progressBar.isVisible = it }
		viewModel.onMangaReady.observeEvent(this) { manga ->
			router.openDetails(manga)
		}
		viewModel.onError.observeEvent(
			this,
			SnackbarErrorObserver(viewBinding.root, null, exceptionResolver, null),
		)
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		viewBinding.fabSelectFolder.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
			rightMargin = topMargin + barsInsets.right
			leftMargin = topMargin + barsInsets.left
			bottomMargin = topMargin + barsInsets.bottom
		}
		viewBinding.appbar.updatePadding(
			left = barsInsets.left,
			right = barsInsets.right,
			top = barsInsets.top,
		)
		viewBinding.recyclerView.updatePadding(
			left = barsInsets.left,
			right = barsInsets.right,
			bottom = barsInsets.bottom,
		)
		return insets.consumeAllSystemBarsInsets()
	}
}
