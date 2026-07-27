package com.azazel.explorer.ui.downloads

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.azazel.explorer.R
import com.azazel.explorer.data.SessionManager
import com.azazel.explorer.network.AuthRequest
import com.azazel.explorer.network.AuthResponse
import com.azazel.explorer.network.AuthRetrofitClient
import com.azazel.explorer.network.RetrofitClient
import com.azazel.explorer.network.models.DownloadRequest
import com.azazel.explorer.network.models.DownloadJob
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import com.google.gson.Gson
import java.io.File

class DownloadsFragment : Fragment(R.layout.fragment_downloads) {

    private var adapter: JobAdapter? = null
    private lateinit var sessionManager: SessionManager
    private val jobList = mutableListOf<DownloadJob>()
    private val displayedJobs = mutableListOf<DownloadJob>()
    private val downloadedJobs = mutableSetOf<String>()
    private val downloadingToDevice = mutableSetOf<String>()
    private var isLoginMode = true
    private var currentFilterId = R.id.chip_filter_all
    private var currentSearchQuery = ""
    private var isDownloadUiInitialized = false

    data class CooldownError(val error: String?, val retryAfterMs: Long?)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = SessionManager(requireContext())
        RetrofitClient.init(requireContext())
        AuthRetrofitClient.init(sessionManager)

        if (sessionManager.isLoggedIn()) {
            showDownloadUi(view)
        } else {
            showAuthUi(view)
        }

