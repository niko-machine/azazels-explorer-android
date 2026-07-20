package com.azazel.explorer.ui.properties

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.azazel.explorer.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FilePropertiesFragment : Fragment(R.layout.fragment_file_properties) {

    private val args: FilePropertiesFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val file = File(args.filePath)
        val preview = view.findViewById<ImageView>(R.id.iv_preview)

        view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val isImage = file.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp")
        val isVideo = file.extension.lowercase() in listOf("mp4", "mkv", "avi", "mov")

        when {
            file.isDirectory -> preview.setImageResource(R.drawable.ic_folder)
            isImage -> Glide.with(this)
                .load(file)
                .placeholder(R.drawable.ic_image)
                .error(R.drawable.ic_image)
                .centerCrop()
                .into(preview)
            isVideo -> Glide.with(this)
                .load(file)
                .placeholder(R.drawable.ic_video)
                .error(R.drawable.ic_video)
                .centerCrop()
                .into(preview)
            else -> preview.setImageResource(R.drawable.ic_file)
        }

        setupPropertyRows(view, file)
    }

    private fun setupPropertyRows(view: View, file: File) {
        val sizeText = formatFileSize(file.length())
        val dateText = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
            .format(Date(file.lastModified()))

        setPropertyRow(view, R.id.row_name, R.drawable.ic_file, "NAME", file.name)
        setPropertyRow(view, R.id.row_path, R.drawable.ic_folder, "LOCATION", file.absolutePath)
        setPropertyRow(view, R.id.row_size, R.drawable.ic_file, "SIZE", sizeText)
        setPropertyRow(view, R.id.row_modified, R.drawable.ic_folder, "LAST MODIFIED", dateText)
        setPropertyRow(view, R.id.row_readable, R.drawable.ic_file, "READABLE", file.canRead().toString())
        setPropertyRow(view, R.id.row_writable, R.drawable.ic_file, "WRITABLE", file.canWrite().toString())
    }

    private fun setPropertyRow(view: View, rowId: Int, iconRes: Int, label: String, value: String) {
        val row = view.findViewById<View>(rowId)
        row.findViewById<ImageView>(R.id.iv_row_icon).setImageResource(iconRes)
        row.findViewById<TextView>(R.id.tv_row_label).text = label
        row.findViewById<TextView>(R.id.tv_row_value).text = value
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
        return String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0)
    }
}
