package com.azazel.explorer.ui.organize

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object OrganizeManager {

    data class OrganizeEntry(
        val file: File,
        val category: String,
        val destinationLabel: String,
        val destinationDir: File
    )

    enum class FileTypeFilter(val label: String) {
        IMAGES("Images"),
        DOCUMENTS("Documents"),
        VIDEOS("Videos"),
        AUDIO("Audio")
    }

    private val docExtensions = listOf("pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "csv")
    private val imgExtensions = listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic")
    private val videoExtensions = listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp")
    private val audioExtensions = listOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "wma")

    private val sourceDirs: List<File> by lazy {
        listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        ).filter { it.exists() }
    }

    fun scanForOrganizable(
        context: Context,
        filters: Set<FileTypeFilter> = FileTypeFilter.entries.toSet()
    ): List<OrganizeEntry> {
        val entries = mutableListOf<OrganizeEntry>()

        sourceDirs.forEach { sourceDir ->
            scanDirectory(context, sourceDir, filters, entries, maxDepth = 4)
        }

        return entries
    }

    private fun scanDirectory(
        context: Context,
        dir: File,
        filters: Set<FileTypeFilter>,
        entries: MutableList<OrganizeEntry>,
        maxDepth: Int,
        currentDepth: Int = 0
    ) {
        if (currentDepth > maxDepth) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                scanDirectory(context, file, filters, entries, maxDepth, currentDepth + 1)
            } else if (file.isFile && !isAlreadyOrganized(file)) {
                val entry = categorizeFile(context, file, filters)
                if (entry != null) entries.add(entry)
            }
        }
    }

    private fun categorizeFile(
        context: Context,
        file: File,
        filters: Set<FileTypeFilter>
    ): OrganizeEntry? {
        val ext = file.extension.lowercase()

        if (FileTypeFilter.IMAGES in filters && ext in imgExtensions) {
            val appName = getSourceAppName(context, file)
            val destDir = File(
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Photos"),
                "$appName/${ext.uppercase()}"
            )
            return OrganizeEntry(file, "image", "Photos/$appName/${ext.uppercase()}", destDir)
        }

        if (FileTypeFilter.DOCUMENTS in filters && ext in docExtensions) {
            val destDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                ext.uppercase()
            )
            return OrganizeEntry(file, "document", "Documents/${ext.uppercase()}", destDir)
        }

        if (FileTypeFilter.VIDEOS in filters && ext in videoExtensions) {
            val appName = getSourceAppName(context, file)
            val destDir = File(
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Videos"),
                "$appName/${ext.uppercase()}"
            )
            return OrganizeEntry(file, "video", "Videos/$appName/${ext.uppercase()}", destDir)
        }

        if (FileTypeFilter.AUDIO in filters && ext in audioExtensions) {
            val destDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                ext.uppercase()
            )
            return OrganizeEntry(file, "audio", "Music/${ext.uppercase()}", destDir)
        }

        return null
    }

    fun organizeFiles(
        context: Context,
        entries: List<OrganizeEntry>,
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
        isCancelled: () -> Boolean = { false }
    ): Map<String, Int> {
        val results = mutableMapOf<String, Int>()
        entries.forEachIndexed { index, entry ->
            if (isCancelled()) return results
            onProgress?.invoke(index + 1, entries.size)
            if (moveFile(context, entry.file, entry.destinationDir)) {
                results[entry.destinationLabel] = (results[entry.destinationLabel] ?: 0) + 1
            }
        }
        return results
    }

    private fun moveFile(context: Context, file: File, targetDir: File): Boolean {
        if (!targetDir.exists()) targetDir.mkdirs()
        val targetFile = File(targetDir, file.name)

        val success = if (file.renameTo(targetFile)) {
            true
        } else {
            try {
                file.copyTo(targetFile, overwrite = true)
                file.delete()
                true
            } catch (e: Exception) {
                false
            }
        }

        if (success) {
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), null, null)
        }

        return success
    }

    fun isAlreadyOrganized(file: File): Boolean {
        val path = file.absolutePath
        return path.contains("/Documents/") || path.contains("/Photos/") ||
                path.contains("/Videos/") || path.contains("/Music/")
    }

    private fun getSourceAppName(context: Context, file: File): String {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val projection = arrayOf(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
            val selection = "${MediaStore.MediaColumns.DATA} = ?"
            val selectionArgs = arrayOf(file.absolutePath)

            val cursor = context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection, selection, selectionArgs, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val pkg = it.getString(it.getColumnIndexOrThrow(MediaStore.MediaColumns.OWNER_PACKAGE_NAME))
                    if (pkg != null) return mapPackageToName(pkg)
                }
            }
        }

        val parent = file.parentFile?.name ?: ""
        val knownApps = listOf("WhatsApp", "Instagram", "Facebook", "Messenger", "Telegram", "Twitter", "Snapchat", "TikTok", "Discord", "Signal")
        knownApps.forEach { if (parent.contains(it, ignoreCase = true)) return it }

        return "Other"
    }

    private fun mapPackageToName(pkg: String): String = when {
        pkg.contains("whatsapp") -> "WhatsApp"
        pkg.contains("facebook.orca") -> "Messenger"
        pkg.contains("facebook.katana") -> "Facebook"
        pkg.contains("instagram") -> "Instagram"
        pkg.contains("google.android.apps.photos") -> "Google Photos"
        pkg.contains("chrome") -> "Chrome"
        pkg.contains("telegram") -> "Telegram"
        pkg.contains("tiktok") -> "TikTok"
        pkg.contains("discord") -> "Discord"
        else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }

    fun getRecentMoves(): List<Pair<File, File>> {
        return try {
            val prefs = File(System.getenv("ANDROID_DATA"), "shared_prefs/organize_undo.xml")
            if (!prefs.exists()) return emptyList()
            val xml = prefs.readText()
            val pairs = mutableListOf<Pair<File, File>>()
            val regex = Regex("<move src=\"(.*?)\" dst=\"(.*?)\"")
            regex.findAll(xml).forEach { match ->
                val src = File(match.groupValues[1])
                val dst = File(match.groupValues[2])
                if (src.exists() || dst.exists()) pairs.add(src to dst)
            }
            pairs
        } catch (e: Exception) {
            emptyList()
        }
    }
}