        setupToolbar(view)
    }

    private fun setupToolbar(view: View) {
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.inflateMenu(R.menu.menu_downloader)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_info) {
                showIntroDialog()
                true
            } else false
        }
    }

    private fun showIntroDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.downloader_intro_title)
            .setMessage(R.string.downloader_intro_desc)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showAuthUi(view: View) {
        isDownloadUiInitialized = false
        adapter = null

        val layoutAuth = view.findViewById<View>(R.id.layout_auth)
        val swipeRefresh = view.findViewById<View>(R.id.swipe_refresh)
        val btnLogout = view.findViewById<View>(R.id.btn_logout)
        layoutAuth.visibility = View.VISIBLE
        swipeRefresh.visibility = View.GONE
        btnLogout.visibility = View.GONE

        val etEmail = view.findViewById<EditText>(R.id.et_email)
        val etPassword = view.findViewById<EditText>(R.id.et_password)
        val btnPrimary = view.findViewById<Button>(R.id.btn_auth_primary)
        val btnSecondary = view.findViewById<Button>(R.id.btn_auth_secondary)
        val tvTitle = view.findViewById<TextView>(R.id.tv_auth_title)

        etEmail.text.clear()
        etPassword.text.clear()
        etEmail.isEnabled = true
        etPassword.isEnabled = true

        updateAuthMode(tvTitle, btnPrimary, btnSecondary)

        btnSecondary.setOnClickListener {
            isLoginMode = !isLoginMode
            updateAuthMode(tvTitle, btnPrimary, btnSecondary)
        }

        btnPrimary.setOnClickListener {
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()

            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(requireContext(), R.string.msg_invalid_input, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnPrimary.isEnabled = false
            btnSecondary.isEnabled = false
            etEmail.isEnabled = false
            etPassword.isEnabled = false
            btnPrimary.setText(R.string.status_starting)

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val request = AuthRequest(email, password)
                    val response = if (isLoginMode) {
                        AuthRetrofitClient.api.signIn(request)
                    } else {
                        AuthRetrofitClient.api.signUp(request)
                    }

                    if (response.access_token != null && response.refresh_token != null) {
                        sessionManager.saveToken(response.access_token, response.refresh_token)
                        if (isAdded) showDownloadUi(view)
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.msg_auth_error, response.error ?: getString(R.string.error_unknown)), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: HttpException) {
                    Toast.makeText(requireContext(), getString(R.string.msg_auth_error, parseAuthError(e)), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), getString(R.string.msg_auth_error, e.message ?: getString(R.string.error_unknown)), Toast.LENGTH_SHORT).show()
                } finally {
                    if (isAdded) {
                        btnPrimary.isEnabled = true
                        btnSecondary.isEnabled = true
                        etEmail.isEnabled = true
                        etPassword.isEnabled = true
                        updateAuthMode(tvTitle, btnPrimary, btnSecondary)
                    }
                }
            }
        }
    }

    private fun parseAuthError(e: HttpException): String {
        return try {
            val errorBodyString = e.response()?.errorBody()?.string()
            val parsed = Gson().fromJson(errorBodyString, AuthResponse::class.java)
            parsed?.msg ?: parsed?.error_description ?: parsed?.error ?: e.message()
        } catch (parseException: Exception) {
            e.message() ?: getString(R.string.error_unknown)
        }
    }

    private fun updateAuthMode(tvTitle: TextView, btnPrimary: Button, btnSecondary: Button) {
        if (isLoginMode) {
            tvTitle.setText(R.string.title_auth)
            btnPrimary.setText(R.string.btn_sign_in)
            btnSecondary.setText(R.string.btn_switch_to_signup)
        } else {
            tvTitle.setText(R.string.btn_sign_up)
            btnPrimary.setText(R.string.btn_sign_up)
            btnSecondary.setText(R.string.btn_switch_to_signin)
        }
    }

    private fun showDownloadUi(view: View) {
        view.findViewById<View>(R.id.layout_auth).visibility = View.GONE
        view.findViewById<View>(R.id.swipe_refresh).visibility = View.VISIBLE
        view.findViewById<View>(R.id.btn_logout).visibility = View.VISIBLE

        val etUrl = view.findViewById<EditText>(R.id.et_url)
        val etOutputName = view.findViewById<EditText>(R.id.et_output_name)
        val rvJobs = view.findViewById<RecyclerView>(R.id.rv_jobs)
        val layoutEmpty = view.findViewById<View>(R.id.layout_empty_downloader)
        val layoutListHeader = view.findViewById<View>(R.id.layout_list_header)
        val btnDownload = view.findViewById<Button>(R.id.btn_download)

        view.findViewById<Button>(R.id.btn_logout).setOnClickListener {
            btnDownload.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    AuthRetrofitClient.api.signOut()
                } catch (e: Exception) {
                    // Fail silently, we still clear local session
                }
                sessionManager.clear()
                jobList.clear()
                displayedJobs.clear()
                downloadedJobs.clear()
                downloadingToDevice.clear()
                if (isAdded) showAuthUi(view)
            }
        }

        if (!isDownloadUiInitialized || adapter == null) {
            adapter = JobAdapter(displayedJobs, downloadingToDevice,
                onRetry = { job -> retryJob(job) },
                onOpen = { job -> openDownloadedFile(job) },
                onShowInFolder = { job -> showInFolder(job) },
            )
            rvJobs.layoutManager = LinearLayoutManager(requireContext())
            rvJobs.adapter = adapter
            isDownloadUiInitialized = true
        } else {
            rvJobs.adapter = adapter
        }

        setupFilters(view)
        updateEmptyState(layoutEmpty, rvJobs, layoutListHeader)
        loadHistory(layoutEmpty, rvJobs, layoutListHeader)

        val swipeRefresh = view.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh)
        swipeRefresh.setColorSchemeResources(R.color.accent_primary)
        swipeRefresh.setOnRefreshListener {
            loadHistory(layoutEmpty, rvJobs, layoutListHeader)
            swipeRefresh.isRefreshing = false
        }

        btnDownload.isEnabled = true
        btnDownload.setText(R.string.btn_go)

        btnDownload.setOnClickListener {
            val url = etUrl.text.toString()
            val outputName = etOutputName.text.toString()
            
            if (url.isBlank() || outputName.isBlank()) {
                Toast.makeText(requireContext(), R.string.msg_output_name_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnDownload.isEnabled = false
            btnDownload.setText(R.string.status_starting)

            startDownload(url, outputName, layoutEmpty, rvJobs)
            etUrl.text.clear()
            etOutputName.text.clear()
        }
    }

    private fun setupFilters(view: View) {
        val etSearch = view.findViewById<EditText>(R.id.et_search_jobs)
        val cgFilter = view.findViewById<ChipGroup>(R.id.cg_status_filter)

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString() ?: ""
                applyFilterAndSearch()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        cgFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: R.id.chip_filter_all
            currentFilterId = checkedId
            applyFilterAndSearch()
        }
    }

    private fun applyFilterAndSearch() {
        val filtered = jobList.filter { job ->
            val matchesSearch = currentSearchQuery.isBlank() || 
                job.outputName?.contains(currentSearchQuery, ignoreCase = true) == true ||
                job.url?.contains(currentSearchQuery, ignoreCase = true) == true
            
            val matchesStatus = when (currentFilterId) {
                R.id.chip_filter_processing -> job.status == "processing"
                R.id.chip_filter_done -> job.status == "done"
                R.id.chip_filter_failed -> job.status == "failed"
                else -> true
            }
            matchesSearch && matchesStatus
        }
        
        displayedJobs.clear()
        displayedJobs.addAll(filtered)
        adapter?.notifyDataSetChanged()
        
        val v = view ?: return
        val layoutEmpty = v.findViewById<View>(R.id.layout_empty_downloader) ?: return
        val rvJobs = v.findViewById<RecyclerView>(R.id.rv_jobs) ?: return
        val layoutListHeader = v.findViewById<View>(R.id.layout_list_header) ?: return
        updateEmptyState(layoutEmpty, rvJobs, layoutListHeader)
    }

    private fun loadHistory(layoutEmpty: View, rvJobs: RecyclerView, layoutListHeader: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val history = RetrofitClient.api.listJobs()
                jobList.clear()
                jobList.addAll(history)
                applyFilterAndSearch()
                updateEmptyState(layoutEmpty, rvJobs, layoutListHeader)
                
                history.filter { it.status == "processing" }.forEach { pollJob(it) }
            } catch (e: HttpException) {
                if (!isAdded) return@launch
                if (e.code() == 401) {
                    if (!attemptTokenRefresh()) {
                        handleSessionExpired()
                    } else {
                        loadHistory(layoutEmpty, rvJobs, layoutListHeader)
                    }
                }
            } catch (e: Exception) {
                if (isAdded) Toast.makeText(requireContext(), R.string.msg_load_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun attemptTokenRefresh(): Boolean {
        val refreshToken = sessionManager.getRefreshToken() ?: return false
        return try {
            val response = AuthRetrofitClient.api.refreshToken(mapOf("refresh_token" to refreshToken))
            if (response.access_token != null && response.refresh_token != null) {
                sessionManager.saveToken(response.access_token, response.refresh_token)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private fun updateEmptyState(layoutEmpty: View, rvJobs: RecyclerView, layoutListHeader: View) {
        if (jobList.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            rvJobs.visibility = View.GONE
            layoutListHeader.visibility = View.GONE
        } else {
            layoutEmpty.visibility = View.GONE
            rvJobs.visibility = View.VISIBLE
            layoutListHeader.visibility = View.VISIBLE
        }
    }

    private fun startDownload(url: String, outputName: String, layoutEmpty: View, rvJobs: RecyclerView) {
        val layoutListHeader = view?.findViewById<View>(R.id.layout_list_header) ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val job = RetrofitClient.api.createJob(DownloadRequest(url, outputName))
                updateJobLocally(job)
                rvJobs.scrollToPosition(0)
                updateEmptyState(layoutEmpty, rvJobs, layoutListHeader)
                startCooldownTimer(15000L)
                pollJob(job)
            } catch (e: HttpException) {
                if (!isAdded) return@launch
                when (e.code()) {
                    401 -> {
                        if (attemptTokenRefresh()) {
                            startDownload(url, outputName, layoutEmpty, rvJobs)
                        } else {
                            handleSessionExpired()
                        }
                    }
                    429 -> {
                        val retryMs = parseRetryAfterMs(e)
                        Toast.makeText(requireContext(), getString(R.string.msg_cooldown, retryMs / 1000), Toast.LENGTH_SHORT).show()
                        startCooldownTimer(retryMs)
                    }
                    else -> {
                        val failedJob = DownloadJob("failed-${System.currentTimeMillis()}", "failed", null, url, outputName)
                        updateJobLocally(failedJob)
                        updateEmptyState(layoutEmpty, rvJobs, layoutListHeader)
                        reEnableDownloadButton()
                    }
                }
            } catch (e: Exception) {
                if (!isAdded) return@launch
                val failedJob = DownloadJob("failed-${System.currentTimeMillis()}", "failed", null, url, outputName)
                updateJobLocally(failedJob)
                updateEmptyState(layoutEmpty, rvJobs, layoutListHeader)
                reEnableDownloadButton()
            }
        }
    }

    private fun reEnableDownloadButton() {
        val btn = view?.findViewById<Button>(R.id.btn_download) ?: return
        btn.isEnabled = true
        btn.setText(R.string.btn_go)
    }

    private fun updateJobLocally(job: DownloadJob) {
        val index = jobList.indexOfFirst { it.id == job.id }
        if (index != -1) {
            val oldUrl = jobList[index].url
            jobList[index] = job
            jobList[index].url = oldUrl
        } else {
            jobList.add(0, job)
        }
        applyFilterAndSearch()
    }

    private fun startCooldownTimer(durationMs: Long) {
        val btnDownload = view?.findViewById<Button>(R.id.btn_download) ?: return
        btnDownload.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            var remaining = durationMs / 1000
            while (remaining > 0) {
                if (!isAdded) return@launch
                btnDownload.text = getString(R.string.btn_go) + " (${remaining}s)"
                delay(1000)
                remaining--
            }
            if (isAdded) {
                btnDownload.isEnabled = true
                btnDownload.setText(R.string.btn_go)
            }
        }
    }

    private fun parseRetryAfterMs(e: HttpException): Long {
        return try {
            val body = e.response()?.errorBody()?.string()
            Gson().fromJson(body, CooldownError::class.java)?.retryAfterMs ?: 15000L
        } catch (parseException: Exception) {
            15000L
        }
    }

    private fun handleSessionExpired() {
        if (!isAdded) return
        sessionManager.clear()
        jobList.clear()
        displayedJobs.clear()
        downloadedJobs.clear()
        downloadingToDevice.clear()
        Toast.makeText(requireContext(), R.string.msg_session_expired, Toast.LENGTH_LONG).show()
        view?.let { showAuthUi(it) }
    }

    private fun retryJob(job: DownloadJob) {
        if (downloadingToDevice.contains(job.id)) return
        val url = job.url ?: return
        val outputName = job.outputName ?: "download"
        val v = view ?: return
        val layoutEmpty = v.findViewById<View>(R.id.layout_empty_downloader) ?: return
        val rvJobs = v.findViewById<RecyclerView>(R.id.rv_jobs) ?: return
        startDownload(url, outputName, layoutEmpty, rvJobs)
    }

    private suspend fun pollJob(job: DownloadJob) {
        var currentJob = job
        while (currentJob.status == "processing") {
            delay(3000)
            try {
                currentJob = RetrofitClient.api.getJob(currentJob.id)
                currentJob.url = job.url
                currentJob.outputName = job.outputName
                updateJobLocally(currentJob)
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    if (attemptTokenRefresh()) {
                        continue
                    } else {
                        handleSessionExpired()
                    }
                }
                break
            } catch (e: Exception) {
                break
            }
        }
        if (!isAdded) return
        if (currentJob.status == "done" && !downloadedJobs.contains(currentJob.id)) {
            downloadedJobs.add(currentJob.id)
            downloadingToDevice.add(currentJob.id)
            downloadToDevice(currentJob)
            waitForFileReady(currentJob)
        }
    }

    private fun downloadToDevice(job: DownloadJob) {
        val url = job.outputUrl ?: return
        val uri = Uri.parse(url)
        val ext = Uri.parse(url).lastPathSegment?.substringAfterLast('.') ?: "mp4"
        val baseName = job.outputName ?: "download"
        val downloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AzazelDownloads")
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        var fileName = "$baseName.$ext"
        var targetFile = File(downloadsDir, fileName)
        var counter = 1
        
        while (targetFile.exists()) {
            fileName = "$baseName ($counter).$ext"
            targetFile = File(downloadsDir, fileName)
            counter++
        }

        val request = DownloadManager.Request(uri)
            .setTitle(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AzazelDownloads/$fileName")

        val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)

        MediaScannerConnection.scanFile(requireContext(), arrayOf(targetFile.absolutePath), null, null)
    }

    private fun waitForFileReady(job: DownloadJob) {
        viewLifecycleOwner.lifecycleScope.launch {
            var file: File? = null
            var attempts = 0
            while (attempts < 60) {
                file = expectedLocalFile(job)
                if (file != null && file.exists()) break
                delay(500)
                attempts++
            }
            downloadingToDevice.remove(job.id)
            if (!isAdded) return@launch
            val index = jobList.indexOfFirst { it.id == job.id }
            if (index != -1) {
                adapter?.notifyItemChanged(index)
            }
        }
    }

    private fun expectedLocalFile(job: DownloadJob): File? {
        val outputUrl = job.outputUrl ?: return null
        val baseName = job.outputName ?: return null
        val ext = Uri.parse(outputUrl).lastPathSegment?.substringAfterLast('.', "mp4") ?: "mp4"
        val downloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AzazelDownloads")
        
        val exactFile = File(downloadsDir, "$baseName.$ext")
        if (exactFile.exists()) return exactFile
        
        for (i in 1..10) {
            val suffixFile = File(downloadsDir, "$baseName ($i).$ext")
            if (suffixFile.exists()) return suffixFile
        }
        
        return exactFile
    }

    private fun openDownloadedFile(job: DownloadJob) {
        val file = expectedLocalFile(job) ?: run {
            Toast.makeText(requireContext(), R.string.msg_file_not_on_device, Toast.LENGTH_SHORT).show()
            return
        }
        if (!file.exists()) {
            Toast.makeText(requireContext(), R.string.msg_file_not_on_device, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file.absoluteFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, requireContext().contentResolver.getType(uri) ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("", uri)
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.msg_open_error, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.msg_open_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showInFolder(job: DownloadJob) {
        val file = expectedLocalFile(job)
        if (file == null || !file.exists()) {
            Toast.makeText(requireContext(), R.string.msg_file_not_on_device, Toast.LENGTH_SHORT).show()
            return
        }
        val action = DownloadsFragmentDirections.actionDownloadsToBrowser(
            initialPath = file.parentFile?.absolutePath,
            highlightFilePath = file.absolutePath
        )
        findNavController().navigate(action)
    }
}
