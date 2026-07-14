import SwiftUI
import AVFoundation

@main
struct SenalTVApp: App {
    @StateObject private var session = SessionStore()
    @StateObject private var catalog = CatalogStore()
    @State private var showSplash = true

    init() {
        SenalAudio.activatePlayback()
    }

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
            .onAppear { SenalAudio.activatePlayback() }
            .task {
                await catalog.loadIfNeeded()
            }
        }
    }
}
