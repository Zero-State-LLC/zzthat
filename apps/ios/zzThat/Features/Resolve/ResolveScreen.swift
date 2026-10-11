import SwiftUI
import ZZAPI
import ZZDesign

/// Resolve (US3, design/UX.md Resolve): the title and body as plain text, or
/// the one not-found state. Share sends the canonical code as text, never a
/// URL (FR-020). Report opens the FR-035 sheet. A code hidden on this device
/// shows `resolve.hidden` and Unhide instead of its title and body, and a code
/// whose creator is blocked shows `resolve.blocked` and Unblock (FR-035).
/// Save keeps the code in the device-only Saved list (FR-050).
struct ResolveScreen: View {
    let canonical: String
    let phase: ResolvePhase
    let demo: Bool
    var hidden = false
    var blocked = false
    var saved = false
    var waiting = false
    var onRetry: () -> Void = {}
    var onReport: () -> Void = {}
    var onHide: () -> Void = {}
    var onUnhide: () -> Void = {}
    var onBlock: () -> Void = {}
    var onUnblock: () -> Void = {}
    var onSave: () -> Void = {}
    var onUnsave: () -> Void = {}

    var body: some View {
        Screen(demo: demo, loading: isLoading) {
            switch phase {
            case .loading(let previous):
                if let previous, !hidden, !blocked { RecordBlock(view: previous) } else { CodeText(canonical: canonical) }
            case .record(let view, let offline):
                if offline { OfflineBanner() }
                if blocked {
                    HiddenBlock(canonical: view.canonical, message: .resolveBlocked, action: .resolveUnblock,
                                identifier: "resolve.unblock", onAction: onUnblock)
                } else if hidden {
                    HiddenBlock(canonical: view.canonical, message: .resolveHidden, action: .resolveUnhide,
                                identifier: "resolve.unhide", onAction: onUnhide)
                } else {
                    RecordBlock(view: view)
                    ResolveActions(shareText: view.shareText, saved: saved, canBlock: view.creator != nil,
                                   onReport: onReport, onHide: onHide, onBlock: onBlock, onSave: onSave,
                                   onUnsave: onUnsave)
                }
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

/// A hidden code, or a code whose creator is blocked (FR-035): the code, the
/// message, and Unhide or Unblock. The title and body are not drawn.
struct HiddenBlock: View {
    let canonical: String
    let message: CopyKey
    let action: CopyKey
    let identifier: String
    let onAction: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: ZZSpace.spaceXs) {
            CodeText(canonical: canonical)
            Message(key: message)
                .accessibilityIdentifier(message.rawValue)
        }
        .panel()
        Button(action: onAction) {
            Text(action)
        }
        .buttonStyle(.zzSecondary)
        .accessibilityIdentifier(identifier)
    }
}

struct ResolveActions: View {
    let shareText: String
    let saved: Bool
    /// Block this creator shows only when Resolve named a creator (RM-112).
    let canBlock: Bool
    let onReport: () -> Void
    let onHide: () -> Void
    let onBlock: () -> Void
    let onSave: () -> Void
    let onUnsave: () -> Void

    var body: some View {
        VStack(spacing: ZZSpace.spaceXs) {
            ShareLink(item: shareText) {
                Text(.resolveShare)
            }
            .buttonStyle(.zzPrimary)
            .accessibilityIdentifier("resolve.share")
            Button(action: saved ? onUnsave : onSave) {
                Text(saved ? .resolveUnsave : .resolveSave)
            }
            .buttonStyle(.zzSecondary)
            .accessibilityIdentifier(saved ? "resolve.unsave" : "resolve.save")
            Button(action: onReport) {
                Text(.resolveReport)
            }
            .buttonStyle(.zzSecondary)
            .accessibilityIdentifier("resolve.report")
            Button(action: onHide) {
                Text(.resolveHide)
            }
            .buttonStyle(.zzSecondary)
            .accessibilityIdentifier("resolve.hide")
            if canBlock {
                Button(action: onBlock) {
                    Text(.resolveBlockCreator)
                }
                .buttonStyle(.zzSecondary)
                .accessibilityIdentifier("resolve.block_creator")
            }
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
        ResolveScreen(canonical: model.canonical, phase: model.phase, demo: app.isDemo,
                      hidden: app.isHidden(model.canonical), blocked: app.isBlocked(creator: creator),
                      saved: app.isSaved(model.canonical), waiting: model.waiting,
                      onRetry: { Task { await model.load(app: app) } },
                      onReport: { reporting = true },
                      onHide: { app.hide(model.canonical) },
                      onUnhide: { app.unhide(model.canonical) },
                      onBlock: { if let creator { app.block(creator: creator) } },
                      onUnblock: { if let creator { app.unblock(creator: creator) } },
                      onSave: { app.save(model.canonical) },
                      onUnsave: { app.unsave(model.canonical) })
            .task { await model.loadOnce(app: app) }
            .sheet(isPresented: $reporting) {
                ReportRoute(canonical: model.canonical, creator: creator)
            }
    }

    /// The creator of the record on screen, if Resolve named one (RM-112).
    private var creator: String? {
        switch model.phase {
        case .record(let view, _): return view.creator
        case .loading(let previous?): return previous.creator
        default: return nil
        }
    }
}
