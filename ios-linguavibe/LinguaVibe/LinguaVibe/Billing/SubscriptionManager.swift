import Foundation
import StoreKit

@available(iOS 15.0, *)
@MainActor
class SubscriptionManager: ObservableObject {
    
    static let shared = SubscriptionManager()
    
    static let premiumMonthlySKU = "premium_monthly"
    static let premiumYearlySKU = "premium_yearly"
    
    @Published private(set) var isPremium: Bool = false
    @Published private(set) var subscriptionStatus: String = "free"
    @Published private(set) var products: [Product] = []
    
    private var updateListenerTask: Task<Void, Error>?
    
    // Callback protocol for bridge communication
    protocol SubscriptionCallback: AnyObject {
        func onSubscriptionPurchased(transactionId: String, productId: String)
        func onSubscriptionRestored(transactionId: String, productId: String)
        func onSubscriptionError(errorCode: Int, message: String)
        func onPremiumStatusChanged(isPremium: Bool)
    }
    
    weak var callback: SubscriptionCallback?
    
    private init() {}
    
    func initialize() async {
        print("[SubscriptionManager] Initializing StoreKit")
        
        // Start listening for transaction updates
        updateListenerTask = listenForTransactions()
        
        // Load products
        await loadProducts()
        
        // Check existing subscriptions
        await checkExistingSubscriptions()
    }
    
    private func listenForTransactions() -> Task<Void, Error> {
        return Task.detached {
            for await result in Transaction.updates {
                do {
                    let transaction = try await self.checkVerified(result)
                    await self.handleTransaction(transaction)
                    await transaction.finish()
                } catch {
                    print("[SubscriptionManager] Transaction verification failed: \(error)")
                }
            }
        }
    }
    
    private func loadProducts() async {
        do {
            let productIds = [Self.premiumMonthlySKU, Self.premiumYearlySKU]
            products = try await Product.products(for: productIds)
            print("[SubscriptionManager] Loaded \(products.count) products")
        } catch {
            print("[SubscriptionManager] Failed to load products: \(error)")
        }
    }
    
    func subscribe(productId: String = premiumMonthlySKU) async {
        guard let product = products.first(where: { $0.id == productId }) else {
            print("[SubscriptionManager] Product not found: \(productId)")
            callback?.onSubscriptionError(errorCode: -1, message: "Product not found")
            return
        }
        
        do {
            let result = try await product.purchase()
            
            switch result {
            case .success(let verification):
                let transaction = try checkVerified(verification)
                await handleTransaction(transaction)
                await transaction.finish()
                
            case .userCancelled:
                print("[SubscriptionManager] User cancelled purchase")
                
            case .pending:
                print("[SubscriptionManager] Purchase pending")
                
            @unknown default:
                break
            }
        } catch {
            print("[SubscriptionManager] Purchase failed: \(error)")
            callback?.onSubscriptionError(errorCode: -1, message: error.localizedDescription)
        }
    }
    
    func restorePurchases() async {
        print("[SubscriptionManager] Restoring purchases")
        await checkExistingSubscriptions()
    }
    
    private func checkExistingSubscriptions() async {
        var hasActiveSubscription = false
        
        for await result in Transaction.currentEntitlements {
            do {
                let transaction = try checkVerified(result)
                
                if transaction.productType == .autoRenewable {
                    hasActiveSubscription = true
                    isPremium = true
                    subscriptionStatus = "active"
                    
                    callback?.onPremiumStatusChanged(isPremium: true)
                    callback?.onSubscriptionRestored(
                        transactionId: String(transaction.id),
                        productId: transaction.productID
                    )
                    
                    print("[SubscriptionManager] Active subscription found: \(transaction.productID)")
                }
            } catch {
                print("[SubscriptionManager] Failed to verify transaction: \(error)")
            }
        }
        
        if !hasActiveSubscription {
            isPremium = false
            subscriptionStatus = "free"
            callback?.onPremiumStatusChanged(isPremium: false)
            print("[SubscriptionManager] No active subscription")
        }
    }
    
    private func handleTransaction(_ transaction: Transaction) async {
        if transaction.productType == .autoRenewable {
            isPremium = true
            subscriptionStatus = "active"
            
            callback?.onPremiumStatusChanged(isPremium: true)
            callback?.onSubscriptionPurchased(
                transactionId: String(transaction.id),
                productId: transaction.productID
            )
            
            print("[SubscriptionManager] Subscription purchased: \(transaction.productID)")
        }
    }
    
