import Foundation
import Combine

@MainActor
final class SessionStore: ObservableObject {
    @Published var token: String?
    @Published var username: String?
    @Published var isBusy = false
    @Published var errorMessage: String?

    private let tokenKey = "senal.jwt"
    private let userKey = "senal.username"
    private let deviceKey = "senal.deviceId"

    var isLoggedIn: Bool { !(token?.isEmpty ?? true) }

    init() {
        token = UserDefaults.standard.string(forKey: tokenKey)
        username = UserDefaults.standard.string(forKey: userKey)
    }

    func login(username: String, password: String) async {
        let user = username.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !user.isEmpty, !password.isEmpty else {
            errorMessage = "Introduce usuario y contraseña"
            return
        }
        isBusy = true
        errorMessage = nil
        defer { isBusy = false }

        do {
            let jwt = try await AuthService.login(
                username: user,
                password: password,
                deviceId: deviceId()
            )
            token = jwt
            self.username = user
            UserDefaults.standard.set(jwt, forKey: tokenKey)
            UserDefaults.standard.set(user, forKey: userKey)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func logout() {
        token = nil
        username = nil
        UserDefaults.standard.removeObject(forKey: tokenKey)
        UserDefaults.standard.removeObject(forKey: userKey)
    }

    func deviceId() -> String {
        if let existing = UserDefaults.standard.string(forKey: deviceKey), !existing.isEmpty {
            return existing
        }
        let id = "senal-ios-" + UUID().uuidString
        UserDefaults.standard.set(id, forKey: deviceKey)
        return id
    }
}

/// Login via Network.framework TCP (bypasses ATS / LiveContainer URLSession block).
enum AuthService {
    static let host = "185.192.20.245"
    static let port: UInt16 = 3000

    struct LoginBody: Encodable {
        let username: String
        let password: String
        let deviceId: String
        let deviceName: String
        let platform: String
    }

    struct LoginResponse: Decodable {
        let token: String?
        let accessToken: String?
        let jwt: String?
        let error: String?
        let message: String?

        var resolved: String? { token ?? accessToken ?? jwt }
        var resolvedError: String? { error ?? message }
    }

    static func login(username: String, password: String, deviceId: String) async throws -> String {
        let body = try JSONEncoder().encode(
            LoginBody(
                username: username,
                password: password,
                deviceId: deviceId,
                deviceName: "SENAL iPhone",
                platform: "ios"
            )
        )

        var request = Data()
        func append(_ s: String) { request.append(contentsOf: s.utf8) }
        append("POST /api/auth/login HTTP/1.1\r\n")
        append("Host: \(host):\(port)\r\n")
        append("Content-Type: application/json\r\n")
        append("Accept: application/json\r\n")
        append("X-Device-Id: \(deviceId)\r\n")
        append("X-Device-Name: SENAL iPhone\r\n")
        append("X-Device-Platform: ios\r\n")
        append("Connection: close\r\n")
        append("Content-Length: \(body.count)\r\n")
        append("\r\n")
        request.append(body)

        let responseData = try await RawHTTP.exchange(host: host, port: port, request: request)
        let decoded = try? JSONDecoder().decode(LoginResponse.self, from: responseData)
        if let jwt = decoded?.resolved, !jwt.isEmpty {
            return jwt
        }
        throw NSError(domain: "SenalAuth", code: 1, userInfo: [
            NSLocalizedDescriptionKey: decoded?.resolvedError ?? "Error de acceso"
        ])
    }
}
