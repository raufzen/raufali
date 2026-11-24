package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Minimal, privacy-sanitized UI tree representation sent to Gemini ONLY during an active task.
 * Never persisted to Room or logs.
 */
@Serializable
data class ScreenTree(
    val packageName: String,
    val windowTitle: String = "",
    val timestampMs: Long = System.currentTimeMillis(),
    val nodes: List<UiNodeSnapshot> = emptyList()
) {
    /**
     * Compact text representation optimized for Gemini token efficiency.
     */
    fun toCompactPromptText(): String {
        if (nodes.isEmpty()) {
            return "App: $packageName (No readable UI elements on screen)"
        }
        return buildString {
            appendLine("Active App: $packageName | Window: ${windowTitle.ifBlank { "Main" }}")
            appendLine("Elements (${nodes.size}):")
            nodes.forEach { n ->
                val flags = buildList {
                    if (n.clickable) add("click")
                    if (n.editable) add("edit")
                    if (n.scrollable) add("scroll")
                    if (n.focused) add("focused")
                    if (n.checked) add("checked")
                }.joinToString(",")
                val label = listOfNotNull(
                    n.text.takeIf { it.isNotBlank() }?.let { "txt=\"$it\"" },
                    n.contentDescription.takeIf { it.isNotBlank() }?.let { "desc=\"$it\"" },
                    n.viewId.takeIf { it.isNotBlank() }?.let { "id=\"$it\"" }
                ).joinToString(" ")
                appendLine("[#${n.index}] ${n.shortClass} $label ($flags) bounds=[${n.left},${n.top},${n.right},${n.bottom}]")
            }
        }
    }

    /** Signature used for loop detection in the AI agent. */
    fun structuralSignature(): String {
        val sample = nodes.take(15).joinToString("|") { "${it.viewId}:${it.text.take(18)}:${it.clickable}" }
        return "$packageName#$windowTitle#${nodes.size}#$sample"
    }
}

@Serializable
data class UiNodeSnapshot(
    val index: Int,
    val text: String = "",
    val contentDescription: String = "",
    val viewId: String = "",
    val className: String = "",
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val focused: Boolean = false,
    val checked: Boolean = false,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
) {
    val shortClass: String
        get() = className.substringAfterLast('.').ifBlank { "View" }

    val centerX: Float
        get() = (left + right) / 2f

    val centerY: Float
        get() = (top + bottom) / 2f
}
