import GoogleSignIn
import SwiftUI
import ZZDesign

@main
struct ZZThatApp: App {
    @State private var app = AppModel.launch()
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // The pinned IBM Plex files, when the pin has them; otherwise the
        // system font (design.md, Sync).
        ZZFontRegistry.registerBundledFonts()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(app)
                .task { await app.start() }
                .onOpenURL { url in
                    // GoogleSignIn finishes its flow through the app's URL scheme.
                    if app.config.googleClientID != nil { GIDSignIn.sharedInstance.handle(url) }
                }
                .onChange(of: scenePhase) { _, phase in
                    // `GET /v1` again on return to the foreground (spec.md, API contract).
                    if phase == .active { Task { await app.refreshDiscovery() } }
                }
        }
    }
}

/// The sheets any tab can open: Account from the toolbar, and Sign in from
/// Create, My codes, or a 401 on a write.
@MainActor
@Observable
final class Sheets {
    var account = false
    var signIn = false
}

enum AppTab: Hashable {
    case scan
    case create
    case codes
}

/// Three tabs: Scan, Create, My codes (runtime.md, Navigation).
struct RootView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.colorScheme) private var scheme
    @State private var sheets = Sheets()
    @State private var tab: AppTab = .scan

    var body: some View {
        @Bindable var sheets = sheets
        TabView(selection: $tab) {
            ScanTab()
                .tabItem { Label { Text(.navScan) } icon: { Image(systemName: "camera.viewfinder") } }
                .tag(AppTab.scan)
            CreateTab()
                .tabItem { Label { Text(.navCreate) } icon: { Image(systemName: "square.and.pencil") } }
                .tag(AppTab.create)
            CodesTab()
                .tabItem { Label { Text(.navMyCodes) } icon: { Image(systemName: "list.bullet.rectangle") } }
                .tag(AppTab.codes)
        }
        .tint(Palette(scheme).accentInk)
        .environment(sheets)
        .sheet(isPresented: $sheets.account) {
            AccountSheet().environment(sheets)
        }
        .sheet(isPresented: $sheets.signIn) {
            SignInRoute().environment(sheets)
        }
    }
}
