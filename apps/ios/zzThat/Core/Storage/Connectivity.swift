import Foundation
import Network
import Observation

/// Whether the phone has a usable path to the network (US6). Offline, a
/// cached public view is labeled offline and writes are refused. A Demo build
/// makes no network call, so it is never offline.
@MainActor
@Observable
final class Connectivity {
    private(set) var isOnline: Bool
    @ObservationIgnored private var monitor: NWPathMonitor?

    init(isOnline: Bool = true) {
        self.isOnline = isOnline
    }

    /// Starts watching the system path. Not used in Demo builds or tests.
    func watch() {
        guard monitor == nil else { return }
        let monitor = NWPathMonitor()
        monitor.pathUpdateHandler = { [weak self] path in
            let online = path.status == .satisfied
            Task { @MainActor in self?.isOnline = online }
        }
        monitor.start(queue: DispatchQueue(label: "com.zer0state.zzthat.path"))
        self.monitor = monitor
    }

    func set(online: Bool) {
        isOnline = online
    }
}
