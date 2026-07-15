import SwiftUI
import AVFoundation
import Combine

/// Guía estilo FLUJO: categorías | canales | vídeo de fondo sin pausar.
struct LiveView: View {
    @EnvironmentObject private var catalog: CatalogStore
    @StateObject private var preview = GuidePreviewModel()
    @State private var selectedCategory: String?
    @State private var focusedId: String?
    @State private var fullscreen: Channel?
    @State private var focusTask: Task<Void, Never>?

    private var categories: [CategoryInfo] { catalog.categories }

    private var channels: [Channel] {
        catalog.channels(in: selectedCategory ?? catalog.selectedCategory)
    }

    var body: some View {
        GeometryReader { geo in
            let landscape = geo.size.width > geo.size.height
            ZStack {
                Color.black.ignoresSafeArea()

                if let player = preview.player {
                    SilentVideoView(player: player)
                        .ignoresSafeArea()
                }

                // Scrim for readable lists
                LinearGradient(
                    colors: [
                        .black.opacity(0.82),
                        .black.opacity(0.45),
                        .black.opacity(0.12),
                        .clear
                    ],
                    startPoint: .leading,
                    endPoint: .trailing
                )
                .ignoresSafeArea()
                .allowsHitTesting(false)

                if landscape {
                    landscapeGuide
                } else {
                    portraitGuide
                }

                if preview.isBuffering {
                    ProgressView()
                        .tint(SenalColors.orange)
                        .scaleEffect(1.2)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .trailing)
                        .padding(.trailing, 36)
                }
            }
        }
        .navigationBarHidden(true)
        .statusBarHidden(true)
        .onAppear {
            if selectedCategory == nil {
                selectedCategory = CatalogRules.defaultCategory(categories) ?? catalog.selectedCategory
            }
            if preview.current == nil, let first = channels.first {
                focusedId = first.id
                preview.play(first)
            }
        }
        .onChange(of: selectedCategory) { newValue in
            catalog.selectedCategory = newValue
            // No pausamos el vídeo; solo esperamos la nueva selección de canal.
            if let first = catalog.channels(in: newValue).first, preview.current == nil {
                focusedId = first.id
                preview.play(first)
            }
        }
        .onChange(of: focusedId) { newId in
            guard let newId else { return }
            focusTask?.cancel()
            focusTask = Task {
                try? await Task.sleep(nanoseconds: 220_000_000)
                guard !Task.isCancelled else { return }
                if let ch = channels.first(where: { $0.id == newId }) {
                    await MainActor.run { preview.play(ch) }
                }
            }
        }        .fullScreenCover(item: $fullscreen) { ch in
            ZStack(alignment: .topLeading) {
                PlayerView(channel: ch, neighbors: channels)
                Button {
                    fullscreen = nil
                } label: {
                    Label("Cerrar", systemImage: "xmark.circle.fill")
                        .labelStyle(.iconOnly)
                        .font(.title)
                        .foregroundStyle(.white)
                        .padding(16)
                }
            }
            .ignoresSafeArea()
        }
    }

    private var landscapeGuide: some View {
        HStack(alignment: .top, spacing: 12) {
            categoryPanel
                .frame(width: 200)
            channelPanel
                .frame(width: 320)
            nowPlayingBanner
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
                .padding(.leading, 8)
        }
        .padding(14)
    }

    private var portraitGuide: some View {
        VStack(spacing: 0) {
            categoryPanel
                .frame(height: 160)
            channelPanel
                .frame(maxHeight: .infinity)
            nowPlayingBanner
                .padding(12)
        }
        .padding(.top, 8)
    }

    private var categoryPanel: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("CATEGORÍAS")
                .font(.caption.bold())
                .foregroundStyle(SenalColors.orange)
                .tracking(1.2)
                .padding(.horizontal, 8)

            ScrollView {
                LazyVStack(spacing: 4) {
                    ForEach(categories) { cat in
                        let active = cat.name == selectedCategory
                        Button {
                            selectedCategory = cat.name
                        } label: {
                            Text(cat.name)
                                .font(.subheadline.weight(active ? .bold : .medium))
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 11)
                                .background(active ? SenalColors.orange : Color.white.opacity(0.06))
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .padding(10)
        .background(Color.black.opacity(0.55))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private var channelPanel: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text((selectedCategory ?? "CANALES").uppercased())
                .font(.caption.bold())
                .foregroundStyle(SenalColors.text)
                .lineLimit(1)
                .padding(.horizontal, 8)

            ScrollView {
                LazyVStack(spacing: 3) {
                    ForEach(channels) { ch in
                        GuideChannelRow(
                            channel: ch,
                            selected: ch.id == preview.current?.id,
                            focused: ch.id == focusedId
                        ) {
                            focusedId = ch.id
                        } onOpen: {
                            focusedId = ch.id
                            preview.play(ch)
                            fullscreen = ch
                        }
                    }
                }
            }
        }
        .padding(10)
        .background(Color.black.opacity(0.52))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private var nowPlayingBanner: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(preview.current?.name ?? "SEÑAL EN VIVO")
                .font(.headline.bold())
                .foregroundStyle(.white)
                .lineLimit(1)
            Text(preview.error ?? (preview.isBuffering ? "Sintonizando…" : "Sin pausar el reproductor · toca un canal para pantalla completa"))
                .font(.caption)
                .foregroundStyle(preview.error == nil ? SenalColors.orange : .red.opacity(0.9))
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            LinearGradient(colors: [.clear, .black.opacity(0.75)], startPoint: .top, endPoint: .bottom)
        )
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

private struct GuideChannelRow: View {
    let channel: Channel
    let selected: Bool
    let focused: Bool
    var onFocus: () -> Void
    var onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            HStack(spacing: 10) {
                Text("\(channel.number)")
                    .font(.caption.bold().monospacedDigit())
                    .foregroundStyle(focused || selected ? .white : SenalColors.orange)
                    .frame(width: 36, alignment: .leading)

                ZStack {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(SenalColors.graphite)
                        .frame(width: 36, height: 28)
                    if let logo = channel.logo, let url = URL(string: logo) {
                        AsyncImage(url: url) { phase in
                            if case .success(let img) = phase {
                                img.resizable().scaledToFit().padding(3)
                            }
                        }
                        .frame(width: 36, height: 28)
                    }
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(channel.name)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                    Text("No información")
                        .font(.caption2)
                        .foregroundStyle(Color.white.opacity(focused ? 0.85 : 0.55))
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(rowBackground)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(selected && !focused ? SenalColors.orange.opacity(0.7) : .clear, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .simultaneousGesture(TapGesture().onEnded { onFocus() })
    }

    private var rowBackground: Color {
        if focused { return SenalColors.orange }
        if selected { return SenalColors.orange.opacity(0.35) }
        return Color.white.opacity(0.06)
    }
}

@MainActor
final class GuidePreviewModel: ObservableObject {
    @Published var current: Channel?
    @Published var isBuffering = false
    @Published var error: String?

    private(set) var player: AVPlayer?
    private var obs: NSKeyValueObservation?
    private var itemObs: NSObjectProtocol?

    func play(_ channel: Channel) {
        if current?.id == channel.id, player?.currentItem != nil {
            player?.play()
            return
        }
        current = channel
        error = nil
        isBuffering = true

        var headers: [String: String] = [:]
        if let ua = channel.userAgent, !ua.isEmpty { headers["User-Agent"] = ua }
        if let ref = channel.referrer, !ref.isEmpty { headers["Referer"] = ref }

        let asset: AVURLAsset
        if headers.isEmpty {
            asset = AVURLAsset(url: channel.url)
        } else {
            asset = AVURLAsset(url: channel.url, options: ["AVURLAssetHTTPHeaderFieldsKey": headers])
        }
        let item = AVPlayerItem(asset: asset)

        if player == nil {
            player = AVPlayer(playerItem: item)
        } else {
            player?.replaceCurrentItem(with: item)
        }
        player?.play()

        obs?.invalidate()
        obs = item.observe(\.status, options: [.new]) { [weak self] item, _ in
            Task { @MainActor in
                guard let self else { return }
                switch item.status {
                case .readyToPlay:
                    self.isBuffering = false
                    self.error = nil
                case .failed:
                    self.isBuffering = false
                    self.error = item.error?.localizedDescription ?? "Señal interrumpida"
                default:
                    break
                }
            }
        }

        if let itemObs { NotificationCenter.default.removeObserver(itemObs) }
        itemObs = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemNewAccessLogEntry,
            object: item,
            queue: .main
        ) { [weak self] _ in
            self?.isBuffering = false
        }
    }

    deinit {
        obs?.invalidate()
        if let itemObs { NotificationCenter.default.removeObserver(itemObs) }
    }
}
