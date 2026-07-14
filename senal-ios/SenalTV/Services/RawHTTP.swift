import Foundation
import Network

/// Minimal HTTP/1.1 client via NWConnection — not subject to App Transport Security.
/// LiveContainer often ignores guest Info.plist ATS, so URLSession HTTP fails there.
enum RawHTTP {
    final class Session {
        let connection: NWConnection
        let request: Data
        private var buffer = Data()
        private var box: ContinuationBox?
        private let lock = NSLock()

        init(host: String, port: UInt16, request: Data) {
            self.request = request
            self.connection = NWConnection(
                host: NWEndpoint.Host(host),
                port: NWEndpoint.Port(rawValue: port)!,
                using: .tcp
            )
        }

        func run() async throws -> Data {
            try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Data, Error>) in
                self.box = ContinuationBox(cont)
                self.connection.stateUpdateHandler = { [weak self] state in
                    guard let self else { return }
                    switch state {
                    case .ready:
                        self.connection.send(content: self.request, completion: .contentProcessed { error in
                            if let error {
                                self.finish(throwing: error)
                            }
                        })
                        self.receiveLoop()
                    case .failed(let error):
                        self.finish(throwing: error)
                    case .cancelled:
                        break
                    default:
                        break
                    }
                }
                self.connection.start(queue: .global(qos: .userInitiated))
            }
        }

        private func receiveLoop() {
            connection.receive(minimumIncompleteLength: 1, maximumLength: 64 * 1024) { [weak self] data, _, isComplete, error in
                guard let self else { return }
                if let error {
                    self.finish(throwing: error)
                    return
                }
                if let data, !data.isEmpty {
                    self.lock.lock()
                    self.buffer.append(data)
                    let snapshot = self.buffer
                    self.lock.unlock()

                    if Self.isComplete(snapshot) {
                        self.finishWithBuffer()
                        return
                    }
                }
                if isComplete {
                    self.finishWithBuffer()
                    return
                }
                self.receiveLoop()
            }
        }

        private func finishWithBuffer() {
            lock.lock()
            let snapshot = buffer
            lock.unlock()
            do {
                finish(returning: try Self.extractBody(from: snapshot))
            } catch {
                finish(throwing: error)
            }
        }

        private func finish(returning value: Data) {
            lock.lock()
            let b = box
            box = nil
            lock.unlock()
            b?.resume(returning: value)
            connection.cancel()
        }

        private func finish(throwing error: Error) {
            lock.lock()
            let b = box
            box = nil
            lock.unlock()
            b?.resume(throwing: error)
            connection.cancel()
        }

        private static func isComplete(_ data: Data) -> Bool {
            guard let headerEnd = data.range(of: Data("\r\n\r\n".utf8)) else { return false }
            let headers = String(decoding: data[..<headerEnd.lowerBound], as: UTF8.self)
            if let range = headers.range(
                of: #"Content-Length:\s*(\d+)"#,
                options: [.regularExpression, .caseInsensitive]
            ) {
                let digits = String(headers[range]).filter(\.isNumber)
                if let length = Int(digits) {
                    return data.count - headerEnd.upperBound >= length
                }
            }
            return false
        }

        private static func extractBody(from data: Data) throws -> Data {
            guard let headerEnd = data.range(of: Data("\r\n\r\n".utf8)) else {
                throw NSError(domain: "SenalHTTP", code: 2, userInfo: [
                    NSLocalizedDescriptionKey: "Respuesta HTTP inválida"
                ])
            }
            let headerText = String(decoding: data[..<headerEnd.lowerBound], as: UTF8.self)
            let status = headerText.split(separator: "\r\n").first.map(String.init) ?? ""
            let body = Data(data[headerEnd.upperBound...])
            // Auth errors still include JSON body
            if status.contains(" 200") || status.contains(" 201")
                || status.contains(" 400") || status.contains(" 401") || status.contains(" 403") {
                return body
            }
            let snippet = String(decoding: body.prefix(200), as: UTF8.self)
            throw NSError(domain: "SenalHTTP", code: 3, userInfo: [
                NSLocalizedDescriptionKey: snippet.isEmpty ? "Error del servidor" : snippet
            ])
        }
    }

    static func post(host: String, port: UInt16, request: Data) async throws -> Data {
        try await Session(host: host, port: port, request: request).run()
    }
}

final class ContinuationBox: @unchecked Sendable {
    private var cont: CheckedContinuation<Data, Error>?
    private let lock = NSLock()

    init(_ cont: CheckedContinuation<Data, Error>) {
        self.cont = cont
    }

    func resume(returning value: Data) {
        lock.lock()
        let c = cont
        cont = nil
        lock.unlock()
        c?.resume(returning: value)
    }

    func resume(throwing error: Error) {
        lock.lock()
        let c = cont
        cont = nil
        lock.unlock()
        c?.resume(throwing: error)
    }
}
