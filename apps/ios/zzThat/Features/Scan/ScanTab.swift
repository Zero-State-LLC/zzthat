import PhotosUI
import SwiftUI
import ZZCore

/// The Scan tab's routes (runtime.md, Navigation).
enum ScanRoute: Hashable {
    case type
    case clarify(ClarifyInput)
    case resolve(String)
}

/// Opens a canonical code. Offline with nothing cached, resolve waits and
/// the screen says so (US6 acceptance 4); a cached copy still opens.
@MainActor
func openCanonical(_ canonical: String, app: AppModel, path: Binding<[ScanRoute]>) -> CopyKey? {
    if !app.isOnline, case .notOnDevice = ResolveLoader.offline(canonical, app: app) {
        app.lastCanonical.value = canonical
        return .resolveNotOnDevice
    }
    path.wrappedValue.append(.resolve(canonical))
    return nil
}

struct ScanTab: View {
    @State private var path: [ScanRoute] = []
    @State private var scan = ScanModel()

    var body: some View {
        NavigationStack(path: $path) {
            ScanRoot(scan: scan, path: $path)
                .navigationTitle(Text(.navScan))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { AccountToolbarItem() }
                .navigationDestination(for: ScanRoute.self) { route in
                    switch route {
                    case .type: TypeRoute(path: $path)
                    case .clarify(let input): ClarifyRoute(input: input, path: $path)
                    case .resolve(let canonical): ResolveRoute(canonical: canonical)
                    }
                }
        }
    }
}

struct ScanRoot: View {
    @Environment(AppModel.self) private var app
    let scan: ScanModel
    @Binding var path: [ScanRoute]
    @State private var pickerItem: PhotosPickerItem?
    @State private var showingCamera = false

    var body: some View {
        ScanScreen(state: ScanScreenState(photo: scan.photo, candidates: scan.candidates, reading: scan.reading,
                                          message: scan.message, demo: app.isDemo,
                                          cameraAvailable: CameraPicker.isAvailable),
                   pickerItem: $pickerItem,
                   onTakePhoto: takePhoto,
                   onTypeInstead: { path.append(.type) },
                   onSelect: select)
            .fullScreenCover(isPresented: $showingCamera) {
                CameraPicker { image in
                    showingCamera = false
                    if let image { Task { await read(image) } }
                }
                .ignoresSafeArea()
            }
            .onChange(of: pickerItem) { _, item in
                guard let item else { return }
                pickerItem = nil
                Task {
                    guard let data = try? await item.loadTransferable(type: Data.self),
                          let image = UIImage(data: data) else {
                        scan.message = .scanNoCode
                        return
                    }
                    await read(image)
                }
            }
            .onDisappear { if path.isEmpty { scan.end(app: app) } }
    }

    private func takePhoto() {
        if app.isDemo {
            Task {
                if let canonical = await scan.readDemoPhoto(app: app) { open(canonical) }
            }
            return
        }
        Task {
            if await CameraPicker.requestAccess() {
                showingCamera = true
            } else {
                scan.cameraIsOff()
            }
        }
    }

    private func read(_ image: UIImage) async {
        if let canonical = await scan.read(image, app: app) { open(canonical) }
    }

    private func select(_ candidate: ReadCandidate) {
        switch CandidateAction.of(candidate, source: .camera) {
        case .resolve(let canonical): open(canonical)
        case .clarify(let input): path.append(.clarify(input))
        case .message(let key): scan.message = key
        }
    }

    private func open(_ canonical: String) {
        scan.message = openCanonical(canonical, app: app, path: $path)
    }
}

struct TypeRoute: View {
    @Environment(AppModel.self) private var app
    @Binding var path: [ScanRoute]
    @State private var model = TypeModel()

    var body: some View {
        TypeScreen(text: $model.text, state: TypeScreenState(message: model.message, demo: app.isDemo),
                   onOpen: open)
            .navigationTitle(Text(.typeFieldLabel))
            .navigationBarTitleDisplayMode(.inline)
            .onAppear {
                if app.isDemo, model.text.isEmpty { model.text = DemoPhoto.canonical }
            }
            .onChange(of: model.text) { model.message = nil }
    }

    private func open() {
        switch model.action(app: app) {
        case .resolve(let canonical): model.message = openCanonical(canonical, app: app, path: $path)
        case .clarify(let input): path.append(.clarify(input))
        case .message(let key): model.message = key
        }
    }
}

struct ClarifyRoute: View {
    @Environment(AppModel.self) private var app
    @Binding var path: [ScanRoute]
    @State private var model: ClarifyModel
    @State private var message: CopyKey?

    init(input: ClarifyInput, path: Binding<[ScanRoute]>) {
        _model = State(initialValue: ClarifyModel(input: input))
        _path = path
    }

    var body: some View {
        let step = model.step(app: app)
        VStack(spacing: 0) {
            ClarifyScreen(state: ClarifyScreenState(parts: model.parts, step: step, demo: app.isDemo),
                          onPick: { word, position in model.pick(word, at: position); advance() },
                          onKeep: { position in model.keepAsWritten(position); advance() },
                          onConfirm: { model.confirm(); advance() },
                          onTypeInstead: { replaceTop(with: .type) },
                          onRetake: { path.removeAll() })
            if let message { Message(key: message).padding() }
        }
        .navigationTitle(Text(.scanClarifyTitle))
        .navigationBarTitleDisplayMode(.inline)
    }

    private func advance() {
        if case .done(let canonical) = model.step(app: app) {
            if let key = openCanonical(canonical, app: app, path: $path) { message = key }
        }
    }

    private func replaceTop(with route: ScanRoute) {
        if !path.isEmpty { path.removeLast() }
        path.append(route)
    }
}
