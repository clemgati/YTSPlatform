import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // Must run before the first composition: the display injects its repositories.
        DisplayViewControllerKt.initializeDisplayKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
