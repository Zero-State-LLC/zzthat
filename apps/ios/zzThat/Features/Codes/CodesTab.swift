import SwiftUI
import ZZAPI

/// Code detail and Edit record take a code id, plus the row when My codes
/// already has it (runtime.md, Navigation).
enum CodesRoute: Hashable {
    case detail(id: String, row: OwnedCode?)
    case edit(id: String, row: OwnedCode?)
}

struct CodesTab: View {
    @Environment(AppModel.self) private var app
    @Environment(Sheets.self) private var sheets
    @State private var path: [CodesRoute] = []
    @State private var model = CodesModel()
    /// The code whose record was just saved, so Code detail says `edit.saved`.
    @State private var savedCodeID: String?

    var body: some View {
        NavigationStack(path: $path) {
            CodesScreen(state: CodesScreenState(signedIn: app.isSignedIn, rows: model.rows, phase: model.phase,
                                                demo: app.isDemo),
                        onSignIn: { sheets.signIn = true },
                        onSelect: { path.append(.detail(id: $0.id, row: $0)) },
                        onLastRow: { Task { await model.loadMore(app: app) } },
                        onRetry: { Task { await model.reload(app: app) } })
                .navigationTitle(Text(.navMyCodes))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { AccountToolbarItem() }
                .refreshable { await model.reload(app: app) }
                .task(id: TaskKey(signedIn: app.isSignedIn, revision: app.ownerRevision, online: app.isOnline)) {
                    await model.reloadIfNeeded(app: app)
                }
                .onChange(of: path) { if path.isEmpty { savedCodeID = nil } }
                .navigationDestination(for: CodesRoute.self) { route in
                    switch route {
                    case .detail(let id, let row):
                        CodeDetailRoute(codeID: id, row: row, savedCodeID: $savedCodeID, path: $path)
                    case .edit(let id, let row):
                        EditRecordRoute(codeID: id, row: row, savedCodeID: $savedCodeID, path: $path)
                    }
                }
        }
    }

    private struct TaskKey: Hashable {
        let signedIn: Bool
        let revision: Int
        let online: Bool
    }
}

struct CodeDetailRoute: View {
    @Environment(AppModel.self) private var app
    @State private var model: CodeDetailModel
    @Binding var savedCodeID: String?
    @Binding var path: [CodesRoute]
    @State private var confirming = false

    init(codeID: String, row: OwnedCode?, savedCodeID: Binding<String?>, path: Binding<[CodesRoute]>) {
        _model = State(initialValue: CodeDetailModel(codeID: codeID, row: row))
        _savedCodeID = savedCodeID
        _path = path
    }

    var body: some View {
        CodeDetailScreen(state: CodeDetailScreenState(row: model.row, record: model.record, phase: model.phase,
                                                      revokePhase: model.revokePhase,
                                                      saved: savedCodeID == model.codeID, demo: app.isDemo),
                         onEdit: { path.append(.edit(id: model.codeID, row: model.row)) },
                         onRevoke: { confirming = true },
                         onRetry: { Task { await model.load(app: app) } })
            .navigationBarTitleDisplayMode(.inline)
            .revokeConfirm(isPresented: $confirming) {
                Task { await model.revoke(app: app) }
            }
            .task(id: app.ownerRevision) { await model.load(app: app) }
    }
}

struct EditRecordRoute: View {
    @Environment(AppModel.self) private var app
    @State private var model: EditRecordModel
    @Binding var savedCodeID: String?
    @Binding var path: [CodesRoute]

    init(codeID: String, row: OwnedCode?, savedCodeID: Binding<String?>, path: Binding<[CodesRoute]>) {
        _model = State(initialValue: EditRecordModel(codeID: codeID, row: row))
        _savedCodeID = savedCodeID
        _path = path
    }

    var body: some View {
        EditRecordScreen(title: $model.title, bodyText: $model.body,
                         state: EditRecordScreenState(phase: model.phase, demo: app.isDemo, offline: !app.isOnline),
                         onSave: save)
            .navigationTitle(Text(.detailEdit))
            .navigationBarTitleDisplayMode(.inline)
            .task { await model.loadOnce(app: app) }
    }

    private func save() {
        Task {
            if await model.save(app: app) {
                // Back to Code detail with `edit.saved` (design/UX.md, Edit record).
                savedCodeID = model.codeID
                if !path.isEmpty { path.removeLast() }
            }
        }
    }
}
