package com.linguawonder.app.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.*
import org.json.JSONObject

/**
 * Wraps Google Play Billing Library v7.
 *
 * Product IDs (must be registered in Google Play Console):
 *   Consumables : boost_hints_small, boost_hints_medium, boost_hints_large
 *                 boost_foggust_small, boost_foggust_bundle
 *   Subscriptions: sub_scholar_monthly, sub_premium_monthly
 *
 * eventDispatcher(detail: JSONObject) fires a 'subscription_event' CustomEvent in JavaScript.
 *
 * subscription_event detail shapes:
 *   { event: "product_purchased",     productId, purchaseToken, transactionId }
 *   { event: "subscription_purchased", productId, purchaseToken }
 *   { event: "product_restored",      productId }
 *   { event: "purchase_error",        productId, message }
 */
class BillingManager(
    private val context: Context,
    private val eventDispatcher: (JSONObject) -> Unit,
) {
    private val TAG = "BillingManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                for (purchase in purchases) handlePurchase(purchase)
            } else {
                Log.w(TAG, "Purchase update error: ${billingResult.debugMessage}")
            }
        }
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    init { connect() }

    private fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "✅ Billing connected")
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing disconnected — retrying in 5s")
                scope.launch { delay(5_000); connect() }
            }
        })
    }

    fun launchPurchase(activity: Activity, productId: String, type: String) {
        if (!billingClient.isReady) {
            dispatchError(productId, "billing_not_ready")
            return
        }
        val productType = if (type == "subs") BillingClient.ProductType.SUBS
        else BillingClient.ProductType.INAPP

        scope.launch {
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(productId)
                            .setProductType(productType)
                            .build()
                    )
                )
                .build()

            val result = billingClient.queryProductDetails(params)
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK ||
                result.productDetailsList.isNullOrEmpty()
            ) {
                Log.w(TAG, "Product not found: $productId")
                dispatchError(productId, "product_not_found")
                return@launch
            }

            val productDetails = result.productDetailsList!!.first()
            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken

            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .apply { if (offerToken != null) setOfferToken(offerToken) }
                            .build()
                    )
                )
                .build()

            withContext(Dispatchers.Main) {
                billingClient.launchBillingFlow(activity, billingFlowParams)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        val productId = purchase.products.firstOrNull() ?: return
        val isSubscription = productId.startsWith("sub_")

        if (!purchase.isAcknowledged) {
            scope.launch {
                val ackResult = billingClient.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                )
                if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "✅ Purchase acknowledged: $productId")
                } else {
                    Log.w(TAG, "Acknowledge failed: ${ackResult.debugMessage}")
                }
            }
        }

        if (!isSubscription) {
            scope.launch {
                billingClient.consumeAsync(
                    ConsumeParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                ) { result, token ->
                    Log.d(TAG, "Consume result: ${result.responseCode} token=$token")
                }
            }
        }

        scope.launch { validateWithServer(productId, purchase.purchaseToken) }

        val eventName = if (isSubscription) "subscription_purchased" else "product_purchased"
        eventDispatcher(JSONObject().apply {
            put("event", eventName)
            put("productId", productId)
            put("purchaseToken", purchase.purchaseToken)
            put("transactionId", purchase.orderId ?: "")
        })
    }

    private suspend fun validateWithServer(productId: String, purchaseToken: String) {
        try {
            val url = java.net.URL("https://your-app.replit.app/api/iap/google")
            val body = """{"productId":"$productId","purchaseToken":"$purchaseToken"}"""
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            Log.d(TAG, "Server validation: $productId → HTTP $code")
            conn.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Server validation failed: ${e.message}")
        }
    }

    fun restorePurchases() {
        if (!billingClient.isReady) return
        scope.launch {
            val inappResult = billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
            val subsResult = billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )
            val all = (inappResult.purchasesList + subsResult.purchasesList)
            for (purchase in all) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    val productId = purchase.products.firstOrNull() ?: continue
                    eventDispatcher(JSONObject().apply {
                        put("event", "product_restored")
                        put("productId", productId)
                    })
                }
            }
        }
    }

    private fun dispatchError(productId: String, message: String) {
        eventDispatcher(JSONObject().apply {
            put("event", "purchase_error")
            put("productId", productId)
            put("message", message)
        })
    }

    fun destroy() {
        scope.cancel()
        billingClient.endConnection()
    }
}
