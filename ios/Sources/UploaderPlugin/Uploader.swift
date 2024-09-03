import Foundation
import Capacitor

@objc public class Uploader: NSObject, URLSessionTaskDelegate {
    private var urlSession: URLSession?
    private var responsesData: [Int: Data] = [:]
    private var tasks: [String: URLSessionTask] = [:]

    @objc public func startUpload(_ filePath: String, _ serverUrl: String, _ headers: [String: String]) async throws -> String {
        let id = UUID().uuidString
        print("startUpload: \(id)")

        guard let url = URL(string: serverUrl) else {
            throw NSError(domain: "UploaderPlugin", code: 0, userInfo: [NSLocalizedDescriptionKey: "Invalid URL"])
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"

        for (key, value) in headers {
            request.setValue(value, forHTTPHeaderField: key)
        }

        let fileUrl = URL(fileURLWithPath: filePath)
        let task = self.getUrlSession().uploadTask(with: request, fromFile: fileUrl)
        task.taskDescription = id
        tasks[id] = task
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

    // MARK: - URLSessionTaskDelegate

    public func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard let id = task.taskDescription else { return }

        var data: [String: Any] = ["id": id]
        if let response = task.response as? HTTPURLResponse {
            data["responseCode"] = response.statusCode
        }

        if let responseData = responsesData[task.taskIdentifier] {
            data["responseBody"] = String(data: responseData, encoding: .utf8) ?? ""
            responsesData.removeValue(forKey: task.taskIdentifier)
        } else {
            data["responseBody"] = NSNull()
        }

        if let error = error {
            data["error"] = error.localizedDescription
            if (error as NSError).code == NSURLErrorCancelled {
                NotificationCenter.default.post(name: Notification.Name("UploaderPlugin-cancelled"), object: nil, userInfo: data)
            } else {
                NotificationCenter.default.post(name: Notification.Name("UploaderPlugin-error"), object: nil, userInfo: data)
            }
        } else {
            NotificationCenter.default.post(name: Notification.Name("UploaderPlugin-completed"), object: nil, userInfo: data)
        }

        tasks.removeValue(forKey: id)
    }

    public func urlSession(_ session: URLSession, task: URLSessionTask, didSendBodyData bytesSent: Int64, totalBytesSent: Int64, totalBytesExpectedToSend: Int64) {
        guard let id = task.taskDescription else { return }

        var progress: Float = -1
        if totalBytesExpectedToSend > 0 {
            progress = Float(totalBytesSent) / Float(totalBytesExpectedToSend) * 100
        }

        let data: [String: Any] = [
            "id": id,
            "progress": progress
        ]

        NotificationCenter.default.post(name: Notification.Name("UploaderPlugin-progress"), object: nil, userInfo: data)
    }
}

// MARK: - URLSessionDataDelegate
extension Uploader: URLSessionDataDelegate {
    public func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        var responseData = responsesData[dataTask.taskIdentifier] ?? Data()
        responseData.append(data)
        responsesData[dataTask.taskIdentifier] = responseData
    }
}
