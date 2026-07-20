package com.azazel.explorer.ui.dashboard

import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azazel.explorer.R
import com.azazel.explorer.model.FileFilter
import com.azazel.explorer.ui.browser.FileAdapter
import java.io.File
import java.util.Locale

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupStorageStats(view)
        setupQuickAccessGrid(view)
        setupRecentFiles(view)
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

        val categories = listOf(
            Category("Photos", R.drawable.ic_image, getCount(FileFilter.IMAGES), "IMAGES", Environment.DIRECTORY_PICTURES),
            Category("Videos", R.drawable.ic_video, getCount(FileFilter.VIDEOS), "VIDEOS", Environment.DIRECTORY_MOVIES),
            Category("Audio", R.drawable.ic_folder, getCount(FileFilter.AUDIO), "AUDIO", Environment.DIRECTORY_MUSIC),
            Category("Docs", R.drawable.ic_file, getCount(FileFilter.DOCUMENTS), "DOCUMENTS", Environment.DIRECTORY_DOCUMENTS),
            Category("APKs", R.drawable.ic_file, getCount(FileFilter.APKS), "APKS", null),
            Category("Archives", R.drawable.ic_file, getCount(FileFilter.ARCHIVES), "ARCHIVES", null)
        )

        rv.adapter = QuickAccessAdapter(categories) { category ->
            val initialPath = category.directory?.let { 
                Environment.getExternalStoragePublicDirectory(it).absolutePath 
            }
            val action = DashboardFragmentDirections.actionDashboardToBrowser(
                initialPath = initialPath,
                initialFilter = category.filterType
            )
            findNavController().navigate(action)
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
        
        val cursor = requireContext().contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            null,
            null
        )
        val count = cursor?.count ?: 0
        cursor?.close()
        return count
    }

    private fun setupRecentFiles(view: View) {
        val rv = view.findViewById<RecyclerView>(R.id.rv_recent_files)
        rv.layoutManager = LinearLayoutManager(requireContext())

        val recentFiles = getRecentFiles()
        rv.adapter = FileAdapter(recentFiles) { file ->
            val action = DashboardFragmentDirections.actionDashboardToDetail(
                filePath = file.absolutePath,
                fileName = file.name
            )
            findNavController().navigate(action)
        }
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

