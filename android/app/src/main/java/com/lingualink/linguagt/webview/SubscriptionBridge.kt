package com.lingualink.linguagt.webview

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.lingualink.linguagt.billing.SubscriptionManager
import org.json.JSONObject

class SubscriptionBridge(
    private val subscriptionManager: SubscriptionManager,
    private val webView: WebView
) : SubscriptionManager.SubscriptionCallback {

    companion object {
        const val BRIDGE_NAME = "AndroidSubscriptionBridge"
    }

    init {
        subscriptionManager.callback = this
    }

    @JavascriptInterface
    fun subscribe(productId: String) {
        webView.post {
            subscriptionManager.subscribe(productId)
        }
    }

    @JavascriptInterface
    fun restorePurchases() {
        webView.post {
            subscriptionManager.restorePurchases()
        }
    }

    @JavascriptInterface
    fun checkSubscription(): Boolean {
        return subscriptionManager.checkSubscription()
    }

    @JavascriptInterface
    fun getSubscriptionStatus(): String {
        return subscriptionManager.getSubscriptionStatus()
    }

    override fun onSubscriptionPurchased(purchaseToken: String, productId: String) {
        val json = JSONObject().apply {
            put("event", "subscription_purchased")
            put("purchaseToken", purchaseToken)
            put("productId", productId)
            put("isPremium", true)
        }
        notifyWebView(json)
    }

    override fun onSubscriptionRestored(purchaseToken: String, productId: String) {
        val json = JSONObject().apply {
            put("event", "subscription_restored")
            put("purchaseToken", purchaseToken)
            put("productId", productId)
            put("isPremium", true)
        }
        notifyWebView(json)
    }

    override fun onSubscriptionError(errorCode: Int, message: String) {
        val json = JSONObject().apply {
            put("event", "subscription_error")
            put("errorCode", errorCode)
            put("message", message)
        }
        notifyWebView(json)
    }

    override fun onPremiumStatusChanged(isPremium: Boolean) {
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
