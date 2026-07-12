package com.azazel.explorer.network.models

data class DownloadRequest(val url: String, val format: String = "mp4")
data class DownloadJob(val id: String, val status: String, val outputUrl: String?)
