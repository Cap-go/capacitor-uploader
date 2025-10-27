import Foundation
import Capacitor

@objc(UploaderPlugin)
public class UploaderPlugin: CAPPlugin, CAPBridgedPlugin {
    private let PLUGIN_VERSION: String = "7.2.6"
    public let identifier = "UploaderPlugin"
    public let jsName = "Uploader"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "startUpload", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "removeUpload", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getPluginVersion", returnType: CAPPluginReturnPromise)
    ]
    private let implementation = Uploader()

    override public func load() {
        implementation.eventHandler = { [weak self] event in
            self?.notifyListeners("events", data: event)
        }
    }

    @objc func startUpload(_ call: CAPPluginCall) {
        guard let filePath = call.getString("filePath"),
              let serverUrl = call.getString("serverUrl") else {
            call.reject("Missing required parameters")
            return
        }

        let options: [String: Any] = [
            "headers": call.getObject("headers") as Any,
            "method": call.getString("method") as Any,
            "mimeType": call.getString("mimeType") as Any,
            "parameters": call.getObject("parameters") as Any
        ]

        let maxRetries = call.getInt("maxRetries") ?? 3

        Task {
            do {
                let id = try await implementation.startUpload(filePath, serverUrl, options, maxRetries: maxRetries)
                call.resolve(["id": id])
            } catch {
                call.reject("Failed to start upload: \(error.localizedDescription)")
            }
        }
    }

    @objc func removeUpload(_ call: CAPPluginCall) {
        guard let id = call.getString("id") else {
            call.reject("Missing required parameter: id")
            return
        }

        Task {
            do {
                try await implementation.removeUpload(id)
                call.resolve()
            } catch {
                call.reject("Failed to remove upload: \(error.localizedDescription)")
            }
        }
    }

    @objc func getPluginVersion(_ call: CAPPluginCall) {
        call.resolve(["version": self.PLUGIN_VERSION])
    }

}
