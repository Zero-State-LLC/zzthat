import PhotosUI
import SwiftUI
import ZZAPI
import ZZCore

enum CreateRoute: Hashable {
    case minted(MintedCode)
    case writeCheck(String)
}

struct CreateTab: View {
    @State private var path: [CreateRoute] = []
    @State private var model = CreateModel()

    var body: some View {
        NavigationStack(path: $path) {
            CreateRoot(model: model, path: $path)
                .navigationTitle(Text(.navCreate))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { AccountToolbarItem() }
                .navigationDestination(for: CreateRoute.self) { route in
                    switch route {
                    case .minted(let code): MintedRoute(code: code, path: $path)
                    case .writeCheck(let canonical): WriteCheckRoute(canonical: canonical)
                    }
                }
        }
    }
}

struct CreateRoot: View {
    @Environment(AppModel.self) private var app
    @Environment(Sheets.self) private var sheets
    @Bindable var model: CreateModel
    @Binding var path: [CreateRoute]
    @State private var checking = true

    var body: some View {
        CreateScreen(title: $model.title, bodyText: $model.body,
                     state: CreateScreenState(gate: gate, phase: model.phase, offline: !app.isOnline, demo: app.isDemo),
                     onSubmit: submit, onSignIn: { sheets.signIn = true })
            .task {
                // GET /v1 before Create (spec.md, API contract).
                model.loadDraft(app: app)
                await app.refreshDiscovery()
                checking = false
            }
            .onChange(of: model.title) { if !app.isOnline { model.saveDraft(app: app) } }
            .onChange(of: model.body) { if !app.isOnline { model.saveDraft(app: app) } }
            .onChange(of: app.isOnline) { _, online in if !online, model.hasDraft { model.saveDraft(app: app) } }
    }

    private var gate: CreateScreenState.Gate {
        if model.mintUnavailable || app.discovery?.freePublic == false { return .unavailable }
        if checking, app.discovery == nil, app.isOnline { return .loading }
        return app.isSignedIn ? .form : .signedOut
    }

    private func submit() {
        Task {
            if let code = await model.mint(app: app) { path.append(.minted(code)) }
        }
    }
}

struct MintedRoute: View {
    @Environment(AppModel.self) private var app
    @State private var model: MintedModel
    @Binding var path: [CreateRoute]

    init(code: MintedCode, path: Binding<[CreateRoute]>) {
        _model = State(initialValue: MintedModel(code: code))
        _path = path
    }

    var body: some View {
        MintedScreen(state: MintedScreenState(canonical: model.code.canonical, checkWord: model.code.checkWord,
                                              capReached: model.capReached, phase: model.phase,
                                              offline: !app.isOnline, demo: app.isDemo),
                     onReroll: { Task { await model.reroll(app: app) } },
                     onWriteCheck: { path.append(.writeCheck(model.code.canonical)) })
            .navigationBarTitleDisplayMode(.inline)
    }
}

struct WriteCheckRoute: View {
    @Environment(AppModel.self) private var app
    @State private var model: WriteCheckModel
    @State private var pickerItem: PhotosPickerItem?
    @State private var showingCamera = false

    init(canonical: String) {
        _model = State(initialValue: WriteCheckModel(canonical: canonical))
    }

    var body: some View {
        WriteCheckScreen(state: WriteCheckScreenState(canonical: model.canonical, photo: model.photo,
                                                      candidates: model.candidates, reading: model.reading,
                                                      verdict: model.verdict, message: model.message,
                                                      demo: app.isDemo, cameraAvailable: CameraPicker.isAvailable),
                         pickerItem: $pickerItem, onTakePhoto: takePhoto, onSelect: { model.pick($0) })
            .navigationTitle(Text(.mintedWriteCheck))
            .navigationBarTitleDisplayMode(.inline)
            .fullScreenCover(isPresented: $showingCamera) {
                CameraPicker { image in
                    showingCamera = false
                    if let image { Task { await model.read(image, app: app) } }
                }
                .ignoresSafeArea()
            }
            .onChange(of: pickerItem) { _, item in
                guard let item else { return }
                pickerItem = nil
                Task {
                    if let data = try? await item.loadTransferable(type: Data.self), let image = UIImage(data: data) {
                        await model.read(image, app: app)
                    }
                }
            }
            .onDisappear { model.end(app: app) }
    }

    private func takePhoto() {
        if app.isDemo {
            // The fixture photo shows the minted code, drawn on the device.
            let image = DemoPhoto.make(lines: [model.canonical]).image
            Task { await model.read(image, app: app) }
            return
        }
        Task {
            if await CameraPicker.requestAccess() { showingCamera = true } else { model.cameraIsOff() }
        }
    }
}
