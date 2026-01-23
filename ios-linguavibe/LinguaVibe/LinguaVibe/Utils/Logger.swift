import Foundation
import os.log

struct Logger {
    
    private static let subsystem = "com.lingualink.linguavibe"
    private static let generalLog = OSLog(subsystem: subsystem, category: "general")
    private static let adLog = OSLog(subsystem: subsystem, category: "ads")
    private static let webViewLog = OSLog(subsystem: subsystem, category: "webview")
    private static let permissionLog = OSLog(subsystem: subsystem, category: "permission")
    
    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss.SSS"
        return formatter
    }()
    
    // MARK: - General Logging
    
    static func log(_ message: String, level: String = "D") {
        let timestamp = dateFormatter.string(from: Date())
        let fullMessage = "[\(timestamp)] \(message)"
        
        switch level {
        case "E":
            os_log(.error, log: generalLog, "%{public}@", fullMessage)
        case "W":
            os_log(.info, log: generalLog, "%{public}@", fullMessage)
        default:
            os_log(.debug, log: generalLog, "%{public}@", fullMessage)
        }
        
        #if DEBUG
        print("[\(level)] \(fullMessage)")
        #endif
    }
    
    // MARK: - Ad Logging
    
    static func logAdEvent(_ message: String) {
        let timestamp = dateFormatter.string(from: Date())
        let fullMessage = "[\(timestamp)] [AD] \(message)"
        
        os_log(.info, log: adLog, "%{public}@", fullMessage)
        
        #if DEBUG
        print(fullMessage)
        #endif
    }
    
    // MARK: - WebView Logging
    
    static func logWebView(_ message: String, url: String? = nil) {
        let timestamp = dateFormatter.string(from: Date())
        var fullMessage = "[\(timestamp)] [WEBVIEW] \(message)"
        if let url = url {
            fullMessage += " - \(url)"
        }
        
        os_log(.debug, log: webViewLog, "%{public}@", fullMessage)
        
        #if DEBUG
        print(fullMessage)
        #endif
    }
    
    // MARK: - Permission Logging
    
    static func logPermission(_ permission: String, granted: Bool, wasRequested: Bool = false, note: String? = nil) {
        let timestamp = dateFormatter.string(from: Date())
        let status = granted ? "GRANTED" : "DENIED"
        let requestNote = wasRequested ? " (requested)" : ""
        var fullMessage = "[\(timestamp)] [PERMISSION] \(permission): \(status)\(requestNote)"
        if let note = note {
            fullMessage += " - \(note)"
        }
        
        os_log(.info, log: permissionLog, "%{public}@", fullMessage)
        
        #if DEBUG
        print(fullMessage)
        #endif
    }
    
    // MARK: - Milestone Logging
    
    static func logMilestone(_ message: String) {
        let timestamp = dateFormatter.string(from: Date())
        let fullMessage = "[\(timestamp)] ★ MILESTONE: \(message)"
        
        os_log(.info, log: generalLog, "%{public}@", fullMessage)
        
        #if DEBUG
        print(fullMessage)
        #endif
    }
    
    // MARK: - Error Logging
    
    static func logError(_ message: String, error: Error? = nil) {
        let timestamp = dateFormatter.string(from: Date())
        var fullMessage = "[\(timestamp)] ✗ ERROR: \(message)"
        if let error = error {
            fullMessage += " - \(error.localizedDescription)"
        }
        
        os_log(.error, log: generalLog, "%{public}@", fullMessage)
        
        #if DEBUG
        print(fullMessage)
        #endif
    }
    
    // MARK: - Debug Logging
    
    static func logDebug(_ message: String) {
        #if DEBUG
        let timestamp = dateFormatter.string(from: Date())
        let fullMessage = "[\(timestamp)] [DEBUG] \(message)"
        print(fullMessage)
        #endif
    }
    
    // MARK: - Warning Logging
    
    static func logWarning(_ message: String) {
        let timestamp = dateFormatter.string(from: Date())
        let fullMessage = "[\(timestamp)] ⚠ WARNING: \(message)"
        
        os_log(.info, log: generalLog, "%{public}@", fullMessage)
        
        #if DEBUG
        print(fullMessage)
        #endif
    }
}
