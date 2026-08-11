package uz.teamwork.mehrgodriver.domain.model

data class Video(
    val id: Int,
    val title: String,
    val url: String,
    val poster: String,
    val type: Int
)