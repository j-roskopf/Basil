import SwiftUI
import ComposeApp

struct ContentView: View {
    @State private var pendingImportUrl: String?

    var body: some View {
        ComposeView(pendingImportUrl: $pendingImportUrl)
            .ignoresSafeArea()
            .onOpenURL { url in
                if let importUrl = Self.extractImportUrl(from: url) {
                    pendingImportUrl = importUrl
                }
            }
    }

    private static func extractImportUrl(from url: URL) -> String? {
        if url.scheme == "basil", url.host == "import" {
            return URLComponents(url: url, resolvingAgainstBaseURL: false)?
                .queryItems?
                .first(where: { $0.name == "url" })?
                .value
        }
        if url.scheme == "http" || url.scheme == "https" {
            return url.absoluteString
        }
        return nil
    }
}

struct ComposeView: UIViewControllerRepresentable {
    @Binding var pendingImportUrl: String?

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        if let url = pendingImportUrl {
            ShareIntentIosKt.setPendingShareUrl(url: url)
            pendingImportUrl = nil
        }
    }
}
