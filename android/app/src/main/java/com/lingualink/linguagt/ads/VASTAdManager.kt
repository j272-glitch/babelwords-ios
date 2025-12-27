package com.lingualink.linguagt.ads

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.lingualink.linguagt.TestRigorLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class VASTAdManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "VASTAdManager"
        
        private const val DEFAULT_VAST_TAG_URL = "https://pubads.g.doubleclick.net/gampad/ads?" +
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
        private var instance: VASTAdManager? = null
        
        fun getInstance(context: Context): VASTAdManager {
            return instance ?: synchronized(this) {
                instance ?: VASTAdManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
    
    val isInitialized = AtomicBoolean(false)
    private val isAdShowing = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    
    private val vastParser = VASTParser()
    
    private var player: ExoPlayer? = null
    private var adDialog: Dialog? = null
    private var currentAdData: VASTAdData? = null
    private var hasEarnedReward = false
    private var quartilesFired = mutableSetOf<String>()
    private var rewardCallbackFired = false
    
    fun initialize(activity: Activity, onComplete: (Boolean) -> Unit) {
        if (isInitialized.get()) {
            TestRigorLogger.logAdEvent("VASTAdManager already initialized")
            onComplete(true)
            return
        }
        
        TestRigorLogger.logAdEvent("Initializing VASTAdManager")
        isInitialized.set(true)
        onComplete(true)
    }
    
    fun showInterstitialAd(activity: Activity, vastUrl: String? = null, onComplete: (Boolean) -> Unit) {
        showAd(activity, vastUrl = vastUrl, isRewarded = false, onRewarded = null, onComplete = onComplete)
    }
    
    fun showRewardedAd(activity: Activity, vastUrl: String? = null, onRewarded: () -> Unit, onComplete: (Boolean) -> Unit) {
        showAd(activity, vastUrl = vastUrl, isRewarded = true, onRewarded = onRewarded, onComplete = onComplete)
    }
    
    private fun showAd(
        activity: Activity,
        vastUrl: String? = null,
        isRewarded: Boolean,
        onRewarded: (() -> Unit)?,
        onComplete: (Boolean) -> Unit
    ) {
        if (!isInitialized.get()) {
            TestRigorLogger.logWarning("VASTAdManager not initialized")
            onComplete(false)
            return
        }
        
        if (activity.isFinishing || activity.isDestroyed) {
            TestRigorLogger.logWarning("Cannot show ad - activity invalid")
            onComplete(false)
            return
        }
        
        if (!isAdShowing.compareAndSet(false, true)) {
            TestRigorLogger.logWarning("Ad already showing - ignoring request")
            onComplete(false)
            return
        }
        
        TestRigorLogger.logAdEvent("Fetching VAST ad (rewarded=$isRewarded)")
        hasEarnedReward = false
        rewardCallbackFired = false
        quartilesFired.clear()
        
        val effectiveUrl = if (!vastUrl.isNullOrBlank()) vastUrl else DEFAULT_VAST_TAG_URL
        TestRigorLogger.logAdEvent("Using VAST URL: ${effectiveUrl.take(50)}...")
        
        scope.launch {
            try {
                val vastXml = fetchVastXml(effectiveUrl + System.currentTimeMillis())
                if (vastXml == null) {
                    TestRigorLogger.logWarning("Failed to fetch VAST XML")
                    onComplete(false)
                    return@launch
                }
                
                val adData = vastParser.parse(vastXml)
                if (adData?.mediaUrl == null) {
                    TestRigorLogger.logWarning("Failed to parse VAST or no media URL")
                    fireErrorTracking(adData?.errorUrls)
                    onComplete(false)
                    return@launch
                }
                
                currentAdData = adData
                
                withContext(Dispatchers.Main) {
                    if (activity.isFinishing || activity.isDestroyed) {
                        onComplete(false)
                        return@withContext
                    }
                    
                    fireImpressionTracking(adData.impressionUrls)
                    showAdDialog(activity, adData, isRewarded, onRewarded, onComplete)
                }
                
            } catch (e: Exception) {
                TestRigorLogger.logError("Error showing VAST ad", e)
                onComplete(false)
            }
        }
    }
    
    private suspend fun fetchVastXml(url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .build()
                
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    TestRigorLogger.logWarning("VAST fetch failed: ${response.code}")
                    null
                }
            } catch (e: Exception) {
                TestRigorLogger.logError("VAST fetch error", e)
                null
            }
        }
    }
    
    private fun showAdDialog(
        activity: Activity,
        adData: VASTAdData,
        isRewarded: Boolean,
        onRewarded: (() -> Unit)?,
        onComplete: (Boolean) -> Unit
    ) {
        try {
            adDialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
                window?.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                setCancelable(false)
                setCanceledOnTouchOutside(false)
            }
            
            val container = FrameLayout(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.BLACK)
            }
            
            val playerView = PlayerView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                useController = false
            }
            container.addView(playerView)
            
            val loadingIndicator = ProgressBar(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            }
            container.addView(loadingIndicator)
            
            val skipButton = TextView(activity).apply {
                text = if (isRewarded) "Watch to earn reward" else "Skip in 5s"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#80000000"))
                setPadding(32, 16, 32, 16)
                textSize = 14f
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END
                ).apply {
                    setMargins(0, 48, 24, 0)
                }
                isEnabled = false
                alpha = 0.7f
            }
            container.addView(skipButton)
            
            player = ExoPlayer.Builder(activity).build().apply {
                playWhenReady = true
                
                addListener(object : Player.Listener {
                    private var skipCountdown = if (isRewarded) -1 else 5
                    private val countdownHandler = Handler(Looper.getMainLooper())
                    private var countdownRunnable: Runnable? = null
                    
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                loadingIndicator.visibility = android.view.View.GONE
                                TestRigorLogger.logAdEvent("Ad video ready to play")
                                
                                if (!isRewarded && skipCountdown > 0) {
                                    startSkipCountdown()
                                }
                            }
                            Player.STATE_ENDED -> {
                                TestRigorLogger.logAdEvent("Ad video playback completed")
                                fireTrackingEvent("complete")
                                
                                if (isRewarded && !rewardCallbackFired) {
                                    rewardCallbackFired = true
                                    hasEarnedReward = true
                                    onRewarded?.invoke()
                                }
                                
                                countdownRunnable?.let { countdownHandler.removeCallbacks(it) }
                                closeAd(true, onComplete)
                            }
                            Player.STATE_BUFFERING -> {
                                loadingIndicator.visibility = android.view.View.VISIBLE
                            }
                            Player.STATE_IDLE -> {}
                        }
                    }
                    
                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int
                    ) {
                        checkQuartiles()
                    }
                    
                    private fun startSkipCountdown() {
                        countdownRunnable = object : Runnable {
                            override fun run() {
                                if (skipCountdown > 1) {
                                    skipCountdown--
                                    skipButton.text = "Skip in ${skipCountdown}s"
                                    countdownHandler.postDelayed(this, 1000)
                                } else {
                                    skipButton.text = "Skip Ad"
                                    skipButton.isEnabled = true
                                    skipButton.alpha = 1f
                                    skipButton.setOnClickListener {
                                        TestRigorLogger.logAdEvent("User skipped ad")
                                        fireTrackingEvent("skip")
                                        countdownRunnable?.let { r -> countdownHandler.removeCallbacks(r) }
                                        closeAd(true, onComplete)
                                    }
                                }
                            }
                        }
                        countdownHandler.postDelayed(countdownRunnable!!, 1000)
                    }
                    
                    private fun checkQuartiles() {
                        val player = this@apply
                        val duration = player.duration
                        val position = player.currentPosition
                        
                        if (duration <= 0) return
                        
                        val progress = position.toFloat() / duration.toFloat()
                        
                        if (progress >= 0.0f && !quartilesFired.contains("start")) {
                            quartilesFired.add("start")
                            fireTrackingEvent("start")
                        }
                        if (progress >= 0.25f && !quartilesFired.contains("firstQuartile")) {
                            quartilesFired.add("firstQuartile")
                            fireTrackingEvent("firstQuartile")
                        }
                        if (progress >= 0.5f && !quartilesFired.contains("midpoint")) {
                            quartilesFired.add("midpoint")
                            fireTrackingEvent("midpoint")
                        }
                        if (progress >= 0.75f && !quartilesFired.contains("thirdQuartile")) {
                            quartilesFired.add("thirdQuartile")
                            fireTrackingEvent("thirdQuartile")
                        }
                    }
                })
            }
            
            playerView.player = player
            
            val mediaItem = MediaItem.fromUri(Uri.parse(adData.mediaUrl))
            player?.setMediaItem(mediaItem)
            player?.prepare()
            
            val progressChecker = object : Runnable {
                override fun run() {
                    player?.let { p ->
                        val duration = p.duration
                        val position = p.currentPosition
                        
                        if (duration > 0) {
                            val progress = position.toFloat() / duration.toFloat()
                            
                            if (progress >= 0.0f && !quartilesFired.contains("start")) {
                                quartilesFired.add("start")
                                fireTrackingEvent("start")
                            }
                            if (progress >= 0.25f && !quartilesFired.contains("firstQuartile")) {
                                quartilesFired.add("firstQuartile")
                                fireTrackingEvent("firstQuartile")
                            }
                            if (progress >= 0.5f && !quartilesFired.contains("midpoint")) {
                                quartilesFired.add("midpoint")
                                fireTrackingEvent("midpoint")
                            }
                            if (progress >= 0.75f && !quartilesFired.contains("thirdQuartile")) {
                                quartilesFired.add("thirdQuartile")
                                fireTrackingEvent("thirdQuartile")
                            }
                        }
                        
                        if (p.playbackState != Player.STATE_ENDED) {
                            mainHandler.postDelayed(this, 250)
                        }
                    }
                }
            }
            mainHandler.post(progressChecker)
            
            adDialog?.setContentView(container)
            adDialog?.setOnDismissListener {
                mainHandler.removeCallbacksAndMessages(null)
            }
            adDialog?.show()
            
            TestRigorLogger.logAdEvent("Ad dialog shown")
            
        } catch (e: Exception) {
            TestRigorLogger.logError("Error showing ad dialog", e)
            closeAd(false, onComplete)
        }
    }
    
    private fun closeAd(success: Boolean, onComplete: (Boolean) -> Unit) {
        mainHandler.post {
            try {
                player?.release()
                player = null
                
                adDialog?.dismiss()
                adDialog = null
                
                currentAdData = null
                isAdShowing.set(false)
                
                TestRigorLogger.logAdEvent("Ad closed (success=$success)")
                onComplete(success)
            } catch (e: Exception) {
                TestRigorLogger.logError("Error closing ad", e)
                isAdShowing.set(false)
                onComplete(false)
            }
        }
    }
    
    private fun fireImpressionTracking(urls: List<String>) {
        urls.forEach { url ->
            firePixel(url, "impression")
        }
    }
    
    private fun fireErrorTracking(urls: List<String>?) {
        urls?.forEach { url ->
            firePixel(url, "error")
        }
    }
    
    private fun fireTrackingEvent(event: String) {
        currentAdData?.trackingEvents?.get(event)?.forEach { url ->
            firePixel(url, event)
        }
    }
    
    private fun firePixel(url: String, eventName: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .build()
                
                httpClient.newCall(request).execute().close()
                TestRigorLogger.logAdEvent("Fired tracking: $eventName")
            } catch (e: Exception) {
                TestRigorLogger.logWarning("Failed to fire tracking $eventName: ${e.message}")
            }
        }
    }
    
    fun isAdAvailable(): Boolean {
        return isInitialized.get()
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
        try {
            mainHandler.removeCallbacksAndMessages(null)
            player?.release()
            player = null
            adDialog?.dismiss()
            adDialog = null
            currentAdData = null
            isInitialized.set(false)
            isAdShowing.set(false)
            TestRigorLogger.logAdEvent("VASTAdManager destroyed")
        } catch (e: Exception) {
            TestRigorLogger.logError("Error destroying VASTAdManager", e)
        }
    }
    
    fun getDiagnostics(): String {
        return """
            VAST Ad Status:
            - Initialized: ${isInitialized.get()}
            - Player Active: ${player != null}
            - Dialog Showing: ${adDialog?.isShowing ?: false}
        """.trimIndent()
    }
    
    fun forceShowInterstitial(activity: Activity, onComplete: (Boolean) -> Unit) {
        showInterstitialAd(activity, onComplete)
    }
}
