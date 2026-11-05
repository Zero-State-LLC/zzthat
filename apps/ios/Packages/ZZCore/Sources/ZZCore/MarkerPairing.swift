// Marker-first localization (recognition-adapter.md, Runtime sequence steps
// 2 to 4; spec.md FR-052; zzThis spec 004 FR-018 to FR-021). Pairing reads
// marker geometry and reading order only: no payload text, grammar,
// wordlist, or resolver state. Same-baseline markers in reading order form
// pair hypotheses; every complete, partial, and bare candidate stays
// visible; a bound overflow fails the whole frame closed.

import CoreGraphics
import Foundation

/// The interior ROI of one pair as a rotated rectangle in upright source
/// pixels, origin at the top left, y down. Crop and rotation only.
public struct ROIGeometry: Sendable, Hashable {
    public let centerX: Double
    public let centerY: Double
    /// Along the reading direction.
    public let width: Double
    /// Across the reading direction.
    public let height: Double
    /// Clockwise positive.
    public let angleRadians: Double
    public let imageWidth: Int
    public let imageHeight: Int

    public var cropPixelWidth: Int { max(1, Int(width.rounded())) }
    public var cropPixelHeight: Int { max(1, Int(height.rounded())) }

    /// A point of the crop, normalized over the crop, in source pixels.
    public func sourcePixel(u: Double, v: Double) -> CGPoint {
        let w = Double(cropPixelWidth), h = Double(cropPixelHeight)
        let dx = u * w - w / 2, dy = v * h - h / 2
        let c = cos(angleRadians), s = sin(angleRadians)
        return CGPoint(x: centerX + c * dx - s * dy, y: centerY + s * dx + c * dy)
    }

    /// The same point normalized over the source.
    public func sourcePoint(u: Double, v: Double) -> NormalizedPoint {
        let p = sourcePixel(u: u, v: v)
        return NormalizedPoint(x: p.x / Double(imageWidth), y: p.y / Double(imageHeight))
    }

    /// Top left, top right, bottom right, bottom left, in source pixels.
    public var cornerPixels: [CGPoint] {
        [sourcePixel(u: 0, v: 0), sourcePixel(u: 1, v: 0), sourcePixel(u: 1, v: 1), sourcePixel(u: 0, v: 1)]
    }

    public var polygon: [NormalizedPoint] {
        [sourcePoint(u: 0, v: 0), sourcePoint(u: 1, v: 0), sourcePoint(u: 1, v: 1), sourcePoint(u: 0, v: 1)]
    }

    /// Row-major affine matrix from normalized crop to normalized source
    /// coordinates, last row 0, 0, 1.
    public var transform: [Double] {
        let w = Double(cropPixelWidth), h = Double(cropPixelHeight)
        let W = Double(imageWidth), H = Double(imageHeight)
        let c = cos(angleRadians), s = sin(angleRadians)
        return [
            c * w / W, -s * h / W, (centerX - c * w / 2 + s * h / 2) / W,
            s * w / H, c * h / H, (centerY - s * w / 2 - c * h / 2) / H,
            0, 0, 1,
        ]
    }

    /// A box normalized over the crop, mapped to the source and bounded.
    public func sourceBox(_ crop: CGRect) -> NormalizedBox? {
        NormalizedBox.bounding([
            sourcePoint(u: crop.minX, v: crop.minY), sourcePoint(u: crop.maxX, v: crop.minY),
            sourcePoint(u: crop.maxX, v: crop.maxY), sourcePoint(u: crop.minX, v: crop.maxY),
        ])
    }
}

public enum MarkerPairing {
    /// One marker in pixel space.
    struct Mark {
        let observation: MarkerObservation
        let box: NormalizedBox
        let center: CGPoint
        let width: Double
        let height: Double
        let angle: Double
    }

