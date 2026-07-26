package com.azazel.explorer.ui.properties

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
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
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)

        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        setupProperties(view, file)

        val isImage = file.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp")
        val isVideo = file.extension.lowercase() in listOf("mp4", "mkv", "avi", "mov")

        when {
            file.isDirectory -> preview.setImageResource(R.drawable.ic_folder)
            isImage -> Glide.with(this).load(file).centerCrop().into(preview)
            isVideo -> Glide.with(this).load(file).centerCrop().into(preview)
            else -> preview.setImageResource(R.drawable.ic_file)
        }

        view.findViewById<View>(R.id.btn_copy_path).setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("path", file.absolutePath)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), R.string.msg_file_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupProperties(view: View, file: File) {
        setPropertyRow(view.findViewById(R.id.row_name), "Name", file.name)
        setPropertyRow(view.findViewById(R.id.row_path), "Location", file.absolutePath)
        setPropertyRow(view.findViewById(R.id.row_size), "Size", if (file.isDirectory) "--" else formatFileSize(file.length()))
        
        val dateText = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
        setPropertyRow(view.findViewById(R.id.row_modified), "Last Modified", dateText)
        
        setPropertyRow(view.findViewById(R.id.row_readable), "Readable", if (file.canRead()) "Yes" else "No")
        setPropertyRow(view.findViewById(R.id.row_writable), "Writable", if (file.canWrite()) "Yes" else "No")
    }

    private fun setPropertyRow(rowView: View, label: String, value: String) {
        rowView.findViewById<TextView>(R.id.tv_row_label).text = label
        rowView.findViewById<TextView>(R.id.tv_row_value).text = value
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