    private func checkVerified<T>(_ result: VerificationResult<T>) throws -> T {
        switch result {
        case .unverified:
            throw StoreError.failedVerification
        case .verified(let safe):
            return safe
        }
    }
    
    func checkSubscription() -> Bool {
        return isPremium
    }
    
    func getSubscriptionStatus() -> String {
        return subscriptionStatus
    }
    
    func destroy() {
        updateListenerTask?.cancel()
        print("[SubscriptionManager] Destroyed")
    }
    
    enum StoreError: Error {
        case failedVerification
    }
}

// Fallback for iOS < 15
class SubscriptionManagerLegacy: NSObject, SKProductsRequestDelegate, SKPaymentTransactionObserver {
    
    static let shared = SubscriptionManagerLegacy()
    
    static let premiumMonthlySKU = "premium_monthly"
    static let premiumYearlySKU = "premium_yearly"
    
    private(set) var isPremium: Bool = false
    private(set) var subscriptionStatus: String = "free"
    private var products: [SKProduct] = []
    
    protocol SubscriptionCallback: AnyObject {
        func onSubscriptionPurchased(transactionId: String, productId: String)
        func onSubscriptionRestored(transactionId: String, productId: String)
        func onSubscriptionError(errorCode: Int, message: String)
        func onPremiumStatusChanged(isPremium: Bool)
    }
    
    weak var callback: SubscriptionCallback?
    
    private override init() {
        super.init()
    }
    
    func initialize() {
        print("[SubscriptionManagerLegacy] Initializing StoreKit")
        SKPaymentQueue.default().add(self)
        loadProducts()
    }
    
    private func loadProducts() {
        let productIds = Set([Self.premiumMonthlySKU, Self.premiumYearlySKU])
        let request = SKProductsRequest(productIdentifiers: productIds)
        request.delegate = self
        request.start()
    }
    
    func productsRequest(_ request: SKProductsRequest, didReceive response: SKProductsResponse) {
        products = response.products
        print("[SubscriptionManagerLegacy] Loaded \(products.count) products")
    }
    
    func subscribe(productId: String = premiumMonthlySKU) {
        guard let product = products.first(where: { $0.productIdentifier == productId }) else {
            print("[SubscriptionManagerLegacy] Product not found: \(productId)")
            callback?.onSubscriptionError(errorCode: -1, message: "Product not found")
            return
        }
        
        let payment = SKPayment(product: product)
        SKPaymentQueue.default().add(payment)
    }
    
    func restorePurchases() {
        print("[SubscriptionManagerLegacy] Restoring purchases")
        SKPaymentQueue.default().restoreCompletedTransactions()
    }
    
    func paymentQueue(_ queue: SKPaymentQueue, updatedTransactions transactions: [SKPaymentTransaction]) {
        for transaction in transactions {
            switch transaction.transactionState {
            case .purchased:
                isPremium = true
                subscriptionStatus = "active"
                callback?.onPremiumStatusChanged(isPremium: true)
                callback?.onSubscriptionPurchased(
                    transactionId: transaction.transactionIdentifier ?? "",
                    productId: transaction.payment.productIdentifier
                )
                queue.finishTransaction(transaction)
                
            case .restored:
                isPremium = true
                subscriptionStatus = "active"
                callback?.onPremiumStatusChanged(isPremium: true)
                callback?.onSubscriptionRestored(
                    transactionId: transaction.transactionIdentifier ?? "",
                    productId: transaction.payment.productIdentifier
                )
                queue.finishTransaction(transaction)
                
            case .failed:
                if let error = transaction.error as? SKError, error.code != .paymentCancelled {
                    callback?.onSubscriptionError(errorCode: error.errorCode, message: error.localizedDescription)
                }
                queue.finishTransaction(transaction)
                
            case .deferred, .purchasing:
                break
                
            @unknown default:
                break
            }
        }
    }
    
    func checkSubscription() -> Bool {
        return isPremium
    }
    
    func getSubscriptionStatus() -> String {
        return subscriptionStatus
    }
    
    func destroy() {
        SKPaymentQueue.default().remove(self)
        print("[SubscriptionManagerLegacy] Destroyed")
    }
}
