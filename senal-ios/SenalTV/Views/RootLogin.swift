import SwiftUI
import AVKit

struct RootView: View {
    @EnvironmentObject private var session: SessionStore
    @EnvironmentObject private var catalog: CatalogStore
    @State private var catalogReady = false

    var body: some View {
        Group {
            if !session.isLoggedIn {
                LoginView()
            } else if !catalogReady {
                CatalogGateView(onReady: { catalogReady = true })
            } else {
                MainTabView()
            }
        }
        .animation(.easeInOut(duration: 0.35), value: session.isLoggedIn)
        .animation(.easeInOut(duration: 0.25), value: catalogReady)
        .onChange(of: session.isLoggedIn) { logged in
            if !logged { catalogReady = false }
        }
    }
}

/// Primera vez: «Cargando todos los canales…». Con caché: abre al instante.
struct CatalogGateView: View {
    @EnvironmentObject private var session: SessionStore
    @EnvironmentObject private var catalog: CatalogStore
    var onReady: () -> Void
    @State private var attempt = 0

    var body: some View {
        ZStack {
            SenalBackground()
            VStack(spacing: 16) {
                Text("SEÑAL")
                    .font(.system(size: 36, weight: .black, design: .rounded))
                    .foregroundStyle(SenalColors.orange)
                if catalog.isLoading || catalog.statusMessage != nil {
                    ProgressView()
                        .tint(SenalColors.orange)
                        .scaleEffect(1.2)
                    Text(catalog.statusMessage ?? "Cargando todos los canales…")
                        .font(.subheadline)
                        .foregroundStyle(SenalColors.text)
                        .multilineTextAlignment(.center)
                    Text(catalog.hasLocalCache
                          ? "Usa la lista guardada en este iPhone"
                          : "Solo la primera vez. Luego queda guardada.")
                        .font(.caption)
                        .foregroundStyle(SenalColors.muted)
                        .multilineTextAlignment(.center)
                } else if let err = catalog.loadError, catalog.channels.isEmpty {
                    Text("No se pudo cargar la lista")
                        .font(.headline)
                        .foregroundStyle(SenalColors.text)
                    Text(err)
                        .font(.caption)
                        .foregroundStyle(.red.opacity(0.9))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                    Button("Reintentar") { attempt += 1 }
                        .buttonStyle(.borderedProminent)
                        .tint(SenalColors.orange)
                }
            }
            .padding(28)
        }
        .task(id: attempt) {
            await catalog.ensureReady(token: session.token)
            if !catalog.channels.isEmpty {
                onReady()
            }
        }
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
                    .shadow(color: SenalColors.orange.opacity(0.45), radius: 18)

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

                Text("La lista de canales se descarga del servidor\nla primera vez y queda guardada en el iPhone.")
                    .font(.caption)
                    .foregroundStyle(SenalColors.muted)
                    .multilineTextAlignment(.center)
                    .padding(.top, 8)
            }
            .padding(.vertical, 40)
        }
    }
}
