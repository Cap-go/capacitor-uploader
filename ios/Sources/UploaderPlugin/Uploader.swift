import Foundation

@objc public class Uploader: NSObject {

    @objc public func startUpload(_ filePath: String, _ serverUrl: String, _ headers: [String: String]) {
        let id = UUID().uuidString
        print("startUpload: \(id)")
        return id
    }

    @objc public func removeUpload(_ id: String) {
        print("removeUpload: \(id)")
        return
    }
}
