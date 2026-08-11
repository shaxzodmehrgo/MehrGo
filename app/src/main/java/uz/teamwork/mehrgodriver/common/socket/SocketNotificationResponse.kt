package uz.teamwork.mehrgodriver.common.socket

data class SocketNotificationResponse(
    val status: Int,
    val key: String,
    val data: Data
) {
    data class Data(
        val id: Int,
        val message: String,
        val text: String
    )
}
