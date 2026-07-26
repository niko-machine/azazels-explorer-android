package com.azazel.explorer.data

import android.content.Context

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    fun saveToken(accessToken: String, refreshToken: String) {
        prefs.edit().putString("access_token", accessToken)
            .putString("refresh_token", refreshToken).apply()
    }

    fun getToken(): String? = prefs.getString("access_token", null)
    fun getRefreshToken(): String? = prefs.getString("refresh_token", null)

    fun isLoggedIn(): Boolean = getToken() != null

    fun clear() {
        prefs.edit().clear().apply()
    }
}
