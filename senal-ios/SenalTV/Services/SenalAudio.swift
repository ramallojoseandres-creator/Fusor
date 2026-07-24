import AVFoundation

/// Activates AVAudioSession for IPTV playback.
/// Required on iPhone / LiveContainer: without `.playback`, video often has no sound
/// when the hardware mute switch is on.
enum SenalAudio {
    static func activatePlayback() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playback, mode: .moviePlayback, options: [])
            try session.setActive(true)
        } catch {
            try? session.setCategory(.playback)
            try? session.setActive(true)
        }
    }

    static func prepare(_ player: AVPlayer?) {
        activatePlayback()
        guard let player else { return }
        player.isMuted = false
        player.volume = 1.0
    }
}
