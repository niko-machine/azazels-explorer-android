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

class JobAdapter(
    private val jobs: MutableList<DownloadJob>,
    private val onRetry: (DownloadJob) -> Unit,
    private val onOpen: (DownloadJob) -> Unit
) : RecyclerView.Adapter<JobAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val url: TextView = view.findViewById(R.id.tv_job_url)
        val status: TextView = view.findViewById(R.id.tv_job_status)
        val detail: TextView = view.findViewById(R.id.tv_job_detail)
        val retryButton: Button = view.findViewById(R.id.btn_retry)
        val openButton: Button = view.findViewById(R.id.btn_open)
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
            else -> R.color.text_on_dark
        }
        holder.status.setTextColor(ContextCompat.getColor(context, statusColor))

        holder.detail.text = when (job.status) {
            "failed" -> job.errorMessage ?: context.getString(R.string.error_unknown)
            "done" -> context.getString(R.string.job_saved_location, "Downloads/${job.outputName}")
            else -> job.url ?: ""
        }
        
        // Ensure the original URL is visible if not already shown in detail
        if (job.status != "processing" && job.url != null && holder.detail.text != job.url) {
            holder.detail.text = "${holder.detail.text}\n${job.url}"
        }

        holder.retryButton.visibility = if (job.status == "failed") View.VISIBLE else View.GONE
        holder.openButton.visibility = if (job.status == "done") View.VISIBLE else View.GONE
        holder.progressBar.visibility = if (job.status == "processing") View.VISIBLE else View.GONE
        
        holder.retryButton.setOnClickListener { onRetry(job) }
        holder.openButton.setOnClickListener { onOpen(job) }
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
