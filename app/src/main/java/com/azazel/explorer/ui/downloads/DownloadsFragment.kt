package com.azazel.explorer.ui.downloads

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azazel.explorer.R
import com.azazel.explorer.data.SessionManager
import com.azazel.explorer.network.AuthRequest
import com.azazel.explorer.network.AuthResponse
import com.azazel.explorer.network.AuthRetrofitClient
import com.azazel.explorer.network.RetrofitClient
import com.azazel.explorer.network.models.DownloadRequest
import com.azazel.explorer.network.models.DownloadJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import com.google.gson.Gson
import java.io.File

class DownloadsFragment : Fragment(R.layout.fragment_downloads) {

    private lateinit var adapter: JobAdapter
    private lateinit var sessionManager: SessionManager
    private val jobList = mutableListOf<DownloadJob>()
    private val jobToLocalFile = mutableMapOf<String, File>()
    private val downloadedJobs = mutableSetOf<String>()
    private var isLoginMode = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = SessionManager(requireContext())
        RetrofitClient.init(requireContext())

        if (sessionManager.isLoggedIn()) {
            showDownloadUi(view)
        } else {
            showAuthUi(view)
        }
    }

    private fun showAuthUi(view: View) {
        val layoutAuth = view.findViewById<View>(R.id.layout_auth)
        val layoutDownloads = view.findViewById<View>(R.id.layout_downloads)
        layoutAuth.visibility = View.VISIBLE
        layoutDownloads.visibility = View.GONE

        val etEmail = view.findViewById<EditText>(R.id.et_email)
        val etPassword = view.findViewById<EditText>(R.id.et_password)
        val btnPrimary = view.findViewById<Button>(R.id.btn_auth_primary)
        val btnSecondary = view.findViewById<Button>(R.id.btn_auth_secondary)
        val tvTitle = view.findViewById<TextView>(R.id.tv_auth_title)

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

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val request = AuthRequest(email, password)
                    val response = if (isLoginMode) {
                        AuthRetrofitClient.api.signIn(request)
                    } else {
                        AuthRetrofitClient.api.signUp(request)
                    }

                    if (response.access_token != null) {
                        sessionManager.saveToken(response.access_token)
                        showDownloadUi(view)
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.msg_auth_error, response.error ?: getString(R.string.error_unknown)), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: HttpException) {
                    Toast.makeText(requireContext(), getString(R.string.msg_auth_error, parseAuthError(e)), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), getString(R.string.msg_auth_error, e.message ?: getString(R.string.error_unknown)), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // HttpException doesn't carry the response body directly — Supabase's error field name
    // varies by endpoint ("msg" on /signup, "error_description" on /token), so try all three
    // before falling back to the raw HTTP status text.
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
        view.findViewById<View>(R.id.layout_downloads).visibility = View.VISIBLE

        val etUrl = view.findViewById<EditText>(R.id.et_url)
        val rvJobs = view.findViewById<RecyclerView>(R.id.rv_jobs)
        val layoutEmpty = view.findViewById<View>(R.id.layout_empty_downloader)

        view.findViewById<Button>(R.id.btn_logout).setOnClickListener {
            sessionManager.clear()
            jobList.clear()
            showAuthUi(view)
        }

        adapter = JobAdapter(jobList,
            onRetry = { job -> retryJob(job) },
            onOpen = { job -> openDownloadedFile(job) }
        )
        rvJobs.layoutManager = LinearLayoutManager(requireContext())
        rvJobs.adapter = adapter

        updateEmptyState(layoutEmpty, rvJobs)

        view.findViewById<Button>(R.id.btn_download).setOnClickListener {
            val url = etUrl.text.toString()
            if (url.isNotBlank()) {
                startDownload(url, layoutEmpty, rvJobs)
                etUrl.text.clear()
            }
        }
    }

    private fun updateEmptyState(layoutEmpty: View, rvJobs: RecyclerView) {
        if (jobList.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            rvJobs.visibility = View.GONE
        } else {
            layoutEmpty.visibility = View.GONE
            rvJobs.visibility = View.VISIBLE
        }
    }

    private fun startDownload(url: String, layoutEmpty: View, rvJobs: RecyclerView) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val job = RetrofitClient.api.createJob(DownloadRequest(url))
                job.url = url
                adapter.updateJob(job)
                updateEmptyState(layoutEmpty, rvJobs)
                pollJob(job)
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    handleSessionExpired()
                } else {
                    val failedJob = DownloadJob("failed-${System.currentTimeMillis()}", "failed", null, url)
                    adapter.updateJob(failedJob)
                    updateEmptyState(layoutEmpty, rvJobs)
                }
            } catch (e: Exception) {
                val failedJob = DownloadJob("failed-${System.currentTimeMillis()}", "failed", null, url)
                adapter.updateJob(failedJob)
                updateEmptyState(layoutEmpty, rvJobs)
            }
        }
    }

    // A token that was valid at login can still expire mid-session (Supabase access
    // tokens are short-lived). Rather than leave the user stuck with jobs silently
    // failing and no way to recover, clear the stale session and send them back to
    // the login screen with an explanation.
    private fun handleSessionExpired() {
        sessionManager.clear()
        Toast.makeText(requireContext(), R.string.msg_session_expired, Toast.LENGTH_LONG).show()
        view?.let { showAuthUi(it) }
    }

    private fun retryJob(job: DownloadJob) {
        val url = job.url ?: return
        val layoutEmpty = view?.findViewById<View>(R.id.layout_empty_downloader) ?: return
        val rvJobs = view?.findViewById<RecyclerView>(R.id.rv_jobs) ?: return
        startDownload(url, layoutEmpty, rvJobs)
    }

    private suspend fun pollJob(job: DownloadJob) {
        var currentJob = job
        while (currentJob.status == "processing") {
            delay(3000)
            try {
                currentJob = RetrofitClient.api.getJob(currentJob.id)
                currentJob.url = job.url
                adapter.updateJob(currentJob)
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    handleSessionExpired()
                }
                break
            } catch (e: Exception) {
                break
            }
        }
        if (currentJob.status == "done" && !downloadedJobs.contains(currentJob.id)) {
            downloadToDevice(currentJob)
            downloadedJobs.add(currentJob.id)
        }
    }

    private fun downloadToDevice(job: DownloadJob) {
        val url = job.outputUrl ?: return
        val uri = Uri.parse(url)
        val fileName = uri.lastPathSegment ?: "download_${job.id}"

        val request = DownloadManager.Request(uri)
            .setTitle(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

        val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)

        jobToLocalFile[job.id] = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
    }

    private fun openDownloadedFile(job: DownloadJob) {
        val file = jobToLocalFile[job.id] ?: return
        if (!file.exists()) {
            Toast.makeText(requireContext(), R.string.msg_file_not_found, Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, requireContext().contentResolver.getType(uri) ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.msg_open_error, Toast.LENGTH_SHORT).show()
        }
    }
}