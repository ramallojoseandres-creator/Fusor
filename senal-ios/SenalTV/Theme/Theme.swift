import SwiftUI

enum SenalColors {
    static let graphite = Color(red: 0.02, green: 0.02, blue: 0.03)
    static let elevated = Color(red: 0.07, green: 0.07, blue: 0.09)
    static let card = Color(red: 0.09, green: 0.09, blue: 0.12)
    static let violet = Color(red: 0.12, green: 0.44, blue: 1.0) // alias → brand blue
    static let teal = Color(red: 0.0, green: 0.90, blue: 0.78)
    static let orange = Color(red: 0.0, green: 0.90, blue: 0.78) // accent = teal
    static let blue = Color(red: 0.12, green: 0.44, blue: 1.0)
    static let blueSoft = Color(red: 0.24, green: 0.62, blue: 1.0)
    static let blueDeep = Color(red: 0.04, green: 0.16, blue: 0.42)
    static let text = Color(red: 0.95, green: 0.97, blue: 0.98)
    static let muted = Color(red: 0.54, green: 0.61, blue: 0.69)
}

struct SenalBackground: View {
    var body: some View {
        LinearGradient(
            colors: [
                Color(red: 0.10, green: 0.07, blue: 0.18),
                SenalColors.graphite,
                Color.black
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .ignoresSafeArea()
    }
}
