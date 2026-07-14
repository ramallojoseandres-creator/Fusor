import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var catalog: CatalogStore

    private var preview: [Channel] {
        let cat = catalog.selectedCategory
        return Array(catalog.channels(in: cat).prefix(24))
    }

    var body: some View {
        NavigationStack {
            ZStack {
                SenalBackground()
                ScrollView {
                    VStack(alignment: .leading, spacing: 22) {
                        header
                        if catalog.isLoading {
                            ProgressView("Cargando lista local…")
                                .tint(SenalColors.violet)
                                .frame(maxWidth: .infinity)
                                .padding(.top, 40)
                        } else if let err = catalog.loadError {
                            Text(err).foregroundStyle(.red).padding()
                        } else {
                            Text("En vivo · \(catalog.selectedCategory ?? "General")")
                                .font(.headline)
                                .foregroundStyle(SenalColors.text)
                                .padding(.horizontal)

                            LazyVGrid(
                                columns: [GridItem(.adaptive(minimum: 110), spacing: 12)],
                                spacing: 12
                            ) {
                                ForEach(preview) { ch in
                                    NavigationLink(value: ch) {
                                        ChannelCard(channel: ch)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .padding(.horizontal)

                            if !catalog.movieChannels.isEmpty {
                                Text("Cine 24/7")
                                    .font(.headline)
                                    .foregroundStyle(SenalColors.text)
                                    .padding(.horizontal)
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 12) {
                                        ForEach(catalog.movieChannels.prefix(20)) { ch in
                                            NavigationLink(value: ch) {
                                                ChannelCard(channel: ch, compact: true)
                                            }
                                            .buttonStyle(.plain)
                                        }
                                    }
                                    .padding(.horizontal)
                                }
                            }
                        }
                    }
                    .padding(.vertical)
                }
            }
            .navigationTitle("SEÑAL")
            .navigationDestination(for: Channel.self) { ch in
                PlayerView(channel: ch, neighbors: catalog.channels(in: ch.group))
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Tu TV")
                .font(.system(size: 34, weight: .bold, design: .rounded))
                .foregroundStyle(SenalColors.text)
            Text("\(catalog.channels.count) canales · lista embebida")
                .font(.subheadline)
                .foregroundStyle(SenalColors.muted)
        }
        .padding(.horizontal)
        .padding(.top, 8)
    }
}

struct ChannelCard: View {
    let channel: Channel
    var compact: Bool = false

    var body: some View {
        VStack(spacing: 8) {
            ZStack {
                RoundedRectangle(cornerRadius: 14)
                    .fill(SenalColors.card)
                if let logo = channel.logo, let url = URL(string: logo) {
                    AsyncImage(url: url) { phase in
                        switch phase {
                        case .success(let img):
                            img.resizable().scaledToFit().padding(10)
                        default:
                            Text(String(channel.name.prefix(1)))
                                .font(.title.bold())
                                .foregroundStyle(SenalColors.violet)
                        }
                    }
                } else {
                    Text(String(channel.name.prefix(1)))
                        .font(.title.bold())
                        .foregroundStyle(SenalColors.violet)
                }
            }
            .frame(width: compact ? 100 : nil, height: compact ? 70 : 88)
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(SenalColors.violet.opacity(0.25), lineWidth: 1)
            )

            Text(channel.name)
                .font(.caption)
                .foregroundStyle(SenalColors.text)
                .lineLimit(2)
                .multilineTextAlignment(.center)
                .frame(width: compact ? 100 : nil)
        }
    }
}
