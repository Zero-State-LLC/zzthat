import AVFoundation
import SwiftUI
import UIKit

/// The system camera for one still photo (runtime.md, Read pipeline step 1).
/// There is no live video reading in v1.
struct CameraPicker: UIViewControllerRepresentable {
    let onFinish: (UIImage?) -> Void

    static var isAvailable: Bool { UIImagePickerController.isSourceTypeAvailable(.camera) }

    /// FR-011: the permission is asked when the person starts a scan, with
    /// the `scan.camera_purpose` string as NSCameraUsageDescription.
    static func requestAccess() async -> Bool {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: true
        case .notDetermined: await AVCaptureDevice.requestAccess(for: .video)
        default: false
        }
    }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.cameraCaptureMode = .photo
        picker.allowsEditing = false
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ controller: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onFinish: onFinish) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let onFinish: (UIImage?) -> Void

        init(onFinish: @escaping (UIImage?) -> Void) {
            self.onFinish = onFinish
        }

        func imagePickerController(_ picker: UIImagePickerController,
                                   didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            onFinish(info[.originalImage] as? UIImage)
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            onFinish(nil)
        }
    }
}

/// The fixture photo a Demo build reads instead of the camera (runtime.md,
/// Screens: Scan and Write check, Demo). It is drawn on the device, so it
/// holds only the code, never a sentence.
enum DemoPhoto {
    static let canonical = "zz-copper-lantern-sky-zz"

    /// The photo and, for snapshots, the lines a reader returns for it.
    static func make(lines: [String]) -> (image: UIImage, lines: [(text: String, box: CGRect)]) {
        let size = CGSize(width: 1200, height: 260 + 200 * lines.count)
        let font = UIFont(name: "MarkerFelt-Wide", size: 104) ?? .systemFont(ofSize: 104, weight: .bold)
        let attributes: [NSAttributedString.Key: Any] = [
            .font: font,
            .foregroundColor: UIColor(red: 0.11, green: 0.106, blue: 0.098, alpha: 1),
        ]
        var placed: [(String, CGRect)] = []
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let image = UIGraphicsImageRenderer(size: size, format: format).image { context in
            UIColor(red: 0.957, green: 0.937, blue: 0.894, alpha: 1).setFill()
            context.fill(CGRect(origin: .zero, size: size))
            for (index, line) in lines.enumerated() {
                let text = NSAttributedString(string: line, attributes: attributes)
                let measured = text.size()
                let origin = CGPoint(x: (size.width - measured.width) / 2, y: 130 + CGFloat(index) * 200)
                text.draw(at: origin)
                let box = CGRect(x: origin.x / size.width, y: origin.y / size.height,
                                 width: measured.width / size.width, height: measured.height / size.height)
                placed.append((line, box))
            }
        }
        return (image, placed)
    }

    static func jpeg(lines: [String]) -> Data? {
        make(lines: lines).image.jpegData(compressionQuality: 0.9)
    }
}
