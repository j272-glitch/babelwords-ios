package com.lingualink.linguagt

import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class TranslateRequest(
    val text: String,
    val from: String,
    val to: String
)

data class TranslateResponse(
    val translatedText: String,
    val confidence: Double? = null
)

data class UserSettings(
    val speechRate: Int = 10,
    val autoPlay: Boolean = true,
    val micSensitivity: Int = 5
)

data class SubscriptionStatus(
    val translationsUsed: Int = 0,
    val translationsRemaining: Int = 10,
    val isPremium: Boolean = false
)

interface ApiService {
    @POST("/api/translate")
    suspend fun translate(@Body request: TranslateRequest): TranslateResponse

    @GET("/api/settings")
    suspend fun getSettings(): UserSettings

    @GET("/api/subscription-status")
    suspend fun getSubscriptionStatus(): SubscriptionStatus

    companion object {
        private const val BASE_URL = "https://lingualink-speech-translator.replit.app/"

        fun create(): ApiService {
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            return retrofit.create(ApiService::class.java)
        }
    }
}
