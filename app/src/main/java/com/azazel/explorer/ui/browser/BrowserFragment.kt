package com.azazel.explorer.ui.browser

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.azazel.explorer.R
import com.azazel.explorer.data.FileRepository
import com.azazel.explorer.model.FileFilter
import com.azazel.explorer.model.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BrowserFragment : Fragment(R.layout.fragment_browser) {

    private val repository = FileRepository()
    private val args: BrowserFragmentArgs by navArgs()
    private var currentDir: File = Environment.getExternalStorageDirectory()
    private val dirStack = ArrayDeque<File>()
    private var currentSort = SortOrder.NAME
    private var currentFilter = FileFilter.ALL
    private var currentSearchQuery = ""
    private var cachedFiles = listOf<File>()

    private lateinit var rvFiles: RecyclerView
    private lateinit var fileAdapter: FileAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: View
    private lateinit var layoutPermission: LinearLayout
    private lateinit var toolbar: Toolbar

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) checkPermissionAndLoad() else showPermissionDeniedState()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        args.initialPath?.let {
            val dir = File(it)
            if (dir.exists() && dir.isDirectory) {
                currentDir = dir
            }
        }

        args.initialFilter?.let {
            try {
                currentFilter = FileFilter.valueOf(it)
            } catch (e: Exception) {
                currentFilter = FileFilter.ALL
            }
        }

        rvFiles = view.findViewById(R.id.rv_files)
        progressBar = view.findViewById(R.id.progress_bar)
        tvEmpty = view.findViewById(R.id.tv_empty)
        layoutPermission = view.findViewById(R.id.layout_permission)
        toolbar = view.findViewById(R.id.toolbar)

        view.findViewById<Button>(R.id.btn_grant_permission).setOnClickListener {
            checkPermissionAndLoad()
        }

        rvFiles.layoutManager = LinearLayoutManager(requireContext())
        val fallAnimation = AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_animation_fade)
        rvFiles.layoutAnimation = fallAnimation

        fileAdapter = FileAdapter(
            mutableListOf(),
            onClick = { file -> onFileClicked(file) },
            onLongClick = if (!args.isPickerMode) { file -> showActionMenu(file) } else null
        )
        rvFiles.adapter = fileAdapter

        val swipeRefresh = view.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh)
        swipeRefresh.setColorSchemeResources(R.color.accent_primary)
        swipeRefresh.setOnRefreshListener {
            loadFiles()
            swipeRefresh.isRefreshing = false
        }

        setupToolbar()
        setupBackNavigation()
        setupSearch()
        checkPermissionAndLoad()
        updateMovingBar(view)
    }

    private fun setupSearch() {
        val etSearch = view?.findViewById<EditText>(R.id.et_search_files) ?: return
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString() ?: ""
                filterAndDisplay()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun updateMovingBar(view: View) {
        val layoutMoving = view.findViewById<View>(R.id.layout_moving)
        if (args.isPickerMode && args.fileToMovePath != null) {
            layoutMoving.visibility = View.VISIBLE
            val fileToMove = File(args.fileToMovePath!!)
            view.findViewById<TextView>(R.id.tv_moving_label).text = getString(R.string.msg_moving_file, fileToMove.name)
            view.findViewById<Button>(R.id.btn_confirm_move).setOnClickListener { confirmMove() }
            view.findViewById<Button>(R.id.btn_cancel_move).setOnClickListener { findNavController().navigateUp() }
        } else {
            layoutMoving.visibility = View.GONE
        }
    }

    private fun setupToolbar() {
        val tvFilterSubtitle = view?.findViewById<TextView>(R.id.tv_filter_subtitle)

        if (args.isPickerMode) {
            toolbar.title = getString(R.string.msg_move_here)
            toolbar.menu.clear()
            tvFilterSubtitle?.visibility = View.GONE
        } else if (args.isFilteredView || args.fromDashboard) {
            val label = if (args.isFilteredView) {
                args.initialFilter?.let { filterName ->
                    when (filterName) {
                        "IMAGES" -> getString(R.string.cat_photos)
                        "VIDEOS" -> getString(R.string.cat_videos)
                        "AUDIO" -> getString(R.string.cat_audio)
                        "DOCUMENTS" -> getString(R.string.cat_docs)
                        "APKS" -> getString(R.string.cat_apks)
                        "ARCHIVES" -> getString(R.string.cat_archives)
                        else -> filterName
                    }
                } ?: getString(R.string.nav_browse)
            } else {
                currentDir.name.ifEmpty { getString(R.string.label_internal_storage) }
            }
            toolbar.title = label
            tvFilterSubtitle?.text = getString(R.string.browser_from_dashboard)
            tvFilterSubtitle?.visibility = View.VISIBLE
            toolbar.menu.clear()
            toolbar.inflateMenu(R.menu.menu_browser)
            toolbar.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_sort_name -> { currentSort = SortOrder.NAME; loadFiles() }
                    R.id.action_sort_date -> { currentSort = SortOrder.DATE; loadFiles() }
                    R.id.action_sort_size -> { currentSort = SortOrder.SIZE; loadFiles() }
                    R.id.action_sort_type -> { currentSort = SortOrder.TYPE; loadFiles() }
                    R.id.action_filter_all -> { currentFilter = FileFilter.ALL; loadFiles() }
                    R.id.action_filter_images -> { currentFilter = FileFilter.IMAGES; loadFiles() }
                    R.id.action_filter_videos -> { currentFilter = FileFilter.VIDEOS; loadFiles() }
                    R.id.action_filter_documents -> { currentFilter = FileFilter.DOCUMENTS; loadFiles() }
                }
                true
            }
        } else {
            toolbar.title = currentDir.name.ifEmpty { getString(R.string.label_internal_storage) }
            tvFilterSubtitle?.visibility = View.GONE
            toolbar.inflateMenu(R.menu.menu_browser)
            toolbar.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_sort_name -> { currentSort = SortOrder.NAME; loadFiles() }
                    R.id.action_sort_date -> { currentSort = SortOrder.DATE; loadFiles() }
                    R.id.action_sort_size -> { currentSort = SortOrder.SIZE; loadFiles() }
                    R.id.action_sort_type -> { currentSort = SortOrder.TYPE; loadFiles() }
                    R.id.action_filter_all -> { currentFilter = FileFilter.ALL; loadFiles() }
                    R.id.action_filter_images -> { currentFilter = FileFilter.IMAGES; loadFiles() }
                    R.id.action_filter_videos -> { currentFilter = FileFilter.VIDEOS; loadFiles() }
                    R.id.action_filter_documents -> { currentFilter = FileFilter.DOCUMENTS; loadFiles() }
                }
                true
            }
        }
        updateToolbarNavigation()
    }

    private fun updateToolbarNavigation() {
        val hasBackStack = dirStack.isNotEmpty() || (args.isPickerMode && currentDir != Environment.getExternalStorageDirectory())
        if (args.isFilteredView || args.fromDashboard) {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        } else if (hasBackStack) {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            toolbar.setNavigationOnClickListener { navigateUp() }
        } else {
            toolbar.navigationIcon = null
        }
    }

    private fun navigateUp() {
        if (dirStack.isNotEmpty()) {
            currentDir = dirStack.removeLast()
            if (!args.isPickerMode) {
                toolbar.title = currentDir.name.ifEmpty { getString(R.string.label_internal_storage) }
            }
            loadFiles()
            updateToolbarNavigation()
        } else if (args.isPickerMode && currentDir != Environment.getExternalStorageDirectory()) {
            currentDir = currentDir.parentFile ?: Environment.getExternalStorageDirectory()
            loadFiles()
            updateToolbarNavigation()
        }
    }

    private fun setupBackNavigation() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val canNavigateUpInternal = dirStack.isNotEmpty() || (args.isPickerMode && currentDir != Environment.getExternalStorageDirectory())
                if (args.isFilteredView || args.fromDashboard) {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                } else if (canNavigateUpInternal) {
                    navigateUp()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private var hasLoadedOnce = false

    override fun onResume() {
        super.onResume()
        if (hasLoadedOnce && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            loadFiles()
        }
    }

    private fun checkPermissionAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                loadFiles()
            } else {
                layoutPermission.visibility = View.VISIBLE
                rvFiles.visibility = View.GONE
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${requireContext().packageName}")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        } else {
            when {
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ->
                    loadFiles()
                else -> permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    private fun showPermissionDeniedState() {
        layoutPermission.visibility = View.VISIBLE
        rvFiles.visibility = View.GONE
        tvEmpty.visibility = View.GONE
        progressBar.visibility = View.GONE
    }

    private fun loadFiles() {
        layoutPermission.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val files = if (args.isFilteredView) {
                withContext(Dispatchers.IO) { repository.listFilesByType(requireContext(), currentFilter) }
            } else {
                withContext(Dispatchers.IO) { repository.listFiles(currentDir) }
            }
            
            cachedFiles = files
            filterAndDisplay()
        }
    }

    private fun filterAndDisplay() {
        viewLifecycleOwner.lifecycleScope.launch {
            val sortedAndFiltered = withContext(Dispatchers.Default) {
                val baseList = if (args.isFilteredView) {
                    applySort(cachedFiles)
                } else {
                    applySort(applyFilter(cachedFiles))
                }
                
                if (currentSearchQuery.isBlank()) {
                    baseList
                } else {
                    baseList.filter { it.name.contains(currentSearchQuery, ignoreCase = true) }
                }
            }

            progressBar.visibility = View.GONE
            hasLoadedOnce = true
            if (sortedAndFiltered.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                rvFiles.visibility = View.GONE
            } else {
                tvEmpty.visibility = View.GONE
                rvFiles.visibility = View.VISIBLE
                fileAdapter.submitList(sortedAndFiltered)

                args.highlightFilePath?.let { path ->
                    val index = sortedAndFiltered.indexOfFirst { it.absolutePath == path }
                    if (index != -1) {
                        rvFiles.post {
                            rvFiles.scrollToPosition(index)
                        }
                    }
                }
            }
        }
    }

    private fun applySort(files: List<File>): List<File> = when (currentSort) {
        SortOrder.NAME -> files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        SortOrder.DATE -> files.sortedWith(compareBy({ !it.isDirectory }, { -it.lastModified() }))
        SortOrder.SIZE -> files.sortedWith(compareBy({ !it.isDirectory }, { -it.length() }))
        SortOrder.TYPE -> files.sortedWith(compareBy({ !it.isDirectory }, { it.extension.lowercase() }))
    }

    private fun applyFilter(files: List<File>): List<File> = when (currentFilter) {
        FileFilter.ALL -> files
        FileFilter.IMAGES -> files.filter { it.isDirectory || it.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp") }
        FileFilter.VIDEOS -> files.filter { it.isDirectory || it.extension.lowercase() in listOf("mp4", "mkv", "avi", "mov") }
        FileFilter.DOCUMENTS -> files.filter { it.isDirectory || it.extension.lowercase() in listOf("pdf", "doc", "docx", "txt") }
        FileFilter.AUDIO -> files.filter { it.isDirectory || it.extension.lowercase() in listOf("mp3", "wav", "ogg", "m4a") }
        FileFilter.APKS -> files.filter { it.isDirectory || it.extension.lowercase() == "apk" }
        FileFilter.ARCHIVES -> files.filter { it.isDirectory || it.extension.lowercase() in listOf("zip", "rar", "7z", "tar", "gz") }
    }

    private fun onFileClicked(file: File) {
        if (!args.isFilteredView && file.isDirectory) {
            dirStack.addLast(currentDir)
            currentDir = file
            if (!args.isPickerMode) {
                toolbar.title = currentDir.name
            }
            loadFiles()
            updateToolbarNavigation()
        } else if (!file.isDirectory && file.extension.lowercase() == "zip") {
            val action = BrowserFragmentDirections.actionBrowserToZipContents(filePath = file.absolutePath)
            findNavController().navigate(action)
        } else if (!args.isPickerMode) {
            val action = BrowserFragmentDirections
                .actionBrowserToDetail(
                    filePath = file.absolutePath, 
                    fileName = file.name,
                    showLocationAction = args.isFilteredView || args.fromDashboard
                )
            findNavController().navigate(action)
        }
    }

    private fun showActionMenu(file: File) {
        val index = fileAdapter.currentItems.indexOf(file)
        val view = rvFiles.findViewHolderForAdapterPosition(index)?.itemView ?: return
        
        val popup = PopupMenu(requireContext(), view)
        popup.menu.add(0, 1, 0, R.string.action_rename).isEnabled = file.canWrite()
        popup.menu.add(0, 2, 1, R.string.action_duplicate).isEnabled = file.canWrite()
        popup.menu.add(0, 7, 2, R.string.action_share)
        popup.menu.add(0, 3, 3, R.string.action_move).isEnabled = file.canWrite()
        popup.menu.add(0, 4, 4, R.string.action_zip).isEnabled = file.canWrite()
        popup.menu.add(0, 5, 5, R.string.action_delete).isEnabled = file.canWrite()
        if (args.isFilteredView || args.fromDashboard) {
            popup.menu.add(0, 6, 6, R.string.action_show_in_folder)
        }
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> showRenameDialog(file)
                2 -> duplicateFile(file)
                3 -> startMoveFlow(file)
                4 -> zipFile(file)
                5 -> showDeleteConfirmation(file)
                6 -> {
                    val action = BrowserFragmentDirections.actionBrowserSelf(
                        initialPath = file.parentFile?.absolutePath,
                        highlightFilePath = file.absolutePath
                    )
                    findNavController().navigate(action)
                }
                7 -> shareFile(file)
            }
            true
        }
        popup.show()
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
                    loadFiles()
                } else {
                    Toast.makeText(requireContext(), R.string.msg_error_operation, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun duplicateFile(file: File) {
        val progressDialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.action_duplicate)
            .setMessage(getString(R.string.msg_copying, file.name))
            .setCancelable(false)
            .create()
        progressDialog.show()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val target = File(file.parentFile, "${file.nameWithoutExtension}_copy.${file.extension}")
            val success = try {
                file.copyTo(target, overwrite = false)
                true
            } catch (e: Exception) { false }
            
            if (success) {
                MediaScannerConnection.scanFile(requireContext(), arrayOf(target.absolutePath), null, null)
            }

            withContext(Dispatchers.Main) {
                progressDialog.dismiss()
                if (success) {
                    Toast.makeText(requireContext(), getString(R.string.msg_copy_success, target.name), Toast.LENGTH_SHORT).show()
                    loadFiles()
                } else {
                    Toast.makeText(requireContext(), R.string.msg_error_operation, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startMoveFlow(file: File) {
        val action = BrowserFragmentDirections.actionBrowserSelf(
            initialPath = currentDir.absolutePath,
            isPickerMode = true,
            fileToMovePath = file.absolutePath
        )
        findNavController().navigate(action)
    }

    private fun confirmMove() {
        val fileToMove = File(args.fileToMovePath!!)
        val targetFile = File(currentDir, fileToMove.name)
        if (fileToMove.renameTo(targetFile)) {
            MediaScannerConnection.scanFile(requireContext(), arrayOf(targetFile.absolutePath), null, null)
            Toast.makeText(requireContext(), getString(R.string.msg_move_success, currentDir.name.ifEmpty { "Root" }), Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        } else {
            Toast.makeText(requireContext(), R.string.msg_error_operation, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteConfirmation(file: File) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_msg, file.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                if (file.deleteRecursively()) {
                    Toast.makeText(requireContext(), R.string.msg_delete_success, Toast.LENGTH_SHORT).show()
                    loadFiles()
                } else {
                    Toast.makeText(requireContext(), R.string.msg_error_operation, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun shareFile(file: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(), "${requireContext().packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = requireContext().contentResolver.getType(uri) ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("", uri)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.chooser_share_file)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.msg_error_operation, Toast.LENGTH_SHORT).show()
        }
    }

    private fun zipFile(file: File) {
        val progressDialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.action_zip)
            .setMessage(getString(R.string.msg_compressing, file.name))
            .setCancelable(false)
            .create()
        progressDialog.show()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val zipTarget = File(file.parentFile, "${file.nameWithoutExtension}.zip")
            val success = try {
                ZipOutputStream(FileOutputStream(zipTarget)).use { zos ->
                    if (file.isDirectory) {
                        file.walkTopDown().filter { it.isFile }.forEach { f ->
                            val entryName = f.absolutePath.substring(file.absolutePath.length + 1)
                            zos.putNextEntry(ZipEntry(entryName))
                            f.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    } else {
                        zos.putNextEntry(ZipEntry(file.name))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
                true
            } catch (e: Exception) { false }
            
            if (success) {
                MediaScannerConnection.scanFile(requireContext(), arrayOf(zipTarget.absolutePath), null, null)
            }

            withContext(Dispatchers.Main) {
                progressDialog.dismiss()
                if (success) {
                    Toast.makeText(requireContext(), getString(R.string.msg_zip_success, zipTarget.name), Toast.LENGTH_SHORT).show()
                    loadFiles()
                } else {
                    Toast.makeText(requireContext(), R.string.msg_error_operation, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
