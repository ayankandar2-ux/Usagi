package org.draken.usagi.local.ui.offline

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.draken.usagi.R
import org.draken.usagi.databinding.ItemOfflineFileBinding
import org.draken.usagi.local.data.pdf.OfflineFile

class OfflineFileAdapter(
	private val onItemClick: (OfflineFile) -> Unit,
) : ListAdapter<OfflineFile, OfflineFileAdapter.ViewHolder>(DiffCallback) {

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val binding = ItemOfflineFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return ViewHolder(binding)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		holder.bind(getItem(position))
	}

	inner class ViewHolder(private val binding: ItemOfflineFileBinding) : RecyclerView.ViewHolder(binding.root) {

		init {
			binding.root.setOnClickListener {
				val position = bindingAdapterPosition
				if (position != RecyclerView.NO_POSITION) {
					onItemClick(getItem(position))
				}
			}
		}

		fun bind(item: OfflineFile) {
			binding.textViewTitle.text = item.displayName
			binding.imageViewIcon.setImageResource(
				when (item) {
					is OfflineFile.Pdf -> R.drawable.ic_book_page
					is OfflineFile.Cbz -> R.drawable.ic_manga_source
				},
			)
		}
	}

	private object DiffCallback : DiffUtil.ItemCallback<OfflineFile>() {
		override fun areItemsTheSame(oldItem: OfflineFile, newItem: OfflineFile) = oldItem.docUri == newItem.docUri
		override fun areContentsTheSame(oldItem: OfflineFile, newItem: OfflineFile) = oldItem == newItem
	}
}
