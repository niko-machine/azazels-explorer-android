package com.azazel.explorer.network

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

data class AuthRequest(val email: String, val password: String)
data class AuthResponse(
    val access_token: String? = null,
    val refresh_token: String? = null,
    val error: String? = null,
    val error_description: String? = null,
    val msg: String? = null
)

interface AuthApiService {
    @Headers("Content-Type: application/json")
    @POST("auth/v1/signup")
    suspend fun signUp(@Body req: AuthRequest): AuthResponse

    @Headers("Content-Type: application/json")
    @POST("auth/v1/token?grant_type=password")
    suspend fun signIn(@Body req: AuthRequest): AuthResponse

    @Headers("Content-Type: application/json")
    @POST("auth/v1/token?grant_type=refresh_token")
    suspend fun refreshToken(@Body req: Map<String, String>): AuthResponse

    @POST("auth/v1/logout")
    suspend fun signOut(): retrofit2.Response<Unit>
}
