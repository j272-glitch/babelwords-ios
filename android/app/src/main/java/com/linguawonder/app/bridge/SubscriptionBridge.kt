package com.linguawonder.app.bridge

import android.app.Activity
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.linguawonder.app.billing.BillingManager
import org.json.JSONObject

/**
 * Exposed to JavaScript as window.AndroidSubscriptionBridge
 *
 * Matches the contract in nativeBridge.ts:
 *   window.AndroidSubscriptionBridge.purchaseProduct(productId, type)
 *   window.AndroidSubscriptionBridge.subscribe(productId)
 *   window.AndroidSubscriptionBridge.restorePurchases()
 *
 * Results dispatched as CustomEvents on window:
 *   new CustomEvent('subscription_event', { detail: { event, productId, purchaseToken, ... } })
 */
class SubscriptionBridge(
    private val activity: Activity,
    private val webView: WebView,
) {
    private val TAG = "SubscriptionBridge"
    private val billingManager = BillingManager(activity) { event -> dispatchSubscriptionEvent(event) }

    @JavascriptInterface
    fun purchaseProduct(productId: String, type: String) {
        Log.d(TAG, "purchaseProduct: $productId type=$type")
        activity.runOnUiThread {
            billingManager.launchPurchase(activity, productId, type)
        }
    }

    @JavascriptInterface
    fun subscribe(productId: String) {
        purchaseProduct(productId, "subs")
    }

    @JavascriptInterface
    fun restorePurchases() {
        Log.d(TAG, "restorePurchases")
        activity.runOnUiThread { billingManager.restorePurchases() }
    }

    private fun dispatchSubscriptionEvent(detail: JSONObject) {
        val json = detail.toString().replace("'", "\\'")
        activity.runOnUiThread {
            webView.evaluateJavascript(
                """
                (function(){
                  var e = new CustomEvent('subscription_event', { detail: $json });
                  window.dispatchEvent(e);
                })();
                """.trimIndent(),
                null
            )
        }
    }

    fun destroy() = billingManager.destroy()
}
