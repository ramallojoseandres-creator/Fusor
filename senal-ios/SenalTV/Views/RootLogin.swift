import SwiftUI
import AVKit

struct RootView: View {
    @EnvironmentObject private var session: SessionStore
    @EnvironmentObject private var catalog: CatalogStore

    var body: some View {
        Group {
            if session.isLoggedIn {
                MainTabView()
            } else {
                LoginView()
            }
        }
        .animation(.easeInOut(duration: 0.35), value: session.isLoggedIn)
    }
}

struct MainTabView: View {
    var body: some View {
        TabView {
            HomeView()
                .tabItem { Label("Inicio", systemImage: "house.fill") }
            LiveView()
                .tabItem { Label("TV en vivo", systemImage: "tv.fill") }
            MoviesView()
                .tabItem { Label("Cine", systemImage: "film.fill") }
            SearchView()
                .tabItem { Label("Buscar", systemImage: "magnifyingglass") }
            SettingsView()
                .tabItem { Label("Ajustes", systemImage: "gearshape.fill") }
        }
        .tint(SenalColors.teal)
    }
}

struct LoginView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var username = ""
    @State private var password = ""

    private var clock: String {
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        return f.string(from: Date())
    }

    var body: some View {
        ZStack {
            SenalBackground()
            LinearGradient(
                colors: [
                    .black.opacity(0.55),
                    .black.opacity(0.82),
                    .black.opacity(0.94)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            Text(clock)
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(.white.opacity(0.9))
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                .padding(.top, 28)
                .padding(.trailing, 28)

            VStack(spacing: 0) {
                Text("SEÑAL")
                    .font(.system(size: 48, weight: .black))
                    .tracking(8)
                    .foregroundStyle(.white)

                Text("Inicia sesión para ver en vivo")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(SenalColors.muted)
                    .padding(.top, 10)

                VStack(alignment: .leading, spacing: 22) {
                    UnderlineLoginField(title: "Usuario", text: $username, isSecure: false)
                    UnderlineLoginField(title: "Contraseña", text: $password, isSecure: true)
                }
                .padding(.top, 36)
                .padding(.horizontal, 32)

                if let err = session.errorMessage {
                    Text(err)
                        .font(.footnote)
                        .foregroundStyle(.red.opacity(0.9))
                        .multilineTextAlignment(.center)
                        .padding(.top, 14)
                        .padding(.horizontal, 28)
                }

                Button {
                    Task { await session.login(username: username, password: password) }
                } label: {
                    Group {
                        if session.isBusy {
                            ProgressView().tint(.black)
                        } else {
                            Text("ENTRAR")
                                .font(.system(size: 16, weight: .black))
                                .tracking(2)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .foregroundStyle(.black)
                    .background(SenalColors.teal)
                    .clipShape(RoundedRectangle(cornerRadius: 4))
                }
                .disabled(session.isBusy)
                .padding(.horizontal, 32)
                .padding(.top, 22)
            }
            .frame(maxWidth: 420)
        }
        .statusBarHidden(true)
    }
}

private struct UnderlineLoginField: View {
    let title: String
    @Binding var text: String
    var isSecure: Bool
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.caption.weight(.semibold))
                .tracking(1)
                .foregroundStyle(focused ? SenalColors.teal : .white.opacity(0.55))
            Group {
                if isSecure {
                    SecureField(title, text: $text)
                } else {
                    TextField(title, text: $text)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
            }
            .focused($focused)
            .font(.system(size: 17, weight: .medium))
            .foregroundStyle(.white)
            .padding(.vertical, 8)
            Rectangle()
                .fill(focused ? SenalColors.teal : Color.white.opacity(0.35))
                .frame(height: focused ? 2 : 1)
        }
    }
}
