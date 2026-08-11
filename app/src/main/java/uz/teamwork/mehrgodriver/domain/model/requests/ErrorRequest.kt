package uz.teamwork.mehrgodriver.domain.model.requests

data class ErrorRequest(
    val url: String,
    val token: String,
    val requestTime: String,
    val responseTime: String,
    val duration: Long,
    val socketStatus: Boolean,
    val responseCode: Int,
    val responseBody: String,
)