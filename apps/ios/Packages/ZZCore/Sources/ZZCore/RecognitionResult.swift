// The platform-neutral recognition boundary (recognition.md, Boundary;
// recognition-adapter.md; tasks.md T023). Vendor objects stop at the
// adapter. Everything here is evidence: geometry, raw payload text, and
// confidence when the engine has a meaningful one. Decoding, canonical
// codes, and bands are ZZCore's (ReadPipeline+Capture.swift, Bands.swift).
//
// Field names follow the upstream ZZ-OCR-QUAL-001 adapter-result.schema.json
// at the pin (fiducialDetection, roiDetection, textCandidate, sampleResult).
// Coordinates are normalized to [0, 1] over the upright source image, with
// the origin at the top left. No type here holds image bytes, a crop, or a
// file path; a RecognitionResult lives in memory for one attempt only.

import CoreGraphics
import Foundation

public struct NormalizedPoint: Sendable, Hashable, Encodable {
    public let x: Double
    public let y: Double

    public init(x: Double, y: Double) {
        self.x = x
        self.y = y
    }

    public var isValid: Bool { x.isFinite && y.isFinite && (0...1).contains(x) && (0...1).contains(y) }
}

public struct NormalizedBox: Sendable, Hashable, Encodable {
    public let x: Double
    public let y: Double
    public let width: Double
    public let height: Double

    public init(x: Double, y: Double, width: Double, height: Double) {
        self.x = x
        self.y = y
        self.width = width
        self.height = height
    }

    /// The schema's box: origin in [0, 1], a positive size, inside the image.
    public var isValid: Bool {
        let values = [x, y, width, height]
        guard values.allSatisfy(\.isFinite) else { return false }
        return x >= 0 && y >= 0 && width > 0 && height > 0 && x + width <= 1 + 1e-9 && y + height <= 1 + 1e-9
    }

    public var rect: CGRect { CGRect(x: x, y: y, width: width, height: height) }

    /// The bounds of points, clamped to the image.
    static func bounding(_ points: [NormalizedPoint]) -> NormalizedBox? {
        guard !points.isEmpty else { return nil }
        let clamp: (Double) -> Double = { min(max($0, 0), 1) }
        let minX = clamp(points.map(\.x).min()!), maxX = clamp(points.map(\.x).max()!)
        let minY = clamp(points.map(\.y).min()!), maxY = clamp(points.map(\.y).max()!)
        guard maxX > minX, maxY > minY else { return nil }
        return NormalizedBox(x: minX, y: minY, width: maxX - minX, height: maxY - minY)
    }
}

public enum FiducialRole: String, Sendable, Hashable, Encodable {
    case opening
    case closing
    case unknown
}

/// One terminal zz marker observation (schema `fiducialDetection`).
public struct FiducialDetection: Sendable, Hashable, Encodable {
    public let detectionId: String
    /// The baseline group the marker belongs to.
    public let baselineId: String?
    /// Opening or closing only when the marker is in exactly one pair
    /// hypothesis, or is a partial whose side is known. Otherwise unknown.
    public let role: FiducialRole
    /// Set only when the marker is in exactly one pair hypothesis.
    public let pairId: String?
    /// The detector's marker score, when it has a meaningful one. Never a
    /// payload-word confidence.
    public let score: Double?
    public let box: NormalizedBox

    enum CodingKeys: String, CodingKey {
        case detectionId = "detection_id"
        case baselineId = "baseline_id"
        case role
        case pairId = "pair_id"
        case score
        case box
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(detectionId, forKey: .detectionId)
        try container.encode(baselineId, forKey: .baselineId)
        try container.encode(role, forKey: .role)
        try container.encode(pairId, forKey: .pairId)
        try container.encodeIfPresent(score, forKey: .score)
        try container.encode(box, forKey: .box)
    }
}

