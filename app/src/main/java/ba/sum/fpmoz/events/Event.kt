package ba.sum.fpmoz.events

import java.io.Serializable

data class Event(
    val id: String,
    val name: String,
    val date: String,
    val typeId: String,
    val typeName: String,
    val locationId: String,
    val locationName: String,
    val description: String
) : Serializable