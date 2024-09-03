import Foundation
import Capacitor

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(UploaderPlugin)
public class UploaderPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "UploaderPlugin"
    public let jsName = "Uploader"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "startUpload", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "removeUpload", returnType: CAPPluginReturnPromise),
    ]
    private let implementation = Uploader()

    @objc func startUpload(_ call: CAPPluginCall) {
        let filePath = call.getString("filePath") ?? ""
        let serverUrl = call.getString("serverUrl") ?? ""
        let headers = call.getObject("headers") ?? [:]
        do {
            let id = await implementation.startUpload(filePath, serverUrl, headers)
            call.resolve([
                "id": id
            ])
        } catch {
            call.reject("Failed to start upload")
        }
    }

    @objc func removeUpload(_ call: CAPPluginCall) {
        let id = call.getString("id") ?? ""
        do {    
            await implementation.removeUpload(id)
            call.resolve()
        } catch {
            call.reject("Failed to remove upload")
        }
    }
}
