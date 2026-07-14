import SwiftUI
import AVKit
import Combine

struct PlayerView: View {
    let channel: Channel
    let neighbors: [Channel]

    @StateObject private var model: PlayerModel
    @State private var showControls = true
    @State private var hideToken = UUID()

    init(channel: Channel, neighbors: [Channel]) {
        self.channel = channel
        self.neighbors = neighbors
        _model = StateObject(wrappedValue: PlayerModel(channel: channel, neighbors: neighbors))
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            // Sin controles nativos de AVKit (play/pausa, etc.).
            SilentVideoView(player: model.player)
                .ignoresSafeArea()
                .onTapGesture {
                    withAnimation(.easeInOut(duration: 0.2)) { showControls = true }
                    scheduleHide()
                }

            if model.isBuffering {
                ProgressView()
                    .scaleEffect(1.4)
                    .tint(SenalColors.orange)
            }

            if let err = model.error {
                VStack(spacing: 12) {
                    Text(err)
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.center)
                        .padding()
                    Button("Reintentar") { model.retry() }
                        .buttonStyle(.borderedProminent)
                        .tint(SenalColors.orange)
                }
                .padding()
                .background(.black.opacity(0.55))
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }

            if showControls {
                VStack {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(model.current.name)
                                .font(.headline)
                                .foregroundStyle(.white)
                            Text(model.current.group)
                                .font(.caption)
                                .foregroundStyle(SenalColors.muted)
                        }
                        Spacer()
                        Text("EN VIVO")
                            .font(.caption2.bold())
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(SenalColors.orange)
                            .clipShape(Capsule())
                    }
                    .padding()
                    .background(
                        LinearGradient(colors: [.black.opacity(0.7), .clear], startPoint: .top, endPoint: .bottom)
                    )

                    Spacer()

                    HStack(spacing: 28) {
                        Button {
                            model.playNeighbor(-1)
                            flashControls()
                        } label: {
                            Label("CH−", systemImage: "chevron.left.circle.fill")
                        }
                        Button {
                            model.togglePlay()
                            flashControls()
                        } label: {
                            Image(systemName: model.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                                .font(.system(size: 44))
                        }
                        Button {
                            model.playNeighbor(1)
                            flashControls()
                        } label: {
                            Label("CH+", systemImage: "chevron.right.circle.fill")
                        }
                    }
                    .foregroundStyle(.white)
                    .padding(.bottom, 28)
                    .frame(maxWidth: .infinity)
                    .background(
                        LinearGradient(colors: [.clear, .black.opacity(0.75)], startPoint: .top, endPoint: .bottom)
                    )
                }
                .transition(.opacity)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            model.start()
            flashControls()
        }
        .onDisappear { model.stop() }
        .onChange(of: model.current.id) { _ in
            flashControls()
        }
        .statusBarHidden(true)
    }

    private func flashControls() {
        withAnimation(.easeInOut(duration: 0.2)) { showControls = true }
        scheduleHide()
    }

    private func scheduleHide() {
        let token = UUID()
        hideToken = token
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 5_000_000_000)
            guard hideToken == token else { return }
            withAnimation(.easeOut(duration: 0.35)) { showControls = false }
        }
    }
}

@MainActor
final class PlayerModel: ObservableObject {
    @Published var current: Channel
    @Published var isPlaying = true
    @Published var isBuffering = true
    @Published var error: String?

    let player = AVPlayer()
    private var neighbors: [Channel]
    private var obs: NSKeyValueObservation?
    private var itemObs: NSObjectProtocol?

    init(channel: Channel, neighbors: [Channel]) {
        self.current = channel
        self.neighbors = neighbors.isEmpty ? [channel] : neighbors
    }

    func start() {
        play(current)
    }

    func stop() {
        player.pause()
        if let itemObs { NotificationCenter.default.removeObserver(itemObs) }
        obs = nil
    }

    func retry() {
        play(current)
    }

    func togglePlay() {
        if player.timeControlStatus == .playing {
            player.pause()
            isPlaying = false
        } else {
            player.play()
            isPlaying = true
        }
    }

    func playNeighbor(_ delta: Int) {
        guard let idx = neighbors.firstIndex(where: { $0.id == current.id }) else { return }
        let next = (idx + delta + neighbors.count) % neighbors.count
        play(neighbors[next])
    }

    private func play(_ channel: Channel) {
        current = channel
        error = nil
        isBuffering = true

        SenalAudio.prepare(player)

        var headers: [String: String] = [:]
        if let ua = channel.userAgent, !ua.isEmpty {
            headers["User-Agent"] = ua
        }
        if let ref = channel.referrer, !ref.isEmpty {
            headers["Referer"] = ref
        }

        let asset: AVURLAsset
        if headers.isEmpty {
            asset = AVURLAsset(url: channel.url)
        } else {
            asset = AVURLAsset(url: channel.url, options: ["AVURLAssetHTTPHeaderFieldsKey": headers])
        }
        let item = AVPlayerItem(asset: asset)
        // Prefer first audio+video when the mux offers several tracks.
        item.preferredForwardBufferDuration = 4
        player.replaceCurrentItem(with: item)
        player.isMuted = false
        player.volume = 1.0
        player.play()
        isPlaying = true

        obs = item.observe(\.status, options: [.new]) { [weak self] item, _ in
            Task { @MainActor in
                guard let self else { return }
                switch item.status {
                case .readyToPlay:
                    SenalAudio.prepare(self.player)
                    self.player.play()
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
}