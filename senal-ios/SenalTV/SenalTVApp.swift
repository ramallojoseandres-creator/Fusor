import SwiftUI

@main
struct SenalTVApp: App {
    @StateObject private var session = SessionStore()
    @StateObject private var catalog = CatalogStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(session)
                .environmentObject(catalog)
                .preferredColorScheme(.dark)
                .task {
                    await catalog.loadIfNeeded()
                }
        }
    }
}
