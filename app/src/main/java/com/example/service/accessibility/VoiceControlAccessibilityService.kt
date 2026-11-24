package com.example.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.domain.model.CommandResult
import com.example.domain.model.ScreenTree
import com.example.domain.model.UiNodeSnapshot
import com.example.domain.parser.GlobalNavAction
import com.example.domain.parser.ScrollDirection
import com.example.domain.safety.ScreenTreeSanitizer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Full-featured AccessibilityService for hands-free phone control:
 * - Reads privacy-sanitized live UI trees (password fields skipped, sensitive strings masked)
 * - Finds nodes by index (#N), text, contentDescription, or viewId
 * - Executes click, long-click, scroll, setText (with clipboard fallback), and focus
 * - Falls back to GestureDescription tap, long-press, swipe, scroll, and pinch
 * - Supports all Android Global Actions (Back, Home, Recents, Notifications, Quick Settings, Power, Lock, Screenshot)
 */
class VoiceControlAccessibilityService : AccessibilityService(), AccessibilityController {

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityBridge.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()
        if (!pkg.isNullOrBlank()) {
            AccessibilityBridge.updateForegroundPackage(pkg)
        }
    }

    override fun onInterrupt() {
        // Handled gracefully without crashing or leaking screen content
    }

    override fun onDestroy() {
        AccessibilityBridge.detach(this)
        super.onDestroy()
    }

    override fun captureScreenTree(): ScreenTree {
        val root = rootInActiveWindow ?: return ScreenTree(
            packageName = AccessibilityBridge.currentPackage.value.ifBlank { "unknown" }
        )
        val nodes = mutableListOf<UiNodeSnapshot>()
        val counter = AtomicInteger(1)
        val pkg = root.packageName?.toString().orEmpty()
        var windowTitle = ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            windowTitle = root.window?.title?.toString().orEmpty()
        }

        traverseNode(root, nodes, counter, depth = 0)
        return ScreenTree(
            packageName = pkg.ifBlank { "unknown" },
            windowTitle = ScreenTreeSanitizer.sanitizeText(windowTitle),
            nodes = nodes
        )
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo,
        out: MutableList<UiNodeSnapshot>,
        counter: AtomicInteger,
        depth: Int
    ) {
        if (depth > MAX_TREE_DEPTH || out.size >= MAX_NODES) return
        if (!node.isVisibleToUser) return

        val viewId = node.viewIdResourceName?.substringAfterLast('/').orEmpty()
        val rawDesc = node.contentDescription?.toString().orEmpty()

        // Privacy rule: completely skip password fields and sensitive inputs
        if (ScreenTreeSanitizer.isSensitiveNode(node.isPassword, viewId, rawDesc)) {
            return
        }

        val rawText = node.text?.toString().orEmpty()
        val sanitizedText = ScreenTreeSanitizer.sanitizeText(rawText)
        val sanitizedDesc = ScreenTreeSanitizer.sanitizeText(rawDesc)
        val isInteractive = node.isClickable || node.isEditable || node.isScrollable ||
            node.isCheckable || node.isLongClickable || node.isFocused

        if (sanitizedText.isNotBlank() || sanitizedDesc.isNotBlank() || isInteractive) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                out.add(
                    UiNodeSnapshot(
                        index = counter.getAndIncrement(),
                        text = sanitizedText,
                        contentDescription = sanitizedDesc,
                        viewId = viewId,
                        className = node.className?.toString().orEmpty(),
                        clickable = node.isClickable,
                        editable = node.isEditable,
                        scrollable = node.isScrollable,
                        focused = node.isFocused,
                        checked = node.isChecked,
                        left = rect.left,
                        top = rect.top,
                        right = rect.right,
                        bottom = rect.bottom
                    )
                )
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNode(child, out, counter, depth + 1)
        }
    }

    override suspend fun performGlobal(action: GlobalNavAction): CommandResult {
        val androidAction = when (action) {
            GlobalNavAction.BACK -> GLOBAL_ACTION_BACK
            GlobalNavAction.HOME -> GLOBAL_ACTION_HOME
            GlobalNavAction.RECENTS -> GLOBAL_ACTION_RECENTS
            GlobalNavAction.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
            GlobalNavAction.QUICK_SETTINGS -> GLOBAL_ACTION_QUICK_SETTINGS
            GlobalNavAction.POWER_DIALOG -> GLOBAL_ACTION_POWER_DIALOG
            GlobalNavAction.LOCK_SCREEN -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                GLOBAL_ACTION_LOCK_SCREEN
            } else {
                return CommandResult.Failed(
                    reasonEn = "Lock screen requires Android 9 or higher.",
                    reasonHi = "स्क्रीन लॉक के लिए Android 9 या नया वर्ज़न चाहिए।"
                )
            }
            GlobalNavAction.SCREENSHOT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                GLOBAL_ACTION_TAKE_SCREENSHOT
            } else {
                return CommandResult.Failed(
                    reasonEn = "Screenshot action requires Android 9 or higher.",
                    reasonHi = "स्क्रीनशॉट के लिए Android 9 या नया वर्ज़न चाहिए।"
                )
            }
        }

        val ok = performGlobalAction(androidAction)
        return if (ok) {
            val labelEn = action.name.lowercase().replace('_', ' ')
            CommandResult.Success(
                messageEn = "Done: $labelEn.",
                messageHi = "हो गया: $labelEn।"
            )
        } else {
            CommandResult.Failed(
                reasonEn = "Could not perform ${action.name.lowercase().replace('_', ' ')}.",
                reasonHi = "यह एक्शन पूरा नहीं हो सका।"
            )
        }
    }

    override suspend fun clickNode(target: String, by: String): CommandResult {
        val root = rootInActiveWindow ?: return noWindowError()
        val match = findBestNode(root, target, by)
            ?: return CommandResult.Failed(
                reasonEn = "Could not find \"$target\" on screen.",
                reasonHi = "स्क्रीन पर \"$target\" नहीं मिला।"
            )

        // Try direct node click (walking up to clickable parent if needed)
        val clickable = findClickableAncestor(match)
        if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return CommandResult.Success(
                messageEn = "Clicked \"$target\".",
                messageHi = "\"$target\" पर क्लिक किया गया।"
            )
        }

        // Gesture fallback on the node's bounding box center
        val rect = Rect()
        match.getBoundsInScreen(rect)
        if (!rect.isEmpty) {
            val tapped = dispatchTapGesture(rect.exactCenterX(), rect.exactCenterY(), 90L)
            if (tapped) {
                return CommandResult.Success(
                    messageEn = "Tapped \"$target\".",
                    messageHi = "\"$target\" पर टैप किया गया।"
                )
            }
        }

        return CommandResult.Failed(
            reasonEn = "Found \"$target\" but could not click it.",
            reasonHi = "\"$target\" मिला, लेकिन उस पर क्लिक नहीं हो सका।"
        )
    }

    override suspend fun longClickNode(target: String): CommandResult {
        val root = rootInActiveWindow ?: return noWindowError()
        val match = findBestNode(root, target, "auto")
            ?: return CommandResult.Failed(
                reasonEn = "Could not find \"$target\" to long-press.",
                reasonHi = "लॉन्ग-प्रेस के लिए \"$target\" नहीं मिला।"
            )

        if (match.isLongClickable && match.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
            return CommandResult.Success(
                messageEn = "Long-pressed \"$target\".",
                messageHi = "\"$target\" को दबाकर रखा गया।"
            )
        }

        val rect = Rect()
        match.getBoundsInScreen(rect)
        if (!rect.isEmpty && dispatchTapGesture(rect.exactCenterX(), rect.exactCenterY(), 650L)) {
            return CommandResult.Success(
                messageEn = "Long-pressed \"$target\".",
                messageHi = "\"$target\" को दबाकर रखा गया।"
            )
        }

        return CommandResult.Failed(
            reasonEn = "Could not long-press \"$target\".",
            reasonHi = "\"$target\" पर लॉन्ग-प्रेस नहीं हो सका।"
        )
    }

    override suspend fun scrollDirection(direction: ScrollDirection): CommandResult {
        val root = rootInActiveWindow ?: return noWindowError()
        val scrollable = findFirstScrollable(root)
        val nodeAction = when (direction) {
            ScrollDirection.DOWN, ScrollDirection.RIGHT -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            ScrollDirection.UP, ScrollDirection.LEFT -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        if (scrollable != null && scrollable.performAction(nodeAction)) {
            return CommandResult.Success(
                messageEn = "Scrolled ${direction.name.lowercase()}.",
                messageHi = "${directionHindi(direction)} स्क्रॉल किया गया।"
            )
        }
        // Fallback to smooth swipe gesture
        return swipeDirection(direction, 0.55f)
    }

    override suspend fun swipeDirection(direction: ScrollDirection, distanceRatio: Float): CommandResult {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val ratio = distanceRatio.coerceIn(0.2f, 0.8f)

        val (startX, startY, endX, endY) = when (direction) {
            ScrollDirection.DOWN -> listOf(w * 0.5f, h * (0.5f + ratio / 2f), w * 0.5f, h * (0.5f - ratio / 2f))
            ScrollDirection.UP -> listOf(w * 0.5f, h * (0.5f - ratio / 2f), w * 0.5f, h * (0.5f + ratio / 2f))
            ScrollDirection.LEFT -> listOf(w * (0.5f + ratio / 2f), h * 0.5f, w * (0.5f - ratio / 2f), h * 0.5f)
            ScrollDirection.RIGHT -> listOf(w * (0.5f - ratio / 2f), h * 0.5f, w * (0.5f + ratio / 2f), h * 0.5f)
        }

        val ok = dispatchSwipeGesture(startX, startY, endX, endY, 320L)
        return if (ok) {
            CommandResult.Success(
                messageEn = "Swiped ${direction.name.lowercase()}.",
                messageHi = "${directionHindi(direction)} स्वाइप किया गया।"
            )
        } else {
            CommandResult.Failed(
                reasonEn = "Swipe gesture failed.",
                reasonHi = "स्वाइप नहीं हो सका।"
            )
        }
    }

    override suspend fun setText(text: String, target: String?): CommandResult {
        val root = rootInActiveWindow ?: return noWindowError()
        val node = when {
            !target.isNullOrBlank() -> findBestNode(root, target, "auto")
            else -> root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: findFirstEditable(root)
        } ?: return CommandResult.Failed(
            reasonEn = "No text input field is focused on screen.",
            reasonHi = "स्क्रीन पर कोई टेक्स्ट बॉक्स फोकस में नहीं है।"
        )

        if (node.isPassword) {
            return CommandResult.Failed(
                reasonEn = "For security, Voice Control AI does not type into password fields automatically.",
                reasonHi = "सुरक्षा कारणों से पासवर्ड फ़ील्ड में अपने आप टाइप नहीं किया जाता।"
            )
        }

        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            return CommandResult.Success(
                messageEn = "Typed \"$text\".",
                messageHi = "\"$text\" टाइप किया गया।"
            )
        }

        // Fallback via clipboard paste
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("voice_input", text))
        if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
            return CommandResult.Success(
                messageEn = "Entered \"$text\".",
                messageHi = "\"$text\" लिखा गया।"
            )
        }

        return CommandResult.Failed(
            reasonEn = "Could not enter text into that field.",
            reasonHi = "इस फ़ील्ड में टेक्स्ट नहीं लिखा जा सका।"
        )
    }

    override suspend fun focusNode(target: String): CommandResult {
        val root = rootInActiveWindow ?: return noWindowError()
        val node = findBestNode(root, target, "auto") ?: return CommandResult.Failed(
            reasonEn = "Could not find \"$target\" to focus.",
            reasonHi = "\"$target\" फोकस करने के लिए नहीं मिला।"
        )
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ||
            node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        return if (ok) {
            CommandResult.Success("Focused \"$target\".", "\"$target\" पर फोकस किया गया।")
        } else {
            CommandResult.Failed("Could not focus \"$target\".", "\"$target\" पर फोकस नहीं हो सका।")
        }
    }

    override suspend fun tapAt(x: Float, y: Float): CommandResult {
        val ok = dispatchTapGesture(x, y, 80L)
        return if (ok) {
            CommandResult.Success("Tapped screen.", "स्क्रीन पर टैप किया गया।")
        } else {
            CommandResult.Failed("Tap gesture failed.", "टैप जेस्चर विफल रहा।")
        }
    }

    override suspend fun pinch(zoomIn: Boolean): CommandResult {
        val dm = resources.displayMetrics
        val cx = dm.widthPixels * 0.5f
        val cy = dm.heightPixels * 0.5f
        val offsetNear = dm.widthPixels * 0.08f
        val offsetFar = dm.widthPixels * 0.28f

        val p1 = Path().apply {
            if (zoomIn) {
                moveTo(cx - offsetNear, cy)
                lineTo(cx - offsetFar, cy)
            } else {
                moveTo(cx - offsetFar, cy)
                lineTo(cx - offsetNear, cy)
            }
        }
        val p2 = Path().apply {
            if (zoomIn) {
                moveTo(cx + offsetNear, cy)
                lineTo(cx + offsetFar, cy)
            } else {
                moveTo(cx + offsetFar, cy)
                lineTo(cx + offsetNear, cy)
            }
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p1, 0, 350))
            .addStroke(GestureDescription.StrokeDescription(p2, 0, 350))
            .build()
        val ok = dispatchGestureSuspend(gesture)
        return if (ok) {
            CommandResult.Success(
                if (zoomIn) "Zoomed in." else "Zoomed out.",
                if (zoomIn) "ज़ूम इन किया गया।" else "ज़ूम आउट किया गया।"
            )
        } else {
            CommandResult.Failed("Pinch gesture failed.", "पिंच जेस्चर विफल रहा।")
        }
    }

    private fun findBestNode(
        root: AccessibilityNodeInfo,
        target: String,
        by: String
    ): AccessibilityNodeInfo? {
        val trimmed = target.trim()
        if (trimmed.isEmpty()) return null

        // Support index reference like "#4" or "4" matching captureScreenTree()
        val indexNum = trimmed.removePrefix("#").toIntOrNull()
        if (indexNum != null && (by == "index" || trimmed.startsWith("#"))) {
            return findNodeByTraversalIndex(root, indexNum)
        }

        // Exact viewId lookup
        if (by == "id" || trimmed.contains(":id/")) {
            val byId = root.findAccessibilityNodeInfosByViewId(trimmed).firstOrNull()
            if (byId != null) return byId
        }

        // Walk visible tree scoring exact > startsWith > contains across text, contentDescription, viewId
        var bestNode: AccessibilityNodeInfo? = null
        var bestScore = 0
        val lowerTarget = trimmed.lowercase()

        fun scan(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > MAX_TREE_DEPTH || !node.isVisibleToUser || node.isPassword) return
            val txt = node.text?.toString()?.trim()?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.trim()?.lowercase().orEmpty()
            val vid = node.viewIdResourceName?.substringAfterLast('/')?.lowercase().orEmpty()

            val score = when {
                txt == lowerTarget || desc == lowerTarget -> 100
                vid == lowerTarget -> 95
                txt.startsWith(lowerTarget) || desc.startsWith(lowerTarget) -> 85
                txt.contains(lowerTarget) || desc.contains(lowerTarget) -> 75
                vid.contains(lowerTarget) && lowerTarget.length >= 3 -> 65
                else -> 0
            } + if (node.isClickable) 5 else 0

            if (score > bestScore) {
                bestScore = score
                bestNode = node
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                scan(child, depth + 1)
            }
        }
        scan(root, 0)
        return bestNode
    }

    private fun findNodeByTraversalIndex(root: AccessibilityNodeInfo, targetIndex: Int): AccessibilityNodeInfo? {
        val counter = AtomicInteger(1)
        var result: AccessibilityNodeInfo? = null

        fun scan(node: AccessibilityNodeInfo, depth: Int) {
            if (result != null || depth > MAX_TREE_DEPTH || !node.isVisibleToUser) return
            val viewId = node.viewIdResourceName?.substringAfterLast('/').orEmpty()
            val rawDesc = node.contentDescription?.toString().orEmpty()
            if (ScreenTreeSanitizer.isSensitiveNode(node.isPassword, viewId, rawDesc)) return

            val rawText = node.text?.toString().orEmpty()
            val isInteractive = node.isClickable || node.isEditable || node.isScrollable ||
                node.isCheckable || node.isLongClickable || node.isFocused
            if (rawText.isNotBlank() || rawDesc.isNotBlank() || isInteractive) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0) {
                    val idx = counter.getAndIncrement()
                    if (idx == targetIndex) {
                        result = node
                        return
                    }
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                scan(child, depth + 1)
            }
        }
        scan(root, 0)
        return result
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var hops = 0
        while (current != null && hops < 5) {
            if (current.isClickable) return current
            current = current.parent
            hops++
        }
        return null
    }

    private fun findFirstScrollable(node: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > MAX_TREE_DEPTH || !node.isVisibleToUser) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findFirstScrollable(child, depth + 1)?.let { return it }
        }
        return null
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > MAX_TREE_DEPTH || !node.isVisibleToUser || node.isPassword) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findFirstEditable(child, depth + 1)?.let { return it }
        }
        return null
    }

    private suspend fun dispatchTapGesture(x: Float, y: Float, durationMs: Long): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGestureSuspend(gesture)
    }

    private suspend fun dispatchSwipeGesture(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMs: Long
    ): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGestureSuspend(gesture)
    }

    private suspend fun dispatchGestureSuspend(gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            val dispatched = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
                null
            )
            if (!dispatched && cont.isActive) {
                cont.resume(false)
            }
        }

    private fun noWindowError() = CommandResult.Failed(
        reasonEn = "Cannot read the current screen right now.",
        reasonHi = "अभी वर्तमान स्क्रीन पढ़ी नहीं जा सकी।"
    )

    private fun directionHindi(direction: ScrollDirection): String = when (direction) {
        ScrollDirection.UP -> "ऊपर"
        ScrollDirection.DOWN -> "नीचे"
        ScrollDirection.LEFT -> "बाएं"
        ScrollDirection.RIGHT -> "दाएं"
    }

    companion object {
        private const val MAX_TREE_DEPTH = 18
        private const val MAX_NODES = 70
    }
}
