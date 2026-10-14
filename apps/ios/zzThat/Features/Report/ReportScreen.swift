import SwiftUI
import ZZAPI
import ZZDesign

/// Report (FR-035, design/UX.md Report): a sheet from Resolve with the four
/// reasons, an optional note of up to 500 characters, and one send. The
/// answer never says whether the code exists. The sent state says how reports
/// are reviewed and offers Hide this code, kept on this device (FR-035).
struct ReportState: Hashable {
    enum Phase: Hashable {
        case editing
        case sending
        case sent
        case failure(CopyKey)
    }

    var reason: ReportReason?
    var note = ""
    var phase: Phase = .editing
    var offline = false
    var demo = false
    /// The reported code is hidden on this device (FR-035).
    var hidden = false
    /// After a 429, Send waits for `Retry-After` (FR-033).
    var waiting = false

    var canSend: Bool { reason != nil && !offline && !waiting && phase != .sending && phase != .sent }
}

struct ReportScreen: View {
    @Binding var state: ReportState
    var onSend: () -> Void = {}
    var onDone: () -> Void = {}
    var onHide: () -> Void = {}

    var body: some View {
        Screen(demo: state.demo, loading: state.phase == .sending) {
            Text(.reportTitle)
                .textRole(.heading)
                .accessibilityAddTraits(.isHeader)
            if state.phase == .sent {
                Message(key: .reportSent)
                Message(key: .reportReview)
                    .accessibilityIdentifier("report.review")
                if state.hidden {
                    Message(key: .resolveHidden)
                } else {
                    Button(action: onHide) { Text(.resolveHide) }
                        .buttonStyle(.zzSecondary)
                        .accessibilityIdentifier("report.hide")
                }
                Button(action: onDone) { Text(.commonDone) }
                    .buttonStyle(.zzPrimary)
                    .accessibilityIdentifier("report.done")
            } else {
                ReasonPicker(selection: $state.reason)
                LimitedField(label: .reportNoteLabel, text: $state.note, limit: FieldLimits.reportNote,
                             multiline: true, identifier: "report.note", countsTrimmed: false)
                if case .failure(let key) = state.phase { Message(key: key) }
                if state.offline { Message(key: .errorNoConnection) }
                Button(action: onSend) { Text(.reportSend) }
                    .buttonStyle(.zzPrimary)
                    .disabled(!state.canSend)
                    .accessibilityIdentifier("report.send")
                Button(action: onDone) { Text(.commonCancel) }
                    .buttonStyle(.zzSecondary)
                    .accessibilityIdentifier("report.cancel")
            }
        }
    }
}

/// The four FR-035 reasons as one choice.
struct ReasonPicker: View {
    @Environment(\.colorScheme) private var scheme
    @Binding var selection: ReportReason?

    static let reasons: [ReportReason] = [.spam, .harassment, .personalData, .other]

    var body: some View {
        let palette = Palette(scheme)
        VStack(spacing: 0) {
            ForEach(Self.reasons, id: \.self) { reason in
                Button {
                    selection = reason
                } label: {
                    HStack {
                        Text(reason.copy).textRole(.body)
                        Spacer()
                        Image(systemName: selection == reason ? "largecircle.fill.circle" : "circle")
                            .foregroundStyle(selection == reason ? palette.accentInk : palette.ruleStrong)
                            .accessibilityHidden(true)
                    }
                    .padding(.horizontal, ZZSpace.spaceSm)
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selection == reason ? [.isSelected] : [])
                .accessibilityIdentifier("report.reason.\(reason.rawValue)")
                if reason != Self.reasons.last { Divider().overlay(palette.rule) }
            }
        }
        .background(palette.panel, in: RoundedRectangle(cornerRadius: ZZRadius.radiusChip))
        .overlay(RoundedRectangle(cornerRadius: ZZRadius.radiusChip).strokeBorder(palette.rule, lineWidth: ZZSpace.ruleHair))
    }
}

/// Sends one report (FR-035). The answer never says whether the code
/// exists. After a 429, Send waits for `Retry-After` (FR-033).
@MainActor
@Observable
final class ReportModel {
    let canonical: String
    var state = ReportState()

    init(canonical: String) {
        self.canonical = canonical
    }

    func send(app: AppModel) async {
        guard let reason = state.reason else { return }
        guard app.isOnline else {
            state.offline = true
            return
        }
        // Not while a send, a sent answer, or a Retry-After wait is on.
        guard state.canSend else { return }
        state.phase = .sending
        switch await app.service.report(canonical: canonical, reason: reason, note: state.note) {
        case .success: state.phase = .sent
        case .failure(.noConnection): state.phase = .failure(.errorNoConnection)
        case .failure(.rateLimited(let seconds)):
            state.phase = .failure(.resolveRateLimited)
            state.waiting = true
            try? await Task.sleep(for: .seconds(max(seconds, 1)))
            state.waiting = false
        case .failure(let failure): state.phase = .failure(CopyKey(failure.message(for: .report)))
        }
    }
}

/// The live Report sheet. Signed out, the report carries no bearer (FR-016).
struct ReportRoute: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var model: ReportModel

    init(canonical: String) {
        _model = State(initialValue: ReportModel(canonical: canonical))
    }

    var body: some View {
        @Bindable var model = model
        NavigationStack {
            ReportScreen(state: $model.state, onSend: { Task { await model.send(app: app) } }, onDone: { dismiss() },
                         onHide: { app.hide(model.canonical) })
        }
        .onAppear {
            model.state.demo = app.isDemo
            model.state.offline = !app.isOnline
            model.state.hidden = app.isHidden(model.canonical)
        }
        .onChange(of: app.isHidden(model.canonical)) { _, hidden in model.state.hidden = hidden }
        .onChange(of: app.isOnline) { _, online in model.state.offline = !online }
    }
}
