import SwiftUI
import ZZAPI
import ZZDesign

/// Resolve (US3, design/UX.md Resolve): the title and body as plain text, or
/// the one not-found state. Share sends the canonical code as text, never a
/// URL (FR-020). Report opens the FR-035 sheet.
struct ResolveScreen: View {
    let canonical: String
    let phase: ResolvePhase
    let demo: Bool
    var waiting = false
    var onRetry: () -> Void = {}
    var onReport: () -> Void = {}

    var body: some View {
        Screen(demo: demo, loading: isLoading) {
            switch phase {
            case .loading(let previous):
                if let previous { RecordBlock(view: previous) } else { CodeText(canonical: canonical) }
            case .record(let view, let offline):
                if offline { OfflineBanner() }
                RecordBlock(view: view)
                ResolveActions(shareText: view.shareText, onReport: onReport)
            case .notFound:
                CodeText(canonical: canonical)
                Message(key: .resolveNotFound)
            case .notOnDevice:
                CodeText(canonical: canonical)
                Message(key: .resolveNotOnDevice)
                RetryButton(waiting: false, action: onRetry)
            case .failure(let key, _):
                CodeText(canonical: canonical)
                Message(key: key)
                RetryButton(waiting: waiting, action: onRetry)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
    }

    private var isLoading: Bool {
        if case .loading = phase { return true }
        return false
    }
}

/// The record: the code in mono, then the title and the body, both as the
/// server sent them. The body is plain text and links in it are not tappable
/// (FR-046).
struct RecordBlock: View {
    let view: ResolvedView

    var body: some View {
        VStack(alignment: .leading, spacing: ZZSpace.spaceXs) {
            CodeText(canonical: view.canonical)
            Text(verbatim: view.title)
                .textRole(.heading)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("record.title")
            if !view.body.isEmpty {
                Text(verbatim: view.body)
                    .textRole(.body)
                    .textSelection(.enabled)
                    .accessibilityIdentifier("record.body")
            }
        }
        .panel()
    }
}

struct ResolveActions: View {
    let shareText: String
    let onReport: () -> Void

    var body: some View {
        VStack(spacing: ZZSpace.spaceXs) {
            ShareLink(item: shareText) {
                Text(.resolveShare)
            }
            .buttonStyle(.zzPrimary)
            .accessibilityIdentifier("resolve.share")
            Button(action: onReport) {
                Text(.resolveReport)
            }
            .buttonStyle(.zzSecondary)
            .accessibilityIdentifier("resolve.report")
        }
    }
}

struct RetryButton: View {
    let waiting: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(.commonTryAgain)
        }
        .buttonStyle(.zzSecondary)
        .disabled(waiting)
        .accessibilityIdentifier("common.try_again")
    }
}

/// The live Resolve route: loads once, retries on request, and opens Report.
struct ResolveRoute: View {
    @Environment(AppModel.self) private var app
    @State private var model: ResolveModel
    @State private var reporting = false

    init(canonical: String) {
        _model = State(initialValue: ResolveModel(canonical: canonical))
    }

    var body: some View {
        ResolveScreen(canonical: model.canonical, phase: model.phase, demo: app.isDemo, waiting: model.waiting,
                      onRetry: { Task { await model.load(app: app) } },
                      onReport: { reporting = true })
            .task { await model.loadOnce(app: app) }
            .sheet(isPresented: $reporting) {
                ReportRoute(canonical: model.canonical)
            }
    }
}
