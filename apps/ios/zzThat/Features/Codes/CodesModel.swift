import Foundation
import Observation
import ZZAPI

/// My codes (US5, FR-021): `GET /v1/me/codes`, 50 a page, following
/// `next_cursor`. Owner responses are `no-store`, so no list is kept on the
/// device and offline shows `error.no_connection`.
@MainActor
@Observable
final class CodesModel {
    enum Phase: Hashable {
        case idle
        case loading
        case loaded
        case failure(CopyKey)
    }

    private(set) var rows: [OwnedCode] = []
    private(set) var nextCursor: String?
    private(set) var phase: Phase = .idle
    private(set) var loadingMore = false
    @ObservationIgnored private var loadedRevision: Int?

    func reloadIfNeeded(app: AppModel) async {
        guard loadedRevision != app.ownerRevision || phase == .idle else { return }
        await reload(app: app)
    }

    func reload(app: AppModel) async {
        loadedRevision = app.ownerRevision
        guard app.isSignedIn else {
            rows = []
            phase = .idle
            return
        }
        guard app.isOnline else {
            rows = []
            phase = .failure(.errorNoConnection)
            return
        }
        phase = .loading
        switch await app.service.myCodes() {
        case .success(let page):
            rows = page.codes
            nextCursor = page.nextCursor
            phase = .loaded
        case .failure(let failure):
            rows = []
            phase = .failure(Self.message(failure, app: app))
        }
    }

    func loadMore(app: AppModel) async {
        guard let cursor = nextCursor, !loadingMore, app.isOnline else { return }
        loadingMore = true
        defer { loadingMore = false }
        if case .success(let page) = await app.service.myCodes(cursor: cursor) {
            let known = Set(rows.map(\.id))
            rows += page.codes.filter { !known.contains($0.id) }
            nextCursor = page.nextCursor
        }
    }

    static func message(_ failure: APIFailure, app: AppModel) -> CopyKey {
        switch failure {
        case .noConnection: return .errorNoConnection
        case .unauthorized:
            app.needsSignIn()
            return .commonSignInNeeded
        default: return CopyKey(failure.message(for: .ownerRead))
        }
    }
}

/// Code detail (runtime.md, Navigation): the row by code id, then the title
/// and body from `GET /v1/records/{record_id}`. Contract 1 has no
/// single-code read.
@MainActor
@Observable
final class CodeDetailModel {
    enum Phase: Hashable {
        case loading
        case loaded
        case notFound
        case failure(CopyKey)
    }

    enum RevokePhase: Hashable {
        case idle
        case revoking
        case failure(CopyKey)
    }

    let codeID: String
    private(set) var row: OwnedCode?
    private(set) var record: OwnerRecord?
    private(set) var phase: Phase = .loading
    private(set) var revokePhase: RevokePhase = .idle

    init(codeID: String, row: OwnedCode?) {
        self.codeID = codeID
        self.row = row
    }

    func load(app: AppModel) async {
        guard app.isOnline else {
            phase = .failure(.errorNoConnection)
            return
        }
        phase = .loading
        if row == nil || record != nil {
            switch await app.service.ownedCode(id: codeID) {
            case .success(let found?): row = found
            case .success(nil):
                phase = .notFound
                return
            case .failure(let failure):
                phase = .failure(CodesModel.message(failure, app: app))
                return
            }
        }
        guard let row else { return }
        switch await app.service.record(id: row.recordId) {
        case .success(let value):
            record = value
            phase = .loaded
        case .failure(.notFound):
            phase = .notFound
        case .failure(let failure):
            phase = .failure(CodesModel.message(failure, app: app))
        }
    }

    /// Revoke posts the code id. Refused offline; a failure keeps the code
    /// active and says `error.nothing_saved`.
    func revoke(app: AppModel) async {
        guard let row else { return }
        guard app.isOnline else {
            revokePhase = .failure(.errorNoConnection)
            return
        }
        revokePhase = .revoking
        switch await app.service.revoke(codeID: row.id) {
        case .success:
            app.ownerChanged(canonical: row.canonical)
            revokePhase = .idle
            await load(app: app)
        case .failure(.noConnection):
            revokePhase = .failure(.errorNoConnection)
        case .failure(.unauthorized):
            app.needsSignIn()
            revokePhase = .failure(.commonSignInNeeded)
        case .failure(let failure):
            revokePhase = .failure(CopyKey(failure.message(for: .revoke)))
        }
    }
}

/// Edit record (design/UX.md): the current title and body, then one Save
/// that posts a version to the row's `record_id`.
@MainActor
@Observable
final class EditRecordModel {
    enum Phase: Hashable {
        case loading
        /// The record could not be read, so there are no fields to edit.
        case loadFailure(CopyKey)
        case editing
        case saving
        case notFound
        /// A save failed; the fields stay as typed.
        case failure(CopyKey)
    }

    let codeID: String
    private(set) var row: OwnedCode?
    var title = ""
    var body = ""
    private(set) var phase: Phase = .loading
    @ObservationIgnored private var loaded = false

    init(codeID: String, row: OwnedCode?) {
        self.codeID = codeID
        self.row = row
    }

    func loadOnce(app: AppModel) async {
        guard !loaded else { return }
        loaded = true
        guard app.isOnline else {
            phase = .loadFailure(.errorNoConnection)
            return
        }
        if row == nil {
            switch await app.service.ownedCode(id: codeID) {
            case .success(let found?): row = found
            case .success(nil):
                phase = .notFound
                return
            case .failure(let failure):
                phase = .loadFailure(CodesModel.message(failure, app: app))
                return
            }
        }
        guard let row else { return }
        switch await app.service.record(id: row.recordId) {
        case .success(let record):
            title = record.title
            body = record.body
            phase = .editing
        case .failure(.notFound):
            phase = .notFound
        case .failure(let failure):
            phase = .loadFailure(CodesModel.message(failure, app: app))
        }
    }

    /// `POST /v1/records/{record_id}/versions`. The fields stay on failure.
    func save(app: AppModel) async -> Bool {
        guard let row else { return false }
        guard !FieldLimits.isBlank(title) else {
            phase = .failure(.createTitleRequired)
            return false
        }
        guard app.isOnline else {
            phase = .failure(.errorNoConnection)
            return false
        }
        phase = .saving
        let result = await app.service.addVersion(recordID: row.recordId,
                                                  title: title.trimmingCharacters(in: .whitespacesAndNewlines),
                                                  body: body.trimmingCharacters(in: .whitespacesAndNewlines))
        switch result {
        case .success:
            app.ownerChanged(canonical: row.canonical)
            phase = .editing
            return true
        case .failure(.noConnection):
            phase = .failure(.errorNoConnection)
        case .failure(.unauthorized):
            app.needsSignIn()
            phase = .failure(.commonSignInNeeded)
        case .failure(let failure):
            phase = .failure(CopyKey(failure.message(for: .recordVersion)))
        }
        return false
    }
}
