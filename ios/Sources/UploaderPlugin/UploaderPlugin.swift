import Foundation
import Capacitor

@objc(UploaderPlugin)
public class UploaderPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "UploaderPlugin"
    public let jsName = "Uploader"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "startUpload", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "removeUpload", returnType: CAPPluginReturnPromise),
    ]
    private let implementation = Uploader()

    override public func load() {
        NotificationCenter.default.addObserver(self, selector: #selector(handleProgress(_:)), name: Notification.Name("UploaderPlugin-progress"), object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(handleCompleted(_:)), name: Notification.Name("UploaderPlugin-completed"), object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(handleError(_:)), name: Notification.Name("UploaderPlugin-error"), object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(handleCancelled(_:)), name: Notification.Name("UploaderPlugin-cancelled"), object: nil)
    }

    @objc func startUpload(_ call: CAPPluginCall) {
        let filePath = call.getString("filePath") ?? ""
        let serverUrl = call.getString("serverUrl") ?? ""
        let headers = call.getObject("headers") as? [String: String] ?? [:]
        
        Task {
            do {
                let id = try await implementation.startUpload(filePath, serverUrl, headers)
                call.resolve([
                    "id": id
                ])
            } catch {
                call.reject("Failed to start upload: \(error.localizedDescription)")
            }
        }
    }

    @objc func removeUpload(_ call: CAPPluginCall) {
        let id = call.getString("id") ?? ""
        
        Task {
            do {
                try await implementation.removeUpload(id)
                call.resolve()
            } catch {
                call.reject("Failed to remove upload: \(error.localizedDescription)")
            }
        }
    }

    @objc private func handleProgress(_ notification: Notification) {
        guard let data = notification.userInfo as? [String: Any] else { return }
        notifyListeners("progress", data: data)
    }

    @objc private func handleCompleted(_ notification: Notification) {
        guard let data = notification.userInfo as? [String: Any] else { return }
        notifyListeners("completed", data: data)
    }

    @objc private func handleError(_ notification: Notification) {
        guard let data = notification.userInfo as? [String: Any] else { return }
        notifyListeners("error", data: data)
    }

    @objc private func handleCancelled(_ notification: Notification) {
        guard let data = notification.userInfo as? [String: Any] else { return }
        notifyListeners("cancelled", data: data)
    }
}
