package com.lingualink.linguagt.ads

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ima.ImaAdsLoader
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.lingualink.linguagt.TestRigorLogger
import java.util.concurrent.atomic.AtomicBoolean

class IMAManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "IMAManager"
        
        private const val SAMPLE_AD_TAG_URL = "https://pubads.g.doubleclick.net/gampad/ads?" +
            "iu=/21775744923/external/single_ad_samples&" +
            "sz=640x480&" +
            "cust_params=sample_ct%3Dlinear&" +
            "ciu_szs=300x250%2C728x90&" +
            "gdfp_req=1&" +
            "output=vast&" +
            "unviewed_position_start=1&" +
            "env=vp&" +
            "impl=s&" +
            "correlator="
        
        @Volatile
        private var instance: IMAManager? = null
        
        fun getInstance(context: Context): IMAManager {
            return instance ?: synchronized(this) {
                instance ?: IMAManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
    
    val isInitialized = AtomicBoolean(false)
    private var consentInformation: ConsentInformation? = null
    private var isConsentGranted = false
    
    private var player: ExoPlayer? = null
    private var imaAdsLoader: ImaAdsLoader? = null
    private var playerView: PlayerView? = null
    
    private var pendingAdCallback: ((Boolean) -> Unit)? = null
    private var pendingRewardCallback: (() -> Unit)? = null
    
    fun initialize(activity: Activity, onComplete: (Boolean) -> Unit) {
        if (isInitialized.get()) {
            TestRigorLogger.logAdEvent("IMA SDK already initialized")
            onComplete(true)
            return
        }
        
        TestRigorLogger.logAdEvent("Initializing IMA SDK")
        
        try {
            handleConsentAndInitialize(activity, onComplete)
        } catch (e: Exception) {
            TestRigorLogger.logError("IMA SDK initialization failed", e)
            onComplete(false)
        }
    }
    
    private fun completeInitialization() {
        isInitialized.set(true)
        TestRigorLogger.logAdEvent("IMA SDK initialized successfully")
    }
    
    private fun handleConsentAndInitialize(activity: Activity, onComplete: (Boolean) -> Unit) {
        try {
            val params = ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false)
                .build()
            
            consentInformation = UserMessagingPlatform.getConsentInformation(activity)
            consentInformation?.requestConsentInfoUpdate(
                activity,
                params,
                {
                    if (consentInformation?.isConsentFormAvailable == true) {
                        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                            if (formError != null) {
                                TestRigorLogger.logWarning("Consent form error: ${formError.message}")
                            }
                            val canShowAds = consentInformation?.canRequestAds() ?: false
                            isConsentGranted = canShowAds
                            completeInitialization()
                            onComplete(canShowAds)
                        }
                    } else {
                        val canShowAds = consentInformation?.canRequestAds() ?: true
                        isConsentGranted = canShowAds
                        completeInitialization()
                        onComplete(canShowAds)
                    }
                },
                { requestConsentError ->
                    TestRigorLogger.logWarning("Consent info update failed: ${requestConsentError.message}")
                    completeInitialization()
                    onComplete(true)
                }
            )
        } catch (e: Exception) {
            TestRigorLogger.logError("Consent handling failed", e)
            completeInitialization()
            onComplete(true)
        }
    }
    
    fun showInterstitialAd(activity: Activity, onComplete: (Boolean) -> Unit) {
        if (!isInitialized.get()) {
            TestRigorLogger.logWarning("IMA SDK not initialized - cannot show interstitial")
            onComplete(false)
            return
        }
        
        TestRigorLogger.logAdEvent("Showing interstitial video ad")
        pendingAdCallback = onComplete
        
        try {
            createAndShowVideoAd(activity, onComplete)
        } catch (e: Exception) {
            TestRigorLogger.logError("Failed to show interstitial ad", e)
            onComplete(false)
        }
    }
    
    fun showRewardedAd(activity: Activity, onRewarded: () -> Unit, onComplete: (Boolean) -> Unit) {
        if (!isInitialized.get()) {
            TestRigorLogger.logWarning("IMA SDK not initialized - cannot show rewarded ad")
            onComplete(false)
            return
        }
        
        TestRigorLogger.logAdEvent("Showing rewarded video ad")
        pendingAdCallback = onComplete
        pendingRewardCallback = onRewarded
        
        try {
            createAndShowVideoAd(activity, onComplete)
        } catch (e: Exception) {
            TestRigorLogger.logError("Failed to show rewarded ad", e)
            onComplete(false)
        }
    }
    
    private fun createAndShowVideoAd(activity: Activity, onComplete: (Boolean) -> Unit) {
        try {
            // Create a temporary PlayerView for the ad
            playerView = PlayerView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            
            // Build IMA ads loader
            imaAdsLoader = ImaAdsLoader.Builder(activity).build()
            
            // Build ExoPlayer with IMA integration
            player = ExoPlayer.Builder(activity)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(activity)
                        .setLocalAdInsertionComponents({ imaAdsLoader }, playerView!!)
                )
                .build()
            
            playerView?.player = player
            imaAdsLoader?.setPlayer(player)
            
            // Set up player listener for ad completion
            player?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_ENDED -> {
                            TestRigorLogger.logAdEvent("Video ad playback ended")
                            pendingRewardCallback?.invoke()
                            cleanupPlayer()
                            onComplete(true)
                        }
                        Player.STATE_IDLE -> {
                            // Player stopped or error
                        }
                        else -> {}
                    }
                }
            })
            
            // Use a short video with pre-roll ad
            val contentUri = Uri.parse("https://storage.googleapis.com/gvabox/media/samples/stock.mp4")
            val adTagUri = Uri.parse(SAMPLE_AD_TAG_URL + System.currentTimeMillis())
            val mediaItem = MediaItem.Builder()
                .setUri(contentUri)
                .setAdsConfiguration(MediaItem.AdsConfiguration.Builder(adTagUri).build())
                .build()
            
            player?.setMediaItem(mediaItem)
            player?.prepare()
            player?.play()
            
            TestRigorLogger.logAdEvent("Video ad player started")
            
        } catch (e: Exception) {
            TestRigorLogger.logError("Failed to create video ad", e)
            cleanupPlayer()
            onComplete(false)
        }
    }
    
    private fun cleanupPlayer() {
        try {
            player?.release()
            player = null
            
            imaAdsLoader?.release()
            imaAdsLoader = null
            
            playerView = null
            
            pendingAdCallback = null
            pendingRewardCallback = null
            
            TestRigorLogger.logAdEvent("Video ad player cleaned up")
        } catch (e: Exception) {
            TestRigorLogger.logError("Error cleaning up player", e)
        }
    }
    
    fun isAdAvailable(): Boolean {
        return isInitialized.get() && isConsentGranted
    }
    
    fun isInterstitialAdAvailable(): Boolean {
        return isAdAvailable()
    }
    
    fun isRewardedAdAvailable(): Boolean {
        return isAdAvailable()
    }
    
    fun resume() {
        player?.play()
    }
    
    fun pause() {
        player?.pause()
    }
    
    fun destroy() {
        cleanupPlayer()
        isInitialized.set(false)
        TestRigorLogger.logAdEvent("IMAManager destroyed")
    }
    
    fun getDiagnostics(): String {
        return """
            IMA SDK Status:
            - Initialized: ${isInitialized.get()}
            - Consent Granted: $isConsentGranted
            - Player Active: ${player != null}
        """.trimIndent()
    }
    
    fun forceShowInterstitial(activity: Activity, onComplete: (Boolean) -> Unit) {
        showInterstitialAd(activity, onComplete)
    }
}
