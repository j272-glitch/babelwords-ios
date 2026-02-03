package com.lingualink.linguagt.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SubscriptionManager(
    private val context: Context,
    private val activity: Activity
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "SubscriptionManager"
        const val PREMIUM_MONTHLY_SKU = "premium_monthly"
        const val PREMIUM_YEARLY_SKU = "premium_yearly"
    }

    private var billingClient: BillingClient? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium

    private val _subscriptionStatus = MutableStateFlow("free")
    val subscriptionStatus: StateFlow<String> = _subscriptionStatus

    interface SubscriptionCallback {
        fun onSubscriptionPurchased(purchaseToken: String, productId: String)
        fun onSubscriptionRestored(purchaseToken: String, productId: String)
        fun onSubscriptionError(errorCode: Int, message: String)
        fun onPremiumStatusChanged(isPremium: Boolean)
    }

    var callback: SubscriptionCallback? = null

    fun initialize() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client connected")
                    queryExistingPurchases()
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
            }
        })
    }

    fun subscribe(productId: String = PREMIUM_MONTHLY_SKU) {
        val billingClient = billingClient ?: return

        scope.launch {
            try {
                val productList = listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )

                val params = QueryProductDetailsParams.newBuilder()
                    .setProductList(productList)
                    .build()

                billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                        val productDetails = productDetailsList[0]
                        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken

                        if (offerToken != null) {
                            val productDetailsParamsList = listOf(
                                BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(productDetails)
                                    .setOfferToken(offerToken)
                                    .build()
                            )

                            val billingFlowParams = BillingFlowParams.newBuilder()
                                .setProductDetailsParamsList(productDetailsParamsList)
                                .build()

                            billingClient.launchBillingFlow(activity, billingFlowParams)
                        } else {
                            callback?.onSubscriptionError(-1, "No offer token available")
                        }
                    } else {
                        callback?.onSubscriptionError(billingResult.responseCode, billingResult.debugMessage ?: "Unknown error")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Subscribe error", e)
                callback?.onSubscriptionError(-1, e.message ?: "Unknown error")
            }
        }
    }

    fun restorePurchases() {
        queryExistingPurchases()
    }

    private fun queryExistingPurchases() {
        val billingClient = billingClient ?: return

        scope.launch {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()

            billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val activePurchase = purchasesList.find { 
                        it.purchaseState == Purchase.PurchaseState.PURCHASED 
                    }

                    if (activePurchase != null) {
                        _isPremium.value = true
                        _subscriptionStatus.value = "active"
                        callback?.onPremiumStatusChanged(true)
                        callback?.onSubscriptionRestored(
                            activePurchase.purchaseToken,
                            activePurchase.products.firstOrNull() ?: ""
                        )

                        if (!activePurchase.isAcknowledged) {
                            acknowledgePurchase(activePurchase)
                        }
                    } else {
                        _isPremium.value = false
                        _subscriptionStatus.value = "free"
                        callback?.onPremiumStatusChanged(false)
                    }
                }
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val billingClient = billingClient ?: return

        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged")
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        _isPremium.value = true
                        _subscriptionStatus.value = "active"
                        callback?.onPremiumStatusChanged(true)
                        callback?.onSubscriptionPurchased(
                            purchase.purchaseToken,
                            purchase.products.firstOrNull() ?: ""
                        )

                        if (!purchase.isAcknowledged) {
                            acknowledgePurchase(purchase)
                        }
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "User canceled purchase")
            }
            else -> {
                callback?.onSubscriptionError(billingResult.responseCode, billingResult.debugMessage ?: "")
            }
        }
    }

    fun checkSubscription(): Boolean = _isPremium.value

    fun getSubscriptionStatus(): String = _subscriptionStatus.value

    fun destroy() {
        scope.cancel()
        billingClient?.endConnection()
    }
}
