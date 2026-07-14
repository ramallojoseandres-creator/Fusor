import SwiftUI

struct MoviesView: View {
    @EnvironmentObject private var catalog: CatalogStore

    var body: some View {
        NavigationStack {
            ZStack {
                SenalBackground()
                List {
                    Section("Películas 24/7") {
                        ForEach(catalog.movieChannels) { ch in
                            NavigationLink(value: ch) { ChannelRow(channel: ch) }
                                .listRowBackground(SenalColors.elevated.opacity(0.7))
                        }
                    }
                    Section("Series 24/7") {
                        ForEach(catalog.seriesChannels) { ch in
                            NavigationLink(value: ch) { ChannelRow(channel: ch) }
                                .listRowBackground(SenalColors.elevated.opacity(0.7))
                        }
                    }
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("Cine / Series")
            .navigationDestination(for: Channel.self) { ch in
                PlayerView(channel: ch, neighbors: [ch])
            }
        }
    }
}

struct ChannelRow: View {
    let channel: Channel

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 10)
                    .fill(SenalColors.card)
                    .frame(width: 54, height: 40)
                if let logo = channel.logo, let url = URL(string: logo) {
                    AsyncImage(url: url) { phase in
                        if case .success(let img) = phase {
                            img.resizable().scaledToFit().padding(6)
                        }
                    }
                    .frame(width: 54, height: 40)
                }
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(channel.name)
                    .foregroundStyle(SenalColors.text)
                    .font(.body.weight(.medium))
                Text(channel.group)
                    .font(.caption)
                    .foregroundStyle(SenalColors.muted)
            }
            Spacer()
            Text("\(channel.number)")
                .font(.caption.monospacedDigit())
                .foregroundStyle(SenalColors.teal)
        }
        .padding(.vertical, 4)
    }
}

struct SearchView: View {
    @EnvironmentObject private var catalog: CatalogStore
    @State private var query = ""

    private var results: [Channel] {
        catalog.search(query)
    }

    var body: some View {
        NavigationStack {
            ZStack {
                SenalBackground()
                VStack {
                    TextField("Buscar canal…", text: $query)
                        .padding()
                        .background(SenalColors.card)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                        .padding()
                        .foregroundStyle(SenalColors.text)

                    List(results) { ch in
                        NavigationLink(value: ch) { ChannelRow(channel: ch) }
                            .listRowBackground(SenalColors.elevated.opacity(0.7))
                    }
                    .scrollContentBackground(.hidden)
                }
            }
            .navigationTitle("Buscar")
            .navigationDestination(for: Channel.self) { ch in
                PlayerView(channel: ch, neighbors: results)
            }
        }
    }
}

struct SettingsView: View {
    @EnvironmentObject private var session: SessionStore
    @EnvironmentObject private var catalog: CatalogStore

    var body: some View {
        NavigationStack {
            ZStack {
                SenalBackground()
                List {
                    Section("Cuenta") {
                        LabeledContent("Usuario", value: session.username ?? "—")
                        LabeledContent("Canales", value: "\(catalog.channels.count)")
                    }
                    Section("Datos") {
                        Text("Auth: solo login en el servidor SEÑAL")
                        Text("Catálogo filtrado (health-check) embebido")
                        Text("Streams: directo desde el iPhone al CDN")
                        Text("Intro: splash TU VENTANA AL MUNDO")
                        Text("TV en vivo: guía FLUJO sin pausar el vídeo")
                    }
                    Section {
                        Button(role: .destructive) {
                            session.logout()
                        } label: {
                            Text("Cerrar sesión")
                        }
                    }
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("Ajustes")
        }
    }
}
