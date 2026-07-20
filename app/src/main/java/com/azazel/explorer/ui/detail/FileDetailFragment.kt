package com.azazel.explorer.ui.detail

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.azazel.explorer.R
import java.io.File

class FileDetailFragment : Fragment(R.layout.fragment_file_detail) {

    private val args: FileDetailFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val file = File(args.filePath)
        val preview = view.findViewById<ImageView>(R.id.iv_preview)

        view.findViewById<TextView>(R.id.tv_file_name).text = args.fileName
        view.findViewById<TextView>(R.id.tv_file_path).text = args.filePath

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

        view.findViewById<Button>(R.id.btn_properties).setOnClickListener {
            val action = FileDetailFragmentDirections.actionDetailToProperties(args.filePath)
            findNavController().navigate(action)
        }

        view.findViewById<Button>(R.id.btn_share).setOnClickListener {
            shareFile(args.filePath)
        }

        preview.setOnClickListener {
            val isImage = file.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp")
            val isVideo = file.extension.lowercase() in listOf("mp4", "mkv", "avi", "mov")
            
            when {
                isImage -> {
                    val action = FileDetailFragmentDirections.actionDetailToPreview(args.filePath)
                    findNavController().navigate(action)
                }
                isVideo -> {
                    openVideo(file)
                }
            }
        }
    }

    private fun openVideo(file: File) {
        val uri = FileProvider.getUriForFile(
            requireContext(), "${requireContext().packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No video player found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(path: String) {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(requireContext(), R.string.msg_file_not_found, Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(
            requireContext(), "${requireContext().packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = requireContext().contentResolver.getType(uri) ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.chooser_share_file)))
    }
}
