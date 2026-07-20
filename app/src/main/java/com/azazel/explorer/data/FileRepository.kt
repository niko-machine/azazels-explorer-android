package com.azazel.explorer.data

import java.io.File

class FileRepository {
    fun listFiles(directory: File): List<File> {
        return directory.listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()
    }
}
