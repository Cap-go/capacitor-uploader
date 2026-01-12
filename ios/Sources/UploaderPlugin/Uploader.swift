import Foundation
import Capacitor
import UniformTypeIdentifiers
import MobileCoreServices

@objc public class Uploader: NSObject, URLSessionTaskDelegate {
    private var urlSession: URLSession?
    private var responsesData: [Int: Data] = [:]
    private var tasks: [String: URLSessionTask] = [:]
    private var retries: [String: Int] = [:]

    var eventHandler: (([String: Any]) -> Void)?

    @objc public func startUpload(_ filePath: String, _ serverUrl: String, _ options: [String: Any], maxRetries: Int = 3) async throws -> String {
        let id = UUID().uuidString
        print("startUpload: \(id)")

        guard let url = URL(string: serverUrl) else {
            throw NSError(domain: "UploaderPlugin", code: 0, userInfo: [NSLocalizedDescriptionKey: "Invalid URL"])
        }

        var request = URLRequest(url: url)
        request.httpMethod = (options["method"] as? String)?.uppercased() ?? "POST"

        let headers = options["headers"] as? [String: String] ?? [:]
        for (key, value) in headers {
            request.setValue(value, forHTTPHeaderField: key)
        }

        guard let fileUrl = URL(string: filePath) else {
            throw NSError(domain: "UploaderPlugin", code: 1, userInfo: [NSLocalizedDescriptionKey: "Invalid file URL"])
        }
        let mimeType = options["mimeType"] as? String ?? guessMIMEType(from: filePath)
        // Use explicit uploadType parameter instead of inferring from HTTP method
        // Default is "binary" for backward compatibility and consistency across platforms
        let uploadType = options["uploadType"] as? String ?? "binary"
        let fileField = options["fileField"] as? String ?? "file"

        let task: URLSessionTask
        if uploadType == "multipart" {
            // For multipart/form-data uploads
            // Encodes the file and parameters as multipart form data
            let boundary = UUID().uuidString
            request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

            let parameters = options["parameters"] as? [String: String] ?? [:]

            let dataBody = createDataBody(withParameters: parameters, filePath: filePath, mimeType: mimeType, boundary: boundary, fileField: fileField)

            task = self.getUrlSession().uploadTask(with: request, from: dataBody)
        } else {
            // For binary uploads (default)
            // Uploads the file as raw binary data in the request body
            request.setValue(mimeType, forHTTPHeaderField: "Content-Type")
            task = self.getUrlSession().uploadTask(with: request, fromFile: fileUrl)
        }

        task.taskDescription = id
        tasks[id] = task
        retries[id] = maxRetries
        task.resume()

        return id
    }

    @objc public func removeUpload(_ id: String) async throws {
        print("removeUpload: \(id)")
        if let task = tasks[id] {
            task.cancel()
            tasks.removeValue(forKey: id)
        }
    }

    private func getUrlSession() -> URLSession {
        if urlSession == nil {
            let config = URLSessionConfiguration.background(withIdentifier: "CapacitorUploaderBackgroundSession")
            urlSession = URLSession(configuration: config, delegate: self, delegateQueue: nil)
        }
        return urlSession!
    }

    private func guessMIMEType(from filePath: String) -> String {
        let url = URL(fileURLWithPath: filePath)
        if let mimeType = UTType(filenameExtension: url.pathExtension)?.preferredMIMEType {
            return mimeType
        }
        return "application/octet-stream"
    }
    public func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard let id = task.taskDescription else { return }

        var payload: [String: Any] = [:]
        if let response = task.response as? HTTPURLResponse {
            payload["statusCode"] = response.statusCode
        }

        if let error = error {
            if let retriesLeft = retries[id], retriesLeft > 0 {
                retries[id] = retriesLeft - 1
                print("Retrying upload (retries left: \(retriesLeft - 1))")
                task.resume()
                return
            }

            payload["error"] = error.localizedDescription
            sendEvent(name: "failed", id: id, payload: payload)
        } else {
            sendEvent(name: "completed", id: id, payload: payload)
        }

        tasks.removeValue(forKey: id)
        retries.removeValue(forKey: id)
    }

    public func urlSession(_ session: URLSession, task: URLSessionTask, didSendBodyData bytesSent: Int64, totalBytesSent: Int64, totalBytesExpectedToSend: Int64) {
        guard let id = task.taskDescription else { return }

        var percent: Float = -1
        if totalBytesExpectedToSend > 0 {
            percent = Float(totalBytesSent) / Float(totalBytesExpectedToSend) * 100
        }

        sendEvent(name: "uploading", id: id, payload: ["percent": percent])
    }

    private func sendEvent(name: String, id: String, payload: [String: Any]) {
        var event: [String: Any] = [
            "name": name,
            "id": id,
            "payload": payload
        ]
        eventHandler?(event)
    }

    private func createDataBody(withParameters params: [String: String], filePath: String, mimeType: String, boundary: String, fileField: String) -> Data {
        let data = NSMutableData()

        for (key, value) in params {
            data.append("--\(boundary)\r\n".data(using: .utf8)!)
            data.append("Content-Disposition: form-data; name=\"\(key)\"\r\n\r\n".data(using: .utf8)!)
            data.append("\(value)\r\n".data(using: .utf8)!)
        }

        data.append("--\(boundary)\r\n".data(using: .utf8)!)
        data.append("Content-Disposition: form-data; name=\"\(fileField)\"; filename=\"\(URL(fileURLWithPath: filePath).lastPathComponent)\"\r\n".data(using: .utf8)!)
        data.append("Content-Type: \(mimeType)\r\n\r\n".data(using: .utf8)!)
        data.append(try! Data(contentsOf: URL(fileURLWithPath: filePath)))
        data.append("\r\n".data(using: .utf8)!)
        data.append("--\(boundary)--".data(using: .utf8)!)

        return data as Data
    }

}

extension Uploader: URLSessionDataDelegate {
    public func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        var responseData = responsesData[dataTask.taskIdentifier] ?? Data()
        responseData.append(data)
        responsesData[dataTask.taskIdentifier] = responseData
    }
}
