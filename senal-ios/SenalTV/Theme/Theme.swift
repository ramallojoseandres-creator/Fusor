import SwiftUI

enum SenalColors {
    static let graphite = Color(red: 0.02, green: 0.02, blue: 0.03)
    static let elevated = Color(red: 0.07, green: 0.07, blue: 0.09)
    static let card = Color(red: 0.09, green: 0.09, blue: 0.12)
    static let violet = Color(red: 0.49, green: 0.23, blue: 0.93)
    static let teal = Color(red: 0.08, green: 0.72, blue: 0.65)
    static let orange = Color(red: 0.86, green: 0.28, blue: 0.0)
    static let text = Color(red: 0.97, green: 0.97, blue: 0.98)
    static let muted = Color(red: 0.66, green: 0.67, blue: 0.72)
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
