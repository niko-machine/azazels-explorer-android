package com.azazel.explorer.ui.zip

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azazel.explorer.R
import com.azazel.explorer.util.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

class ZipContentsFragment : Fragment(R.layout.fragment_zip_contents) {
    private val args: ZipContentsFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
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

        val fileCount = entries.count { !it.isDirectory }
        val dirCount = entries.count { it.isDirectory }
        val totalSize = entries.filter { !it.isDirectory }.sumOf { it.size }
        view.findViewById<TextView>(R.id.tv_zip_info).text =
            getString(R.string.zip_info_format, fileCount, dirCount, formatFileSize(totalSize))

        val adapter = ZipEntryAdapter(entries) { entry ->
            if (!entry.isDirectory) {
                showExtractSingleDialog(file, entry)
            }
        }

        val rv = view.findViewById<RecyclerView>(R.id.rv_zip_contents)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_extract_all -> {
                    extractAll(file, entries)
                    true
                }
                else -> false
            }
        }
    }

    private fun showExtractSingleDialog(zipFile: File, entry: ZipEntryInfo) {
        AlertDialog.Builder(requireContext())
            .setTitle(entry.name.substringAfterLast('/'))
            .setMessage(getString(R.string.zip_extract_single_msg, formatFileSize(entry.size)))
            .setPositiveButton(R.string.zip_extract) { _, _ ->
                extractSingleFile(zipFile, entry)
            }
            .setNegativeButton(R.string.zip_share) { _, _ ->
                shareSingleFile(zipFile, entry)
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    private fun extractAll(zipFile: File, entries: List<ZipEntryInfo>) {
        val progressDialog = AlertDialog.Builder(requireContext())
            .setMessage(R.string.zip_extracting)
            .setCancelable(false)
            .show()

        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val outputDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "Extracted/${zipFile.nameWithoutExtension}"
                    )
                    if (!outputDir.exists()) outputDir.mkdirs()

                    var extractedCount = 0
                    val extractedPaths = mutableListOf<String>()
                    ZipFile(zipFile).use { zip ->
                        entries.filter { !it.isDirectory }.forEach { entryInfo ->
                            val entry = zip.getEntry(entryInfo.name) ?: return@forEach
                            val outFile = File(outputDir, entry.name)
                            outFile.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { input ->
                                FileOutputStream(outFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            extractedPaths.add(outFile.absolutePath)
                            extractedCount++
                        }
                    }

                    if (extractedPaths.isNotEmpty()) {
                        android.media.MediaScannerConnection.scanFile(
                            requireContext(),
                            extractedPaths.toTypedArray(),
                            null, null
                        )
                    }

                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        AlertDialog.Builder(requireContext())
                            .setTitle(R.string.zip_extract_complete)
                            .setMessage(getString(R.string.zip_extract_complete_msg, extractedCount, outputDir.absolutePath))
                            .setPositiveButton(R.string.zip_open_folder) { _, _ ->
                                openFolder(outputDir)
                            }
                            .setNegativeButton(android.R.string.ok, null)
                            .show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        Toast.makeText(requireContext(), getString(R.string.zip_extract_error, e.message), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun extractSingleFile(zipFile: File, entryInfo: ZipEntryInfo) {
        val progressDialog = AlertDialog.Builder(requireContext())
            .setMessage(R.string.zip_extracting)
            .setCancelable(false)
            .show()

        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val outputDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "Extracted/${zipFile.nameWithoutExtension}"
                    )
                    if (!outputDir.exists()) outputDir.mkdirs()

                    ZipFile(zipFile).use { zip ->
                        val entry = zip.getEntry(entryInfo.name) ?: return@withContext
                        val outFile = File(outputDir, entry.name)
                        outFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                        }

                        android.media.MediaScannerConnection.scanFile(
                            requireContext(),
                            arrayOf(outFile.absolutePath),
                            null, null
                        )

                        withContext(Dispatchers.Main) {
                            progressDialog.dismiss()
                            AlertDialog.Builder(requireContext())
                                .setTitle(R.string.zip_extract_complete)
                                .setMessage(getString(R.string.zip_extract_single_complete_msg, entryInfo.name.substringAfterLast('/'), outFile.absolutePath))
                                .setPositiveButton(R.string.zip_open) { _, _ ->
                                    openFile(outFile)
                                }
                                .setNegativeButton(android.R.string.ok, null)
                                .show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        Toast.makeText(requireContext(), getString(R.string.zip_extract_error, e.message), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun shareSingleFile(zipFile: File, entryInfo: ZipEntryInfo) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val outputDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "Extracted/${zipFile.nameWithoutExtension}"
                    )
                    if (!outputDir.exists()) outputDir.mkdirs()

                    val outFile: File
                    ZipFile(zipFile).use { zip ->
                        val entry = zip.getEntry(entryInfo.name) ?: return@withContext
                        outFile = File(outputDir, entry.name)
                        outFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        val uri = FileProvider.getUriForFile(
                            requireContext(),
                            "${requireContext().packageName}.fileprovider",
                            outFile
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = getMimeType(outFile)
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(shareIntent, getString(R.string.chooser_share_file)))
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), getString(R.string.zip_extract_error, e.message), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.msg_open_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFolder(dir: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    dir
                )
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.msg_open_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMimeType(file: File): String {
        val ext = file.extension.lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            else -> "*/*"
        }
    }
}

data class ZipEntryInfo(val name: String, val size: Long, val isDirectory: Boolean)

class ZipEntryAdapter(
    private val items: List<ZipEntryInfo>,
    private val onItemClick: (ZipEntryInfo) -> Unit
) : RecyclerView.Adapter<ZipEntryAdapter.ViewHolder>() {

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
        holder.size.text = if (entry.isDirectory) "Directory" else formatFileSize(entry.size)
        holder.icon.setImageResource(if (entry.isDirectory) R.drawable.ic_folder else R.drawable.ic_file)
        holder.itemView.setOnClickListener { onItemClick(entry) }
    }

    override fun getItemCount() = items.size
}
