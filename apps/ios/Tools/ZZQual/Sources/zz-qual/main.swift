import CryptoKit
import Foundation
import ZZCore

// zz-qual (tasks.md T027, T023c): runs the shipped iOS reader over a folder of
// photos, offline, and reports what it read. Photos are read in place and
// never copied, uploaded, or written anywhere.
//
//   zz-qual quick --images DIR --truth truth.csv [--wordlists DIR] [--list VERSION] [--out rows.csv]
//   zz-qual run   --manifest manifest.json --images DIR --candidate-bundle bundle.json
//                 --device-matrix matrix.json --device-entry ID --out adapter-results.json
//                 [--wordlists DIR] [--list VERSION]
//
// quick: truth.csv has a header and the rows `file,code`; an empty code means
// the photo holds no code, and several codes are separated by spaces. It prints one line per photo and a summary.
// run: writes the ZZ-OCR-QUAL-001 adapter-results.json for the zzThis
// qualification CLI (`npm run qual:ocr`), which scores it.

struct Failure: Error, CustomStringConvertible {
    let description: String
    init(_ description: String) { self.description = description }
}

/// Every pair read on its own, keyed by pair id, with no truth-driven
/// selection (T023c). Partial and bare boxes are not read (FR-052).
struct SampleRead {
    let frame: RecognitionResult
    let outcomes: [CaptureOutcome]
    let elapsedMs: Double

    /// The frame's evidence with every pair's ROI and hypotheses, as one
    /// sample row.
    var merged: [String: Any] {
        var rois: [ROIDetection] = []
        var candidates: [TextCandidate] = []
        var reasons = frame.reasonCodes
        var outcome = frame.runtimeOutcome
        for read in outcomes {
            rois += read.result.rois
            candidates += read.result.candidates
            for code in read.result.reasonCodes where !reasons.contains(code) { reasons.append(code) }
            if read.result.runtimeOutcome == .exception { outcome = .exception }
        }
        let evidence = Evidence(runtimeOutcome: outcome, fiducials: frame.fiducials, rois: rois,
                                candidates: candidates, reasonCodes: reasons)
        var row = (try? JSONSerialization.jsonObject(with: JSONEncoder().encode(evidence))) as? [String: Any] ?? [:]
        if outcome == .exception {
            // The scorer requires a crashed or failed row to carry no evidence.
            row["fiducials"] = []
            row["rois"] = []
            row["candidates"] = []
        }
        return row
    }

    /// The first reading of each pair: its canonical code (nil when it does
    /// not parse) and its band.
    var readings: [(canonical: String?, band: Band, valid: Bool)] {
        outcomes.compactMap { read in
            guard let first = read.decoded.first else { return nil }
            return (first.canonical, first.band, first.check == .ok)
        }
    }
}

struct Evidence: Encodable {
    let runtimeOutcome: RecognitionResult.RuntimeOutcome
    let fiducials: [FiducialDetection]
    let rois: [ROIDetection]
    let candidates: [TextCandidate]
    let reasonCodes: [String]

    enum CodingKeys: String, CodingKey {
        case runtimeOutcome = "runtime_outcome"
        case fiducials, rois, candidates
        case reasonCodes = "reason_codes"
    }
}

func read(_ url: URL, pipeline: CapturePipeline) async throws -> SampleRead {
    let data = try Data(contentsOf: url)
    let start = ContinuousClock.now
    let session = await pipeline.detect(imageData: data, imageId: url.lastPathComponent)
    var outcomes: [CaptureOutcome] = []
    if pipeline.frameDisposition(session.result) == nil {
        for box in session.result.visualCandidates where box.pairId != nil {
            outcomes.append(await pipeline.process(session, candidateId: box.id))
        }
    }
    let elapsed = ContinuousClock.now - start
    let ms = Double(elapsed.components.seconds) * 1000 + Double(elapsed.components.attoseconds) / 1e15
    return SampleRead(frame: session.result, outcomes: outcomes, elapsedMs: ms)
}

func sha256(_ data: Data) -> String {
    SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
}

/// RFC 8785 for the JSON these files hold (objects, arrays, strings,
/// integers, booleans, null): sorted keys and no whitespace.
func jcsSHA256(_ url: URL) throws -> String {
    let object = try JSONSerialization.jsonObject(with: Data(contentsOf: url))
    return sha256(try JSONSerialization.data(withJSONObject: object, options: [.sortedKeys, .withoutEscapingSlashes]))
}

