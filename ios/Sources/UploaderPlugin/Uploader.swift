import Foundation
import Capacitor
import UniformTypeIdentifiers
import MobileCoreServices

@objc public class Uploader: NSObject, URLSessionTaskDelegate {
    private var urlSession: URLSession?
    private var responsesData: [Int: Data] = [:]
    private var tasks: [String: URLSessionTask] = [:]
    private var retries: [String: Int] = [:]
    private var tempBodyFiles: [String: URL] = [:]

    private struct UploadConfig {
        let filePath: String
        let serverUrl: String
        let options: [String: Any]
    }

    private var uploadConfigs: [String: UploadConfig] = [:]

    var eventHandler: (([String: Any]) -> Void)?

    private let pendingEventsKey = "CapacitorUploaderPendingEvents"

    private func savePendingEvent(eventId: String, event: [String: Any]) {
        var pending = UserDefaults.standard.dictionary(forKey: pendingEventsKey) ?? [:]
        pending[eventId] = event
        UserDefaults.standard.set(pending, forKey: pendingEventsKey)
    }

    public func acknowledgeEvent(eventId: String) {
        var pending = UserDefaults.standard.dictionary(forKey: pendingEventsKey) ?? [:]
        pending.removeValue(forKey: eventId)
        UserDefaults.standard.set(pending, forKey: pendingEventsKey)
    }

    public func getPendingEvents() -> [[String: Any]] {
        let pending = UserDefaults.standard.dictionary(forKey: pendingEventsKey) ?? [:]
        return pending.values.compactMap { $0 as? [String: Any] }
    }

    @objc public func startUpload(_ filePath: String, _ serverUrl: String, _ options: [String: Any], maxRetries: Int = 3) async throws -> String {
        let id = UUID().uuidString
        print("startUpload: \(id)")

        let config = UploadConfig(filePath: filePath, serverUrl: serverUrl, options: options)
        do {
            let task = try createUploadTask(id: id, config: config)
            uploadConfigs[id] = config
            tasks[id] = task
            retries[id] = maxRetries
            task.resume()
            return id
        } catch {
            if let tempUrl = tempBodyFiles.removeValue(forKey: id) {
                try? FileManager.default.removeItem(at: tempUrl)
            }
            throw error
        }
    }

    private func createUploadTask(id: String, config: UploadConfig) throws -> URLSessionTask {
        guard let url = URL(string: config.serverUrl) else {
            throw NSError(domain: "UploaderPlugin", code: 0, userInfo: [NSLocalizedDescriptionKey: "Invalid URL"])
        }

        var request = URLRequest(url: url)
        request.httpMethod = (config.options["method"] as? String)?.uppercased() ?? "POST"

        let headers = config.options["headers"] as? [String: String] ?? [:]
        for (key, value) in headers {
            request.setValue(value, forHTTPHeaderField: key)
        }

        let filePath = config.filePath
        let fileUrl: URL
        if let candidateUrl = URL(string: filePath), candidateUrl.isFileURL {
            fileUrl = candidateUrl
        } else {
            fileUrl = URL(fileURLWithPath: filePath)
        }
        guard fileUrl.isFileURL else {
            throw NSError(domain: "UploaderPlugin", code: 1, userInfo: [NSLocalizedDescriptionKey: "Invalid file URL"])
        }
        let mimeType = config.options["mimeType"] as? String ?? guessMIMEType(from: filePath)

        let task: URLSessionTask
        if request.httpMethod == "PUT" {
            // For S3 presigned URL uploads
            request.setValue(mimeType, forHTTPHeaderField: "Content-Type")
            task = self.getUrlSession().uploadTask(with: request, fromFile: fileUrl)
        } else {
            // For POST uploads
            let boundary = UUID().uuidString
            request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

            let parameters = config.options["parameters"] as? [String: String] ?? [:]

            if let oldTemp = tempBodyFiles[id] {
                try? FileManager.default.removeItem(at: oldTemp)
            }
            let tempDir = FileManager.default.temporaryDirectory
            let tempFile = tempDir.appendingPathComponent("upload-\(id).tmp")
            try writeMultipartBodyToFile(at: tempFile, parameters: parameters, fileUrl: fileUrl, mimeType: mimeType, boundary: boundary)
            tempBodyFiles[id] = tempFile

            task = self.getUrlSession().uploadTask(with: request, fromFile: tempFile)
        }

        task.taskDescription = id
        return task
    }

    @objc public func removeUpload(_ id: String) async throws {
        print("removeUpload: \(id)")
        if let task = tasks[id] {
            task.cancel()
            tasks.removeValue(forKey: id)
            retries.removeValue(forKey: id)
            uploadConfigs.removeValue(forKey: id)
            if let tempUrl = tempBodyFiles.removeValue(forKey: id) {
                try? FileManager.default.removeItem(at: tempUrl)
            }
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
            let isCancelled = (error as NSError).code == NSURLErrorCancelled
            if !isCancelled, let retriesLeft = retries[id], retriesLeft > 0, let config = uploadConfigs[id] {
                let newRetries = retriesLeft - 1
                retries[id] = newRetries
                print("Retrying upload (retries left: \(newRetries))")
                do {
                    let newTask = try createUploadTask(id: id, config: config)
                    tasks[id] = newTask
                    newTask.resume()
                    return
                } catch {
                    payload["error"] = error.localizedDescription
                }
            } else {
                payload["error"] = error.localizedDescription
            }
            sendEvent(name: "failed", id: id, payload: payload)
        } else {
            sendEvent(name: "completed", id: id, payload: payload)
        }

        if let tempUrl = tempBodyFiles.removeValue(forKey: id) {
            try? FileManager.default.removeItem(at: tempUrl)
        }
        tasks.removeValue(forKey: id)
        retries.removeValue(forKey: id)
        uploadConfigs.removeValue(forKey: id)
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
        if name == "completed" || name == "failed" {
            let eventId = UUID().uuidString
            event["eventId"] = eventId
            savePendingEvent(eventId: eventId, event: event)
        }
        eventHandler?(event)
    }

    // Writes multipart/form-data body directly to a file, streaming the file content in chunks
    private func writeMultipartBodyToFile(at tempFile: URL, parameters: [String: String], fileUrl: URL, mimeType: String, boundary: String) throws {
        FileManager.default.createFile(atPath: tempFile.path, contents: nil)
        let writeHandle = try FileHandle(forWritingTo: tempFile)
        defer { try? writeHandle.close() }

        func write(_ string: String) throws {
            try writeHandle.write(contentsOf: string.data(using: .utf8)!)
        }

        for (key, value) in parameters {
            try write("--\(boundary)\r\n")
            try write("Content-Disposition: form-data; name=\"\(key)\"\r\n\r\n")
            try write("\(value)\r\n")
        }

        try write("--\(boundary)\r\n")
        try write("Content-Disposition: form-data; name=\"file\"; filename=\"\(fileUrl.lastPathComponent)\"\r\n")
        try write("Content-Type: \(mimeType)\r\n\r\n")

        let readHandle = try FileHandle(forReadingFrom: fileUrl)
        defer { try? readHandle.close() }
        let chunkSize = 64 * 1024
        while true {
            guard let chunk = try readHandle.read(upToCount: chunkSize), !chunk.isEmpty else { break }
            try writeHandle.write(contentsOf: chunk)
        }

        try write("\r\n--\(boundary)--")
    }
}

extension Uploader: URLSessionDataDelegate {
    public func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        var responseData = responsesData[dataTask.taskIdentifier] ?? Data()
        responseData.append(data)
        responsesData[dataTask.taskIdentifier] = responseData
    }
}
