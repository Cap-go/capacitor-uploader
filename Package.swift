// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CapgoCapacitorUploader",
    platforms: [.iOS(.v14)],
    products: [
        .library(
            name: "CapgoCapacitorUploader",
            targets: ["UploaderPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "7.2.0")
    ],
    targets: [
        .target(
            name: "UploaderPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/UploaderPlugin"),
        .testTarget(
            name: "UploaderPluginTests",
            dependencies: ["UploaderPlugin"],
            path: "ios/Tests/UploaderPluginTests")
    ]
)