    /// Steps 2 to 4 for one upright image: marker observations in, the
    /// frame's evidence and selectable candidates out. Never reads payload.
    public static func frame(imageId: String, markers: [MarkerObservation], imageWidth: Int, imageHeight: Int,
                             engineId: String, engineVersion: String,
                             configuration: CaptureConfiguration = .current) -> RecognitionResult {
        func failed(_ code: ReasonCode, _ fiducials: [FiducialDetection] = []) -> RecognitionResult {
            .failed(imageId: imageId, engineId: engineId, engineVersion: engineVersion,
                    configurationVersion: configuration.version, reasonCode: code,
                    imageWidth: imageWidth, imageHeight: imageHeight, fiducials: fiducials)
        }
        let limits = configuration.limits
        let geometry = configuration.geometry
        guard configuration.thresholds.isValid, limits.isComplete, geometry.isComplete,
              let maxMarkers = limits.maxMarkers, let maxPairs = limits.maxPairs
        else { return failed(.invalidConfiguration) }
        guard imageWidth > 0, imageHeight > 0 else { return failed(.invalidEvidence) }

        // Every observation must be a finite quad inside the image.
        var marks: [Mark] = []
        for observation in markers {
            guard observation.quad.count == 4, observation.quad.allSatisfy(\.isValid),
                  observation.baselineAngleDegrees.isFinite, let box = observation.box, box.isValid
            else { return failed(.invalidEvidence) }
            let px = observation.quad.map { CGPoint(x: $0.x * Double(imageWidth), y: $0.y * Double(imageHeight)) }
            let width = (distance(px[0], px[1]) + distance(px[3], px[2])) / 2
            let height = (distance(px[0], px[3]) + distance(px[1], px[2])) / 2
            guard width > 0, height > 0 else { return failed(.invalidEvidence) }
            let center = CGPoint(x: px.map(\.x).reduce(0, +) / 4, y: px.map(\.y).reduce(0, +) / 4)
            marks.append(Mark(observation: observation, box: box, center: center, width: width, height: height,
                              angle: observation.baselineAngleDegrees * .pi / 180))
        }

        // The marker bound comes before any pairing work. Over it, the whole
        // frame fails closed: every detection is still listed as evidence,
        // and none is paired, ranked, or dropped.
        if marks.count > maxMarkers {
            let order = marks.indices.sorted { readingOrderLess(marks[$0].center, marks[$1].center) }
            let fiducials = order.enumerated().map { index, mark in
                FiducialDetection(detectionId: "\(imageId).f\(index + 1)", baselineId: nil, role: .unknown,
                                  pairId: nil, score: marks[mark].observation.score, box: marks[mark].box,
                                  markerGlyph: marks[mark].observation.glyph, rawToken: marks[mark].observation.rawToken)
            }
            return failed(.markerLimitExceeded, fiducials)
        }

        // Baseline groups: connected components of the same-baseline relation.
        // A component in which some two markers do not share a baseline is a
        // chain across drifting baselines (a curved or wrapped surface): the
        // frame fails closed rather than guess where the baseline is.
        var parent = Array(marks.indices)
        func root(_ i: Int) -> Int { parent[i] == i ? i : root(parent[i]) }
        for a in marks.indices {
            for b in marks.indices where b > a && sameBaseline(marks[a], marks[b], geometry) {
                parent[root(b)] = root(a)
            }
        }
        var components: [Int: [Int]] = [:]
        for i in marks.indices { components[root(i), default: []].append(i) }
        var groups: [[Int]] = []
        for (_, members) in components {
            for a in members {
                for b in members where b > a && !sameBaseline(marks[a], marks[b], geometry) {
                    return failed(.unsupportedGeometry)
                }
            }
            // Reading order along the group's own direction.
            let first = marks[members[0]]
            let direction = CGPoint(x: cos(first.angle), y: sin(first.angle))
            groups.append(members.sorted { dot(marks[$0].center, direction) < dot(marks[$1].center, direction) })
        }
        groups.sort { readingOrderLess(marks[$0[0]].center, marks[$1[0]].center) }
        groups = groups.map { kept($0, marks: marks) }.filter { !$0.isEmpty }

        // Image-scoped ids in reading order.
        var detectionId: [Int: String] = [:]
        var baselineId: [Int: String] = [:]
        var counter = 0
        for (groupIndex, group) in groups.enumerated() {
            for mark in group {
                counter += 1
                detectionId[mark] = "\(imageId).f\(counter)"
                baselineId[mark] = "\(imageId).b\(groupIndex + 1)"
            }
        }

        // Pair hypotheses: neighbors on one baseline, in reading order. A
        // middle marker can open one pair and close another; both stay.
        struct Pair {
            let id: String
            let opening: Int
            let closing: Int
            let baseline: String
        }
        var pairs: [Pair] = []
        for group in groups where group.count >= 2 {
            for (a, b) in zip(group, group.dropFirst()) {
                pairs.append(Pair(id: "\(imageId).p\(pairs.count + 1)", opening: a, closing: b,
                                  baseline: baselineId[a]!))
            }
        }
        if pairs.count > maxPairs {
            let fiducials = groups.flatMap { $0 }.map { mark in
                FiducialDetection(detectionId: detectionId[mark]!, baselineId: baselineId[mark], role: .unknown,
                                  pairId: nil, score: marks[mark].observation.score, box: marks[mark].box,
                                  markerGlyph: marks[mark].observation.glyph, rawToken: marks[mark].observation.rawToken)
            }
            return failed(.pairLimitExceeded, fiducials)
        }

        // Roles and pair ids: set only when a marker is in exactly one pair.
        var memberships: [Int: [Pair]] = [:]
        for pair in pairs {
            memberships[pair.opening, default: []].append(pair)
            memberships[pair.closing, default: []].append(pair)
        }
        var fiducials: [FiducialDetection] = []
        var candidates: [VisualCandidate] = []
        var reasonCodes: [String] = []
        func note(_ code: ReasonCode) {
            if !reasonCodes.contains(code.rawValue) { reasonCodes.append(code.rawValue) }
        }

        for group in groups {
            if group.count == 1 {
                let mark = group[0]
                let observation = marks[mark].observation
                let role: FiducialRole
                let kind: VisualCandidate.Kind
                let code: ReasonCode
                switch (observation.inkBefore, observation.inkAfter) {
                case (false, true): (role, kind, code) = (.opening, .partial, .missingClosingFiducial)
                case (true, false): (role, kind, code) = (.closing, .partial, .missingOpeningFiducial)
                case (true, true): (role, kind, code) = (.unknown, .partial, .partialFiducial)
                case (false, false): (role, kind, code) = (.unknown, .bare, .bareMark)
                }
                note(code)
                fiducials.append(FiducialDetection(detectionId: detectionId[mark]!, baselineId: baselineId[mark],
                                                   role: role, pairId: nil, score: observation.score,
                                                   box: marks[mark].box, markerGlyph: observation.glyph,
                                                   rawToken: observation.rawToken))
                candidates.append(VisualCandidate(id: "\(imageId).c\(candidates.count + 1)", kind: kind,
                                                  detectionIds: [detectionId[mark]!], boxes: [marks[mark].box],
                                                  roi: nil, geometry: nil, reasonCode: code.rawValue))
                continue
            }
            for mark in group {
                let mine = memberships[mark] ?? []
                let role: FiducialRole = mine.count == 1 ? (mine[0].opening == mark ? .opening : .closing) : .unknown
                fiducials.append(FiducialDetection(detectionId: detectionId[mark]!, baselineId: baselineId[mark],
                                                   role: role, pairId: mine.count == 1 ? mine[0].id : nil,
                                                   score: marks[mark].observation.score, box: marks[mark].box,
                                                   markerGlyph: marks[mark].observation.glyph,
                                                   rawToken: marks[mark].observation.rawToken))
            }
            for pair in pairs where pair.baseline == baselineId[group[0]] {
                let opening = marks[pair.opening], closing = marks[pair.closing]
                let built = roi(opening, closing, geometry: geometry, imageWidth: imageWidth, imageHeight: imageHeight)
                var boxes = [opening.box, closing.box]
                var detection: ROIDetection?
                var safe: ROIGeometry?
                var code: String?
                switch built {
                case .success(let roi):
                    safe = roi
                    detection = ROIDetection(pairId: pair.id, openingDetectionId: detectionId[pair.opening]!,
                                             closingDetectionId: detectionId[pair.closing]!, baselineId: pair.baseline,
                                             polygon: roi.polygon, orientationDegrees: roi.angleRadians * 180 / .pi,
                                             rectificationTransform: roi.transform, rectificationSucceeded: nil)
                    if let bounds = NormalizedBox.bounding(roi.polygon) { boxes.append(bounds) }
                case .failure(let reason):
                    note(reason)
                    code = reason.rawValue
                }
                candidates.append(VisualCandidate(id: "\(imageId).c\(candidates.count + 1)",
                                                  kind: .complete(pairId: pair.id),
                                                  detectionIds: [detectionId[pair.opening]!, detectionId[pair.closing]!],
                                                  boxes: boxes, roi: detection, geometry: safe, reasonCode: code))
            }
        }

        if marks.isEmpty { note(.noFiducial) }
        return RecognitionResult(imageId: imageId, engineId: engineId, engineVersion: engineVersion,
                                 configurationVersion: configuration.version, runtimeOutcome: .completed,
                                 imageWidth: imageWidth, imageHeight: imageHeight, fiducials: fiducials, rois: [],
                                 candidates: [], visualCandidates: candidates, reasonCodes: reasonCodes,
                                 frameReasonCode: nil)
    }

