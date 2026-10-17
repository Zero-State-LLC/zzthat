// swift-tools-version: 6.0
// zz-qual: the ZZ-OCR-QUAL-001 harness for the iOS reader (tasks.md T027,
// T023c). It runs the same ZZCore CapturePipeline and Apple Vision adapter
// the app ships over a folder of photos, on a Mac, offline. Not part of the
// app.
import PackageDescription

let package = Package(
    name: "ZZQual",
    platforms: [.macOS(.v14)],
    dependencies: [.package(path: "../../Packages/ZZCore")],
    targets: [
        .executableTarget(name: "zz-qual", dependencies: [.product(name: "ZZCore", package: "ZZCore")]),
    ]
)
