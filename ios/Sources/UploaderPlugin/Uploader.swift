import Foundation
import Capacitor

@objc public class Uploader: NSObject, URLSessionTaskDelegate {
    private var urlSession: URLSession?
    private var responsesData: [Int: Data] = [:]
    private var tasks: [String: URLSessionTask] = [:]
    
    var eventHandler: (([String: Any]) -> Void)?

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

        var payload: [String: Any] = [:]
        if let response = task.response as? HTTPURLResponse {
            payload["statusCode"] = response.statusCode
        }

        if let error = error {
            payload["error"] = error.localizedDescription
            sendEvent(name: "failed", id: id, payload: payload)
        } else {
            sendEvent(name: "completed", id: id, payload: payload)
        }

        tasks.removeValue(forKey: id)
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
}

// MARK: - URLSessionDataDelegate
extension Uploader: URLSessionDataDelegate {
    public func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        var responseData = responsesData[dataTask.taskIdentifier] ?? Data()
        responseData.append(data)
        responsesData[dataTask.taskIdentifier] = responseData
    }
}
