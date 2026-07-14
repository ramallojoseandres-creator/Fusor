import SwiftUI

/// Palette from SEÑAL intro (splash.mp4): deep navy + cyan rings + silver.
enum SenalColors {
    static let graphite = Color(red: 0.00, green: 0.03, blue: 0.06)
    static let elevated = Color(red: 0.04, green: 0.09, blue: 0.13)
    static let card = Color(red: 0.06, green: 0.13, blue: 0.16)
    /// Ring cyan (replaces previous violet / orange brand).
    static let violet = Color(red: 0.49, green: 0.72, blue: 0.78)
    static let teal = Color(red: 0.16, green: 0.66, blue: 0.75)
    static let orange = Color(red: 0.10, green: 0.61, blue: 0.77)
    static let text = Color(red: 0.91, green: 0.94, blue: 0.96)
    static let muted = Color(red: 0.54, green: 0.63, blue: 0.68)
}

struct SenalBackground: View {
    var body: some View {
        LinearGradient(
            colors: [
                Color(red: 0.00, green: 0.09, blue: 0.16),
                SenalColors.graphite,
                Color.black
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .ignoresSafeArea()
    }
}