func json(_ url: URL) throws -> [String: Any] {
    guard let object = try JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any] else {
        throw Failure("\(url.path) is not a JSON object")
    }
    return object
}

func pipeline(arguments: [String: String]) throws -> CapturePipeline {
    let directory = URL(fileURLWithPath: arguments["--wordlists"] ?? "contracts/wordlists")
    let lists: [WordList]
    do { lists = try WordListLoader.load(directory: directory) } catch {
        throw Failure("cannot load word lists from \(directory.path): \(error)")
    }
    let version = arguments["--list"] ?? "fixture-7"
    guard let list = lists.first(where: { $0.version == version }) else {
        throw Failure("no word list \(version) in \(directory.path)")
    }
    return CapturePipeline(decoder: ReadPipeline(classifyList: list, checkList: list),
                           adapter: VisionRecognitionAdapter())
}

func images(in directory: String) throws -> [URL] {
    let url = URL(fileURLWithPath: directory)
    let names = try FileManager.default.contentsOfDirectory(atPath: url.path)
    let kinds: Set<String> = ["jpg", "jpeg", "png", "heic", "heif", "tif", "tiff"]
    return names.sorted().map { url.appendingPathComponent($0) }.filter { kinds.contains($0.pathExtension.lowercased()) }
}

// MARK: quick

func quick(_ arguments: [String: String]) async throws {
    guard let imagesDir = arguments["--images"], let truthPath = arguments["--truth"] else {
        throw Failure("quick needs --images DIR and --truth truth.csv")
    }
    let pipeline = try pipeline(arguments: arguments)
    let lines = try String(contentsOfFile: truthPath, encoding: .utf8).split(whereSeparator: \.isNewline).dropFirst()
    var rows = ["file,expected,read,band,result,elapsed_ms"]
    var exact = 0, falseValid = 0, total = 0
    var bands: [String: Int] = [:]
    for line in lines where !line.trimmingCharacters(in: .whitespaces).isEmpty {
        let fields = line.split(separator: ",", maxSplits: 1, omittingEmptySubsequences: false).map {
            $0.trimmingCharacters(in: .whitespaces)
        }
        let file = fields[0]
        // Several codes in one photo are separated by spaces.
        let expected = fields.count > 1 ? fields[1].lowercased().split(separator: " ").map(String.init).sorted() : []
        let url = URL(fileURLWithPath: imagesDir).appendingPathComponent(file)
        guard FileManager.default.fileExists(atPath: url.path) else { throw Failure("missing image \(url.path)") }
        let sample = try await read(url, pipeline: pipeline)
        let readings = sample.readings
        let first = readings.first
        // With no reading, the reason the frame or the first box gave.
        let band = first?.band.rawValue ?? sample.frame.frameReasonCode
            ?? sample.frame.visualCandidates.compactMap(\.reasonCode).first ?? "no-code"
        bands[band, default: 0] += 1
        let readCodes = readings.compactMap(\.canonical)
        let result: String
        if readCodes.sorted() == expected {
            result = "exact"
            exact += 1
        } else if readings.contains(where: { $0.valid && !expected.contains($0.canonical ?? "") }) {
            // A code that parses and passes its check word, but is not the one
            // written: the primary safety metric (T028).
            result = "FALSE-VALID"
            falseValid += 1
        } else {
            result = "miss"
        }
        total += 1
        let readText = readCodes.joined(separator: " ")
        let expectedText = expected.joined(separator: " ")
        print("\(file): expected \(expectedText.isEmpty ? "-" : expectedText), read \(readText.isEmpty ? "-" : readText) [\(band)] \(result)")
        rows.append("\(file),\(expectedText),\(readText),\(band),\(result),\(Int(sample.elapsedMs))")
    }
    guard total > 0 else { throw Failure("no rows in \(truthPath)") }
    let pct = Double(exact) / Double(total) * 100
    print("")
    print("photos \(total)  exact \(exact) (\(String(format: "%.1f", pct))%)  false-valid \(falseValid)")
    print("bands " + bands.sorted { $0.key < $1.key }.map { "\($0.key) \($0.value)" }.joined(separator: ", "))
    if let out = arguments["--out"] {
        try (rows.joined(separator: "\n") + "\n").write(toFile: out, atomically: true, encoding: .utf8)
        print("rows written to \(out)")
    }
}