/// The interior ROI of one complete pair hypothesis (schema `roiDetection`).
public struct ROIDetection: Sendable, Hashable, Encodable {
    public let pairId: String
    public let openingDetectionId: String
    public let closingDetectionId: String
    public let baselineId: String
    /// Top left, top right, bottom right, bottom left, in reading direction.
    public let polygon: [NormalizedPoint]
    /// The baseline's turn from horizontal, clockwise positive.
    public let orientationDegrees: Double?
    /// Row-major 3 by 3 affine matrix from normalized crop coordinates to
    /// normalized source coordinates. The last row is always 0, 0, 1: v1
    /// applies crop and rotation only, never a projective transform.
    public let rectificationTransform: [Double]?
    /// True only when crop and rotation succeeded and payload OCR ran on it.
    public let rectificationSucceeded: Bool?

    enum CodingKeys: String, CodingKey {
        case pairId = "pair_id"
        case openingDetectionId = "opening_detection_id"
        case closingDetectionId = "closing_detection_id"
        case baselineId = "baseline_id"
        case polygon
        case orientationDegrees = "orientation_degrees"
        case rectificationTransform = "rectification_transform"
        case rectificationSucceeded = "rectification_succeeded"
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(pairId, forKey: .pairId)
        try container.encode(openingDetectionId, forKey: .openingDetectionId)
        try container.encode(closingDetectionId, forKey: .closingDetectionId)
        try container.encode(baselineId, forKey: .baselineId)
        try container.encode(polygon, forKey: .polygon)
        try container.encodeIfPresent(orientationDegrees, forKey: .orientationDegrees)
        try container.encodeIfPresent(rectificationTransform, forKey: .rectificationTransform)
        try container.encodeIfPresent(rectificationSucceeded, forKey: .rectificationSucceeded)
    }

    func with(rectificationSucceeded: Bool) -> ROIDetection {
        ROIDetection(pairId: pairId, openingDetectionId: openingDetectionId, closingDetectionId: closingDetectionId,
                     baselineId: baselineId, polygon: polygon, orientationDegrees: orientationDegrees,
                     rectificationTransform: rectificationTransform, rectificationSucceeded: rectificationSucceeded)
    }

    /// v1 permits only an affine crop and rotation: a 3 by 3 matrix whose
    /// last row is exactly 0, 0, 1 and whose entries are finite.
    public var transformIsCropAndRotationOnly: Bool {
        guard let matrix = rectificationTransform else { return true }
        return matrix.count == 9 && matrix.allSatisfy(\.isFinite)
            && matrix[6] == 0 && matrix[7] == 0 && matrix[8] == 1
    }
}

public enum ConfidenceStatus: String, Sendable, Hashable, Encodable {
    case available
    case notAvailable = "not-available"
}

/// One recognized line inside the ROI, with its own confidence when the
/// engine has one (recognition.md `line_provenance`). Not part of the
/// upstream serialized schema, so it is not encoded.
public struct LineProvenance: Sendable, Hashable {
    public let text: String
    public let confidence: Double?
    /// In normalized source coordinates.
    public let region: NormalizedBox?
}

/// One raw payload hypothesis for one pair (schema `textCandidate`).
public struct TextCandidate: Sendable, Hashable, Encodable {
    public let candidateId: String
    public let pairId: String?
    /// The payload between the paired markers. The adapter never includes
    /// or infers the markers.
    public let rawText: String
    public let confidenceStatus: ConfidenceStatus
    /// The lowest line confidence among the lines that hold a payload part,
    /// or nil when any such line has none. Never synthesized.
    public let confidence: Double?
    public let regions: [NormalizedBox]
    public let lines: [LineProvenance]

    enum CodingKeys: String, CodingKey {
        case candidateId = "candidate_id"
        case pairId = "pair_id"
        case rawText = "raw_text"
        case confidenceStatus = "confidence_status"
        case confidence
        case regions
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(candidateId, forKey: .candidateId)
        try container.encode(pairId, forKey: .pairId)
        try container.encode(rawText, forKey: .rawText)
        try container.encode(confidenceStatus, forKey: .confidenceStatus)
        try container.encode(confidence, forKey: .confidence)
        try container.encode(regions, forKey: .regions)
    }
}

/// A box the person can select before any payload OCR (FR-052): a complete
/// pair, a partial code with one marker, or a bare mark.
public struct VisualCandidate: Sendable, Hashable, Identifiable {
    public enum Kind: Sendable, Hashable {
        case complete(pairId: String)
        case partial
        case bare
    }

