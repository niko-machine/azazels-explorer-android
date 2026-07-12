package com.azazel.explorer.ui.downloads

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.azazel.explorer.R
import com.azazel.explorer.network.RetrofitClient
import com.azazel.explorer.network.models.DownloadRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DownloadsFragment : Fragment(R.layout.fragment_downloads) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etUrl = view.findViewById<EditText>(R.id.et_url)
        val tvStatus = view.findViewById<TextView>(R.id.tv_status)

        view.findViewById<Button>(R.id.btn_download).setOnClickListener {
            val url = etUrl.text.toString()
            if (url.isBlank()) return@setOnClickListener

            tvStatus.text = getString(R.string.status_starting)
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val job = RetrofitClient.api.createJob(DownloadRequest(url))
                    pollJob(job.id, tvStatus)
                } catch (e: Exception) {
                    tvStatus.text = getString(R.string.status_error, e.message ?: getString(R.string.error_unknown))
                }
            }
        }
    }

    private suspend fun pollJob(jobId: String, tvStatus: TextView) {
        while (true) {
            val job = RetrofitClient.api.getJob(jobId)
            tvStatus.text = getString(R.string.status_label, job.status)
            if (job.status == "done" || job.status == "failed") break
            delay(2000)
        }
    }
}
