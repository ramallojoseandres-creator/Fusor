import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var catalog: CatalogStore

    private var previewChannel: Channel? {
        let cat = catalog.selectedCategory
        return catalog.channels(in: cat).first
    }

    var body: some View {
        NavigationStack {
            ZStack {
                SenalBackground()

                // Atmosphere gradient (phone has no live bleed player on hub).
                LinearGradient(
                    colors: [
                        Color.black.opacity(0.2),
                        Color.black.opacity(0.55),
                        Color.black.opacity(0.9)
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()

                VStack(alignment: .leading, spacing: 0) {
                    Text("SEÑAL")
                        .font(.system(size: 32, weight: .black))
                        .tracking(6)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 20)
                        .padding(.top, 12)

                    Spacer()

                    VStack(alignment: .leading, spacing: 8) {
                        Text(previewChannel?.name ?? "SEÑAL · En vivo")
                            .font(.system(size: 26, weight: .bold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                        Text("Continuar viendo")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(SenalColors.teal)
                    }
                    .padding(.horizontal, 20)

                    HStack(spacing: 10) {
                        HubLink(title: "VIVO", systemImage: "tv.fill", tint: SenalColors.blue) {
                            LiveView()
                        }
                        HubLink(title: "PELÍCULAS", systemImage: "film.fill", tint: SenalColors.blueSoft) {
                            MoviesView()
                        }
                        HubLink(title: "SERIES", systemImage: "play.rectangle.fill", tint: SenalColors.blueDeep) {
                            SeriesPlaceholder()
                        }
                        HubLink(title: "AJUSTES", systemImage: "gearshape.fill", tint: SenalColors.teal) {
                            SettingsView()
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 28)
                    .padding(.bottom, 20)
                }
            }
            .navigationBarHidden(true)
            .navigationDestination(for: Channel.self) { ch in
                PlayerView(channel: ch, neighbors: catalog.channels(in: ch.group))
            }
        }
    }
}

private struct HubLink<Dest: View>: View {
    let title: String
    let systemImage: String
    let tint: Color
    @ViewBuilder let destination: () -> Dest

    var body: some View {
        NavigationLink {
            destination()
        } label: {
            VStack(spacing: 8) {
                Image(systemName: systemImage)
                    .font(.system(size: 18, weight: .semibold))
                Text(title)
                    .font(.system(size: 11, weight: .bold))
                    .tracking(0.6)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(tint.opacity(0.85))
            .clipShape(RoundedRectangle(cornerRadius: 6))
        }
        .buttonStyle(.plain)
    }
}

private struct SeriesPlaceholder: View {
    var body: some View {
        ZStack {
            SenalBackground()
            Text("Series próximamente")
                .foregroundStyle(SenalColors.muted)
        }
        .navigationTitle("Series")
    }
}
