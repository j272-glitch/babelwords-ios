package com.lingualink.linguagt.ads

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ima.ImaAdsLoader
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.google.ads.interactivemedia.v3.api.AdErrorEvent
import com.google.ads.interactivemedia.v3.api.AdEvent
import com.google.ads.interactivemedia.v3.api.AdsLoader
import com.google.ads.interactivemedia.v3.api.AdsManager
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent
import com.google.ads.interactivemedia.v3.api.AdsRequest
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.lingualink.linguagt.TestRigorLogger
import java.util.concurrent.atomic.AtomicBoolean
import androidx.appcompat.app.AppCompatActivity

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
    private var sdkFactory: ImaSdkFactory? = null
    private var adsLoader: AdsLoader? = null
    private var adsManager: AdsManager? = null
    private var consentInformation: ConsentInformation? = null
    private var isConsentGranted = false
    
    private var player: ExoPlayer? = null
    private var imaAdsLoader: ImaAdsLoader? = null
    
    private var pendingAdCallback: ((Boolean) -> Unit)? = null
    
    fun initialize(activity: AppCompatActivity, onComplete: (Boolean) -> Unit) {
        if (isInitialized.get()) {
            TestRigorLogger.logAdEvent("IMA SDK already initialized")
            onComplete(true)
            return
        }
        
        TestRigorLogger.logAdEvent("Initializing IMA SDK")
        
        handleConsentAndInitialize(activity) { consentGranted ->
            isConsentGranted = consentGranted
            
            try {
                sdkFactory = ImaSdkFactory.getInstance()
                
                val settings: ImaSdkSettings = sdkFactory!!.createImaSdkSettings()
                settings.isDebugMode = false
                
                isInitialized.set(true)
                TestRigorLogger.logAdEvent("IMA SDK initialized successfully")
                onComplete(true)
            } catch (e: Exception) {
                TestRigorLogger.logError("IMA SDK initialization failed", e)
                onComplete(false)
            }
        }
    }
    
    private fun handleConsentAndInitialize(activity: AppCompatActivity, onComplete: (Boolean) -> Unit) {
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
                            onComplete(canShowAds)
                        }
                    } else {
                        val canShowAds = consentInformation?.canRequestAds() ?: true
                        onComplete(canShowAds)
                    }
                },
                { requestConsentError ->
                    TestRigorLogger.logWarning("Consent info update failed: ${requestConsentError.message}")
                    onComplete(true)
                }
            )
        } catch (e: Exception) {
            TestRigorLogger.logError("Consent handling failed", e)
            onComplete(true)
        }
    }
    
    fun requestAds(activity: AppCompatActivity, adTagUrl: String = SAMPLE_AD_TAG_URL, onComplete: (Boolean) -> Unit) {
        if (!isInitialized.get()) {
            TestRigorLogger.logWarning("IMA SDK not initialized")
            onComplete(false)
            return
        }
        
        try {
            val adsLoader = sdkFactory?.createAdsLoader(activity, sdkFactory!!.createImaSdkSettings(), null)
            
            adsLoader?.addAdErrorListener { adErrorEvent ->
                TestRigorLogger.logError("Ad error: ${adErrorEvent.error.message}", null)
                onComplete(false)
            }
            
            adsLoader?.addAdsLoadedListener { adsManagerLoadedEvent ->
                adsManager = adsManagerLoadedEvent.adsManager
                adsManager?.addAdEventListener { adEvent ->
                    when (adEvent.type) {
                        AdEvent.AdEventType.LOADED -> {
                            TestRigorLogger.logAdEvent("Ad loaded")
                        }
                        AdEvent.AdEventType.STARTED -> {
                            TestRigorLogger.logAdEvent("Ad started")
                        }
                        AdEvent.AdEventType.COMPLETED -> {
                            TestRigorLogger.logAdEvent("Ad completed")
                            onComplete(true)
                        }
                        AdEvent.AdEventType.ALL_ADS_COMPLETED -> {
                            TestRigorLogger.logAdEvent("All ads completed")
                            adsManager?.destroy()
                        }
                        else -> {}
                    }
                }
                adsManager?.addAdErrorListener { adErrorEvent ->
                    TestRigorLogger.logError("AdsManager error: ${adErrorEvent.error.message}", null)
                    onComplete(false)
                }
                adsManager?.init()
            }
            
            val adsRequest: AdsRequest = sdkFactory!!.createAdsRequest()
            adsRequest.adTagUrl = adTagUrl
            adsLoader?.requestAds(adsRequest)
            
        } catch (e: Exception) {
            TestRigorLogger.logError("Failed to request ads", e)
            onComplete(false)
        }
    }
    
    fun showVideoAd(activity: AppCompatActivity, playerView: PlayerView?, onComplete: (Boolean) -> Unit) {
        if (!isInitialized.get()) {
            TestRigorLogger.logWarning("IMA SDK not initialized - cannot show video ad")
            onComplete(false)
            return
        }
        
        pendingAdCallback = onComplete
        
        try {
            imaAdsLoader = ImaAdsLoader.Builder(activity).build()
            
            player = ExoPlayer.Builder(activity)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(activity)
                        .setLocalAdInsertionComponents({ imaAdsLoader }, playerView ?: PlayerView(activity))
                )
                .build()
            
            playerView?.player = player
            imaAdsLoader?.setPlayer(player)
            
            val contentUri = Uri.parse("https://storage.googleapis.com/gvabox/media/samples/stock.mp4")
            val adTagUri = Uri.parse(SAMPLE_AD_TAG_URL)
            val mediaItem = MediaItem.Builder()
                .setUri(contentUri)
                .setAdsConfiguration(MediaItem.AdsConfiguration.Builder(adTagUri).build())
                .build()
            
            player?.setMediaItem(mediaItem)
            player?.prepare()
            player?.playWhenReady = true
            
            player?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        TestRigorLogger.logAdEvent("Video ad playback ended")
                        pendingAdCallback?.invoke(true)
                        pendingAdCallback = null
                    }
                }
            })
            
            TestRigorLogger.logAdEvent("Video ad started")
            
        } catch (e: Exception) {
            TestRigorLogger.logError("Failed to show video ad", e)
            onComplete(false)
        }
    }
    
    fun showInterstitialAd(activity: AppCompatActivity, onComplete: (Boolean) -> Unit) {
        requestAds(activity) { success ->
            if (success) {
                adsManager?.start()
            }
            onComplete(success)
        }
    }
    
    fun isAdAvailable(): Boolean {
        return isInitialized.get() && isConsentGranted
    }
    
    fun isRewardedAdAvailable(): Boolean {
        return isAdAvailable()
    }
    
    fun isInterstitialAdAvailable(): Boolean {
        return isAdAvailable()
    }
    
    fun showRewardedAd(activity: AppCompatActivity, onRewarded: () -> Unit, onComplete: (Boolean) -> Unit) {
        requestAds(activity) { success ->
            if (success) {
                adsManager?.start()
                onRewarded()
            }
            onComplete(success)
        }
    }
    
    fun pause() {
        player?.pause()
        TestRigorLogger.logDebug("IMA Manager paused")
    }
    
    fun resume() {
        player?.play()
        TestRigorLogger.logDebug("IMA Manager resumed")
    }
    
    fun destroy() {
        try {
            adsManager?.destroy()
            adsManager = null
            
            imaAdsLoader?.release()
            imaAdsLoader = null
            
            player?.release()
            player = null
            
            isInitialized.set(false)
            TestRigorLogger.logAdEvent("IMA Manager destroyed")
        } catch (e: Exception) {
            TestRigorLogger.logError("Error destroying IMA Manager", e)
        }
    }
    
    fun getDiagnostics(): String {
        return """
            IMA SDK Diagnostics:
            - Initialized: ${isInitialized.get()}
            - Consent Granted: $isConsentGranted
            - AdsManager Active: ${adsManager != null}
            - Player Active: ${player != null}
        """.trimIndent()
    }
    
    fun forceShowInterstitial(activity: AppCompatActivity, onComplete: (Boolean) -> Unit) {
        showInterstitialAd(activity, onComplete)
    }
}
