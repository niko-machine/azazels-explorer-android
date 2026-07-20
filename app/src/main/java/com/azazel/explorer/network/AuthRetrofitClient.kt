package com.azazel.explorer.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object AuthRetrofitClient {
    private const val SUPABASE_URL = "https://dvrrlezcxanvuggyuqyb.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImR2cnJsZXpjeGFudnVnZ3l1cXliIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODM4MzU4MTMsImV4cCI6MjA5OTQxMTgxM30.n_4FqR1Zh61z4dSlp3nvv29npi16pgfa7uEklcOXeGE"

    val api: AuthApiService by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .build()
                chain.proceed(request)
            }
            .build()

        Retrofit.Builder()
            .baseUrl(SUPABASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApiService::class.java)
    }
}
