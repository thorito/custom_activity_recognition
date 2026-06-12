// swift-tools-version: 5.9
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "custom_activity_recognition",
    platforms: [
        .iOS("12.0")
    ],
    products: [
        .library(name: "custom-activity-recognition", targets: ["custom_activity_recognition"])
    ],
    dependencies: [
        .package(name: "FlutterFramework", path: "../FlutterFramework")
    ],
    targets: [
        .target(
            name: "custom_activity_recognition",
            dependencies: [
                .product(name: "FlutterFramework", package: "FlutterFramework")
            ],
            resources: [
                .process("Resources/PrivacyInfo.xcprivacy")
            ],
            linkerSettings: [
                .linkedFramework("CoreMotion", .when(platforms: [.iOS]))
            ]
        )
    ]
)
