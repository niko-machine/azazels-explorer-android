package com.azazel.explorer.ui.browser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.azazel.explorer.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileAdapter(
    val items: List<File>,
    private val onClick: (File) -> Unit,
    private val onLongClick: ((File) -> Unit)? = null
) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tv_file_name)
        val details: TextView = view.findViewById(R.id.tv_file_details)
        val icon: ImageView = view.findViewById(R.id.iv_file_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = items[position]
        holder.name.text = file.name
        
        val dateText = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            .format(Date(file.lastModified()))
        val sizeText = if (file.isDirectory) "" else " - ${formatFileSize(file.length())}"
        holder.details.text = "$dateText$sizeText"

        val isImage = file.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp")
        val isVideo = file.extension.lowercase() in listOf("mp4", "mkv", "avi", "mov")

        when {
            file.isDirectory -> holder.icon.setImageResource(R.drawable.ic_folder)
            isImage -> Glide.with(holder.itemView)
                .load(file)
                .placeholder(R.drawable.ic_image)
                .error(R.drawable.ic_image)
                .centerCrop()
                .into(holder.icon)
            isVideo -> Glide.with(holder.itemView)
                .load(file)
                .placeholder(R.drawable.ic_video)
                .error(R.drawable.ic_video)
                .centerCrop()
                .into(holder.icon)
            else -> holder.icon.setImageResource(R.drawable.ic_file)
        }

        holder.itemView.setOnClickListener { onClick(file) }
        holder.itemView.setOnLongClickListener {
            onLongClick?.invoke(file)
            onLongClick != null
        }
    }

    override fun getItemCount() = items.size

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
        return String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0)
    }
}