    /// Same detected baseline: the reading directions agree, the centers lie
    /// on one line across the reading direction, the sizes match, and the
    /// two marks do not overlap.
    static func sameBaseline(_ a: Mark, _ b: Mark, _ geometry: CaptureConfiguration.Geometry) -> Bool {
        guard let angleTolerance = geometry.baselineAngleToleranceDegrees,
              let offsetTolerance = geometry.baselineOffsetTolerance,
              let ratio = geometry.maxMarkerHeightRatio else { return false }
        var turn = abs(a.angle - b.angle).truncatingRemainder(dividingBy: 2 * .pi)
        if turn > .pi { turn = 2 * .pi - turn }
        guard turn * 180 / .pi <= angleTolerance else { return false }
        guard max(a.height, b.height) / min(a.height, b.height) <= ratio else { return false }
        let mean = (a.angle + b.angle) / 2
        let direction = CGPoint(x: cos(mean), y: sin(mean))
        let normal = CGPoint(x: -sin(mean), y: cos(mean))
        let delta = CGPoint(x: b.center.x - a.center.x, y: b.center.y - a.center.y)
        guard abs(dot(delta, normal)) <= offsetTolerance * min(a.height, b.height) else { return false }
        return abs(dot(delta, direction)) >= (a.width + b.width) / 2
    }

