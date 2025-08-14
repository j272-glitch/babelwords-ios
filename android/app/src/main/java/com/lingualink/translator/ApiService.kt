package com.lingualink.translator

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

// Data classes for API responses
data class TranslationRequest(
    val text: String,
    val sourceLanguage: String,
    val targetLanguage: String
)

data class TranslationResponse(
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String
)

data class UserSubscription(
    val subscriptionStatus: String,
    val translationsUsed: Int,
    val translationsRemaining: Any, // Can be Int or "unlimited"
    val isPremium: Boolean
)

// Retrofit API interface
interface LinguaLinkApiService {
    @POST("api/translate")
    suspend fun translateText(@Body request: TranslationRequest): TranslationResponse
    
    @GET("api/subscription-status")
    suspend fun getSubscriptionStatus(): UserSubscription
    
    @GET("api/auth/user")
    suspend fun getCurrentUser(): Any // Replace with actual user model
}

// API client singleton
object ApiClient {
    private const val BASE_URL = "https://your-replit-app.replit.app/"
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val apiService: LinguaLinkApiService = retrofit.create(LinguaLinkApiService::class.java)
}

// Usage example in MainActivity or other components:
/*
class TranslationManager {
    suspend fun translateText(text: String, sourceLang: String, targetLang: String): TranslationResponse? {
        return try {
            val request = TranslationRequest(text, sourceLang, targetLang)
            ApiClient.apiService.translateText(request)
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun getSubscriptionStatus(): UserSubscription? {
        return try {
            ApiClient.apiService.getSubscriptionStatus()
        } catch (e: Exception) {
            null
        }
    }
}
*/