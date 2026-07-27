package com.azazel.explorer.network

import com.azazel.explorer.network.models.DownloadJob
import com.azazel.explorer.network.models.DownloadRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @POST("jobs")
    suspend fun createJob(@Body req: DownloadRequest): DownloadJob

    @GET("jobs/{id}")
    suspend fun getJob(@Path("id") id: String): DownloadJob

    @GET("jobs")
    suspend fun listJobs(): List<DownloadJob>
}
