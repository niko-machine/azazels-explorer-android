package com.azazel.explorer.network.models

data class DownloadRequest(val url: String)
data class DownloadJob(
    val id: String,
    val status: String,
    val outputUrl: String?,
    @Transient var url: String? = null
)
