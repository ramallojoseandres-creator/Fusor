import SwiftUI
import AVKit
import AVFoundation
import UIKit
import Combine

/// Full-bleed AVPlayer without system chrome.
struct SilentVideoView: UIViewRepresentable {
    let player: AVPlayer

    func makeUIView(context: Context) -> PlayerUIView {
        PlayerUIView(player: player)
    }

    func updateUIView(_ uiView: PlayerUIView, context: Context) {
        uiView.playerLayer.player = player
    }

    final class PlayerUIView: UIView {
        let playerLayer = AVPlayerLayer()

        init(player: AVPlayer) {
            super.init(frame: .zero)
            backgroundColor = .black
            playerLayer.player = player
            playerLayer.videoGravity = .resizeAspectFill
            layer.addSublayer(playerLayer)
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) { fatalError() }

        override func layoutSubviews() {
            super.layoutSubviews()
            playerLayer.frame = bounds
        }
    }
}

/// Splash oficial SEÑAL — anillos de señal + «TU VENTANA AL MUNDO».
struct SplashView: View {
    var onFinished: () -> Void

    @StateObject private var model = SplashPlayerModel()
    @State private var visible = true

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if let player = model.player {
                SilentVideoView(player: player)
                    .ignoresSafeArea()
            } else {
                SplashFallback()
            }
        }
        .opacity(visible ? 1 : 0)
        .onAppear { model.start() }
        .onReceive(model.$didFinish) { done in
            guard done else { return }
            withAnimation(.easeOut(duration: 0.35)) { visible = false }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
                onFinished()
            }
        }
        .task {
            try? await Task.sleep(nanoseconds: 9_000_000_000)
            if !model.didFinish {
                model.didFinish = true
            }
        }
        .statusBarHidden(true)
    }
}

@MainActor
final class SplashPlayerModel: ObservableObject {
    @Published var didFinish = false
    private(set) var player: AVPlayer?

    private var endObs: NSObjectProtocol?

    func start() {
        guard player == nil else { return }
        let url =
            Bundle.main.url(forResource: "splash", withExtension: "mp4")
            ?? Bundle.main.bundleURL.appendingPathComponent("splash.mp4")
        guard FileManager.default.fileExists(atPath: url.path) else {
            // Let fallback paint for ~3.2s then finish
            DispatchQueue.main.asyncAfter(deadline: .now() + 3.2) {
                self.didFinish = true
            }
            return
        }
        let item = AVPlayerItem(url: url)
        let p = AVPlayer(playerItem: item)
        SenalAudio.prepare(p)
        player = p
        endObs = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: item,
            queue: .main
        ) { [weak self] _ in
            self?.didFinish = true
        }
        p.play()
    }

    deinit {
        if let endObs {
            NotificationCenter.default.removeObserver(endObs)
        }
    }
}

struct SplashFallback: View {
    @State private var titleProgress: CGFloat = 0
    @State private var lineWidth: CGFloat = 0
    @State private var tagOpacity: Double = 0
    @State private var ringPulse: CGFloat = 0.85

    private let cyan = Color(red: 0.35, green: 0.78, blue: 0.92)

    var body: some View {
        ZStack {
            Color.black
            RadialGradient(
                colors: [Color(red: 0.05, green: 0.10, blue: 0.22), .black],
                center: .center,
                startRadius: 20,
                endRadius: 420
            )
            ForEach(0..<6, id: \.self) { i in
                Ellipse()
                    .stroke(cyan.opacity(0.12 - Double(i) * 0.015), lineWidth: 1)
                    .frame(width: 160 + CGFloat(i) * 70, height: 70 + CGFloat(i) * 32)
                    .scaleEffect(ringPulse)
            }
            VStack(spacing: 14) {
                Text("SEÑAL")
                    .font(.system(size: 64, weight: .black, design: .default))
                    .tracking(8)
                    .foregroundStyle(.white)
                    .mask(
                        Rectangle()
                            .scaleEffect(x: titleProgress, y: 1, anchor: .leading)
                    )
                Capsule()
                    .fill(cyan)
                    .frame(width: lineWidth, height: 2)
                Text("Tu ventana al mundo")
                    .font(.system(size: 16, weight: .medium))
                    .tracking(2)
                    .foregroundStyle(cyan)
                    .opacity(tagOpacity)
            }
        }
        .ignoresSafeArea()
        .onAppear {
            withAnimation(.easeOut(duration: 1.1)) { titleProgress = 1 }
            withAnimation(.easeOut(duration: 0.7).delay(0.9)) { lineWidth = 220 }
            withAnimation(.easeOut(duration: 0.6).delay(1.2)) { tagOpacity = 1 }
            withAnimation(.easeInOut(duration: 1.4).repeatForever(autoreverses: true)) {
                ringPulse = 1.05
            }
        }
    }
}
