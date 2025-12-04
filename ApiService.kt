package com.lingualink.linguagt

import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Header
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

data class TranslateRequest(
    val text: String,
    val from: String,
    val to: String
)

data class TranslateResponse(
    val translatedText: String,
    val confidence: Double? = null,
    val detectedLanguage: String? = null
)

data class UserSettings(
    val speechRate: Int = 10,
    val autoPlay: Boolean = true,
    val micSensitivity: Int = 5,
    val saveHistory: Boolean = true,
    val offlineMode: Boolean = false
)

data class SubscriptionStatus(
    val translationsUsed: Int = 0,
    val translationsRemaining: Int = 10,
    val isPremium: Boolean = false,
    val subscriptionExpiry: Long? = null
)

interface ApiService {
    @POST("/api/translate")
    suspend fun translate(
        @Body request: TranslateRequest,
        @Header("Authorization") auth: String? = null
    ): TranslateResponse

    @GET("/api/settings")
    suspend fun getSettings(
        @Header("Authorization") auth: String? = null
    ): UserSettings

    @GET("/api/subscription-status")
    suspend fun getSubscriptionStatus(
        @Header("Authorization") auth: String? = null
    ): SubscriptionStatus

    companion object {
        private const val BASE_URL = "https://linguagt.com/"
        private const val TIMEOUT_SECONDS = 30L

        fun create(): ApiService {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }
            
            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
            
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            return retrofit.create(ApiService::class.java)
        }
    }
}

