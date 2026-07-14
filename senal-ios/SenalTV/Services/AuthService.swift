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

    private func deviceId() -> String {
        if let existing = UserDefaults.standard.string(forKey: deviceKey) {
            return existing
        }
        let id = UUID().uuidString
        UserDefaults.standard.set(id, forKey: deviceKey)
        return id
    }
}

enum AuthService {
    /// Servidor solo para usuarios — no sirve catálogo.
    static let baseURL = URL(string: "http://185.192.20.245:3000/")!

    struct LoginBody: Encodable {
        let username: String
        let password: String
        let deviceId: String
        let deviceName: String
    }

    struct LoginResponse: Decodable {
        let token: String?
        let accessToken: String?
        let jwt: String?
        let error: String?

        var resolved: String? { token ?? accessToken ?? jwt }
    }

    static func login(username: String, password: String, deviceId: String) async throws -> String {
        var req = URLRequest(url: baseURL.appendingPathComponent("api/auth/login"))
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        req.httpBody = try JSONEncoder().encode(
            LoginBody(
                username: username,
                password: password,
                deviceId: deviceId,
                deviceName: "SEÑAL iPhone"
            )
        )

        let (data, response) = try await URLSession.shared.data(for: req)
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        let decoded = try? JSONDecoder().decode(LoginResponse.self, from: data)
        if let jwt = decoded?.resolved, !jwt.isEmpty {
            return jwt
        }
        let msg = decoded?.error ?? "Error de acceso (\(code))"
        throw NSError(domain: "SenalAuth", code: code, userInfo: [
            NSLocalizedDescriptionKey: msg
        ])
    }
}
