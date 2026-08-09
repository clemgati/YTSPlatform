package com.yellowtrack.platform.core.export

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

/**
 * Writes documents to the app's Documents directory.
 *
 * Reachable from the Files app when the app declares `UIFileSharingEnabled`, and offered
 * onward through the system share sheet.
 */
class IosDocumentSink : DocumentSink {
    override val canShare: Boolean = true

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun save(document: Document): SavedDocument {
        val directory =
            NSSearchPathForDirectoriesInDomains(
                directory = NSDocumentDirectory,
                domainMask = NSUserDomainMask,
                expandTilde = true,
            ).first() as String

        // Created if absent, which the Android sink has always done and this did not. The
        // Documents directory exists in a normal application container, so this changed
        // nothing on a device — and it is why the first run of `IosDocumentSinkTest` passed
        // on a simulator that had run the app before and failed on a fresh one in CI.
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )

        val path = "$directory/${document.fileName}"

        // The error is read rather than discarded, and a failure throws.
        //
        // `writeToURL` returns false and fills in an error when it cannot write; this passed
        // `error = null`, ignored the result, and returned a `SavedDocument` naming a file
        // that did not exist. A studio would have been told where its call sheet was saved
        // and found nothing there. Android's `writeText` throws in the same situation, so
        // throwing here makes the platforms agree — the view models already report a failed
        // save on screen.
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val written =
                (document.content as NSString).writeToURL(
                    url = NSURL.fileURLWithPath(path),
                    atomically = true,
                    encoding = NSUTF8StringEncoding,
                    error = error.ptr,
                )

            check(written) {
                "could not write ${document.fileName}: ${error.value?.localizedDescription ?: "no reason given"}"
            }
        }

        return SavedDocument(fileName = document.fileName, location = path)
    }

    /**
     * Writes the file, then presents the system share sheet on top of whatever is showing.
     *
     * The window hierarchy is reached rather than injected, so nothing has to be threaded
     * from the Swift side into dependency injection. Everything after the write is wrapped:
     * a window that has gone away, or a controller already presenting something, must not
     * cost the studio the document it asked for.
     */
    @OptIn(ExperimentalForeignApi::class)
    override suspend fun share(document: Document): SavedDocument {
        val saved = save(document)

        runCatching {
            val url = NSURL.fileURLWithPath(saved.location)
            val sheet =
                UIActivityViewController(
                    activityItems = listOf(url),
                    applicationActivities = null,
                )

            // Presenting from the topmost controller rather than the root: presenting on a
            // controller that is itself presenting something is silently ignored.
            var presenter = presentingWindow()?.rootViewController
            while (presenter?.presentedViewController != null) {
                presenter = presenter.presentedViewController
            }

            // Required on iPad, and a crash rather than a refusal without it: UIKit raises
            // NSInvalidArgumentException when a UIActivityViewController is presented with
            // no popover source. That is an Objective-C exception, so the `runCatching`
            // around this would not catch it — the application would go away. On iPhone the
            // sheet ignores this entirely.
            presenter?.view?.let { anchor ->
                sheet.popoverPresentationController?.sourceView = anchor
            }

            presenter?.presentViewController(sheet, animated = true, completion = null)
        }

        return saved
    }

    /**
     * The window to present from, found through the scene rather than the application.
     *
     * `UIApplication.keyWindow` has been deprecated since iOS 13 and is documented as
     * unreliable once an application adopts scenes — which this one does: `iOSApp.swift` is
     * a SwiftUI `App` with a `WindowGroup`. When it returns nil the share sheet is not
     * presented and **nothing is reported**, because there is no error to catch: the code
     * simply finds no presenter and returns the saved file. That is the quietest possible
     * failure and the reason this walks the scenes instead.
     *
     * Falls back to the deprecated property rather than giving up, so a state this does not
     * anticipate is no worse than it was before.
     */
    @Suppress("DEPRECATION")
    private fun presentingWindow(): UIWindow? {
        val scenes = UIApplication.sharedApplication.connectedScenes
        val active =
            scenes
                .filterIsInstance<UIWindowScene>()
                .firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
                ?: scenes.filterIsInstance<UIWindowScene>().firstOrNull()

        val windows = active?.windows.orEmpty().filterIsInstance<UIWindow>()

        return windows.firstOrNull { it.isKeyWindow() }
            ?: windows.firstOrNull()
            ?: UIApplication.sharedApplication.keyWindow
    }
}
