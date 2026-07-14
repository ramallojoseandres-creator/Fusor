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
        .tint(SenalColors.violet)
    }
}

struct LoginView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var username = ""
    @State private var password = ""

    var body: some View {
        ZStack {
            SenalBackground()
            VStack(spacing: 18) {
                Text("SEÑAL")
                    .font(.system(size: 44, weight: .black, design: .rounded))
                    .foregroundStyle(
                        LinearGradient(
                            colors: [SenalColors.violet, SenalColors.teal],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .shadow(color: SenalColors.violet.opacity(0.5), radius: 18)

                Text("Acceso de usuario · catálogo en el iPhone")
                    .font(.subheadline)
                    .foregroundStyle(SenalColors.muted)

                VStack(spacing: 12) {
                    TextField("Usuario", text: $username)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .padding()
                        .background(SenalColors.card)
                        .clipShape(RoundedRectangle(cornerRadius: 14))

                    SecureField("Contraseña", text: $password)
                        .padding()
                        .background(SenalColors.card)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                }
                .foregroundStyle(SenalColors.text)
                .padding(.horizontal, 28)

                if let err = session.errorMessage {
                    Text(err)
                        .font(.footnote)
                        .foregroundStyle(.red.opacity(0.9))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Button {
                    Task { await session.login(username: username, password: password) }
                } label: {
                    Group {
                        if session.isBusy {
                            ProgressView().tint(.white)
                        } else {
                            Text("Entrar").fontWeight(.semibold)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(
                        LinearGradient(
                            colors: [SenalColors.violet, SenalColors.orange],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                }
                .disabled(session.isBusy)
                .padding(.horizontal, 28)

                Text("El servidor solo valida tu cuenta.\nLos canales van embebidos en la app.")
                    .font(.caption)
                    .foregroundStyle(SenalColors.muted)
                    .multilineTextAlignment(.center)
                    .padding(.top, 8)
            }
            .padding(.vertical, 40)
        }
    }
}
