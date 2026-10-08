import Foundation
import Testing
import UIKit
@testable import zzThat

private actor RecognitionStarted {
    private var started = false
    private var waiters: [CheckedContinuation<Void, Never>] = []

    func signal() {
        started = true
        let pending = waiters
        waiters.removeAll()
        for waiter in pending { waiter.resume() }
    }

    func wait() async {
        if started { return }
        await withCheckedContinuation { waiters.append($0) }
    }
}

@MainActor
@Suite("Scan photo lifecycle")
struct ScanLifecycleTests {
    @Test func endingScanCancelsRecognitionAndDoesNotRestorePhoto() async throws {
        let started = RecognitionStarted()
        let scan = ScanModel(recognize: { _ in
            await started.signal()
            try await Task.sleep(for: .seconds(30))
            return []
        })
        let app = TestApp.make(service: TestApp.demoService(), kind: .demo)
        let image = DemoPhoto.make(lines: [DemoPhoto.canonical]).image
        #expect(image.jpegData(compressionQuality: 0.9) != nil)

        let read = Task { await scan.read(image, app: app) }
        await started.wait()
        let scratchPhoto = try #require(app.photos.currentURL)
        #expect(scan.reading)
        #expect(FileManager.default.fileExists(atPath: scratchPhoto.path))

        scan.end(app: app)
        #expect(!scan.reading)
        #expect(scan.photo.map { _ in true } == nil)
        #expect(!FileManager.default.fileExists(atPath: scratchPhoto.path))

        let result = await read.value
        #expect(result == nil)
        #expect(scan.photo.map { _ in true } == nil)
        #expect(scan.candidates.isEmpty)
    }
}
