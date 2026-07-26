package com.azazel.explorer.network

import android.content.Context
import com.azazel.explorer.data.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://azazel-backend.onrender.com/"
    private var sessionManager: SessionManager? = null

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = sessionManager?.getToken()
                val request = if (token != null) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            .authenticator { _, response ->
                val refreshToken = sessionManager?.getRefreshToken()
                if (refreshToken != null && (response.code() == 401)) {
                    synchronized(this) {
                        val newToken = sessionManager?.getToken()
                        if (response.request().header("Authorization") != "Bearer $newToken") {
                            return@synchronized response.request().newBuilder()
                                .header("Authorization", "Bearer $newToken")
                                .build()
                        }

                        try {
                            val refreshResponse = runBlocking {
                                AuthRetrofitClient.api.refreshToken(mapOf("refresh_token" to refreshToken))
                            }
                            if (refreshResponse.access_token != null && refreshResponse.refresh_token != null) {
                                sessionManager?.saveToken(refreshResponse.access_token, refreshResponse.refresh_token)
                                return@synchronized response.request().newBuilder()
                                    .header("Authorization", "Bearer ${refreshResponse.access_token}")
                                    .build()
                            }
                        } catch (ignored: Exception) {
                            // Refresh failed
                        }
                    }
                }
                null
            }
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    fun init(context: Context) {
        if (sessionManager == null) {
            sessionManager = SessionManager(context)
        }
    }
}