// MARK: run

func run(_ arguments: [String: String]) async throws {
    guard let manifestPath = arguments["--manifest"], let imagesDir = arguments["--images"],
          let bundlePath = arguments["--candidate-bundle"], let matrixPath = arguments["--device-matrix"],
          let entryId = arguments["--device-entry"], let out = arguments["--out"] else {
        throw Failure("run needs --manifest, --images, --candidate-bundle, --device-matrix, --device-entry, --out")
    }
    let pipeline = try pipeline(arguments: arguments)
    let manifestURL = URL(fileURLWithPath: manifestPath)
    let manifest = try json(manifestURL)
    let bundle = try json(URL(fileURLWithPath: bundlePath))
    let matrix = try json(URL(fileURLWithPath: matrixPath))
    guard let entries = matrix["entries"] as? [[String: Any]],
          let entry = entries.first(where: { $0["device_matrix_entry_id"] as? String == entryId }) else {
        throw Failure("no device matrix entry \(entryId)")
    }
    guard var adapter = bundle["adapter"] as? [String: Any], let platform = bundle["platform"] as? String,
          let matrixSHA = bundle["device_matrix_sha256"] as? String else {
        throw Failure("the candidate bundle has no adapter, platform, or device_matrix_sha256")
    }
    let reader = VisionRecognitionAdapter()
    if adapter["engine_id"] as? String != reader.engineId || adapter["engine_version"] as? String != reader.engineVersion {
        print("warning: the bundle names \(adapter["engine_id"] ?? "?") \(adapter["engine_version"] ?? "?"), "
              + "this build runs \(reader.engineId) \(reader.engineVersion)")
    }
    adapter["platform"] = platform
    adapter["device_matrix_sha256"] = matrixSHA

    // Images are found by the hash the manifest records, so no naming rule.
    var byHash: [String: URL] = [:]
    for url in try images(in: imagesDir) { byHash[sha256(try Data(contentsOf: url))] = url }

    var results: [[String: Any]] = []
    for sample in manifest["samples"] as? [[String: Any]] ?? [] {
        guard let sampleId = sample["sample_id"] as? String, let hash = sample["asset_sha256"] as? String else {
            throw Failure("a sample has no sample_id or asset_sha256")
        }
        guard let url = byHash[hash] else { throw Failure("no image in \(imagesDir) has the hash of \(sampleId)") }
        let read = try await read(url, pipeline: pipeline)
        var row = read.merged
        row["sample_id"] = sampleId
        row["split"] = sample["split"]
        row["input_image_sha256"] = hash
        row["device_matrix_entry_id"] = entryId
        row["os_version"] = entry["os_version"]
        row["device_class"] = entry["device_class"]
        row["elapsed_ms"] = (read.elapsedMs * 1000).rounded() / 1000
        row["runtime_memory_bytes"] = NSNull()
        results.append(row)
        print("\(sampleId): \(read.readings.compactMap(\.canonical).joined(separator: " "))")
    }
    let document: [String: Any] = [
        "schema_version": 1,
        "qualification_id": "ZZ-OCR-QUAL-001",
        "manifest_sha256": sha256(try Data(contentsOf: manifestURL)),
        "candidate_id": bundle["candidate_id"] ?? "",
        "candidate_bundle_sha256": try jcsSHA256(URL(fileURLWithPath: bundlePath)),
        "adapter": adapter,
        "results": results,
    ]
    let data = try JSONSerialization.data(withJSONObject: document, options: [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes])
    try data.write(to: URL(fileURLWithPath: out))
    print("\(results.count) samples written to \(out)")
}

// MARK: main

let argv = Array(CommandLine.arguments.dropFirst())
var options: [String: String] = [:]
var index = 1
while index < argv.count {
    if argv[index].hasPrefix("--"), index + 1 < argv.count {
        options[argv[index]] = argv[index + 1]
        index += 2
    } else {
        index += 1
    }
}
do {
    switch argv.first {
    case "quick": try await quick(options)
    case "run": try await run(options)
    default: throw Failure("usage: zz-qual quick|run ... (see the comment at the top of main.swift)")
    }
} catch {
    FileHandle.standardError.write(Data("zz-qual: \(error)\n".utf8))
    exit(1)
}
