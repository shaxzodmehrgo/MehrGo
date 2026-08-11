package uz.teamwork.mehrgodriver.domain.model.paylov

data class PaylovCard(
    val cardId: String? = null,
    val number: String? = null,
    val balance: Long? = null, // TIYIN
    val bankName: String? = null,
    val vendor: String? = null
)
