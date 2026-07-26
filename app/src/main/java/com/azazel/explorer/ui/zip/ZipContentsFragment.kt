package com.azazel.explorer.ui.zip

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azazel.explorer.R
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

class ZipContentsFragment : Fragment(R.layout.fragment_zip_contents) {
    private val args: ZipContentsFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        val file = File(args.filePath)
        toolbar.title = file.name
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val entries = mutableListOf<ZipEntryInfo>()
        try {
            ZipFile(file).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    entries.add(ZipEntryInfo(entry.name, entry.size, entry.isDirectory))
                }
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.msg_zip_read_error, Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        val rv = view.findViewById<RecyclerView>(R.id.rv_zip_contents)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = ZipEntryAdapter(entries)
    }
}

data class ZipEntryInfo(val name: String, val size: Long, val isDirectory: Boolean)

class ZipEntryAdapter(private val items: List<ZipEntryInfo>) : RecyclerView.Adapter<ZipEntryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_entry_icon)
        val name: TextView = view.findViewById(R.id.tv_entry_name)
        val size: TextView = view.findViewById(R.id.tv_entry_size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_zip_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = items[position]
        holder.name.text = entry.name
        holder.size.text = formatFileSize(entry.size)
        holder.icon.setImageResource(if (entry.isDirectory) R.drawable.ic_folder else R.drawable.ic_file)
    }

    override fun getItemCount() = items.size

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 0) return ""
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
        return String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0)
    }
}
