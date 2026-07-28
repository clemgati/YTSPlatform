import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // Must run before the first composition: `App()` injects its repositories.
        InitializeKoinKt.initializeKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
