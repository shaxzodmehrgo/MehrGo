package uz.teamwork.mehrgodriver.domain.model

data class Instruction(
    val id: Int,
    val name: String,
    val position: String,
    val video: String,
    val type: Type
) {
    data class Type(
        val id: Int,
        val name: String
    )
}
