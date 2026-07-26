package com.azazel.explorer.ui.dashboard

import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azazel.explorer.R
import com.azazel.explorer.model.FileFilter
import com.azazel.explorer.ui.browser.FileAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupStorageStats(view)
        setupQuickAccessGrid(view)
        setupRecentFiles(view)
        setupOrganizeButton(view)
    }

    private fun setupOrganizeButton(view: View) {
        view.findViewById<Button>(R.id.btn_organize).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_organize_title)
                .setMessage(R.string.dialog_organize_msg)
                .setPositiveButton("Organize") { _, _ -> runFileOrganizer() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun runFileOrganizer() {
        viewLifecycleOwner.lifecycleScope.launch {
            val movedByDestination = withContext(Dispatchers.IO) {
                val results = mutableMapOf<String, Int>()
                val sources = listOf(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                )

                sources.forEach { sourceDir ->
                    sourceDir.listFiles()?.forEach { file ->
                        if (file.isFile && !isAlreadyOrganized(file)) {
                            val destinationLabel = getDestinationLabel(file)
                            if (destinationLabel != null && organizeFile(file)) {
                                results[destinationLabel] = (results[destinationLabel] ?: 0) + 1
                            }
                        }
                    }
                }
                results
            }
            showOrganizeSummary(movedByDestination)
            setupQuickAccessGrid(requireView()) // Refresh counts
        }
    }

    private fun getDestinationLabel(file: File): String? {
        val ext = file.extension.lowercase()
        val docExtensions = listOf("pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt")
        val imgExtensions = listOf("jpg", "jpeg", "png", "gif", "webp")

        return when {
            ext in docExtensions -> "Documents/${ext.uppercase()}"
            ext in imgExtensions -> {
                val sourceName = getSourceAppName(file)
                "Photos/$sourceName/${ext.uppercase()}"
            }
            else -> null
        }
    }

    private fun showOrganizeSummary(results: Map<String, Int>) {
        if (results.isEmpty()) {
            Toast.makeText(requireContext(), R.string.msg_organize_nothing, Toast.LENGTH_SHORT).show()
            return
        }
        val message = results.entries.joinToString("\n") { (dest, count) -> "$count → $dest" }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.label_organize_complete)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun isAlreadyOrganized(file: File): Boolean {
        val path = file.absolutePath
        return path.contains("/Documents/") || path.contains("/Photos/")
    }

    private fun organizeFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        val docExtensions = listOf("pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt")
        val imgExtensions = listOf("jpg", "jpeg", "png", "gif", "webp")

        return when {
            ext in docExtensions -> {
                val targetDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), ext.uppercase())
                moveFile(file, targetDir)
            }
            ext in imgExtensions -> {
                val sourceName = getSourceAppName(file)
                val targetDir = File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Photos"), "$sourceName/${ext.uppercase()}")
                moveFile(file, targetDir)
            }
            else -> false
        }
    }

    private fun getSourceAppName(file: File): String {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val projection = arrayOf(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
            val selection = "${MediaStore.MediaColumns.DATA} = ?"
            val selectionArgs = arrayOf(file.absolutePath)
            
            val cursor = requireContext().contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection, selection, selectionArgs, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val pkg = it.getString(it.getColumnIndexOrThrow(MediaStore.MediaColumns.OWNER_PACKAGE_NAME))
                    if (pkg != null) return mapPackageToName(pkg)
                }
            }
        }

        val parent = file.parentFile?.name ?: ""
        val knownApps = listOf("WhatsApp", "Instagram", "Facebook", "Messenger", "Telegram", "Twitter", "Snapchat")
        knownApps.forEach { if (parent.contains(it, ignoreCase = true)) return it }

        return getString(R.string.source_other)
    }

    private fun mapPackageToName(pkg: String): String = when {
        pkg.contains("whatsapp") -> "WhatsApp"
        pkg.contains("facebook.orca") -> "Messenger"
        pkg.contains("facebook.katana") -> "Facebook"
        pkg.contains("instagram") -> "Instagram"
        pkg.contains("google.android.apps.photos") -> "Google Photos"
        pkg.contains("chrome") -> "Chrome"
        pkg.contains("telegram") -> "Telegram"
        else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }

    private fun moveFile(file: File, targetDir: File): Boolean {
        if (!targetDir.exists()) targetDir.mkdirs()
        val targetFile = File(targetDir, file.name)
        
        return if (file.renameTo(targetFile)) {
            true
        } else {
            try {
                file.copyTo(targetFile, overwrite = true)
                file.delete()
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun setupStorageStats(view: View) {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val totalBytes = stat.totalBytes
        val availableBytes = stat.availableBytes
        val usedBytes = totalBytes - availableBytes

        val pbStorage = view.findViewById<ProgressBar>(R.id.pb_storage)
        val tvStats = view.findViewById<TextView>(R.id.tv_storage_stats)

        pbStorage.max = 100
        pbStorage.progress = ((usedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt()

        tvStats.text = getString(R.string.label_storage_used, formatFileSize(usedBytes), formatFileSize(totalBytes))
    }

    private fun setupQuickAccessGrid(view: View) {
        val rv = view.findViewById<RecyclerView>(R.id.rv_quick_access)
        rv.layoutManager = GridLayoutManager(requireContext(), 3)

        viewLifecycleOwner.lifecycleScope.launch {
            val categories = withContext(Dispatchers.IO) {
                val list = mutableListOf(
                    Category(getString(R.string.cat_photos), R.drawable.ic_image, getCount(FileFilter.IMAGES), "IMAGES", Environment.DIRECTORY_PICTURES),
                    Category(getString(R.string.cat_videos), R.drawable.ic_video, getCount(FileFilter.VIDEOS), "VIDEOS", Environment.DIRECTORY_MOVIES),
                    Category(getString(R.string.cat_audio), R.drawable.ic_folder, getCount(FileFilter.AUDIO), "AUDIO", Environment.DIRECTORY_MUSIC),
                    Category(getString(R.string.cat_docs), R.drawable.ic_file, getCount(FileFilter.DOCUMENTS), "DOCUMENTS", Environment.DIRECTORY_DOCUMENTS),
                    Category(getString(R.string.cat_apks), R.drawable.ic_file, getCount(FileFilter.APKS), "APKS", null),
                    Category(getString(R.string.cat_archives), R.drawable.ic_file, getCount(FileFilter.ARCHIVES), "ARCHIVES", null)
                )

                // Task F: Add organized folders if they exist
                val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                if (docsDir.exists() && docsDir.list()?.isNotEmpty() == true) {
                    list.add(Category(getString(R.string.cat_documents), R.drawable.ic_folder, docsDir.list()?.size ?: 0, null, Environment.DIRECTORY_DOCUMENTS))
                }

                val organizedPhotosDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Photos")
                if (organizedPhotosDir.exists() && organizedPhotosDir.list()?.isNotEmpty() == true) {
                    list.add(Category(getString(R.string.cat_organized_photos), R.drawable.ic_folder, organizedPhotosDir.list()?.size ?: 0, null, "Pictures/Photos"))
                }
                list
            }

            rv.adapter = QuickAccessAdapter(categories) { category ->
                if (category.filterType != null) {
                    val action = DashboardFragmentDirections.actionDashboardToBrowser(
                        initialPath = null,
                        initialFilter = category.filterType,
                        isFilteredView = true
                    )
                    findNavController().navigate(action)
                } else {
                    val action = DashboardFragmentDirections.actionDashboardToBrowser(
                        initialPath = if (category.directory?.startsWith('/') == true) category.directory else 
                            File(Environment.getExternalStorageDirectory(), category.directory!!).absolutePath,
                        initialFilter = null,
                        isFilteredView = false
                    )
                    findNavController().navigate(action)
                }
            }
        }
    }

    private fun getCount(filter: FileFilter): Int {
        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        val selection = when (filter) {
            FileFilter.IMAGES -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}"
            FileFilter.VIDEOS -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}"
            FileFilter.AUDIO -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO}"
            FileFilter.DOCUMENTS -> "${MediaStore.Files.FileColumns.MIME_TYPE} IN ('application/pdf', 'text/plain', 'application/msword')"
            FileFilter.APKS -> "${MediaStore.Files.FileColumns.MIME_TYPE} = 'application/vnd.android.package-archive'"
            FileFilter.ARCHIVES -> "${MediaStore.Files.FileColumns.MIME_TYPE} IN ('application/zip', 'application/x-rar-compressed', 'application/x-7z-compressed')"
            else -> null
        }
        
        return try {
            requireContext().contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                null,
                null
            )?.use { it.count } ?: 0
        } catch (ignored: Exception) {
            0
        }
    }

    private fun setupRecentFiles(view: View) {
        val rv = view.findViewById<RecyclerView>(R.id.rv_recent_files)
        rv.layoutManager = LinearLayoutManager(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            val recentFiles = withContext(Dispatchers.IO) { getRecentFiles() }
            rv.adapter = FileAdapter(recentFiles, 
                onClick = { file ->
                    val action = DashboardFragmentDirections.actionDashboardToDetail(
                        filePath = file.absolutePath,
                        fileName = file.name
                    )
                    findNavController().navigate(action)
                },
                onLongClick = { file -> showRecentFileMenu(file) }
            )
        }
    }

    private fun showRecentFileMenu(file: File) {
        val rv = view?.findViewById<RecyclerView>(R.id.rv_recent_files) ?: return
        val adapter = rv.adapter as? FileAdapter ?: return
        val index = adapter.items.indexOf(file)
        val view = rv.findViewHolderForAdapterPosition(index)?.itemView ?: return

        val popup = PopupMenu(requireContext(), view)
        popup.menu.add(0, 1, 0, R.string.action_show_in_folder)
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == 1) {
                val action = DashboardFragmentDirections.actionDashboardToBrowser(
                    initialPath = file.parentFile?.absolutePath,
                    highlightFilePath = file.absolutePath
                )
                findNavController().navigate(action)
                true
            } else false
        }
        popup.show()
    }

    private fun getRecentFiles(): List<File> {
        val files = mutableListOf<File>()
        val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        
        val cursor = requireContext().contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            null,
            null,
            sortOrder
        )

        cursor?.use {
            var count = 0
            val dataIndex = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            while (it.moveToNext() && count < 10) {
                val path = it.getString(dataIndex)
                val file = File(path)
                if (file.exists() && !file.isDirectory) {
                    files.add(file)
                    count++
                }
            }
        }
        return files
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
