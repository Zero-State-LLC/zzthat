import SwiftUI
import ZZAPI
import ZZDesign

/// Report (FR-035, design/UX.md Report): a sheet from Resolve with the four
/// reasons, an optional note of up to 500 characters, and one send. The
/// answer never says whether the code exists. No block list is kept.
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
    /// After a 429, Send waits for `Retry-After` (FR-033).
    var waiting = false

    var canSend: Bool { reason != nil && !offline && !waiting && phase != .sending && phase != .sent }
}

struct ReportScreen: View {
    @Binding var state: ReportState
    var onSend: () -> Void = {}
    var onDone: () -> Void = {}

    var body: some View {
        Screen(demo: state.demo, loading: state.phase == .sending) {
            Text(.reportTitle)
                .textRole(.heading)
                .accessibilityAddTraits(.isHeader)
            if state.phase == .sent {
                Message(key: .reportSent)
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

/// The live Report sheet. Signed out, the report carries no bearer (FR-016).
struct ReportRoute: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    let canonical: String
    @State private var state = ReportState()

    var body: some View {
        NavigationStack {
            ReportScreen(state: $state, onSend: send, onDone: { dismiss() })
        }
        .onAppear {
            state.demo = app.isDemo
            state.offline = !app.isOnline
        }
        .onChange(of: app.isOnline) { _, online in state.offline = !online }
    }

    private func send() {
        guard let reason = state.reason else { return }
        guard app.isOnline else {
            state.offline = true
            return
        }
        state.phase = .sending
        Task {
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
}
