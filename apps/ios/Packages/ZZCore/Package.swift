// swift-tools-version: 6.0
// ZZCore: the zzThis Section 2.2a grammar, the spec 003 classifier, check word,
// and list loader, the G10 matching key, the spec 004 scanner and bands, and
// the on-device read pipeline (runtime.md, Modules and Read pipeline).
import PackageDescription

let package = Package(
    name: "ZZCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "ZZCore", targets: ["ZZCore"]),
    ],
    targets: [
        .target(name: "ZZCore"),
        .testTarget(
            name: "ZZCoreTests",
            dependencies: ["ZZCore"],
            resources: [.copy("Fixtures")]
        ),
    ]
)
