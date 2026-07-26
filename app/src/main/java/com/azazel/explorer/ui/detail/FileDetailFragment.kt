package com.azazel.explorer.ui.detail

import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.azazel.explorer.R
import java.io.File
import java.util.Locale

class FileDetailFragment : Fragment(R.layout.fragment_file_detail) {

    private val args: FileDetailFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val file = File(args.filePath)
        val preview = view.findViewById<ImageView>(R.id.iv_preview)
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)

        toolbar.title = file.name
        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        view.findViewById<TextView>(R.id.tv_file_name_header).text = file.name
        
        view.findViewById<Button>(R.id.btn_properties).setOnClickListener {
            val action = FileDetailFragmentDirections.actionDetailToProperties(args.filePath)
            findNavController().navigate(action)
        }

        view.findViewById<Button>(R.id.btn_share).setOnClickListener {
            shareFile(args.filePath)
        }

        setupActionButtons(view, file)
        validateActions(view, file)

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

        preview.setOnClickListener {
            if (isImage) {
                val action = FileDetailFragmentDirections.actionDetailToPreview(args.filePath)
                findNavController().navigate(action)
            } else if (isVideo) {
                openVideo(file)
            }
        }
    }

    private fun validateActions(view: View, file: File) {
        val isWritable = file.canWrite()
        view.findViewById<View>(R.id.btn_rename).isEnabled = isWritable
        view.findViewById<View>(R.id.btn_delete).isEnabled = isWritable
        view.findViewById<View>(R.id.btn_move).isEnabled = isWritable

        // Pre-validate Open With
        if (file.isDirectory) {
            view.findViewById<View>(R.id.btn_open_with).isEnabled = false
        } else {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file.absoluteFile)
                setDataAndType(uri, requireContext().contentResolver.getType(uri) ?: "*/*")
            }
            val activities = requireContext().packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            view.findViewById<View>(R.id.btn_open_with).isEnabled = activities.isNotEmpty()
        }
    }

    private fun setupActionButtons(view: View, file: File) {
        view.findViewById<View>(R.id.btn_rename).setOnClickListener {
            showRenameDialog(file)
        }
        view.findViewById<View>(R.id.btn_delete).setOnClickListener {
            showDeleteConfirmation(file)
        }
        view.findViewById<View>(R.id.btn_move).setOnClickListener {
            val action = FileDetailFragmentDirections.actionDetailToBrowser(
                initialPath = file.parentFile?.absolutePath,
                isPickerMode = true,
                fileToMovePath = file.absolutePath
            )
            findNavController().navigate(action)
        }
        view.findViewById<View>(R.id.btn_copy).setOnClickListener {
            duplicateFile(file)
        }
        view.findViewById<View>(R.id.btn_open_with).setOnClickListener {
            openWith(file)
        }
        view.findViewById<View>(R.id.btn_show_in_folder).setOnClickListener {
            val action = FileDetailFragmentDirections.actionDetailToBrowser(
                initialPath = file.parentFile?.absolutePath,
                highlightFilePath = file.absolutePath
            )
            findNavController().navigate(action)
        }
    }

    private fun showRenameDialog(file: File) {
        val input = EditText(requireContext())
        input.setText(file.name)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_rename_title)
            .setView(input)
            .setPositiveButton(R.string.action_rename) { _, _ ->
                val newName = input.text.toString()
                val newFile = File(file.parentFile, newName)
                if (newName.isNotBlank() && file.renameTo(newFile)) {
                    MediaScannerConnection.scanFile(requireContext(), arrayOf(newFile.absolutePath), null, null)
                    Toast.makeText(requireContext(), R.string.msg_rename_success, Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                } else {
                    Toast.makeText(requireContext(), R.string.msg_error_operation, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteConfirmation(file: File) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_msg, file.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                if (file.deleteRecursively()) {
                    Toast.makeText(requireContext(), R.string.msg_delete_success, Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                } else {
                    Toast.makeText(requireContext(), R.string.msg_error_operation, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun duplicateFile(file: File) {
        val target = File(file.parentFile, "${file.nameWithoutExtension}_copy.${file.extension}")
        try {
            file.copyTo(target, overwrite = false)
            MediaScannerConnection.scanFile(requireContext(), arrayOf(target.absolutePath), null, null)
            Toast.makeText(requireContext(), getString(R.string.msg_copy_success, target.name), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.msg_error_operation, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWith(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(), "${requireContext().packageName}.fileprovider", file.absoluteFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, requireContext().contentResolver.getType(uri) ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("", uri)
            }
            val chooser = Intent.createChooser(intent, getString(R.string.action_open_with))
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(chooser)
        } catch (ignored: Exception) {
            Toast.makeText(requireContext(), R.string.msg_open_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openVideo(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(), "${requireContext().packageName}.fileprovider", file.absoluteFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("", uri)
            }
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
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(), "${requireContext().packageName}.fileprovider", file.absoluteFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = requireContext().contentResolver.getType(uri) ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, getString(R.string.chooser_share_file))
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.msg_error_operation, Toast.LENGTH_SHORT).show()
        }
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
