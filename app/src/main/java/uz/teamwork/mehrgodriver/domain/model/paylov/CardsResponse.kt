package uz.teamwork.mehrgodriver.domain.model.paylov

// The cards endpoint returns either data.cards[] or a bare data[] array depending on
// the backend build. Model the object shape here; callers that get a bare list can fall
// back to reading the wrapper's data as the list directly.
data class CardsResponse(
    val cards: List<PaylovCard>? = null
)