    public let id: String
    public let kind: Kind
    /// The markers it is built from, in reading order.
    public let detectionIds: [String]
    /// The marker boxes, then the ROI bounds for a complete pair.
    public let boxes: [NormalizedBox]
    /// The interior ROI, for a complete pair whose geometry is safe.
    public let roi: ROIDetection?
    /// The same ROI in source pixels, for the crop.
    public let geometry: ROIGeometry?
    /// Why this candidate cannot reach payload OCR: a partial or bare
    /// marker, or unsafe pair geometry. Nil only for a safe complete pair.
    public let reasonCode: String?

    public var pairId: String? {
        if case .complete(let pairId) = kind { return pairId }
        return nil
    }

    /// One box around the whole candidate, for drawing it on the photo.
    public var outline: CGRect {
        boxes.dropFirst().reduce(boxes.first?.rect ?? .zero) { $0.union($1.rect) }
    }

    /// A complete pair with a safe ROI: the only kind payload OCR may read.
    public var isReadable: Bool { pairId != nil && roi != nil && geometry != nil && reasonCode == nil }
}

/// The adapter's evidence for one captured image (one attempt).
public struct RecognitionResult: Sendable, Hashable, Encodable {
    public enum RuntimeOutcome: String, Sendable, Hashable, Encodable {
        case completed
        case exception
    }

    /// Stable within the attempt; a new image gets a new id. Every child id
    /// starts with it.
    public let imageId: String
    public let engineId: String
    public let engineVersion: String
    public let configurationVersion: String
    public let runtimeOutcome: RuntimeOutcome
    /// Upright source size in pixels, for the geometry.
    public let imageWidth: Int
    public let imageHeight: Int
    public let fiducials: [FiducialDetection]
    /// The ROI of the pair payload OCR read: none until a pair is read.
    public let rois: [ROIDetection]
    /// Raw hypotheses for that one pair, in the engine's order.
    public let candidates: [TextCandidate]
    /// Every selectable box, in reading order. Empty when the frame failed closed.
    public let visualCandidates: [VisualCandidate]
    public let reasonCodes: [String]
    /// Set when the whole frame failed closed (a limit exceeded, invalid
    /// configuration, unsupported geometry, an unplaced mark, invalid
    /// evidence, recognizer failure). No
    /// candidate is selectable then, and no payload OCR runs.
    public let frameReasonCode: String?

    enum CodingKeys: String, CodingKey {
        case runtimeOutcome = "runtime_outcome"
        case fiducials
        case rois
        case candidates
        case reasonCodes = "reason_codes"
    }

    /// Encodes the fields the upstream `sampleResult` shares with a runtime
    /// result. The harness adds sample, split, device, and timing fields.
    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(runtimeOutcome, forKey: .runtimeOutcome)
        try container.encode(fiducials, forKey: .fiducials)
        try container.encode(rois, forKey: .rois)
        try container.encode(candidates, forKey: .candidates)
        try container.encode(reasonCodes, forKey: .reasonCodes)
    }

    func with(roi: ROIDetection?, candidates: [TextCandidate], reasonCodes extra: [String],
              runtimeOutcome: RuntimeOutcome? = nil) -> RecognitionResult {
        var codes = reasonCodes
        for code in extra where !codes.contains(code) { codes.append(code) }
        return RecognitionResult(imageId: imageId, engineId: engineId, engineVersion: engineVersion,
                                 configurationVersion: configurationVersion,
                                 runtimeOutcome: runtimeOutcome ?? self.runtimeOutcome,
                                 imageWidth: imageWidth, imageHeight: imageHeight, fiducials: fiducials,
                                 rois: roi.map { [$0] } ?? [], candidates: candidates,
                                 visualCandidates: visualCandidates, reasonCodes: codes,
                                 frameReasonCode: frameReasonCode)
    }

    static func failed(imageId: String, engineId: String, engineVersion: String, configurationVersion: String,
                       reasonCode: ReasonCode, runtimeOutcome: RuntimeOutcome = .completed,
                       imageWidth: Int = 0, imageHeight: Int = 0,
                       fiducials: [FiducialDetection] = []) -> RecognitionResult {
        RecognitionResult(imageId: imageId, engineId: engineId, engineVersion: engineVersion,
                          configurationVersion: configurationVersion, runtimeOutcome: runtimeOutcome,
                          imageWidth: imageWidth, imageHeight: imageHeight, fiducials: fiducials, rois: [],
                          candidates: [], visualCandidates: [], reasonCodes: [reasonCode.rawValue],
                          frameReasonCode: reasonCode.rawValue)
    }
}

