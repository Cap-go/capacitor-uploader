import Foundation
import Capacitor

@objc(UploaderPlugin)
public class UploaderPlugin: CAPPlugin, CAPBridgedPlugin {
    private let pluginVersion: String = "8.2.8"
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
        let filePath = call.getString("filePath")
        let files = call.getArray("files")
        guard let serverUrl = call.getString("serverUrl") else {
            call.reject("Missing required parameter: serverUrl")
            return
        }
        if (filePath == nil || filePath?.isEmpty == true) && (files == nil || files?.isEmpty == true) {
            call.reject("Missing required parameter: filePath or files")
            return
        }

        let headers = (call.getObject("headers") ?? [:]).compactMapValues { $0 as? String }
        let parameters = (call.getObject("parameters") ?? [:]).compactMapValues { $0 as? String }

        var options: [String: Any] = [
            "headers": headers,
            "parameters": parameters
        ]

        if let method = call.getString("method") { options["method"] = method }
        if let mimeType = call.getString("mimeType") { options["mimeType"] = mimeType }
        if let uploadType = call.getString("uploadType") { options["uploadType"] = uploadType }
        if let fileField = call.getString("fileField") { options["fileField"] = fileField }

        if let files {
            let normalizedFiles: [[String: String]] = files.compactMap { item in
                guard let obj = item as? JSObject else { return nil }
                var out: [String: String] = [:]
                if let filePath = obj["filePath"] as? String { out["filePath"] = filePath }
                if let fieldName = obj["fieldName"] as? String { out["fieldName"] = fieldName }
                if let mimeType = obj["mimeType"] as? String { out["mimeType"] = mimeType }
                return out.isEmpty ? nil : out
            }
            if !normalizedFiles.isEmpty { options["files"] = normalizedFiles }
        }

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
        call.resolve(["version": self.pluginVersion])
    }

}
