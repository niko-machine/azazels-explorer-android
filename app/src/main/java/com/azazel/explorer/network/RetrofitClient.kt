package com.azazel.explorer.network

import android.content.Context
import com.azazel.explorer.data.SessionManager
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
