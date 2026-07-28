import Foundation
import StoreKit

/// StoreKit 2-based billing manager. Replaces Android `BillingManager`.
@MainActor
final class BillingManager: NSObject {
    private let TAG = "BillingManager"

    var eventDispatcher: (([String: Any]) -> Void)?

    /// Purchase a product or subscription by its App Store product ID.
    func purchaseProduct(_ productId: String) async {
        do {
            let products = try await Product.products(for: [productId])
            guard let product = products.first else {
                dispatchError(productId: productId, message: "product_not_found")
                return
            }

            let result = try await product.purchase()
            switch result {
            case .success(let verification):
                await handleVerification(verification, productId: productId)
            case .userCancelled:
                dispatchError(productId: productId, message: "user_cancelled")
            case .pending:
                dispatchError(productId: productId, message: "purchase_pending")
            @unknown default:
                dispatchError(productId: productId, message: "unknown_result")
            }
        } catch {
            dispatchError(productId: productId, message: error.localizedDescription)
        }
    }

    /// Restore previous purchases.
    func restorePurchases() async {
        do {
            try await AppStore.sync()
            // Query current entitlements and consumables.
            for await result in Transaction.currentEntitlements {
                if case .verified(let transaction) = result {
                    let productId = transaction.productID
                    eventDispatcher?([
                        "event": "product_restored",
                        "productId": productId
                    ])
                    AnalyticsManager.logBillingEvent(event: "restore_purchase", productId: productId)
                }
            }
        } catch {
            print("[\(TAG)] Restore failed: \(error.localizedDescription)")
        }
    }

    // MARK: - Private

    private func handleVerification(_ verification: VerificationResult<Transaction>, productId: String) async {
        switch verification {
        case .verified(let transaction):
            guard await validateWithServer(
                productId: productId,
                transactionId: transaction.id,
                signedTransaction: transaction.jwsRepresentation
            ) else {
                dispatchError(productId: productId, message: "server_validation_failed")
                return
            }

            await transaction.finish()
            let isSubscription = productId.hasPrefix("sub_")
            let eventName = isSubscription ? "subscription_purchased" : "product_purchased"
            eventDispatcher?([
                "event": eventName,
                "productId": productId,
                "purchaseToken": transaction.id,
                "transactionId": String(transaction.id)
            ])
            AnalyticsManager.logBillingEvent(event: eventName, productId: productId)
        case .unverified(_, let error):
            print("[\(TAG)] Unverified transaction: \(error.localizedDescription)")
            dispatchError(productId: productId, message: "verification_failed")
        }
    }

    private func validateWithServer(
        productId: String,
        transactionId: UInt64,
        signedTransaction: String
    ) async -> Bool {
        guard let url = URL(string: "https://linguagt.com/api/iap/apple/verify") else { return false }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let body = [
            "productId": productId,
            "transactionId": String(transactionId),
            "signedTransaction": signedTransaction
        ]
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: body, options: [])
            let (_, response) = try await URLSession.shared.data(for: request)
            let code = (response as? HTTPURLResponse)?.statusCode ?? 0
            print("[\(TAG)] Server validation: \(productId) → HTTP \(code)")
            return (200..<300).contains(code)
        } catch {
            print("[\(TAG)] Server validation failed: \(error.localizedDescription)")
            return false
        }
    }

    private func dispatchError(productId: String, message: String) {
        eventDispatcher?([
            "event": "purchase_error",
            "productId": productId,
            "message": message
        ])
    }
}
