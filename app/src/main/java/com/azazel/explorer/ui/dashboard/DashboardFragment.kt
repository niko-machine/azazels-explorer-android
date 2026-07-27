package com.azazel.explorer.ui.dashboard

import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView

import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.azazel.explorer.R
import com.azazel.explorer.util.formatFileSize
import com.azazel.explorer.model.FileFilter
import com.azazel.explorer.ui.browser.FileAdapter
import com.azazel.explorer.ui.views.StorageDonutView
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
        setupToolbar(view)

        val swipeRefresh = view.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh)
        swipeRefresh.setColorSchemeResources(R.color.accent_primary)
        swipeRefresh.setOnRefreshListener {
            setupStorageStats(view)
            setupQuickAccessGrid(view)
            setupRecentFiles(view)
            swipeRefresh.isRefreshing = false
        }
    }

    private fun setupToolbar(view: View) {
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.inflateMenu(R.menu.menu_dashboard)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_settings) {
                val action = DashboardFragmentDirections.actionDashboardToSettings()
                findNavController().navigate(action)
                true
            } else false
        }
    }

    private fun setupOrganizeButton(view: View) {
        val btnOrganize = view.findViewById<Button>(R.id.btn_organize)
        btnOrganize.setOnClickListener {
            val action = DashboardFragmentDirections.actionDashboardToOrganize()
            findNavController().navigate(action)
        }
    }


    private fun setupStorageStats(view: View) {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val totalBytes = stat.totalBytes
        val availableBytes = stat.availableBytes
        val usedBytes = totalBytes - availableBytes

        val donutView = view.findViewById<StorageDonutView>(R.id.donut_storage)
        val tvStats = view.findViewById<TextView>(R.id.tv_storage_stats)

        viewLifecycleOwner.lifecycleScope.launch {
            val breakdown = withContext(Dispatchers.IO) { getStorageBreakdown() }
            
            val images = breakdown[FileFilter.IMAGES] ?: 0
            val videos = breakdown[FileFilter.VIDEOS] ?: 0
            val audio = breakdown[FileFilter.AUDIO] ?: 0
            val docs = breakdown[FileFilter.DOCUMENTS] ?: 0
            val other = maxOf(0L, usedBytes - (images + videos + audio + docs))
            val available = availableBytes

            val categories = listOf(
                StorageDonutView.CategoryData(images, R.color.accent_primary, getString(R.string.cat_photos)),
                StorageDonutView.CategoryData(videos, R.color.status_processing, getString(R.string.cat_videos)),
                StorageDonutView.CategoryData(audio, R.color.status_done, getString(R.string.cat_audio)),
                StorageDonutView.CategoryData(docs, R.color.accent_secondary, getString(R.string.cat_docs)),
                StorageDonutView.CategoryData(other, R.color.surface_secondary, getString(R.string.source_other)),
                StorageDonutView.CategoryData(available, R.color.background_primary, getString(R.string.label_available))
            )
            donutView.setData(totalBytes, usedBytes, categories)
            setupStorageLegend(view.findViewById(R.id.layout_storage_legend), categories)
        }

        tvStats.text = getString(R.string.label_storage_used, formatFileSize(usedBytes), formatFileSize(totalBytes))
    }

    private fun setupStorageLegend(container: LinearLayout, categories: List<StorageDonutView.CategoryData>) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        
        categories.forEach { category ->
            val row = inflater.inflate(R.layout.item_storage_legend, container, false)
            row.findViewById<View>(R.id.view_color_swatch).setBackgroundColor(
                androidx.core.content.ContextCompat.getColor(requireContext(), category.colorRes)
            )
            row.findViewById<TextView>(R.id.tv_legend_label).text = category.label
            row.findViewById<TextView>(R.id.tv_legend_size).text = formatFileSize(category.bytes)
            container.addView(row)
        }
    }

    private fun getStorageBreakdown(): Map<FileFilter, Long> {
        val results = mutableMapOf<FileFilter, Long>()
        val projection = arrayOf(MediaStore.Files.FileColumns.SIZE, MediaStore.Files.FileColumns.MEDIA_TYPE)
        
        val cursor = requireContext().contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection, null, null, null
        )

        cursor?.use {
            val sizeIndex = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val typeIndex = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            
            while (it.moveToNext()) {
                val size = it.getLong(sizeIndex)
                val type = it.getInt(typeIndex)
                
                val filter = when (type) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> FileFilter.IMAGES
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> FileFilter.VIDEOS
                    MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> FileFilter.AUDIO
                    else -> FileFilter.DOCUMENTS // Approximation for breakdown
                }
                results[filter] = (results[filter] ?: 0) + size
            }
        }
        return results
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
                        isFilteredView = true,
                        fromDashboard = true
                    )
                    findNavController().navigate(action)
                } else {
                    val action = DashboardFragmentDirections.actionDashboardToBrowser(
                        initialPath = if (category.directory?.startsWith('/') == true) category.directory else 
                            File(Environment.getExternalStorageDirectory(), category.directory!!).absolutePath,
                        initialFilter = null,
                        isFilteredView = false,
                        fromDashboard = true
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
        val fallAnimation = AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_animation_fade)
        rv.layoutAnimation = fallAnimation

        viewLifecycleOwner.lifecycleScope.launch {
            val recentFiles = withContext(Dispatchers.IO) { getRecentFiles() }
            rv.adapter = FileAdapter(recentFiles.toMutableList(), 
                onClick = { file ->
                    val action = DashboardFragmentDirections.actionDashboardToDetail(
                        filePath = file.absolutePath,
                        fileName = file.name,
                        showLocationAction = true
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
        val index = adapter.currentItems.indexOf(file)
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
}
