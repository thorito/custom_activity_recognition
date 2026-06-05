// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "custom_activity_recognition",
    platforms: [
        .iOS("12.0")
    ],
    products: [
        .library(name: "custom_activity_recognition", targets: ["custom_activity_recognition"])
    ],
    dependencies: [],
    targets: [
        .target(
            name: "custom_activity_recognition",
            dependencies: [],
            path: "Classes",
            resources: [
                .process("Resources/PrivacyInfo.xcprivacy")
            ],
            linkerSettings: [
                .linkedFramework("Flutter", .when(platforms: [.iOS])),
                .linkedFramework("CoreMotion", .when(platforms: [.iOS]))
            ]
        )
    ]
)
