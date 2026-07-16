import SwiftUI

@main
struct SenalTVApp: App {
    @StateObject private var session = SessionStore()
    @StateObject private var catalog = CatalogStore()
    @State private var showSplash = true

    var body: some Scene {
        WindowGroup {
            ZStack {
                RootView()
                    .environmentObject(session)
                    .environmentObject(catalog)
                    .preferredColorScheme(.dark)
                    .opacity(showSplash ? 0 : 1)

                if showSplash {
                    SplashView {
                        withAnimation(.easeOut(duration: 0.3)) {
                            showSplash = false
                        }
                    }
                    .transition(.opacity)
                    .zIndex(1)
                }
            }
            .task {
                if session.isLoggedIn {
                    await catalog.ensureReady(token: session.token)
                } else {
                    await catalog.loadIfNeeded()
                }
            }
        }
    }
}
