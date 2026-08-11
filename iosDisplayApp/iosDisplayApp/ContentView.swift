import UIKit
import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Self.Context) -> UIViewController {
        DisplayViewControllerKt.createDisplayViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            // Edge to edge, because this is a sign rather than an application somebody is
            // reading: the status bar is clutter on a table.
            .ignoresSafeArea()
            .statusBarHidden()
    }
}
