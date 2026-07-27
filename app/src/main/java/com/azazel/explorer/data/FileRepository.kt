package com.azazel.explorer.data

import android.content.Context
import android.provider.MediaStore
import com.azazel.explorer.model.FileFilter
import java.io.File

class FileRepository {
    fun listFiles(directory: File): List<File> {
        return directory.listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()
    }

    fun listFilesByType(context: Context, filter: FileFilter): List<File> {
        val files = mutableListOf<File>()
        val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
        val selection = when (filter) {
            FileFilter.IMAGES -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}"
            FileFilter.VIDEOS -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}"
            FileFilter.AUDIO -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO}"
            FileFilter.DOCUMENTS -> "${MediaStore.Files.FileColumns.MIME_TYPE} IN ('application/pdf', 'text/plain', 'application/msword')"
            FileFilter.APKS -> "${MediaStore.Files.FileColumns.MIME_TYPE} = 'application/vnd.android.package-archive'"
            FileFilter.ARCHIVES -> "${MediaStore.Files.FileColumns.MIME_TYPE} IN ('application/zip', 'application/x-rar-compressed', 'application/x-7z-compressed')"
            else -> null
        }
        val cursor = context.contentResolver.query(
            MediaStore.Files.getContentUri("external"), projection, selection, null, null
        )
        cursor?.use {
            val dataIndex = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            while (it.moveToNext()) {
                val file = File(it.getString(dataIndex))
                if (file.exists()) files.add(file)
            }
        }
        return files
    }
}
