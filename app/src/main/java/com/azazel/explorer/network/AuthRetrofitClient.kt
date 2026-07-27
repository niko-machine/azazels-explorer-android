package com.azazel.explorer.network

import com.azazel.explorer.data.SessionManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object AuthRetrofitClient {
    private const val SUPABASE_URL = "https://dvrrlezcxanvuggyuqyb.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImR2cnJsZXpjeGFudnVnZ3l1cXliIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODM4MzU4MTMsImV4cCI6MjA5OTQxMTgxM30.n_4FqR1Zh61z4dSlp3nvv29npi16pgfa7uEklcOXeGE"
    
    private var sessionManager: SessionManager? = null

    fun init(manager: SessionManager) {
        sessionManager = manager
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                
                // Add Authorization header if token exists (for logout)
                sessionManager?.getToken()?.let {
                    requestBuilder.addHeader("Authorization", "Bearer $it")
                }
                
                chain.proceed(requestBuilder.build())
            }
            .build()
    }

    val api: AuthApiService by lazy {
        Retrofit.Builder()
            .baseUrl(SUPABASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApiService::class.java)
    }
}
