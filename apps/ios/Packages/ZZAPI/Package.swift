// swift-tools-version: 6.0
// ZZAPI: the client generated from the pinned contract (runtime.md, API
// client), the request middleware, the token store, and the demo
// implementation of the generated APIProtocol. No hand-written request types.
import PackageDescription

let package = Package(
    name: "ZZAPI",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "ZZAPI", targets: ["ZZAPI"]),
    ],
    dependencies: [
        .package(url: "https://github.com/apple/swift-openapi-generator", exact: "1.13.1"),
        .package(url: "https://github.com/apple/swift-openapi-runtime", exact: "1.12.2"),
        .package(url: "https://github.com/apple/swift-openapi-urlsession", exact: "1.3.2"),
        .package(url: "https://github.com/apple/swift-http-types", exact: "1.8.0"),
    ],
    targets: [
        .target(
            name: "ZZAPI",
            dependencies: [
                .product(name: "OpenAPIRuntime", package: "swift-openapi-runtime"),
                .product(name: "OpenAPIURLSession", package: "swift-openapi-urlsession"),
                .product(name: "HTTPTypes", package: "swift-http-types"),
            ],
            plugins: [
                .plugin(name: "OpenAPIGenerator", package: "swift-openapi-generator"),
            ]
        ),
        .testTarget(
            name: "ZZAPITests",
            dependencies: [
                "ZZAPI",
                .product(name: "OpenAPIRuntime", package: "swift-openapi-runtime"),
                .product(name: "HTTPTypes", package: "swift-http-types"),
            ],
            resources: [.copy("Fixtures")]
        ),
    ]
)
