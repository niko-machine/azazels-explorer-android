package com.azazel.explorer.ui.downloads

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.azazel.explorer.R
import com.azazel.explorer.network.models.DownloadJob
import java.io.File
import android.os.Environment
import android.net.Uri

class JobAdapter(
    private val jobs: MutableList<DownloadJob>,
    private val downloadingJobIds: Set<String>,
    private val onRetry: (DownloadJob) -> Unit,
    private val onOpen: (DownloadJob) -> Unit,
    private val onShowInFolder: (DownloadJob) -> Unit
) : RecyclerView.Adapter<JobAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val url: TextView = view.findViewById(R.id.tv_job_url)
        val status: TextView = view.findViewById(R.id.tv_job_status)
        val detail: TextView = view.findViewById(R.id.tv_job_detail)
        val retryButton: Button = view.findViewById(R.id.btn_retry)
        val openButton: Button = view.findViewById(R.id.btn_open)
        val showInFolderButton: Button = view.findViewById(R.id.btn_show_in_folder)
        val progressBar: ProgressBar = view.findViewById(R.id.pb_job)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_job, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val job = jobs[position]
        val context = holder.itemView.context
        
        holder.url.text = job.outputName ?: context.getString(R.string.job_id_label, job.id)
        
        holder.status.text = context.getString(R.string.job_status, job.status)
        val statusColor = when (job.status) {
            "failed" -> R.color.status_failed
            "done" -> R.color.status_done
            "processing" -> R.color.status_processing
            else -> R.color.text_primary
        }
        holder.status.setTextColor(ContextCompat.getColor(context, statusColor))

        val localFile = expectedLocalFile(job)
        val fileExists = localFile?.exists() == true

        holder.detail.text = when {
            job.status == "failed" -> job.errorMessage ?: context.getString(R.string.error_unknown)
            job.status == "done" && fileExists -> context.getString(R.string.job_saved_location, "Downloads/AzazelDownloads")
            job.status == "done" && !fileExists -> context.getString(R.string.msg_file_not_found)
            else -> job.url ?: ""
        }
        
        // Ensure the original URL is visible if not already shown in detail
        if (job.status != "processing" && job.url != null && !holder.detail.text.contains(job.url!!)) {
            holder.detail.text = "${holder.detail.text}\n${job.url}"
        }

        holder.retryButton.visibility = if (job.status == "failed" || (job.status == "done" && !fileExists && !downloadingJobIds.contains(job.id))) View.VISIBLE else View.GONE
        holder.retryButton.text = if (job.status == "done") "Redownload" else context.getString(R.string.btn_retry)
        
        holder.openButton.visibility = if (job.status == "done" && fileExists) View.VISIBLE else View.GONE
        holder.showInFolderButton.visibility = if (job.status == "done" && fileExists) View.VISIBLE else View.GONE
        holder.progressBar.visibility = if (job.status == "processing") View.VISIBLE else View.GONE
        
        holder.retryButton.setOnClickListener { onRetry(job) }
        holder.openButton.setOnClickListener { onOpen(job) }
        holder.showInFolderButton.setOnClickListener { onShowInFolder(job) }
    }

    fun expectedLocalFile(job: DownloadJob): File? {
        val outputUrl = job.outputUrl ?: return null
        val baseName = job.outputName ?: return null
        val ext = Uri.parse(outputUrl).lastPathSegment?.substringAfterLast('.', "mp4") ?: "mp4"
        val downloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AzazelDownloads")
        
        // Check for base name
        val exactFile = File(downloadsDir, "$baseName.$ext")
        if (exactFile.exists()) return exactFile
        
        // Check for numeric suffixes (1) through (10) for recent downloads
        for (i in 1..10) {
            val suffixFile = File(downloadsDir, "$baseName ($i).$ext")
            if (suffixFile.exists()) return suffixFile
        }
        
        return exactFile
    }

    override fun getItemCount() = jobs.size

    fun updateJob(job: DownloadJob) {
        val index = jobs.indexOfFirst { it.id == job.id }
        if (index != -1) {
            val oldUrl = jobs[index].url
            jobs[index] = job
            jobs[index].url = oldUrl
            notifyItemChanged(index)
        } else {
            jobs.add(0, job)
            notifyItemInserted(0)
        }
    }
}
