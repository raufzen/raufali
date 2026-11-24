package com.example.service.accessibility

import com.example.domain.model.CommandResult
import com.example.domain.model.PermissionType
import com.example.domain.model.ScreenTree
import com.example.domain.parser.GlobalNavAction
import com.example.domain.parser.ScrollDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Contract implemented by [VoiceControlAccessibilityService] and consumed by Domain/Agent layers.
 */
interface AccessibilityController {
    fun captureScreenTree(): ScreenTree
    suspend fun performGlobal(action: GlobalNavAction): CommandResult
    suspend fun clickNode(target: String, by: String = "auto"): CommandResult
    suspend fun longClickNode(target: String): CommandResult
    suspend fun scrollDirection(direction: ScrollDirection): CommandResult
    suspend fun swipeDirection(direction: ScrollDirection, distanceRatio: Float = 0.55f): CommandResult
    suspend fun setText(text: String, target: String? = null): CommandResult
    suspend fun focusNode(target: String): CommandResult
    suspend fun tapAt(x: Float, y: Float): CommandResult
    suspend fun pinch(zoomIn: Boolean): CommandResult
}

/**
 * Process-wide reactive bridge between [VoiceControlAccessibilityService] and the rest of the app.
 */
object AccessibilityBridge {

    @Volatile
    private var activeController: AccessibilityController? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _currentPackage = MutableStateFlow("")
    val currentPackage: StateFlow<String> = _currentPackage.asStateFlow()

    fun attach(controller: AccessibilityController) {
        activeController = controller
        _isConnected.value = true
    }

    fun detach(controller: AccessibilityController) {
        if (activeController === controller) {
            activeController = null
            _isConnected.value = false
        }
    }

    fun updateForegroundPackage(packageName: String) {
        if (packageName.isNotBlank()) {
            _currentPackage.value = packageName
        }
    }

    fun controllerOrNull(): AccessibilityController? = activeController

    fun captureScreenTree(): ScreenTree? = activeController?.captureScreenTree()

    fun missingServiceResult(): CommandResult.NeedsPermission = CommandResult.NeedsPermission(
        permissionType = PermissionType.ACCESSIBILITY,
        messageEn = "Accessibility Service is turned off. Please enable Voice Control AI in Accessibility Settings.",
        messageHi = "एक्सेसिबिलिटी सेवा बंद है। कृपया सेटिंग्स में Voice Control AI एक्सेसिबिलिटी चालू करें।"
    )
}
