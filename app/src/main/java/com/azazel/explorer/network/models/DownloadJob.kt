package com.azazel.explorer.network.models

data class DownloadRequest(val url: String, val outputName: String)
data class DownloadJob(
    val id: String,
    val status: String,
    val outputUrl: String?,
    var url: String? = null,
    var outputName: String? = null,
    val errorMessage: String? = null
)
