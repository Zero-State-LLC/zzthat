// swift-tools-version: 6.0
// ZZDesign: the pinned Tokens.swift from zzThis design/generated (copied by
// scripts/sync-zzthis.sh, never edited here) and font registration
// (runtime.md, Modules; design.md, Sync).
import PackageDescription

let package = Package(
    name: "ZZDesign",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "ZZDesign", targets: ["ZZDesign"]),
    ],
    targets: [
        .target(name: "ZZDesign"),
        .testTarget(name: "ZZDesignTests", dependencies: ["ZZDesign"]),
    ]
)
