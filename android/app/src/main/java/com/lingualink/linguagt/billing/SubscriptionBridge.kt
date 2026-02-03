package com.lingualink.linguagt.billing

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.lingualink.linguagt.TestRigorLogger
import org.json.JSONObject

class SubscriptionBridge(
    private val subscriptionManager: SubscriptionManager,
    private val webView: WebView
) : SubscriptionManager.SubscriptionCallback {

    companion object {
        const val BRIDGE_NAME = "AndroidSubscriptionBridge"
        private const val TAG = "SubscriptionBridge"
    }

    init {
        subscriptionManager.callback = this
        TestRigorLogger.logAdEvent("SubscriptionBridge: Initialized")
    }

    @JavascriptInterface
    fun subscribe(productId: String) {
        TestRigorLogger.logAdEvent("SubscriptionBridge: subscribe($productId) called from JS")
        webView.post {
            subscriptionManager.subscribe(productId)
        }
    }

    @JavascriptInterface
    fun restorePurchases() {
        TestRigorLogger.logAdEvent("SubscriptionBridge: restorePurchases() called from JS")
        webView.post {
            subscriptionManager.restorePurchases()
        }
    }

    @JavascriptInterface
    fun checkSubscription(): Boolean {
        val isPremium = subscriptionManager.checkSubscription()
        TestRigorLogger.logAdEvent("SubscriptionBridge: checkSubscription() = $isPremium")
        return isPremium
    }

    @JavascriptInterface
    fun getSubscriptionStatus(): String {
        val status = subscriptionManager.getSubscriptionStatus()
        TestRigorLogger.logAdEvent("SubscriptionBridge: getSubscriptionStatus() = $status")
        return status
    }

    override fun onSubscriptionPurchased(purchaseToken: String, productId: String) {
        TestRigorLogger.logAdEvent("SubscriptionBridge: Subscription purchased - $productId")
        val json = JSONObject().apply {
            put("event", "subscription_purchased")
            put("purchaseToken", purchaseToken)
            put("productId", productId)
            put("isPremium", true)
        }
        notifyWebView(json)
    }

    override fun onSubscriptionRestored(purchaseToken: String, productId: String) {
        TestRigorLogger.logAdEvent("SubscriptionBridge: Subscription restored - $productId")
        val json = JSONObject().apply {
            put("event", "subscription_restored")
            put("purchaseToken", purchaseToken)
            put("productId", productId)
            put("isPremium", true)
        }
        notifyWebView(json)
    }

    override fun onSubscriptionError(errorCode: Int, message: String) {
        TestRigorLogger.logError("SubscriptionBridge: Error $errorCode - $message")
        val json = JSONObject().apply {
            put("event", "subscription_error")
            put("errorCode", errorCode)
            put("message", message)
        }
        notifyWebView(json)
    }

    override fun onPremiumStatusChanged(isPremium: Boolean) {
        TestRigorLogger.logAdEvent("SubscriptionBridge: Premium status changed to $isPremium")
        val json = JSONObject().apply {
            put("event", "premium_status_changed")
            put("isPremium", isPremium)
        }
        notifyWebView(json)
    }

    private fun notifyWebView(json: JSONObject) {
        val script = """
            (function() {
                if (window.onSubscriptionEvent) {
                    window.onSubscriptionEvent($json);
                }
                window.dispatchEvent(new CustomEvent('subscription_event', { detail: $json }));
            })();
        """.trimIndent()

        webView.post {
            webView.evaluateJavascript(script, null)
        }
    }
}
