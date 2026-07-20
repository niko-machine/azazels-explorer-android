package com.azazel.explorer.ui.browser

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azazel.explorer.R
import com.azazel.explorer.data.FileRepository
import com.azazel.explorer.model.FileFilter
import com.azazel.explorer.model.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class BrowserFragment : Fragment(R.layout.fragment_browser) {

    private val repository = FileRepository()
    private val args: BrowserFragmentArgs by navArgs()
    private var currentDir: File = Environment.getExternalStorageDirectory()
    private val dirStack = ArrayDeque<File>()
    private var currentSort = SortOrder.NAME
    private var currentFilter = FileFilter.ALL

    private lateinit var rvFiles: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
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
            } catch (e: Exception) {}
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

        setupToolbar()
        setupBackNavigation()
        checkPermissionAndLoad()
    }

    private fun setupToolbar() {
        toolbar.title = currentDir.name.ifEmpty { "Internal Storage" }
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
        updateToolbarNavigation()
    }

    private fun updateToolbarNavigation() {
        if (dirStack.isNotEmpty()) {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            toolbar.setNavigationOnClickListener { navigateUp() }
        } else {
            toolbar.navigationIcon = null
        }
    }

    private fun navigateUp() {
        if (dirStack.isNotEmpty()) {
            currentDir = dirStack.removeLast()
            toolbar.title = currentDir.name.ifEmpty { "Internal Storage" }
            loadFiles()
            updateToolbarNavigation()
        }
    }

    private fun setupBackNavigation() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (dirStack.isNotEmpty()) {
                    navigateUp()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
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
            val files = withContext(Dispatchers.IO) {
                applySort(applyFilter(repository.listFiles(currentDir)))
            }
            
            progressBar.visibility = View.GONE
            if (files.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                rvFiles.visibility = View.GONE
            } else {
                tvEmpty.visibility = View.GONE
                rvFiles.visibility = View.VISIBLE
                rvFiles.adapter = FileAdapter(files) { file -> onFileClicked(file) }
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
        if (file.isDirectory) {
            dirStack.addLast(currentDir)
            currentDir = file
            toolbar.title = currentDir.name
            loadFiles()
            updateToolbarNavigation()
        } else {
            val action = BrowserFragmentDirections
                .actionBrowserToDetail(filePath = file.absolutePath, fileName = file.name)
            findNavController().navigate(action)
        }
    }
}
