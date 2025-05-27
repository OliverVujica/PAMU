package ba.sum.fpmoz.events

data class Event(
    val id: String,
    val name: String,
    val date: String,
    val typeId: String, // Reference to event_types document ID
    val typeName: String, // Display name for UI
    val locationId: String, // Reference to locations document ID
    val locationName: String // Display name for UI
)