// MARK: Adapter input and output

/// What a marker detector reports for one terminal zz mark, before pairing.
/// Geometry only: the detector reports no payload text.
public struct MarkerObservation: Sendable, Hashable {
    /// Top left, top right, bottom right, bottom left of the mark, in
    /// reading direction, normalized over the upright image.
    public let quad: [NormalizedPoint]
    /// The reading direction of the mark's text line, in degrees from
    /// horizontal, clockwise positive (the y axis points down).
    public let baselineAngleDegrees: Double
    /// Whether other marks sit next to it on its own text line, before or
    /// after it. Presence only, never their text.
    public let inkBefore: Bool
    public let inkAfter: Bool
    public let score: Double?

    public init(quad: [NormalizedPoint], baselineAngleDegrees: Double, inkBefore: Bool, inkAfter: Bool,
                score: Double? = nil) {
        self.quad = quad
        self.baselineAngleDegrees = baselineAngleDegrees
        self.inkBefore = inkBefore
        self.inkAfter = inkAfter
        self.score = score
    }

    public var box: NormalizedBox? { NormalizedBox.bounding(quad) }
}

/// One line the payload reader found inside the crop.
public struct PayloadLine: Sendable, Hashable {
    public let text: String
    /// The engine's score for the line, when it has one.
    public let confidence: Double?
    /// Normalized over the crop, origin at the top left.
    public let cropBox: CGRect

    public init(text: String, confidence: Double?, cropBox: CGRect) {
        self.text = text
        self.confidence = confidence
        self.cropBox = cropBox
    }
}

/// One payload reading of the crop, as the engine ordered it.
public struct PayloadHypothesis: Sendable, Hashable {
    public let lines: [PayloadLine]

    public init(lines: [PayloadLine]) {
        self.lines = lines
    }
}

/// The upright image an adapter prepared. The backing store is the
/// adapter's own (a CGImage for Vision); ZZCore reads only its size.
public struct CaptureImage: @unchecked Sendable {
    public let pixelWidth: Int
    public let pixelHeight: Int
    public let backing: AnyObject?

    public init(pixelWidth: Int, pixelHeight: Int, backing: AnyObject?) {
        self.pixelWidth = pixelWidth
        self.pixelHeight = pixelHeight
        self.backing = backing
    }
}

public enum RecognitionFailure: Error, Sendable, Hashable {
    /// The engine cannot run on this device or failed to start.
    case unavailable
    /// The image could not be decoded.
    case unreadableImage
    /// The engine failed on this image.
    case failed
    /// The engine found a zz mark but cannot place it.
    case unlocalizedMarker
}

/// A platform recognizer behind the boundary (recognition.md, Platform
/// implementations). It runs on the device, sends nothing, and writes no
/// image, crop, or text to storage.
public protocol RecognitionAdapter: Sendable {
    var engineId: String { get }
    var engineVersion: String { get }
    /// Decode the photo and normalize it upright, once.
    func prepare(imageData: Data) async throws -> CaptureImage
    /// Find the terminal zz marks. No payload text leaves this call.
    func detectMarkers(in image: CaptureImage) async throws -> [MarkerObservation]
    /// Crop and rotate to the ROI, then read only that crop.
    func readPayload(in image: CaptureImage, roi: ROIGeometry) async throws -> [PayloadHypothesis]
    /// The text already read on the line, strictly between the two marks that
    /// bound this ROI. Nil when that span is not a one-box-per-character line.
    /// The crop read stays primary. This is not a marker rewrite.
    func lineSpan(in image: CaptureImage, roi: ROIGeometry) async throws -> PayloadHypothesis?
}

extension RecognitionAdapter {
    public func lineSpan(in image: CaptureImage, roi: ROIGeometry) async throws -> PayloadHypothesis? { nil }
}
