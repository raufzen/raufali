package com.example.domain.model

enum class SensitiveCategory(
    val titleEn: String,
    val titleHi: String
) {
    PAYMENT("Payment / Money Transfer", "भुगतान / पैसे ट्रांसफर"),
    UNINSTALL("Uninstall App", "ऐप अनइंस्टॉल"),
    FACTORY_RESET("Factory Reset", "फ़ैक्टरी रीसेट"),
    DELETE_DATA("Delete Data / Content", "डेटा हटाना"),
    SEND_MESSAGE("Send Message / SMS", "मैसेज / SMS भेजना"),
    PASSWORD_SECURITY("Password / Security Change", "पासवर्ड / सुरक्षा बदलाव")
}

data class ConfirmationRequest(
    val id: String,
    val category: SensitiveCategory,
    val actionSummary: String,
    val promptEn: String,
    val promptHi: String
)

enum class StepOutcome {
    RUNNING,
    SUCCESS,
    FAILED,
    SKIPPED,
    WAITING_CONFIRMATION
}

data class AgentStepLog(
    val stepNumber: Int,
    val toolName: String,
    val actionSummary: String,
    val detail: String,
    val outcome: StepOutcome,
    val timestampMs: Long = System.currentTimeMillis()
)

enum class AgentTaskStatus {
    IDLE,
    RUNNING,
    WAITING_CONFIRMATION,
    WAITING_USER_ANSWER,
    COMPLETED,
    STOPPED,
    FAILED
}

data class AgentTaskState(
    val taskId: String = "",
    val userCommand: String = "",
    val status: AgentTaskStatus = AgentTaskStatus.IDLE,
    val currentStep: Int = 0,
    val maxSteps: Int = 20,
    val streamingThought: String = "",
    val activeActionLabel: String = "",
    val finalMessage: String = "",
    val pendingConfirmation: ConfirmationRequest? = null,
    val pendingQuestion: String? = null,
    val steps: List<AgentStepLog> = emptyList()
) {
    val isActive: Boolean
        get() = status == AgentTaskStatus.RUNNING ||
            status == AgentTaskStatus.WAITING_CONFIRMATION ||
            status == AgentTaskStatus.WAITING_USER_ANSWER
}