    /// Step 4: the interior ROI between the two marker patches, or why there
    /// is no safe one.
    static func roi(_ opening: Mark, _ closing: Mark, geometry: CaptureConfiguration.Geometry,
                    imageWidth: Int, imageHeight: Int) -> Result<ROIGeometry, ReasonCode> {
        guard let maxRotation = geometry.maxRotationDegrees, let inset = geometry.roiInset,
              let halfHeight = geometry.roiHalfHeight, let minimumWidth = geometry.minimumROIWidth
        else { return .failure(.invalidConfiguration) }
        let angle = (opening.angle + closing.angle) / 2
        guard abs(angle * 180 / .pi) <= maxRotation else { return .failure(.unsupportedGeometry) }
        let direction = CGPoint(x: cos(angle), y: sin(angle))
        let normal = CGPoint(x: -sin(angle), y: cos(angle))
        let delta = CGPoint(x: closing.center.x - opening.center.x, y: closing.center.y - opening.center.y)
        let markHeight = (opening.height + closing.height) / 2
        let start = opening.width / 2 + inset * markHeight
        let end = dot(delta, direction) - closing.width / 2 - inset * markHeight
        guard end - start >= minimumWidth * markHeight else { return .failure(.unsafeROI) }
        let across = dot(delta, normal) / 2
        let along = (start + end) / 2
        let roi = ROIGeometry(centerX: opening.center.x + direction.x * along + normal.x * across,
                              centerY: opening.center.y + direction.y * along + normal.y * across,
                              width: end - start, height: 2 * halfHeight * markHeight, angleRadians: angle,
                              imageWidth: imageWidth, imageHeight: imageHeight)
        let inside = roi.cornerPixels.allSatisfy {
            $0.x >= -0.5 && $0.y >= -0.5 && $0.x <= Double(imageWidth) + 0.5 && $0.y <= Double(imageHeight) + 0.5
        }
        return inside ? .success(roi) : .failure(.unsafeROI)
    }

    /// Exact markers first. A tolerant token is a fiducial only at an outer
    /// end, and only when this baseline has no exact pair candidate.
    static func kept(_ group: [Int], marks: [Mark]) -> [Int] {
        let exacts = group.filter { marks[$0].observation.glyph == .exact }
        if exacts.count >= 2 { return exacts }
        guard let first = group.first, let last = group.last else { return group }
        return group.filter { index in
            marks[index].observation.glyph == .exact || index == first || index == last
        }
    }

    /// Top to bottom, then left to right; centers within a few pixels share a row.
    static func readingOrderLess(_ a: CGPoint, _ b: CGPoint) -> Bool {
        abs(a.y - b.y) > 1 ? a.y < b.y : a.x < b.x
    }

    static func distance(_ a: CGPoint, _ b: CGPoint) -> Double { hypot(a.x - b.x, a.y - b.y) }
    static func dot(_ a: CGPoint, _ b: CGPoint) -> Double { a.x * b.x + a.y * b.y }
}
